/**
 * LuminaStreams — Cloud Sync Manager
 * Push/pull watchlist and watch progress to/from Firestore.
 * Real-time sync via Firestore snapshots with newer-wins merge.
 */
(() => {
  'use strict';

  /* ── State ─────────────────────────────────────────────────────── */
  let realtimeUnsubscribe = null;
  let isSyncingFromCloud  = false; // Guard against push-back loops

  /* ── Helpers ───────────────────────────────────────────────────── */

  function _getDb() {
    if (!window.FirebaseAuth) return null;
    return window.FirebaseAuth.getFirestore();
  }

  function _getUid() {
    if (!window.FirebaseAuth) return null;
    const user = window.FirebaseAuth.getCurrentUser();
    return user ? user.uid : null;
  }

  function _getUserDocRef() {
    const db  = _getDb();
    const uid = _getUid();
    if (!db || !uid) return null;
    return db.collection('users').doc(uid);
  }

  /* ── Push ───────────────────────────────────────────────────────── */

  /**
   * Upload local watchlist and watch progress to Firestore.
   * Stores data in users/{uid} document.
   * @returns {Promise<void>}
   */
  async function push() {
    if (isSyncingFromCloud) return; // Don't push back during a pull

    const docRef = _getUserDocRef();
    if (!docRef) {
      console.warn('[SyncManager] Cannot push — not logged in or Firestore unavailable.');
      return;
    }

    try {
      // Gather local data
      const watchlist = window.Storage ? window.Storage.getWatchlist() : [];
      const progress  = window.WatchProgress ? window.WatchProgress.getAll() : {};
      const continueWatching = window.Storage ? window.Storage.getContinueWatching() : [];

      // Serialize progress entries with timestamps for merge
      const progressData = {};
      for (const [key, entry] of Object.entries(progress)) {
        progressData[key] = typeof entry === 'string' ? entry : JSON.stringify(entry);
      }

      // Build the document payload
      const payload = {
        favorites:         watchlist.map(item => item.id || item),
        watchlist:         watchlist, // Full watchlist items with metadata
        progress:          progressData,
        continue_watching: continueWatching,
        lastSync:          Date.now(),
        updatedAt:         firebase.firestore.FieldValue.serverTimestamp(),
      };

      await docRef.set(payload, { merge: true });
      console.log('[SyncManager] Push complete.');
    } catch (err) {
      console.error('[SyncManager] Push failed:', err);
    }
  }

  /* ── Pull ───────────────────────────────────────────────────────── */

  /**
   * Download and merge data from Firestore into local storage.
   * Uses newer-wins strategy for progress entries based on timestamps.
   * @returns {Promise<void>}
   */
  async function pull() {
    const docRef = _getUserDocRef();
    if (!docRef) {
      console.warn('[SyncManager] Cannot pull — not logged in or Firestore unavailable.');
      return;
    }

    try {
      isSyncingFromCloud = true;

      const snapshot = await docRef.get();
      if (!snapshot.exists) {
        console.log('[SyncManager] No cloud data found.');
        isSyncingFromCloud = false;
        return;
      }

      const cloudData = snapshot.data();
      _mergeFromCloud(cloudData);

      console.log('[SyncManager] Pull complete.');
    } catch (err) {
      console.error('[SyncManager] Pull failed:', err);
    } finally {
      isSyncingFromCloud = false;
    }
  }

  /**
   * Merge cloud data into local storage.
   * @param {Object} cloudData  Firestore document data
   */
  function _mergeFromCloud(cloudData) {
    if (!cloudData) return;

    // ── Merge Watchlist ───────────────────────────────────────
    if (cloudData.watchlist && Array.isArray(cloudData.watchlist) && window.Storage) {
      const localList = window.Storage.getWatchlist();
      const localIds  = new Set(localList.map(w => `${w.id}_${w.type}`));
      const merged    = [...localList];

      for (const cloudItem of cloudData.watchlist) {
        const key = `${cloudItem.id}_${cloudItem.type}`;
        if (!localIds.has(key)) {
          merged.push(cloudItem);
          localIds.add(key);
        }
      }

      // Sort by addedAt descending
      merged.sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));

      // Write merged list back (use the raw storage key)
      try {
        localStorage.setItem('lumina_watchlist', JSON.stringify(merged));
      } catch {}
    }

    // ── Merge Watch Progress (newer-wins) ────────────────────
    if (cloudData.progress && window.WatchProgress) {
      const localProgress = window.WatchProgress.getAll();

      for (const [key, rawVal] of Object.entries(cloudData.progress)) {
        try {
          const cloudEntry = typeof rawVal === 'string' ? JSON.parse(rawVal) : rawVal;
          const localEntry = localProgress[key];

          // If local doesn't have it, or cloud is newer → use cloud
          if (!localEntry || (cloudEntry.timestamp && cloudEntry.timestamp > (localEntry.timestamp || 0))) {
            localProgress[key] = cloudEntry;
          }
        } catch {
          // Skip malformed entries
        }
      }

      // Write merged progress back
      try {
        localStorage.setItem('lumina_watch_progress', JSON.stringify(localProgress));
      } catch {}
    }

    // ── Merge Continue Watching ──────────────────────────────
    if (cloudData.continue_watching && Array.isArray(cloudData.continue_watching) && window.Storage) {
      const localCW = window.Storage.getContinueWatching();
      const localMap = new Map(localCW.map(i => [i.id || i.key, i]));

      for (const cloudItem of cloudData.continue_watching) {
        const id = cloudItem.id || cloudItem.key;
        if (!id) continue;
        const existing = localMap.get(id);
        const cloudTime = cloudItem.timestamp || cloudItem.updatedAt || 0;
        const existTime = existing ? (existing.timestamp || existing.updatedAt || 0) : 0;
        if (!existing || cloudTime > existTime) {
          localMap.set(id, cloudItem);
        }
      }

      const mergedCW = Array.from(localMap.values())
        .sort((a, b) => (b.timestamp || b.updatedAt || 0) - (a.timestamp || a.updatedAt || 0));

      window.Storage.setContinueWatching(mergedCW);
    }
  }

  /* ── Real-time Sync ────────────────────────────────────────────── */

  /**
   * Start listening to Firestore snapshots for real-time sync.
   * Automatically merges incoming changes using newer-wins strategy.
   */
  function startRealtimeSync() {
    // Stop existing listener first
    stopRealtimeSync();

    const docRef = _getUserDocRef();
    if (!docRef) {
      console.warn('[SyncManager] Cannot start realtime sync — not logged in.');
      return;
    }

    realtimeUnsubscribe = docRef.onSnapshot(
      snapshot => {
        if (!snapshot.exists) return;

        // Skip if this change was triggered by our own push
        const data = snapshot.data();
        const metadata = snapshot.metadata;

        // If the change has pending writes (from us), skip to avoid loop
        if (metadata.hasPendingWrites) return;

        console.log('[SyncManager] Received cloud update.');
        isSyncingFromCloud = true;

        try {
          _mergeFromCloud(data);
        } finally {
          // Reset flag after a short delay to let local storage settle
          setTimeout(() => { isSyncingFromCloud = false; }, 500);
        }
      },
      err => {
        console.error('[SyncManager] Realtime sync error:', err);
      }
    );

    console.log('[SyncManager] Realtime sync started.');
  }

  /**
   * Stop the realtime sync listener.
   */
  function stopRealtimeSync() {
    if (realtimeUnsubscribe) {
      realtimeUnsubscribe();
      realtimeUnsubscribe = null;
      console.log('[SyncManager] Realtime sync stopped.');
    }
  }

  /* ── Full Sync ─────────────────────────────────────────────────── */

  /**
   * Perform a full sync: push local data, then pull and merge cloud data.
   * @returns {Promise<void>}
   */
  async function fullSync() {
    await push();
    await pull();
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.SyncManager = Object.freeze({
    push,
    pull,
    startRealtimeSync,
    stopRealtimeSync,
    fullSync,
  });
})();
