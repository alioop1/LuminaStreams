/**
 * LuminaStreams — Cloud Sync Manager
 * Keeps watchlist and watch progress in sync with Firestore using the exact
 * schema the Android TV app writes (data/sync/SyncManager.kt), so one account
 * shares data across Android, iOS and Web.
 *
 * users/{uid}:
 *   favorites: string[]  — each a JSON Movie {id:"movie_123"|"tv_123", title, backdropUrl,
 *                          posterUrl, overview, rating, year, genre, mediaType}; replaced whole
 *   progress:  {key: string} — JSON {p, d, t[, tmdb]} in milliseconds, "" = deleted
 *   _exists:   true
 */
(() => {
  'use strict';

  /* ── State ─────────────────────────────────────────────────────── */
  let realtimeUnsubscribe = null;
  let syncedUid = null;            // uid whose data has been reconciled this session
  const dirtyProgress = new Map(); // key → cloud string ("" = tombstone)
  let progressTimer   = null;
  const PROGRESS_PUSH_DELAY = 5000;
  const LAST_UID_KEY = 'lumina_sync_uid';

  /* ── Helpers ───────────────────────────────────────────────────── */

  function _getUid() {
    if (!window.FirebaseAuth) return null;
    const user = window.FirebaseAuth.getCurrentUser();
    return user ? user.uid : null;
  }

  function _getUserDocRef() {
    const db  = window.FirebaseAuth ? window.FirebaseAuth.getFirestore() : null;
    const uid = _getUid();
    if (!db || !uid) return null;
    return db.collection('users').doc(uid);
  }

  function _fullImageUrl(pathOrUrl, size) {
    if (!pathOrUrl) return '';
    if (String(pathOrUrl).startsWith('http')) return pathOrUrl;
    return `https://image.tmdb.org/t/p/${size}${pathOrUrl}`;
  }

  /* ── Favorites ⇄ Android Movie JSON ────────────────────────────── */

  function _favoriteToCloud(item) {
    const isTv = item.type === 'tv' || item.type === 'series';
    return JSON.stringify({
      id:          item.cloudId || `${isTv ? 'tv' : 'movie'}_${item.id}`,
      title:       item.title || '',
      backdropUrl: _fullImageUrl(item.backdrop, 'w1280'),
      posterUrl:   _fullImageUrl(item.poster, 'w500'),
      overview:    item.overview || '',
      rating:      Number(item.rating) || 0,
      year:        parseInt(item.year, 10) || 0,
      genre:       item.genre || '',
      mediaType:   isTv ? 'tv' : 'movie',
    });
  }

  function _favoriteFromCloud(raw, index) {
    if (typeof raw !== 'string') return null;
    try {
      const j = JSON.parse(raw);
      if (!j || !j.id || !j.title) return null;
      const m = /^(movie|tv)_(\d+)$/.exec(j.id);
      return {
        id:       m ? Number(m[2]) : j.id,
        type:     m ? m[1] : (j.mediaType === 'tv' ? 'tv' : 'movie'),
        cloudId:  m ? undefined : j.id,
        title:    j.title,
        poster:   j.posterUrl || '',
        backdrop: j.backdropUrl || '',
        overview: j.overview || '',
        rating:   Number(j.rating) || 0,
        year:     parseInt(j.year, 10) || 0,
        genre:    j.genre || '',
        // Keep cloud order (newest first) stable for the local sort.
        addedAt:  Date.now() - index,
      };
    } catch {
      return null;
    }
  }

  /* ── Progress ⇄ Android {p,d,t,tmdb} JSON ──────────────────────── */

  function _progressToCloud(entry) {
    if (!entry || !(entry.duration > 0)) return null;
    const out = {
      p: Math.round((entry.position || 0) * 1000),
      d: Math.round(entry.duration * 1000),
      t: entry.timestamp || Date.now(),
    };
    if (entry.tmdb) {
      out.tmdb = entry.tmdb;
    } else if (entry.mediaId) {
      const isTv = entry.type === 'series' || entry.type === 'tv';
      out.tmdb = `${isTv ? 'tv' : 'movie'}_${entry.mediaId}`;
    }
    return JSON.stringify(out);
  }

  function _progressFromCloud(key, raw, local) {
    let j;
    try { j = JSON.parse(raw); } catch { return null; }
    if (!j || typeof j.p !== 'number' || typeof j.d !== 'number' || typeof j.t !== 'number' || j.d <= 0) {
      return null;
    }
    const position = j.p / 1000;
    const duration = j.d / 1000;
    const pct = Math.min(100, Math.max(0, (position / duration) * 100));
    const ep = /^ep_(.+)_s(\d+)e(\d+)$/.exec(key);
    const mv = /^movie_(.+)$/.exec(key);
    const tmdb = j.tmdb || (local && local.tmdb) || null;
    const tmdbMatch = tmdb ? /^(movie|tv)_(\d+)$/.exec(tmdb) : null;
    return {
      ...(local || {}),
      key,
      imdbId:     ep ? ep[1] : (mv ? mv[1] : (local && local.imdbId) || null),
      type:       ep ? 'series' : 'movie',
      season:     ep ? Number(ep[2]) : null,
      episode:    ep ? Number(ep[3]) : null,
      mediaId:    tmdbMatch ? Number(tmdbMatch[2]) : (local && local.mediaId) || null,
      tmdb,
      position,
      duration,
      percentage: Math.round(pct * 100) / 100,
      progress:   Math.round(pct * 100) / 100,
      timestamp:  j.t,
    };
  }

  /* ── Push ───────────────────────────────────────────────────────── */

  async function _writeFavorites(docRef) {
    const favorites = window.Storage.getWatchlist().map(_favoriteToCloud);
    try {
      await docRef.set({ favorites, _exists: true }, { merge: true });
    } catch (err) {
      console.error('[SyncManager] pushFavorites failed:', err);
      throw err;
    }
  }

  /** True once onSignedIn has reconciled local and cloud data for this user. */
  function _isReady() {
    return !!syncedUid && _getUid() === syncedUid;
  }

  /**
   * Upload the whole local watchlist (replaces the cloud array, like Android).
   * Skipped until sign-in reconciliation is done, so a stale local list can
   * never overwrite the account's watchlist.
   */
  async function pushFavorites() {
    const docRef = _getUserDocRef();
    if (!docRef || !window.Storage || !_isReady()) return;
    await _writeFavorites(docRef);
  }

  /**
   * Queue a progress change for upload. Changes are batched and flushed
   * after PROGRESS_PUSH_DELAY, matching Android's 5 s progress cadence.
   * @param {string} key
   * @param {Object|null} entry  local entry, or null to tombstone the key
   */
  function queueProgress(key, entry) {
    if (!key) return;
    const value = entry ? _progressToCloud(entry) : '';
    if (value === null) return;
    dirtyProgress.set(key, value);
    if (!progressTimer) {
      progressTimer = setTimeout(() => {
        progressTimer = null;
        flushProgress().catch(() => {});
      }, PROGRESS_PUSH_DELAY);
    }
  }

  /** Push any queued progress immediately (e.g. when the player closes). */
  async function flushProgress() {
    if (progressTimer) { clearTimeout(progressTimer); progressTimer = null; }
    if (dirtyProgress.size === 0) return;
    const docRef = _getUserDocRef();
    if (!docRef || !_isReady()) return; // keep queued until signed in and reconciled
    const batch = Object.fromEntries(dirtyProgress);
    dirtyProgress.clear();
    try {
      await docRef.set({ progress: batch, _exists: true }, { merge: true });
    } catch (err) {
      console.error('[SyncManager] flushProgress failed:', err);
      for (const [k, v] of Object.entries(batch)) {
        if (!dirtyProgress.has(k)) dirtyProgress.set(k, v);
      }
      throw err;
    }
  }

  async function _pushAllProgress(docRef, filterFn) {
    if (!window.WatchProgress) return;
    const progress = {};
    for (const [key, entry] of Object.entries(window.WatchProgress.getAll())) {
      if (!window.WatchProgress.isValidKey(key)) continue;
      if (filterFn && !filterFn(key, entry)) continue;
      const v = _progressToCloud(entry);
      if (v) progress[key] = v;
    }
    if (Object.keys(progress).length === 0) return;
    await docRef.set({ progress, _exists: true }, { merge: true });
  }

  /** Manual push of everything local (Android pushToCloud). */
  async function push() {
    const docRef = _getUserDocRef();
    if (!docRef || !_isReady()) {
      console.warn('[SyncManager] Cannot push — not signed in yet.');
      return;
    }
    await _writeFavorites(docRef);
    await _pushAllProgress(docRef);
  }

  /* ── Pull / merge ───────────────────────────────────────────────── */

  /**
   * Merge a users/{uid} snapshot into local storage.
   * Watchlist: cloud replaces local (Android semantics).
   * Progress: newer `t` wins; "" deletes.
   */
  function _mergeFromCloud(data) {
    if (!data) return;

    if (Array.isArray(data.favorites) && window.Storage) {
      const parsed = data.favorites.map(_favoriteFromCloud).filter(Boolean);
      // Never wipe the local list because of entries we can't parse.
      if (parsed.length > 0 || data.favorites.length === 0) {
        window.Storage.setWatchlist(parsed);
      }
    }

    if (data.progress && typeof data.progress === 'object' && window.WatchProgress) {
      const local = window.WatchProgress.getAll();
      let changed = false;
      for (const [key, raw] of Object.entries(data.progress)) {
        if (typeof raw !== 'string') continue;
        if (raw === '') {
          if (local[key]) { delete local[key]; changed = true; }
          continue;
        }
        const entry = _progressFromCloud(key, raw, local[key]);
        if (!entry) continue;
        if (!local[key] || entry.timestamp > (local[key].timestamp || 0)) {
          local[key] = entry;
          changed = true;
        }
      }
      if (changed) window.WatchProgress.replaceAll(local);
    }

    window.dispatchEvent(new CustomEvent('lumina:sync'));
  }

  /** Download the cloud document and merge it locally. */
  async function pull() {
    const docRef = _getUserDocRef();
    if (!docRef) {
      console.warn('[SyncManager] Cannot pull — not logged in or Firestore unavailable.');
      return;
    }
    const snapshot = await docRef.get();
    if (snapshot.exists) _mergeFromCloud(snapshot.data());
  }

  /**
   * Called once a user is signed in (startup restore or fresh login).
   * On the first sign-in on this browser, local data is merged into the
   * account; afterwards the cloud is the source of truth, like Android.
   */
  async function onSignedIn() {
    const docRef = _getUserDocRef();
    if (!docRef) return;
    const uid = _getUid();
    let lastUid = null;
    try { lastUid = localStorage.getItem(LAST_UID_KEY); } catch {}
    const firstSignInHere = lastUid !== uid;

    const snapshot = await docRef.get();
    const cloud = snapshot.exists ? snapshot.data() : null;

    if (firstSignInHere && window.Storage) {
      // Union local watchlist items into the cloud list before adopting it.
      const cloudItems = (cloud && Array.isArray(cloud.favorites))
        ? cloud.favorites.map(_favoriteFromCloud).filter(Boolean) : [];
      const seen = new Set(cloudItems.map(i => `${i.type}_${i.id}`));
      const localOnly = window.Storage.getWatchlist().filter(i => !seen.has(`${i.type === 'series' ? 'tv' : i.type}_${i.id}`));
      window.Storage.setWatchlist([...localOnly, ...cloudItems]);
      if (localOnly.length > 0 || !cloud || !Array.isArray(cloud.favorites)) {
        await _writeFavorites(docRef);
      }
    }

    if (cloud) {
      const cloudFavs = Array.isArray(cloud.favorites) ? cloud.favorites : null;
      _mergeFromCloud(firstSignInHere ? { ...cloud, favorites: undefined } : cloud);
      if (!cloudFavs && !firstSignInHere) await _writeFavorites(docRef);
    }

    // Upload local progress the cloud doesn't have (or has older). This pass
    // replaces anything queued while signed out, so a stale queue can't
    // resurrect entries another device deleted.
    dirtyProgress.clear();
    const cloudProgress = (cloud && cloud.progress) || {};
    await _pushAllProgress(docRef, (key, entry) => {
      const raw = cloudProgress[key];
      if (raw === undefined) return true;
      if (raw === '') return false;
      try { return (entry.timestamp || 0) > (JSON.parse(raw).t || 0); } catch { return true; }
    });

    try { localStorage.setItem(LAST_UID_KEY, uid); } catch {}
    syncedUid = uid;
    startRealtimeSync();
    flushProgress().catch(() => {});
  }

  /** Called on sign-out: stop listening; next sign-in merges local data again. */
  function onSignedOut() {
    syncedUid = null;
    stopRealtimeSync();
    dirtyProgress.clear();
    if (progressTimer) { clearTimeout(progressTimer); progressTimer = null; }
    try { localStorage.removeItem(LAST_UID_KEY); } catch {}
  }

  /* ── Real-time Sync ────────────────────────────────────────────── */

  function startRealtimeSync() {
    stopRealtimeSync();
    const docRef = _getUserDocRef();
    if (!docRef) return;

    realtimeUnsubscribe = docRef.onSnapshot(
      snapshot => {
        if (!snapshot.exists || snapshot.metadata.hasPendingWrites) return;
        _mergeFromCloud(snapshot.data());
      },
      err => console.warn('[SyncManager] Realtime sync error (will retry):', err)
    );
  }

  function stopRealtimeSync() {
    if (realtimeUnsubscribe) {
      realtimeUnsubscribe();
      realtimeUnsubscribe = null;
    }
  }

  /* ── Full Sync (manual button; Android fullSync: push, then pull) ─ */

  async function fullSync() {
    await flushProgress();
    await push();
    await pull();
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.SyncManager = Object.freeze({
    push,
    pull,
    pushFavorites,
    queueProgress,
    flushProgress,
    onSignedIn,
    onSignedOut,
    startRealtimeSync,
    stopRealtimeSync,
    fullSync,
  });
})();
