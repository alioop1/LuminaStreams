/**
 * LuminaStreams — Watch Progress Tracker
 * Stores playback position, duration, and percentage per media key.
 * Key format: movie_{imdbId} or ep_{imdbId}_s{S}e{E}
 */
(() => {
  'use strict';

  const STORAGE_KEY = 'lumina_watch_progress';

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

  let cloudSyncTimer = null;
  function _scheduleCloudSync() {
    if (cloudSyncTimer) return;
    cloudSyncTimer = setTimeout(() => {
      cloudSyncTimer = null;
      if (window.SyncManager && typeof window.SyncManager.push === 'function') {
        window.SyncManager.push().catch(() => {});
      }
    }, 6000);
  }

  /* ── Public API ────────────────────────────────────────────────── */

  /**
   * Save playback progress for a media key or progress object.
   * @param {string|Object} keyOrObj
   * @param {number} [position]
   * @param {number} [duration]
   * @param {Object} [extra]
   */
  function save(keyOrObj, position, duration, extra = {}) {
    let key, pos, dur, meta = {};
    if (typeof keyOrObj === 'object' && keyOrObj !== null) {
      pos = keyOrObj.position;
      dur = keyOrObj.duration;
      key = keyOrObj.key || keyOrObj.id;
      if (!key) {
        if (keyOrObj.type === 'series') {
          key = `ep_${keyOrObj.imdbId || keyOrObj.id}_s${keyOrObj.season}e${keyOrObj.episode}`;
        } else {
          key = `movie_${keyOrObj.imdbId || keyOrObj.id}`;
        }
      }
      meta = keyOrObj;
    } else {
      key = keyOrObj;
      pos = position;
      dur = duration;
      meta = extra || {};
    }

    if (!key || !dur || dur <= 0 || !isFinite(pos)) return;

    const percentage = Math.min(100, Math.max(0, (pos / dur) * 100));
    const data = _loadAll();

    const entry = {
      ...meta,
      key: key,
      id: meta.id || key,
      mediaId: meta.mediaId || meta.id,
      imdbId: meta.imdbId || null,
      type: meta.type || 'movie',
      title: meta.title || '',
      poster_path: meta.poster_path || meta.poster || '',
      season: meta.season ?? null,
      episode: meta.episode ?? null,
      position: Math.round(pos * 100) / 100,
      duration: Math.round(dur * 100) / 100,
      percentage: Math.round(percentage * 100) / 100,
      progress: Math.round(percentage * 100) / 100,
      timestamp: Date.now(),
    };

    data[key] = entry;
    _saveAll(data);

    // Keep ContinueWatching in sync
    if (window.Storage && typeof window.Storage.updateContinueWatching === 'function') {
      window.Storage.updateContinueWatching(entry);
    }

    _scheduleCloudSync();
  }

  /**
   * Get progress for a media key or alias.
   * @param {string} key
   * @returns {{ position: number, duration: number, percentage: number, timestamp: number }|null}
   */
  function get(key) {
    if (!key) return null;
    const data = _loadAll();
    if (data[key]) return data[key];
    const movieKey = `movie_${key}`;
    if (data[movieKey]) return data[movieKey];
    for (const k of Object.keys(data)) {
      const item = data[k];
      if (item && (item.id === key || item.imdbId === key || String(item.mediaId) === String(key) || item.key === key)) {
        return item;
      }
    }
    return null;
  }

  /**
   * Get all progress entries.
   * @returns {Object} Map of key → progress entry
   */
  function getAll() {
    return _loadAll();
  }

  /**
   * Get items suitable for "Continue Watching" row.
   * Items between 2% and 90% watched, sorted by most recently watched, max 20.
   * @returns {Array<{ key: string, position: number, duration: number, percentage: number, timestamp: number }>}
   */
  function getContinueWatching() {
    const data = _loadAll();
    const items = [];

    for (const [key, entry] of Object.entries(data)) {
      if (entry.percentage >= 2 && entry.percentage < 90) {
        items.push({ key, ...entry });
      }
    }

    // Sort by most recently watched (newest first)
    items.sort((a, b) => b.timestamp - a.timestamp);

    // Cap at 20 items
    return items.slice(0, 20);
  }

  /**
   * Check if a media item is considered "watched" (≥90% progress).
   * @param {string} key
   * @returns {boolean}
   */
  function isWatched(key) {
    const entry = get(key);
    return entry ? entry.percentage >= 90 : false;
  }

  /**
   * Check if a media item has been started (≥2% progress).
   * @param {string} key
   * @returns {boolean}
   */
  function isStarted(key) {
    const entry = get(key);
    return entry ? entry.percentage >= 2 : false;
  }

  /**
   * Remove progress for a specific key.
   * @param {string} key
   */
  function remove(key) {
    if (!key) return;
    const data = _loadAll();
    delete data[key];
    _saveAll(data);
  }

  /**
   * Clear all watch progress data.
   */
  function clear() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (err) {
      console.error('[WatchProgress] clear failed:', err);
    }
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.WatchProgress = Object.freeze({
    save,
    get,
    getAll,
    getContinueWatching,
    isWatched,
    isStarted,
    remove,
    clear,
  });
})();
