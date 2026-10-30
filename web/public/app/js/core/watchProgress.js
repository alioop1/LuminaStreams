/**
 * LuminaStreams — Watch Progress Tracker
 * Stores playback position, duration, and percentage per media key.
 * Key format (shared with Android/iOS): movie_{imdbId} or ep_{imdbId}_s{S}e{E}
 * Positions are kept in seconds locally; SyncManager converts to Android's ms format.
 */
(() => {
  'use strict';

  const STORAGE_KEY = 'lumina_watch_progress';
  // Keys must carry an IMDb id; older builds sometimes saved TMDB ids, which
  // the other apps can't resolve, so those are never written or synced.
  const VALID_KEY = /^(movie_tt\d+|ep_tt\d+_s\d+e\d+)$/;

  /* ── Internal Helpers ──────────────────────────────────────────── */

  function _loadAll() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch {
      return {};
    }
  }

  function _saveAll(data) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (err) {
      console.error('[WatchProgress] save failed:', err);
    }
  }

  function _queueSync(key, entry) {
    if (window.SyncManager && typeof window.SyncManager.queueProgress === 'function') {
      window.SyncManager.queueProgress(key, entry);
    }
  }

  /** Build the shared progress key. */
  function keyFor(type, imdbId, season, episode) {
    if (!imdbId) return null;
    return (type === 'series' || type === 'tv')
      ? `ep_${imdbId}_s${season}e${episode}`
      : `movie_${imdbId}`;
  }

  /* ── Public API ────────────────────────────────────────────────── */

  /**
   * Save playback progress.
   * @param {Object} obj  { imdbId, type, season, episode, position, duration, mediaId, title, poster_path, ... }
   */
  function save(obj) {
    if (!obj) return;
    const key = obj.key || keyFor(obj.type, obj.imdbId, obj.season, obj.episode);
    const pos = obj.position;
    const dur = obj.duration;
    if (!key || !VALID_KEY.test(key) || !dur || dur <= 0 || !isFinite(pos)) return;

    const percentage = Math.min(100, Math.max(0, (pos / dur) * 100));
    const isSeries = obj.type === 'series' || obj.type === 'tv';
    const data = _loadAll();

    const entry = {
      ...obj,
      key,
      mediaId: obj.mediaId || null,
      tmdb: obj.mediaId ? `${isSeries ? 'tv' : 'movie'}_${obj.mediaId}` : (obj.tmdb || null),
      imdbId: obj.imdbId || null,
      type: isSeries ? 'series' : 'movie',
      title: obj.title || '',
      poster_path: obj.poster_path || obj.poster || '',
      season: isSeries ? Number(obj.season) : null,
      episode: isSeries ? Number(obj.episode) : null,
      position: Math.round(pos * 100) / 100,
      duration: Math.round(dur * 100) / 100,
      percentage: Math.round(percentage * 100) / 100,
      progress: Math.round(percentage * 100) / 100,
      timestamp: Date.now(),
    };

    data[key] = entry;
    _saveAll(data);
    _queueSync(key, entry);
  }

  /**
   * Get progress for an exact key.
   * @param {string} key
   */
  function get(key) {
    if (!key) return null;
    return _loadAll()[key] || null;
  }

  /** Get all progress entries (key → entry). */
  function getAll() {
    return _loadAll();
  }

  /** Replace all entries (used by cloud sync; does not push back). */
  function replaceAll(data) {
    _saveAll(data || {});
  }

  /**
   * Most recently watched episode of a series (Android getLatestEpisodeProgress).
   * @param {string} imdbId
   * @returns {Object|null} entry with season/episode
   */
  function getLatestEpisode(imdbId) {
    if (!imdbId) return null;
    const prefix = `ep_${imdbId}_s`;
    let best = null;
    for (const [key, entry] of Object.entries(_loadAll())) {
      if (!key.startsWith(prefix)) continue;
      const m = /_s(\d+)e(\d+)$/.exec(key);
      if (!m) continue;
      if (!best || (entry.timestamp || 0) > (best.timestamp || 0)) {
        best = { ...entry, key, season: Number(m[1]), episode: Number(m[2]) };
      }
    }
    return best;
  }

  /**
   * Items for the "Continue Watching" row: one per show, between 2% and 90%,
   * newest first, max 20 (Android HomeViewModel).
   */
  function getContinueWatching() {
    const byTitle = new Map();
    for (const [key, entry] of Object.entries(_loadAll())) {
      const groupKey = entry.type === 'series'
        ? `series_${entry.imdbId || key.replace(/_s\d+e\d+$/, '')}`
        : key;
      const prev = byTitle.get(groupKey);
      if (!prev || (entry.timestamp || 0) > (prev.timestamp || 0)) {
        byTitle.set(groupKey, { key, ...entry });
      }
    }
    return Array.from(byTitle.values())
      .filter(e => e.percentage >= 2 && e.percentage < 90)
      .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
      .slice(0, 20);
  }

  /** ≥90% watched (Android isFinished). */
  function isWatched(key) {
    const entry = get(key);
    return entry ? entry.percentage >= 90 : false;
  }

  /** ≥2% watched (Android hasStarted). */
  function isStarted(key) {
    const entry = get(key);
    return entry ? entry.percentage >= 2 : false;
  }

  /** Remove progress for a key (synced as a deletion). */
  function remove(key) {
    if (!key) return;
    const data = _loadAll();
    delete data[key];
    _saveAll(data);
    _queueSync(key, null);
  }

  /** Clear all watch progress (synced as deletions). */
  function clear() {
    for (const key of Object.keys(_loadAll())) {
      if (VALID_KEY.test(key)) _queueSync(key, null);
    }
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (err) {
      console.error('[WatchProgress] clear failed:', err);
    }
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.WatchProgress = Object.freeze({
    isValidKey: key => VALID_KEY.test(key || ''),
    keyFor,
    save,
    get,
    getAll,
    replaceAll,
    getLatestEpisode,
    getContinueWatching,
    isWatched,
    isStarted,
    remove,
    clear,
  });
})();
