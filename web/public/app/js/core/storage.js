/**
 * LuminaStreams — Storage Manager
 * localStorage wrapper with 'lumina_' prefix, RD token helpers,
 * language, search history, and generic get/set/remove.
 */
(() => {
  'use strict';

  const PREFIX = 'lumina_';

  /* ── Key Constants ────────────────────────────────────────────────── */
  const KEYS = {
    RD_TOKEN:          `${PREFIX}rd_token`,
    RD_REFRESH_TOKEN:  `${PREFIX}rd_refresh_token`,
    RD_CLIENT_ID:      `${PREFIX}rd_client_id`,
    RD_CLIENT_SECRET:  `${PREFIX}rd_client_secret`,
    RD_TOKEN_EXPIRY:   `${PREFIX}rd_token_expiry`,
    LANGUAGE:          `${PREFIX}language`,
    SEARCH_HISTORY:    `${PREFIX}search_history`,
    WATCHLIST:         `${PREFIX}watchlist`,
    WATCHED:           `${PREFIX}watched`,
    LAST_PLAYED:       `${PREFIX}last_played`,
    PLAYER_VOLUME:     `${PREFIX}player_volume`,
    PLAYER_SUBTITLE:   `${PREFIX}player_subtitle_lang`,
    THEME:             `${PREFIX}theme`,
    SIDEBAR_COLLAPSED: `${PREFIX}sidebar_collapsed`,
    IPTV_URL:          `${PREFIX}iptv_url`,
    FUZER_TOKEN:       `${PREFIX}fuzer_token`,
    CONTINUE_WATCHING: `${PREFIX}continue_watching`,
  };

  /* ── Generic helpers ──────────────────────────────────────────────── */

  /**
   * Read a value from localStorage (JSON-parsed).
   * @param {string} key  Unprefixed key
   * @param {*} defaultVal  Returned when key is missing
   * @returns {*}
   */
  function get(key, defaultVal = null) {
    try {
      const raw = localStorage.getItem(`${PREFIX}${key}`);
      if (raw === null) return defaultVal;
      return JSON.parse(raw);
    } catch {
      return defaultVal;
    }
  }

  /**
   * Write a value to localStorage (JSON-stringified).
   * @param {string} key  Unprefixed key
   * @param {*} val
   */
  function set(key, val) {
    try {
      localStorage.setItem(`${PREFIX}${key}`, JSON.stringify(val));
    } catch (err) {
      console.error('[Storage] set failed:', err);
    }
  }

  /**
   * Remove a key from localStorage.
   * @param {string} key  Unprefixed key
   */
  function remove(key) {
    localStorage.removeItem(`${PREFIX}${key}`);
  }

  /* ── Raw helpers (use full key, no prefix added) ──────────────────── */

  function _getRaw(fullKey, defaultVal = null) {
    try {
      const raw = localStorage.getItem(fullKey);
      if (raw === null) return defaultVal;
      return JSON.parse(raw);
    } catch {
      return defaultVal;
    }
  }

  function _setRaw(fullKey, val) {
    try {
      localStorage.setItem(fullKey, JSON.stringify(val));
    } catch (err) {
      console.error('[Storage] _setRaw failed:', err);
    }
  }

  function _removeRaw(fullKey) {
    localStorage.removeItem(fullKey);
  }

  /* ── Real-Debrid Token Management ─────────────────────────────────── */

  function getRdToken() {
    return _getRaw(KEYS.RD_TOKEN, null);
  }

  function setRdToken(token) {
    _setRaw(KEYS.RD_TOKEN, token);
  }

  function getRdRefreshToken() {
    return _getRaw(KEYS.RD_REFRESH_TOKEN, null);
  }

  function setRdRefreshToken(token) {
    _setRaw(KEYS.RD_REFRESH_TOKEN, token);
  }

  function getRdClientId() {
    return _getRaw(KEYS.RD_CLIENT_ID, null);
  }

  function setRdClientId(id) {
    _setRaw(KEYS.RD_CLIENT_ID, id);
  }

  function getRdClientSecret() {
    return _getRaw(KEYS.RD_CLIENT_SECRET, null);
  }

  function setRdClientSecret(s) {
    _setRaw(KEYS.RD_CLIENT_SECRET, s);
  }

  function getRdTokenExpiry() {
    return _getRaw(KEYS.RD_TOKEN_EXPIRY, 0);
  }

  function setRdTokenExpiry(epochMs) {
    _setRaw(KEYS.RD_TOKEN_EXPIRY, epochMs);
  }

  /* ── Language ─────────────────────────────────────────────────────── */

  function getLanguage() {
    return _getRaw(KEYS.LANGUAGE, 'he');
  }

  function setLanguage(lang) {
    _setRaw(KEYS.LANGUAGE, lang);
    document.documentElement.setAttribute('lang', lang);
    document.documentElement.setAttribute('dir', lang === 'he' ? 'rtl' : 'ltr');
  }

  /* ── Search History ───────────────────────────────────────────────── */

  const MAX_SEARCH_HISTORY = 20;

  function getSearchHistory() {
    return _getRaw(KEYS.SEARCH_HISTORY, []);
  }

  function addSearchHistory(term) {
    if (!term || !term.trim()) return;
    const trimmed = term.trim();
    let history = getSearchHistory();
    // Remove duplicates
    history = history.filter(h => h.toLowerCase() !== trimmed.toLowerCase());
    // Prepend
    history.unshift(trimmed);
    // Cap size
    if (history.length > MAX_SEARCH_HISTORY) {
      history = history.slice(0, MAX_SEARCH_HISTORY);
    }
    _setRaw(KEYS.SEARCH_HISTORY, history);
  }

  function clearSearchHistory() {
    _setRaw(KEYS.SEARCH_HISTORY, []);
  }

  /* ── Watchlist ────────────────────────────────────────────────────── */

  function getWatchlist() {
    return _getRaw(KEYS.WATCHLIST, []);
  }

  // Ids may arrive as numbers (TMDB) or strings (dataset attributes).
  function _sameItem(w, id, type) {
    const norm = t => (t === 'series' ? 'tv' : t);
    return String(w.id) === String(id) && norm(w.type) === norm(type);
  }

  function _pushWatchlist() {
    if (window.SyncManager) window.SyncManager.pushFavorites().catch(() => {});
  }

  function addToWatchlist(item) {
    const list = getWatchlist();
    if (list.some(w => _sameItem(w, item.id, item.type))) return;
    const date = item.release_date || item.first_air_date || '';
    list.unshift({
      id:        item.id,
      type:      item.type === 'series' ? 'tv' : item.type,
      title:     item.title || item.name,
      poster:    item.poster_path || item.poster,
      backdrop:  item.backdrop_path || item.backdrop || '',
      overview:  item.overview || '',
      rating:    item.vote_average || item.rating || 0,
      year:      item.year || (date ? parseInt(date.slice(0, 4), 10) : 0),
      genre:     item.genre || '',
      addedAt:   Date.now(),
    });
    _setRaw(KEYS.WATCHLIST, list);
    _pushWatchlist();
  }

  function removeFromWatchlist(id, type) {
    const list = getWatchlist().filter(w => !_sameItem(w, id, type));
    _setRaw(KEYS.WATCHLIST, list);
    _pushWatchlist();
  }

  function isInWatchlist(id, type) {
    return getWatchlist().some(w => _sameItem(w, id, type));
  }

  /** Replace the whole list (used by cloud sync; does not push back). */
  function setWatchlist(list) {
    _setRaw(KEYS.WATCHLIST, Array.isArray(list) ? list : []);
  }

  /* ── Continue Watching ────────────────────────────────────────────── */

  function getContinueWatching() {
    return _getRaw(KEYS.CONTINUE_WATCHING, []);
  }

  function setContinueWatching(list) {
    _setRaw(KEYS.CONTINUE_WATCHING, list);
  }

  function updateContinueWatching(item) {
    let list = getContinueWatching();
    list = list.filter(w => w.id !== item.id);
    list.unshift({
      ...item,
      updatedAt: Date.now(),
    });
    if (list.length > 50) list = list.slice(0, 50);
    _setRaw(KEYS.CONTINUE_WATCHING, list);
  }

  function removeContinueWatching(id) {
    const list = getContinueWatching().filter(w => w.id !== id);
    _setRaw(KEYS.CONTINUE_WATCHING, list);
  }

  /* ── Volume / Player ──────────────────────────────────────────────── */

  function getVolume() {
    return _getRaw(KEYS.PLAYER_VOLUME, 1);
  }

  function setVolume(v) {
    _setRaw(KEYS.PLAYER_VOLUME, v);
  }

  function getSubtitleLang() {
    return _getRaw(KEYS.PLAYER_SUBTITLE, 'heb');
  }

  function setSubtitleLang(lang) {
    _setRaw(KEYS.PLAYER_SUBTITLE, lang);
  }

  /* ── Clear All ────────────────────────────────────────────────────── */

  function clearAll() {
    const keys = Object.keys(localStorage);
    keys.forEach(k => {
      if (k.startsWith(PREFIX)) {
        localStorage.removeItem(k);
      }
    });
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.Storage = Object.freeze({
    // Generic
    get,
    set,
    remove,

    // Real-Debrid
    getRdToken,
    setRdToken,
    getRdRefreshToken,
    setRdRefreshToken,
    getRdClientId,
    setRdClientId,
    getRdClientSecret,
    setRdClientSecret,
    getRdTokenExpiry,
    setRdTokenExpiry,

    // Language
    getLanguage,
    setLanguage,

    // Search History
    getSearchHistory,
    addSearchHistory,
    clearSearchHistory,

    // Watchlist
    getWatchlist,
    addToWatchlist,
    removeFromWatchlist,
    isInWatchlist,
    setWatchlist,

    // Continue Watching
    getContinueWatching,
    setContinueWatching,
    updateContinueWatching,
    removeContinueWatching,

    // Player
    getVolume,
    setVolume,
    getSubtitleLang,
    setSubtitleLang,

    // Util
    clearAll,
    KEYS,
  });
})();
