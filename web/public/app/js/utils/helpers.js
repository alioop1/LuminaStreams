/**
 * LuminaStreams — Utility Helpers
 * Formatting, timing, sanitisation, locale helpers
 */
(() => {
  'use strict';

  /**
   * Format a runtime in minutes into a human-readable string.
   * @param {number} minutes
   * @returns {string} e.g. '2h 15m', '45m', '—'
   */
  function formatRuntime(minutes) {
    if (!minutes || minutes <= 0) return '—';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  }

  /**
   * Extract the year from a date string (YYYY-MM-DD).
   * @param {string} dateString
   * @returns {number|string}
   */
  function formatYear(dateString) {
    if (!dateString) return '—';
    const y = new Date(dateString).getFullYear();
    return isNaN(y) ? '—' : y;
  }

  /**
   * Format bytes into a human-readable file size.
   * @param {number} bytes
   * @returns {string} e.g. '1.5 GB'
   */
  function formatFileSize(bytes) {
    if (bytes == null || bytes <= 0) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let idx = 0;
    let size = bytes;
    while (size >= 1024 && idx < units.length - 1) {
      size /= 1024;
      idx++;
    }
    return `${size.toFixed(idx === 0 ? 0 : 1)} ${units[idx]}`;
  }

  /**
   * Format a number with commas for display.
   * @param {number} num
   * @returns {string}
   */
  function formatNumber(num) {
    if (num == null) return '—';
    return Number(num).toLocaleString();
  }

  /**
   * Format a vote average to one decimal place with a star.
   * @param {number} vote
   * @returns {string}
   */
  function formatRating(vote) {
    if (!vote || vote === 0) return '—';
    return `★ ${Number(vote).toFixed(1)}`;
  }

  /**
   * Format a currency amount.
   * @param {number} amount
   * @returns {string}
   */
  function formatCurrency(amount) {
    if (!amount || amount === 0) return '—';
    return '$' + Number(amount).toLocaleString();
  }

  /**
   * Debounce a function — delay execution until `ms` after the last call.
   * @param {Function} fn
   * @param {number} ms  Delay in milliseconds
   * @returns {Function}
   */
  function debounce(fn, ms = 300) {
    let timer = null;
    const debounced = (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(null, args), ms);
    };
    debounced.cancel = () => clearTimeout(timer);
    return debounced;
  }

  /**
   * Throttle a function — execute at most once every `ms` milliseconds.
   * @param {Function} fn
   * @param {number} ms
   * @returns {Function}
   */
  function throttle(fn, ms = 300) {
    let lastCall = 0;
    let timer = null;
    return (...args) => {
      const now = Date.now();
      const remaining = ms - (now - lastCall);
      clearTimeout(timer);
      if (remaining <= 0) {
        lastCall = now;
        fn.apply(null, args);
      } else {
        timer = setTimeout(() => {
          lastCall = Date.now();
          fn.apply(null, args);
        }, remaining);
      }
    };
  }

  /**
   * Escape HTML special characters to prevent XSS.
   * @param {string} str
   * @returns {string}
   */
  function escapeHtml(str) {
    if (!str) return '';
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
    return String(str).replace(/[&<>"']/g, ch => map[ch]);
  }

  /**
   * Generate a random alphanumeric ID.
   * @param {number} length
   * @returns {string}
   */
  function generateId(length = 12) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let id = '';
    const arr = crypto.getRandomValues(new Uint8Array(length));
    for (let i = 0; i < length; i++) {
      id += chars[arr[i] % chars.length];
    }
    return id;
  }

  /**
   * Sleep for a given number of milliseconds.
   * @param {number} ms
   * @returns {Promise<void>}
   */
  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Inline translation shorthand.
   * Returns Hebrew text if current language is 'he', else English.
   * @param {string} en  English text
   * @param {string} he  Hebrew text
   * @returns {string}
   */
  function tr(en, he) {
    const lang = (window.Storage && window.Storage.getLanguage)
      ? window.Storage.getLanguage()
      : 'he';
    return lang === 'he' ? he : en;
  }

  /**
   * Truncate a string to a maximum length, appending '…' if truncated.
   * @param {string} str
   * @param {number} max
   * @returns {string}
   */
  function truncate(str, max = 120) {
    if (!str) return '';
    return str.length <= max ? str : str.slice(0, max).trimEnd() + '…';
  }

  /**
   * Clamp a number between min and max.
   * @param {number} val
   * @param {number} min
   * @param {number} max
   * @returns {number}
   */
  function clamp(val, min, max) {
    return Math.max(min, Math.min(max, val));
  }

  /**
   * Parse a season/episode string like "S02E05" into { season, episode }.
   * @param {string} str
   * @returns {{ season: number, episode: number } | null}
   */
  function parseSeasonEpisode(str) {
    const m = str.match(/S(\d+)E(\d+)/i);
    if (!m) return null;
    return { season: parseInt(m[1], 10), episode: parseInt(m[2], 10) };
  }

  /**
   * Build a season/episode display string.
   * @param {number} season
   * @param {number} episode
   * @returns {string}
   */
  function seLabel(season, episode) {
    const s = String(season).padStart(2, '0');
    const e = String(episode).padStart(2, '0');
    return `S${s}E${e}`;
  }

  /**
   * Deep-clone a plain object / array.
   * @param {*} obj
   * @returns {*}
   */
  function deepClone(obj) {
    return typeof structuredClone === 'function' ? structuredClone(obj) : JSON.parse(JSON.stringify(obj));
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.Helpers = Object.freeze({
    formatRuntime,
    formatYear,
    formatFileSize,
    formatNumber,
    formatRating,
    formatCurrency,
    debounce,
    throttle,
    escapeHtml,
    generateId,
    sleep,
    tr,
    truncate,
    clamp,
    parseSeasonEpisode,
    seLabel,
    deepClone,
  });
})();
