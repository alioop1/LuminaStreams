/**
 * LuminaStreams — Fuzer Torrent Site Integration
 * Login, search, category browsing, and magnet link extraction.
 */
(() => {
  'use strict';

  const BASE_URL = 'https://www.fuzer.xyz';

  /* ── Category IDs ──────────────────────────────────────────────── */
  const CATEGORIES = Object.freeze({
    SERIES:         1,
    MOVIES:         2,
    SERIES_HD:     41,
    MOVIES_HD:     42,
    SERIES_4K:     65,
    MOVIES_4K:     66,
    DUBBED_SERIES: 83,
    DUBBED_MOVIES: 84,
  });

  /* ── State ─────────────────────────────────────────────────────── */
  let sessionCookie = null;
  let loggedIn      = false;

  /* ── Internal Helpers ──────────────────────────────────────────── */

  /**
   * Restore saved session from Storage on load.
   */
  function _restoreSession() {
    try {
      const saved = window.Storage ? window.Storage.get('fuzer_session') : null;
      if (saved && saved.cookie) {
        sessionCookie = saved.cookie;
        loggedIn = true;
      }
    } catch {}
  }

  /**
   * Make a fetch request to Fuzer with session cookies.
   * @param {string} path
   * @param {Object} [options]
   * @returns {Promise<Response>}
   */
  function _fuzerFetch(path, options = {}) {
    const url = path.startsWith('http') ? path : `${BASE_URL}${path}`;

    const headers = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      ...(options.headers || {}),
    };

    if (sessionCookie) {
      headers['Cookie'] = sessionCookie;
    }

    return fetch(url, {
      ...options,
      headers,
      credentials: 'include',
    });
  }

  /**
   * Parse torrent rows from Fuzer HTML response.
   * @param {string} html  Raw HTML
   * @returns {Array<Object>}
   */
  function _parseResults(html) {
    const results = [];

    try {
      const parser = new DOMParser();
      const doc = parser.parseFromString(html, 'text/html');

      // Look for torrent rows in the results table
      const rows = doc.querySelectorAll('tr.box_torrent, tr.box_torrent_alt, table.table-striped tbody tr, .torrent-row');

      rows.forEach(row => {
        try {
          const titleEl   = row.querySelector('a[href*="showthread"], a.torrent-title, td:nth-child(2) a, .torrent-name a, a[href*="viewtopic"]');
          const tds       = row.querySelectorAll('td');
          const sizeEl    = tds[3] || row.querySelector('td.size, td:nth-child(4), .torrent-size');
          const seedEl    = tds[5] || row.querySelector('td.r22, td.seeds, td.seeders, td:nth-child(6), .torrent-seeders');
          const leechEl   = tds[6] || row.querySelector('td.r23, td.leeches, td.leechers, td:nth-child(7), .torrent-leechers');
          const dateEl    = row.querySelector('td.date, td:nth-child(3), .torrent-date');
          const magnetEl  = row.querySelector('a[href^="magnet:"], a.magnet-link');
          const catEl     = row.querySelector('td.category img, td:first-child img');

          if (!titleEl) return;

          // Extract torrent ID from the link
          const href = titleEl.getAttribute('href') || '';
          const idMatch = href.match(/t=(\d+)|attachmentid=(\d+)|viewtopic.*?(\d+)/);
          const torrentId = idMatch ? (idMatch[1] || idMatch[2] || idMatch[3]) : null;

          const rawSeed = seedEl ? seedEl.textContent.trim().replace(/,/g, '') : '0';
          const rawLeech = leechEl ? leechEl.textContent.trim().replace(/,/g, '') : '0';

          results.push({
            id:         torrentId,
            title:      titleEl.textContent.trim(),
            size:       sizeEl ? sizeEl.textContent.trim() : '—',
            seeders:    parseInt(rawSeed, 10) || 0,
            leechers:   parseInt(rawLeech, 10) || 0,
            date:       dateEl ? dateEl.textContent.trim() : '',
            magnetLink: magnetEl ? magnetEl.getAttribute('href') : null,
            categoryId: catEl ? (catEl.getAttribute('alt') || '') : '',
            url:        href.startsWith('http') ? href : `${BASE_URL}/${href}`,
          });
        } catch {
          // Skip malformed rows
        }
      });
    } catch (err) {
      console.error('[Fuzer] Parse error:', err);
    }

    return results;
  }

  /* ── Public API ────────────────────────────────────────────────── */

  /**
   * Login to Fuzer with username and password.
   * @param {string} username
   * @param {string} password
   * @returns {Promise<boolean>} true if login succeeded
   */
  async function login(username, password) {
    try {
      const formData = new URLSearchParams({
        username: username,
        password: password,
        autologin: 'on',
        login:     'כניסה',
      });

      const res = await _fuzerFetch('/login.php', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: formData.toString(),
        redirect: 'manual',
      });

      // Check for Set-Cookie header or successful redirect
      const setCookie = res.headers.get('set-cookie');
      if (setCookie) {
        sessionCookie = setCookie;
      }

      // Verify login by checking response
      const text = await res.text();
      const success = !text.includes('שם משתמש או סיסמה שגויים') &&
                      !text.includes('login_failed') &&
                      (res.status === 200 || res.status === 302);

      if (success) {
        loggedIn = true;

        // Save credentials (base64-encoded for minimal obfuscation)
        if (window.Storage) {
          window.Storage.set('fuzer_session', {
            cookie:   sessionCookie,
            username: username,
          });
          window.Storage.set('fuzer_creds', {
            u: btoa(username),
            p: btoa(password),
          });
        }

        console.log('[Fuzer] Login successful.');
        return true;
      }

      console.warn('[Fuzer] Login failed.');
      return false;
    } catch (err) {
      console.error('[Fuzer] Login error:', err);
      return false;
    }
  }

  /**
   * Check if currently logged in to Fuzer.
   * @returns {boolean}
   */
  function isLoggedIn() {
    return loggedIn && !!sessionCookie;
  }

  /**
   * Browse a specific category.
   * @param {number} categoryId  One of CATEGORIES values
   * @param {number} [page=0]    Page number (0-indexed)
   * @returns {Promise<Array<Object>>}  Array of torrent results
   */
  async function getCategory(categoryId, page = 0) {
    try {
      const start = page * 25; // Fuzer uses 25 per page
      const res = await _fuzerFetch(
        `/browse.php?cat=${categoryId}&start=${start}`,
        { method: 'GET' }
      );

      if (!res.ok) {
        throw new Error(`Fuzer ${res.status}: ${res.statusText}`);
      }

      const html = await res.text();
      return _parseResults(html);
    } catch (err) {
      console.error('[Fuzer] getCategory failed:', err);
      return [];
    }
  }

  /**
   * Percent-encode string in windows-1255 charset for Hebrew search on Fuzer.
   * @param {string} str
   * @returns {string}
   */
  function encodeWindows1255(str) {
    let result = '';
    for (let i = 0; i < str.length; i++) {
      const code = str.charCodeAt(i);
      if (code >= 0x05D0 && code <= 0x05EA) {
        // Hebrew letters \u05D0-\u05EA map to 0xE0-0xFA in Windows-1255
        const hex = (code - 0x05D0 + 0xE0).toString(16).toUpperCase();
        result += '%' + hex;
      } else if (
        (code >= 0x30 && code <= 0x39) || // 0-9
        (code >= 0x41 && code <= 0x5A) || // A-Z
        (code >= 0x61 && code <= 0x7A) || // a-z
        code === 0x2D || code === 0x2E || code === 0x5F || code === 0x7E // - . _ ~
      ) {
        result += str[i];
      } else if (code === 0x20) {
        result += '%20';
      } else {
        result += encodeURIComponent(str[i]);
      }
    }
    return result;
  }

  /**
   * Search Fuzer for torrents (supports both Hebrew and English).
   * @param {string} query  Search query
   * @returns {Promise<Array<Object>>}  Array of torrent results
   */
  async function search(query) {
    if (!query || !query.trim()) return [];

    try {
      const encoded = encodeWindows1255(query.trim());
      const res = await _fuzerFetch(
        `/browse.php?ref_=basic&query=${encoded}&matchquery=any`,
        { method: 'GET' }
      );

      if (!res.ok) {
        throw new Error(`Fuzer search ${res.status}: ${res.statusText}`);
      }

      const html = await res.text();
      return _parseResults(html);
    } catch (err) {
      console.error('[Fuzer] Search failed:', err);
      return [];
    }
  }

  /**
   * Get the magnet link for a specific torrent ID.
   * Fetches the torrent detail page and extracts the magnet URI.
   * @param {string|number} torrentId
   * @returns {Promise<string|null>}  Magnet URI or null
   */
  async function getMagnetLink(torrentId) {
    if (!torrentId) return null;

    try {
      const res = await _fuzerFetch(
        `/viewtopic.php?t=${torrentId}`,
        { method: 'GET' }
      );

      if (!res.ok) return null;

      const html = await res.text();

      // Extract magnet link from the page
      const magnetMatch = html.match(/href="(magnet:\?[^"]+)"/);
      if (magnetMatch) {
        return magnetMatch[1];
      }

      // Try alternative pattern
      const altMatch = html.match(/magnet:\?xt=urn:btih:[a-fA-F0-9]{40}[^"'\s]*/);
      if (altMatch) {
        return altMatch[0];
      }

      return null;
    } catch (err) {
      console.error('[Fuzer] getMagnetLink failed:', err);
      return null;
    }
  }

  /**
   * Logout from Fuzer — clear session data.
   */
  function logout() {
    sessionCookie = null;
    loggedIn = false;
    if (window.Storage) {
      window.Storage.remove('fuzer_session');
      window.Storage.remove('fuzer_creds');
    }
  }

  // Restore session on load
  _restoreSession();

  /* ── Export ────────────────────────────────────────────────────── */
  window.Fuzer = Object.freeze({
    CATEGORIES,
    login,
    isLoggedIn,
    logout,
    getCategory,
    search,
    getMagnetLink,
  });
})();
