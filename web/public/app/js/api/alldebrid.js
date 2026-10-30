/**
 * LuminaStreams — AllDebrid API Client
 * Complete integration: Pin authentication, token management,
 * magnet handling, file selection, link unlocking, stream resolution.
 */
(() => {
  'use strict';

  const AD_BASE = 'https://api.alldebrid.com/v4';
  const AD_BASE_V41 = 'https://api.alldebrid.com/v4.1';
  const DEFAULT_TOKEN = 'GkRRCMwdGBSvjzWqnNP8';
  const STORAGE_KEY = 'lumina_ad_token';

  function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
  }

  function getToken() {
    return localStorage.getItem(STORAGE_KEY) || DEFAULT_TOKEN;
  }

  function setToken(token) {
    if (token) {
      localStorage.setItem(STORAGE_KEY, token);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  function isAuthenticated() {
    return !!getToken();
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEY);
  }

  async function adFetch(url, options = {}) {
    const token = getToken();
    const headers = {
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...options.headers,
    };
    const res = await fetch(url, { ...options, headers });
    if (!res.ok) {
      throw new Error(`AllDebrid HTTP ${res.status}: ${res.statusText}`);
    }
    const data = await res.json();
    if (data.status !== 'success') {
      const msg = data.error?.message || data.error?.code || 'AllDebrid API error';
      throw new Error(msg);
    }
    return data.data;
  }

  /* ── User / Account ──────────────────────────────────────────────── */
  async function getUser() {
    const data = await adFetch(`${AD_BASE}/user?agent=LuminaStreams`);
    const u = data.user || data;
    return {
      username: u.username,
      email: u.email,
      isPremium: u.isPremium,
      expiration: u.premiumUntil ? u.premiumUntil * 1000 : null,
    };
  }

  /* ── Pin Auth Flow ───────────────────────────────────────────────── */
  async function startAuth() {
    const data = await adFetch(`${AD_BASE}/pin/get?agent=LuminaStreams`);
    return {
      pin: data.pin,
      check: data.check,
      userUrl: `${data.base_url}?pin=${data.pin}`,
      verificationUrl: `${data.base_url}?pin=${data.pin}`,
      userCode: data.pin,
      deviceCode: data.check,
      interval: 5,
    };
  }

  async function pollAuth(check, pin) {
    const MAX_ATTEMPTS = 60;
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      await sleep(5000);
      try {
        const res = await fetch(`${AD_BASE}/pin/check?agent=LuminaStreams&check=${encodeURIComponent(check)}&pin=${encodeURIComponent(pin)}`);
        if (!res.ok) continue;
        const json = await res.json();
        if (json.status === 'success' && json.data && json.data.activated) {
          const apikey = json.data.apikey;
          setToken(apikey);
          return apikey;
        }
      } catch (e) {
        // continue polling
      }
    }
    throw new Error('AllDebrid PIN authentication timed out');
  }

  /* ── Magnet / Stream Resolution ──────────────────────────────────── */
  async function uploadMagnet(magnetUri) {
    const token = getToken();
    const form = new URLSearchParams();
    form.append('magnets[]', magnetUri);

    const res = await fetch(`${AD_BASE}/magnet/upload?agent=LuminaStreams`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: form.toString(),
    });
    if (!res.ok) throw new Error(`AllDebrid magnet upload HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') {
      throw new Error(json.error?.message || 'Failed to upload magnet');
    }
    const magnetInfo = json.data?.magnets?.[0];
    if (!magnetInfo || magnetInfo.error) {
      throw new Error(magnetInfo?.error?.message || 'Magnet upload failed');
    }
    return magnetInfo.id;
  }

  async function getMagnetStatus(id) {
    const token = getToken();
    const res = await fetch(`${AD_BASE_V41}/magnet/status?id=${encodeURIComponent(id)}&agent=LuminaStreams`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(`AllDebrid status HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') {
      throw new Error(json.error?.message || 'Status check failed');
    }
    const m = json.data?.magnets;
    return Array.isArray(m) ? m[0] : m;
  }

  /** File tree for a magnet (/v4/magnet/files) — used when status omits it. */
  async function getMagnetFiles(id) {
    const form = new URLSearchParams();
    form.append('id[]', id);
    const res = await fetch(`${AD_BASE}/magnet/files?agent=LuminaStreams`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getToken()}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: form.toString(),
    });
    if (!res.ok) throw new Error(`AllDebrid files HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') {
      throw new Error(json.error?.message || 'Files lookup failed');
    }
    return json.data?.magnets?.[0]?.files || [];
  }

  async function unlockLink(link) {
    const token = getToken();
    const form = new URLSearchParams();
    form.append('link', link);

    const res = await fetch(`${AD_BASE}/link/unlock?agent=LuminaStreams`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: form.toString(),
    });
    if (!res.ok) throw new Error(`AllDebrid unlock HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') {
      throw new Error(json.error?.message || 'Failed to unlock link');
    }
    return json.data?.link;
  }

  const VIDEO_EXTENSIONS = ['.mkv', '.mp4', '.avi', '.mov', '.webm', '.m4v', '.ts'];

  function flattenFiles(items) {
    let result = [];
    if (!items) return result;
    if (!Array.isArray(items)) items = [items];
    for (const item of items) {
      if (item && item.e && Array.isArray(item.e)) {
        result = result.concat(flattenFiles(item.e));
      } else if (item) {
        result.push(item);
      }
    }
    return result;
  }

  function selectBestFile(files, season = null, episode = null) {
    if (!files || files.length === 0) return null;
    const flattened = flattenFiles(files);
    if (flattened.length === 0) return null;

    // Filter out files without links or non-video
    const withLinks = flattened.filter(f => !!(f.l || f.link));
    const validPool = withLinks.length > 0 ? withLinks : flattened;

    // Filter out samples / nfo / txt
    const nonSamples = validPool.filter(f => {
      const name = (f.n || f.filename || '').toLowerCase();
      return !name.includes('sample') && !name.endsWith('.nfo') && !name.endsWith('.txt');
    });
    const candidates = nonSamples.length > 0 ? nonSamples : validPool;

    // Filter videos
    const videos = candidates.filter(f => {
      const name = (f.n || f.filename || '').toLowerCase();
      return VIDEO_EXTENSIONS.some(ext => name.endsWith(ext));
    });
    const pool = videos.length > 0 ? videos : candidates;

    // If season and episode are specified, try to find the matching episode file
    if (season != null && episode != null) {
      const sNum = parseInt(season, 10);
      const eNum = parseInt(episode, 10);
      const epPatterns = [
        new RegExp(`s0*${sNum}e0*${eNum}\\b`, 'i'),
        new RegExp(`0*${sNum}x0*${eNum}\\b`, 'i'),
        new RegExp(`ep0*${eNum}\\b`, 'i'),
        new RegExp(`episode\\s*0*${eNum}\\b`, 'i'),
      ];
      const epMatch = pool.filter(f => {
        const name = (f.n || f.filename || '').toLowerCase();
        return epPatterns.some(p => p.test(name));
      });
      if (epMatch.length > 0) {
        epMatch.sort((a, b) => (b.s || b.size || 0) - (a.s || a.size || 0));
        return epMatch[0];
      }
    }

    pool.sort((a, b) => (b.s || b.size || 0) - (a.s || a.size || 0));
    return pool[0];
  }

  /**
   * Resolve a magnet link or hash into a playable direct streaming URL.
   * @param {string} magnetOrHash
   * @param {Function} [onProgress]
   * @param {number|null} [season]
   * @param {number|null} [episode]
   * @returns {Promise<string>}
   */
  async function resolveStream(magnetOrHash, onProgress, season = null, episode = null) {
    const report = (msg) => {
      if (typeof onProgress === 'function') onProgress(msg);
    };

    let magnet = magnetOrHash;
    if (!magnet.startsWith('magnet:?')) {
      magnet = `magnet:?xt=urn:btih:${magnetOrHash}`;
    }

    report('מעלה ל-AllDebrid…');
    const torrentId = await uploadMagnet(magnet);
    return waitAndUnlock(torrentId, report, season, episode, 60);
  }

  /**
   * Upload raw .torrent bytes (Fuzer releases) and resolve them to a stream URL.
   * @param {Uint8Array} bytes
   */
  async function resolveTorrentFile(bytes, onProgress, season = null, episode = null) {
    const report = (msg) => {
      if (typeof onProgress === 'function') onProgress(msg);
    };
    report('מעלה קובץ טורנט ל-AllDebrid…');
    const form = new FormData();
    form.append('files[]', new Blob([bytes], { type: 'application/x-bittorrent' }), 'upload.torrent');
    const res = await fetch(`${AD_BASE}/magnet/upload/file?agent=LuminaStreams`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}` },
      body: form,
    });
    if (!res.ok) throw new Error(`AllDebrid upload HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') throw new Error(json.error?.message || 'Torrent upload failed');
    const file = json.data?.files?.[0];
    if (!file || file.error) throw new Error(file?.error?.message || 'Torrent upload failed');
    // Android polls torrent-file uploads for up to 150 × 2 s.
    return waitAndUnlock(file.id, report, season, episode, 150);
  }

  /** Poll a magnet until AllDebrid has it, pick the best file and unlock it. */
  async function waitAndUnlock(torrentId, report, season, episode, maxPolls) {
    report('ממתין לעיבוד ב-AllDebrid…');
    const POLL_INTERVAL = 2000;

    for (let i = 0; i < maxPolls; i++) {
      const statusData = await getMagnetStatus(torrentId);
      if (!statusData) {
        await sleep(POLL_INTERVAL);
        continue;
      }

      const statusCode = statusData.statusCode ?? 0;
      if (statusCode === 4) {
        // Ready!
        let files = statusData.files || statusData.links || [];
        if (files.length === 0) files = await getMagnetFiles(torrentId);
        const best = selectBestFile(files, season, episode);
        if (!best) throw new Error('לא נמצאו קבצי וידאו בטורנט');

        const linkToUnlock = best.l || best.link || (typeof best === 'string' ? best : null);
        if (!linkToUnlock) throw new Error('לא נמצא קישור תקין להזרמה בקובץ זה');

        report('פותח קישור AllDebrid…');
        const directUrl = await unlockLink(linkToUnlock);
        report('מוכן להפעלה!');
        return directUrl;
      } else if (statusCode > 4) {
        throw new Error(`שגיאה בהורדת הטורנט ב-AllDebrid (${statusData.status || statusCode})`);
      } else if (statusCode >= 1 && statusCode <= 3) {
        const percent = statusData.size > 0
          ? Math.round((statusData.downloaded / statusData.size) * 100)
          : 0;
        report(`מוריד ב-AllDebrid… ${percent}%`);
      }

      await sleep(POLL_INTERVAL);
    }

    throw new Error('זמן הפענוח ב-AllDebrid פג (Timeout)');
  }

  const AllDebrid = Object.freeze({
    getToken,
    setToken,
    isAuthenticated,
    logout,
    getUser,
    startAuth,
    pollAuth,
    uploadMagnet,
    getMagnetStatus,
    getMagnetFiles,
    unlockLink,
    resolveStream,
    resolveTorrentFile,
  });

  window.AllDebrid = AllDebrid;
})();
