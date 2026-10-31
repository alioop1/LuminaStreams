/**
 * LuminaStreams TV — IPTV, 1:1 with the Android TV app:
 *   IptvScreen        PS5 category ribbon, hero / Recently Watched circles, 16:9 channel grid,
 *                     in-screen EPG guide, IPTV settings, empty state, cinematic screensaver
 *   IptvSetupOverlay  manual setup (the phone/QR setup needs Android's LocalWebServer: replaced
 *                     by typing with the TV keyboard)
 *   IptvPlayerScreen  live player: OSD, channel zapping, options panel, catch-up seek bar
 *   EpgGuideOverlay   7-day guide with catch-up
 * M3uParser / EpgParser are ported to JS; channels and the guide live in IndexedDB
 * (Room on Android), favourites / recents / settings in Storage.
 * Sizes are Android dp (1 CSS px = 1 dp), colours from the Compose code.
 */
(() => {
  'use strict';
  if (typeof window === 'undefined' || !window.TV) return;
  const { tr, esc, icon, el } = TV;

  /* ═══ Constants ═══════════════════════════════════════════════════════ */
  const DAY = 86400000;
  const K = {
    playlist: 'iptv_tv_playlist',
    fav: 'iptv_tv_favorites',
    recent: 'iptv_tv_recent',
    settings: 'iptv_tv_settings',
    lastGroup: 'iptv_tv_last_group',
  };
  // getAmbientGlow(): Crimson, Cyber Blue, Deep Purple, Sunset Orange, Neon Green (alpha 0.15)
  const HUES = ['229,9,20', '0,229,255', '138,43,226', '255,149,0', '50,215,75'];
  // LazyVerticalGrid(GridCells.Adaptive(280.dp), spacedBy 24, contentPadding top 16 / bottom 64), 48 dp side padding.
  const GRID_W = TV.W - 96, GAP = 24;
  const COLS = Math.max(1, Math.floor((GRID_W + GAP) / (280 + GAP)));
  const CELL_W = (GRID_W - GAP * (COLS - 1)) / COLS;
  const CELL_H = CELL_W * 9 / 16;
  const PITCH = CELL_H + GAP, GRID_TOP = 16;
  // EpgGuideScreen rows: 220 dp channel box + 16 dp spacer, rows 80 dp spaced 16.
  const GUIDE_ROW = 96, GUIDE_VIEW_W = GRID_W - 236;

  const PATHS = {
    edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
    del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    link: 'M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z',
  };
  const ico = (name, size) => `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path d="${PATHS[name]}"/></svg>`;

  /* ═══ Small helpers ═══════════════════════════════════════════════════ */
  const two = n => String(n).padStart(2, '0');
  const hhmm = t => { const d = new Date(t); return `${two(d.getHours())}:${two(d.getMinutes())}`; };
  const fmtDur = ms => {
    const s = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
    return h > 0 ? `${h}:${two(m)}:${two(x)}` : `${m}:${two(x)}`;
  };
  const sleep0 = () => new Promise(r => setTimeout(r, 0));
  /** Java String.hashCode (Android derives channel ids and glow colours from it). */
  const jhash = str => { let h = 0; for (let i = 0; i < str.length; i++) h = (Math.imul(31, h) + str.charCodeAt(i)) | 0; return h; };
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const pct = (now, s, e) => clamp((now - s) / Math.max(1, e - s), 0, 1) * 100;
  const isInput = () => {
    const a = document.activeElement;
    return a && a.tagName === 'INPUT' ? a : null;
  };

  const St = {
    get(k, d) {
      try {
        if (window.Storage && window.Storage.get) return window.Storage.get(k, d);
        const raw = localStorage.getItem(`lumina_${k}`);
        return raw == null ? d : JSON.parse(raw);
      } catch { return d; }
    },
    set(k, v) {
      try {
        if (window.Storage && window.Storage.set) window.Storage.set(k, v);
        else localStorage.setItem(`lumina_${k}`, JSON.stringify(v));
      } catch {}
    },
    del(k) {
      try {
        if (window.Storage && window.Storage.remove) window.Storage.remove(k);
        else localStorage.removeItem(`lumina_${k}`);
      } catch {}
    },
  };

  /* IptvSettingsViewModel defaults (Android keeps them in memory; here they persist). */
  const DEFAULTS = { autoStart: false, parental: false, catchup: true, buffer: 1, epgSync: 0, cat: 2, decoder: 0 };
  const settings = () => Object.assign({}, DEFAULTS, St.get(K.settings, null) || {});
  const OPTIONS = {
    buffer: () => [tr('Fast Start', 'התחלה מהירה'), tr('Stable (Recommended)', 'יציב (מומלץ)'), tr('Deep Buffer (Slow Net)', 'באפר עמוק (רשת איטית)')],
    epgSync: () => [tr('On Boot', 'בכל הפעלה'), tr('Every 12 Hours', 'כל 12 שעות'), tr('Every 24 Hours', 'כל 24 שעות')],
    cat: () => [tr('All Channels', 'כל הערוצים'), tr('Favorites', 'מועדפים'), tr('Last Viewed', 'נצפה לאחרונה')],
    decoder: () => [tr('Hardware (Fast)', 'חומרה (מהיר)'), tr('Software (Safe)', 'תוכנה (בטוח)')],
  };

  /** Compose LazyRow bring-into-view: scroll the row by the least amount that shows `item` (dp, RTL-aware). */
  function rowReveal(view, track, item) {
    if (!view || !track || !item) return;
    const vw = view.offsetWidth, tw = track.offsetWidth;
    const start = TV.rtl() ? tw - item.offsetLeft - item.offsetWidth : item.offsetLeft;
    const end = start + item.offsetWidth;
    let x = Number(track.dataset.x || 0);
    if (item === track.firstElementChild) x = 0;
    else if (end > x + vw) x = end - vw;
    else if (start < x) x = start;
    x = Math.max(0, x);
    track.dataset.x = String(x);
    track.style.transform = `translateX(${TV.rtl() ? x : -x}px)`;
  }

  /* ═══ IndexedDB (Room on Android): one key/value store ═══════════════ */
  const IDB = (() => {
    let dbp = null;
    const mem = new Map();
    const open = () => {
      if (!dbp) {
        dbp = new Promise((resolve, reject) => {
          if (!window.indexedDB) { reject(new Error('no indexedDB')); return; }
          const r = indexedDB.open('lumina_iptv', 1);
          r.onupgradeneeded = () => r.result.createObjectStore('kv');
          r.onsuccess = () => resolve(r.result);
          r.onerror = () => reject(r.error);
        });
      }
      return dbp;
    };
    const run = (mode, fn) => open().then(db => new Promise((resolve, reject) => {
      const t = db.transaction('kv', mode);
      const req = fn(t.objectStore('kv'));
      t.oncomplete = () => resolve(req && mode === 'readonly' ? req.result : undefined);
      t.onerror = () => reject(t.error);
      t.onabort = () => reject(t.error);
    }));
    return {
      get: k => run('readonly', s => s.get(k)).catch(() => mem.get(k)),
      set: (k, v) => run('readwrite', s => { s.put(v, k); }).catch(() => { mem.set(k, v); }),
      del: k => { mem.delete(k); return run('readwrite', s => { s.delete(k); }).catch(() => {}); },
    };
  })();

  /* ═══ M3uParser.parseLines ════════════════════════════════════════════ */
  const ATTR = /([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))/g;
  function parseM3u(text) {
    const out = [];
    const seen = new Set();
    let info = null, group = null, num = 1;
    const lines = text.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      let line = lines[i];
      if (i === 0) line = line.replace(/^﻿/, '');
      line = line.trim();
      if (!line) continue;
      if (line.startsWith('#EXTINF:')) {
        const a = {};
        ATTR.lastIndex = 0;
        let m;
        while ((m = ATTR.exec(line))) a[m[1].toLowerCase()] = (m[2] || m[3] || m[4] || '').trim();
        const comma = line.lastIndexOf(',');
        a._name = (comma >= 0 ? line.slice(comma + 1) : (a['tvg-name'] || `Channel ${num}`)).trim();
        info = a;
        if (a['group-title'] != null) group = a['group-title'];
      } else if (line.startsWith('#EXTGRP:')) {
        group = line.slice(8).trim();
      } else if (!line.startsWith('#') && info) {
        const g = info['group-title'] != null ? info['group-title'] : (group != null ? group : 'General');
        if (!/adult|xxx/i.test(g)) {
          const name = info._name || `Channel ${num}`;
          const tvgId = info['tvg-id'] || '';
          let id = tvgId || `${jhash(name)}_${num}`;
          if (seen.has(id)) id = `${id}_${num}`; // Room would drop the duplicate; keep the channel instead
          seen.add(id);
          out.push({
            id, name, url: line, number: num, tvgId,
            tvgName: info['tvg-name'] || '',
            logo: info['tvg-logo'] || info.logo || info.icon || '',
            group: g || 'General',
          });
          num++;
        }
        info = null;
      }
    }
    return out;
  }

  /* ═══ gzip (XMLTV guides are often .xml.gz; webOS 6 has no DecompressionStream) ═══ */
  const LBASE = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
  const LEXT = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
  const DBASE = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577];
  const DEXT = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];
  const CLORDER = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];

  /** Canonical Huffman code → direct lookup table indexed by the next (bit-reversed) bits. */
  function huffTable(lens, n) {
    let max = 0;
    for (let i = 0; i < n; i++) if (lens[i] > max) max = lens[i];
    if (!max) max = 1;
    const count = new Uint16Array(16);
    for (let i = 0; i < n; i++) count[lens[i]]++;
    count[0] = 0;
    const next = new Uint16Array(16);
    let code = 0;
    for (let b = 1; b <= 15; b++) { code = (code + count[b - 1]) << 1; next[b] = code; }
    const size = 1 << max;
    const t = new Int32Array(size);
    for (let s = 0; s < n; s++) {
      const L = lens[s];
      if (!L) continue;
      const c = next[L]++;
      let r = 0;
      for (let i = 0; i < L; i++) r |= ((c >> i) & 1) << (L - 1 - i);
      for (let j = r; j < size; j += 1 << L) t[j] = (L << 16) | s;
    }
    return { t, mask: size - 1, bits: max };
  }

  /** Inflate a gzip file, handing out decoded chunks; yields to the UI between blocks. */
  async function gunzip(src, onChunk) {
    if (src[0] !== 0x1f || src[1] !== 0x8b || src[2] !== 8) throw new Error('bad gzip');
    const flg = src[3];
    let pos = 10;
    if (flg & 4) pos += 2 + (src[pos] | (src[pos + 1] << 8));
    if (flg & 8) while (src[pos++]);
    if (flg & 16) while (src[pos++]);
    if (flg & 2) pos += 2;

    const len = src.length;
    let bitbuf = 0, bitcnt = 0;
    const need = n => {
      while (bitcnt < n) {
        if (pos > len + 8) throw new Error('truncated gzip');
        bitbuf |= (pos < len ? src[pos] : 0) << bitcnt;
        pos++;
        bitcnt += 8;
      }
    };
    const bits = n => { need(n); const v = bitbuf & ((1 << n) - 1); bitbuf >>>= n; bitcnt -= n; return v; };
    const sym = h => {
      need(h.bits);
      const e = h.t[bitbuf & h.mask];
      const L = e >>> 16;
      if (!L) throw new Error('bad deflate code');
      bitbuf >>>= L;
      bitcnt -= L;
      return e & 0xffff;
    };
    const W = 32768, CAP = 1 << 18;
    const out = new Uint8Array(CAP);
    let op = 0;
    const flush = keep => {
      const end = op - keep;
      if (end <= 0) return;
      onChunk(out.slice(0, end));
      out.copyWithin(0, end, op);
      op -= end;
    };
    let fixedL = null, fixedD = null, blocks = 0;
    for (;;) {
      const final = bits(1), type = bits(2);
      if (type === 0) {
        pos -= bitcnt >> 3;
        bitbuf = 0;
        bitcnt = 0;
        const n = src[pos] | (src[pos + 1] << 8);
        pos += 4;
        for (let i = 0; i < n; i++) { if (op === CAP) flush(W); out[op++] = src[pos++]; }
      } else {
        let L, D;
        if (type === 1) {
          if (!fixedL) {
            const l = new Uint8Array(288);
            l.fill(8, 0, 144); l.fill(9, 144, 256); l.fill(7, 256, 280); l.fill(8, 280, 288);
            fixedL = huffTable(l, 288);
            fixedD = huffTable(new Uint8Array(30).fill(5), 30);
          }
          L = fixedL; D = fixedD;
        } else if (type === 2) {
          const hlit = bits(5) + 257, hdist = bits(5) + 1, hclen = bits(4) + 4;
          const cl = new Uint8Array(19);
          for (let i = 0; i < hclen; i++) cl[CLORDER[i]] = bits(3);
          const ct = huffTable(cl, 19);
          const lens = new Uint8Array(hlit + hdist);
          for (let i = 0; i < hlit + hdist;) {
            const s = sym(ct);
            if (s < 16) { lens[i++] = s; continue; }
            let rep, val = 0;
            if (s === 16) { if (!i) throw new Error('bad deflate lengths'); val = lens[i - 1]; rep = 3 + bits(2); }
            else if (s === 17) rep = 3 + bits(3);
            else rep = 11 + bits(7);
            while (rep-- && i < lens.length) lens[i++] = val;
          }
          L = huffTable(lens.subarray(0, hlit), hlit);
          D = huffTable(lens.subarray(hlit), hdist);
        } else {
          throw new Error('bad deflate block');
        }
        for (;;) {
          let s = sym(L);
          if (s < 256) { if (op === CAP) flush(W); out[op++] = s; continue; }
          if (s === 256) break;
          s -= 257;
          const n = LBASE[s] + bits(LEXT[s]);
          const ds = sym(D);
          const d = DBASE[ds] + bits(DEXT[ds]);
          for (let i = 0; i < n; i++) { if (op === CAP) flush(W); out[op] = out[op - d]; op++; }
        }
      }
      if (final) break;
      if (++blocks % 6 === 0) await sleep0();
    }
    flush(0);
  }

  /* ═══ EpgParser (XMLTV), streamed ═════════════════════════════════════ */
  const RE = {
    id: /\sid\s*=\s*(?:"([^"]*)"|'([^']*)')/,
    channel: /\schannel\s*=\s*(?:"([^"]*)"|'([^']*)')/,
    start: /\sstart\s*=\s*(?:"([^"]*)"|'([^']*)')/,
    stop: /\sstop\s*=\s*(?:"([^"]*)"|'([^']*)')/,
    src: /<icon\b[^>]*\ssrc\s*=\s*(?:"([^"]*)"|'([^']*)')/,
    dname: /<display-name\b[^>]*>([\s\S]*?)<\/display-name>/,
    title: /<title\b[^>]*>([\s\S]*?)<\/title>/,
    desc: /<desc\b[^>]*>([\s\S]*?)<\/desc>/,
  };
  const ENT = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
  const xmlText = str => str
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (all, e) => {
      if (e[0] !== '#') return ENT[e] != null ? ENT[e] : all;
      const cp = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : Number(e.slice(1));
      try { return String.fromCodePoint(cp); } catch { return ''; }
    });
  const pick = (re, s) => { const m = re.exec(s); return m ? xmlText(m[1] != null ? m[1] : (m[2] || '')).trim() : ''; };
  /** "yyyyMMddHHmmss Z"; no offset = UTC (XMLTV convention, as EpgParser.parseTime). */
  function xmltvTime(v) {
    const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-])?(\d{2})?(\d{2})?/.exec((v || '').trim());
    if (!m) return null;
    let t = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0));
    if (m[7] && m[8]) t -= (m[7] === '-' ? -1 : 1) * (Number(m[8]) * 60 + Number(m[9] || 0)) * 60000;
    return t;
  }

  function epgParser() {
    const minStop = Date.now() - 7 * DAY; // EPG_LOOKBACK_MS
    const names = Object.create(null);
    const icons = Object.create(null);
    const progs = Object.create(null);
    let dec = null, buf = '', count = 0;
    const TAG = /<(channel|programme)[\s>/]/g;

    function handle(tag, body) {
      if (tag === 'channel') {
        const id = pick(RE.id, body);
        if (!id) return;
        const dn = pick(RE.dname, body);
        if (dn && !(id in names)) names[id] = dn;
        const src = pick(RE.src, body);
        if (src) icons[id] = src;
        return;
      }
      const ch = pick(RE.channel, body);
      if (!ch) return;
      const s = xmltvTime(pick(RE.start, body));
      const e = xmltvTime(pick(RE.stop, body));
      if (s == null || e == null || e <= minStop) return;
      const key = (names[ch] || ch).toLowerCase();
      (progs[key] || (progs[key] = [])).push([s, e, pick(RE.title, body), pick(RE.desc, body).slice(0, 300)]);
      count++;
    }
    function scan() {
      TAG.lastIndex = 0;
      let keep = -1, lastEnd = 0, m;
      while ((m = TAG.exec(buf))) {
        const s = m.index, tag = m[1];
        const gt = buf.indexOf('>', s);
        if (gt < 0) { keep = s; break; }
        let e;
        if (buf.charCodeAt(gt - 1) === 47 /* / */) e = gt + 1;
        else {
          const close = buf.indexOf(`</${tag}>`, gt);
          if (close < 0) { keep = s; break; }
          e = close + tag.length + 3;
        }
        handle(tag, buf.slice(s, e));
        lastEnd = e;
        TAG.lastIndex = e;
      }
      buf = keep >= 0 ? buf.slice(keep) : buf.slice(Math.max(lastEnd, buf.length - 16));
    }
    return {
      bytes(b) {
        if (!dec) {
          // Honour the declared encoding (windows-1255 / iso-8859-8 Hebrew guides).
          const head = String.fromCharCode.apply(null, Array.from(b.subarray(0, Math.min(256, b.length))));
          const m = /encoding\s*=\s*["']([\w.:-]+)["']/i.exec(head);
          try { dec = new TextDecoder(m ? m[1].toLowerCase() : 'utf-8'); } catch { dec = new TextDecoder('utf-8'); }
        }
        buf += dec.decode(b, { stream: true });
        scan();
      },
      end() {
        if (dec) buf += dec.decode();
        scan();
        const alias = Object.create(null), logos = Object.create(null);
        for (const id in names) {
          const n = names[id].toLowerCase();
          if (id.toLowerCase() !== n) alias[id.toLowerCase()] = n;
        }
        for (const id in icons) {
          const src = icons[id];
          logos[id.toLowerCase()] = src;
          if (names[id]) {
            const n = names[id].toLowerCase();
            logos[n] = src;
            logos[n.replace(/\s+/g, '')] = src;
          }
        }
        for (const k in progs) progs[k].sort((a, b) => a[0] - b[0]);
        return { p: progs, a: alias, l: logos, n: count };
      },
    };
  }

  async function fetchEpg(url) {
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const P = epgParser();
    const reader = res.body && res.body.getReader ? res.body.getReader() : null;
    let first;
    if (reader) { const r = await reader.read(); first = r.done ? new Uint8Array(0) : r.value; }
    else first = new Uint8Array(await res.arrayBuffer());
    const gz = first.length > 2 && first[0] === 0x1f && first[1] === 0x8b;
    if (!gz) {
      P.bytes(first);
      let n = 0;
      if (reader) {
        for (;;) {
          const r = await reader.read();
          if (r.done) break;
          P.bytes(r.value);
          if (++n % 8 === 0) await sleep0();
        }
      }
      return P.end();
    }
    const parts = [first];
    let size = first.length;
    if (reader) {
      for (;;) {
        const r = await reader.read();
        if (r.done) break;
        parts.push(r.value);
        size += r.value.length;
      }
    }
    const all = new Uint8Array(size);
    let o = 0;
    parts.forEach(p => { all.set(p, o); o += p.length; });
    if (typeof DecompressionStream === 'function' && window.Blob && Blob.prototype.stream) {
      const rd = new Blob([all]).stream().pipeThrough(new DecompressionStream('gzip')).getReader();
      let n = 0;
      for (;;) {
        const r = await rd.read();
        if (r.done) break;
        P.bytes(r.value);
        if (++n % 8 === 0) await sleep0();
      }
    } else {
      await gunzip(all, chunk => P.bytes(chunk));
    }
    return P.end();
  }

  /* ═══ Data (IptvViewModel + IptvRepository) ═══════════════════════════ */
  const D = {
    pl: null, channels: [], byId: new Map(), byGroup: new Map(), groups: [],
    epg: null, epgCache: new Map(), loading: null, epgLoading: null, epgThisLaunch: false,
    listeners: new Set(),
  };
  const emit = () => D.listeners.forEach(f => { try { f(); } catch (e) { console.error(e); } });

  function setChannels(list) {
    D.channels = list;
    D.byId = new Map(list.map(c => [c.id, c]));
    D.byGroup = new Map();
    list.forEach(c => {
      let g = D.byGroup.get(c.group);
      if (!g) D.byGroup.set(c.group, (g = []));
      g.push(c);
    });
    D.groups = [...D.byGroup.keys()];
    D.epgCache.clear();
  }
  function setEpg(ep) {
    D.epg = ep;
    D.epgCache.clear();
    // repository.loadEpg → updateChannelLogo: fill channels that have no logo.
    if (!ep || !ep.l) return;
    let changed = false;
    for (const c of D.channels) {
      if (c.logo) continue;
      const keys = [c.tvgId, c.name, c.tvgName].filter(Boolean).map(k => k.toLowerCase());
      keys.push(c.name.toLowerCase().replace(/\s+/g, ''));
      const hit = keys.find(k => ep.l[k]);
      if (hit) { c.logo = ep.l[hit]; changed = true; }
    }
    if (changed && D.pl) IDB.set('channels', { pid: D.pl.id, list: D.channels });
  }

  const favSet = () => new Set(St.get(K.fav, []) || []);
  function toggleFav(id) {
    const f = (St.get(K.fav, []) || []).slice();
    const i = f.indexOf(id);
    if (i >= 0) f.splice(i, 1); else f.push(id);
    St.set(K.fav, f);
    return i < 0;
  }
  function markWatched(id) {
    const r = (St.get(K.recent, []) || []).filter(x => x && x.id !== id);
    r.unshift({ id, t: Date.now() });
    St.set(K.recent, r.slice(0, 30));
  }
  /** getRecentlyWatched(): lastWatched DESC LIMIT 10. */
  const recents = () => (St.get(K.recent, []) || []).map(x => x && D.byId.get(x.id)).filter(Boolean).slice(0, 10);
  function channelsFor(cat) {
    if (cat === 'All') return D.channels;
    if (cat === 'Favorites') { const f = favSet(); return D.channels.filter(c => f.has(c.id)); }
    return D.byGroup.get(cat) || [];
  }

  /** Room's EPG match: channelId IN (id, tvgId, tvgName, name) or LIKE %cleanName%. */
  function progsOf(ch) {
    if (!ch || !D.epg) return [];
    if (D.epgCache.has(ch.id)) return D.epgCache.get(ch.id);
    const P = D.epg.p, A = D.epg.a || {};
    let list = null;
    for (const k of [ch.id, ch.tvgId, ch.tvgName, ch.name]) {
      if (!k) continue;
      const l = k.toLowerCase();
      if (P[l]) { list = P[l]; break; }
      if (A[l] && P[A[l]]) { list = P[A[l]]; break; }
    }
    if (!list) {
      // An empty cleanName (e.g. a Hebrew name) would match every channel in Room; skip it here.
      const clean = ch.name.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
      if (clean.length >= 2) {
        for (const k in P) if (k.indexOf(clean) >= 0) { list = P[k]; break; }
      }
    }
    list = list || [];
    D.epgCache.set(ch.id, list);
    return list;
  }
  /** getCurrentProgram: first programme that has not ended. */
  const currentProg = ch => { const now = Date.now(); return progsOf(ch).find(p => p[1] > now) || null; };
  const dayProgs = (ch, from, to) => progsOf(ch).filter(p => p[1] > from && p[0] < to).slice(0, 100);
  /** Guide row: the last day of programmes (getProgramsForChannel), windowed around the live one. */
  function guideProgs(ch, now) {
    const all = progsOf(ch).filter(p => p[1] > now - DAY);
    let at = all.findIndex(p => p[1] >= now);
    if (at < 0) at = all.length;
    const from = Math.max(0, at - 2);
    return all.slice(from, from + 10);
  }
  const catchupUrl = (url, start) => `${url}${url.indexOf('?') >= 0 ? '&' : '?'}utc=${Math.floor(start / 1000)}&lutc=${Math.floor(Date.now() / 1000)}`;

  async function fetchChannels(url) {
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const list = parseM3u(await res.text());
    if (!list.length) throw new Error(tr('no channels in this playlist', 'אין ערוצים בפלייליסט'));
    return list;
  }

  function ensureLoaded() {
    const pl = St.get(K.playlist, null);
    if (!pl || !pl.url) {
      D.pl = null;
      if (D.channels.length) setChannels([]);
      D.epg = null;
      return Promise.resolve();
    }
    if (D.pl && D.pl.id === pl.id && (D.channels.length || D.loading)) { D.pl = pl; return D.loading || Promise.resolve(); }
    D.pl = pl;
    D.loading = (async () => {
      const rec = await IDB.get('channels');
      let list = rec && rec.pid === pl.id ? rec.list : null;
      if (!list || !list.length) {
        try {
          list = await fetchChannels(pl.url);
          IDB.set('channels', { pid: pl.id, list });
        } catch (e) {
          list = [];
          TV.toast(tr(`Couldn't load the playlist (${e.message})`, `טעינת הפלייליסט נכשלה (${e.message})`));
        }
      }
      setChannels(list);
      const ep = await IDB.get('epg');
      if (ep && ep.pid === pl.id && ep.url === pl.epgUrl) setEpg(ep);
    })().catch(() => {}).then(() => { D.loading = null; });
    return D.loading;
  }

  /** EPG auto-sync (settings: On Boot / 12 h / 24 h). */
  function epgDue() {
    const pl = D.pl;
    if (!pl || !pl.epgUrl) return false;
    const ep = D.epg;
    if (!ep || ep.url !== pl.epgUrl) return true;
    const age = Date.now() - (ep.at || 0);
    const sync = settings().epgSync;
    if (sync === 0) return !D.epgThisLaunch && age > 3600000;
    return age > (sync === 1 ? 12 : 24) * 3600000;
  }
  function loadEpg(quiet) {
    const pl = D.pl;
    if (!pl || !pl.epgUrl) return Promise.resolve();
    if (D.epgLoading) return D.epgLoading;
    D.epgThisLaunch = true;
    if (!quiet) TV.toast(tr('Loading the TV guide…', 'טוען את מדריך השידורים…'));
    D.epgLoading = fetchEpg(pl.epgUrl).then(data => {
      if (!D.pl || D.pl.id !== pl.id || D.pl.epgUrl !== pl.epgUrl) return;
      const ep = Object.assign(data, { pid: pl.id, url: pl.epgUrl, at: Date.now() });
      setEpg(ep);
      IDB.set('epg', ep);
      emit();
      if (!quiet) TV.toast(tr('TV guide updated', 'מדריך השידורים עודכן'));
    }).catch(e => {
      if (!quiet) TV.toast(tr(`The TV guide failed to load (${e.message})`, `טעינת מדריך השידורים נכשלה (${e.message})`));
    }).then(() => { D.epgLoading = null; });
    return D.epgLoading;
  }

  /** addManualPlaylist → loadAndSavePlaylist (+ loadEpg). */
  async function addPlaylist(name, url, epgUrl) {
    url = url.trim();
    epgUrl = epgUrl.trim();
    const list = await fetchChannels(url);
    const pl = { id: `pl_${Date.now()}`, name: name.trim(), url, epgUrl, lastUpdated: Date.now() };
    St.set(K.playlist, pl);
    D.pl = pl;
    setChannels(list);
    D.epg = null;
    IDB.set('channels', { pid: pl.id, list });
    IDB.del('epg');
    emit();
    if (epgUrl) loadEpg(false);
  }
  /** updatePlaylist: re-fetch channels if the M3U changed, the guide if the EPG changed. */
  async function updatePlaylist(name, url, epgUrl) {
    const cur = D.pl;
    if (!cur) return;
    url = url.trim();
    epgUrl = epgUrl.trim();
    const urlChanged = !!url && url !== cur.url;
    const epgChanged = epgUrl !== cur.epgUrl;
    const pl = Object.assign({}, cur, { name: name.trim() || cur.name, url: url || cur.url, epgUrl, lastUpdated: Date.now() });
    St.set(K.playlist, pl);
    D.pl = pl;
    emit();
    try {
      if (urlChanged) {
        const list = await fetchChannels(pl.url);
        setChannels(list);
        IDB.set('channels', { pid: pl.id, list });
        emit();
      }
    } catch (e) {
      TV.toast(tr(`Couldn't load the playlist (${e.message})`, `טעינת הפלייליסט נכשלה (${e.message})`));
    }
    if (epgChanged) {
      D.epg = null;
      D.epgCache.clear();
      IDB.del('epg');
      emit();
      if (epgUrl) loadEpg(false);
    }
  }
  function deletePlaylist() {
    St.del(K.playlist);
    D.pl = null;
    setChannels([]);
    D.epg = null;
    IDB.del('channels');
    IDB.del('epg');
    emit();
  }

  /* ═══ Text field (CrispTextField: OutlinedTextField, radius 12, no outline) ═══ */
  const fieldHtml = (key, label, value, extra = '') => `
    <div class="ip-fld${value ? ' has' : ''}" data-fld="${key}" ${extra}>
      <div class="ip-fld-l">${esc(label)}</div>
      <input type="text" dir="${key === 'name' ? 'auto' : 'ltr'}" spellcheck="false" autocomplete="off" value="${esc(value)}">
    </div>`;
  function wireField(node, onValue) {
    const input = node.querySelector('input');
    input.addEventListener('input', () => { node.classList.toggle('has', !!input.value); onValue(input.value); });
    input.addEventListener('focus', () => node.classList.add('typing'));
    input.addEventListener('blur', () => node.classList.remove('typing'));
    return input;
  }

  /* ═══ IptvPlayerScreen ════════════════════════════════════════════════ */
  let PLAYER = null;
  const MEDIA_ERR = { 1: 'MEDIA_ERR_ABORTED', 2: 'MEDIA_ERR_NETWORK', 3: 'MEDIA_ERR_DECODE', 4: 'MEDIA_ERR_SRC_NOT_SUPPORTED' };
  const HE_DAYS = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
  const EN_DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const EN_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function openPlayer(first, list, onClose) {
    if (PLAYER) PLAYER.close();
    const rtl = TV.rtl();
    const P = {
      ch: first, url: first.url, list: list && list.length ? list : [first],
      osd: false, panel: null, err: false, retry: 0, fit: 'contain', timers: {}, o: null, g: null,
    };
    const root = el(`
      <div class="ip-player">
        <video class="ip-v" autoplay preload="auto"></video>
        <div class="ip-pload"><i class="ip-spin"></i></div>
        <div class="ip-pmsg"></div>
        <div class="ip-perr hidden"><div class="ip-perr-t">${tr('Playback Interrupted', 'הניגון הופסק')}</div><div class="ip-perr-s"></div></div>
        <div class="ip-osd"></div>
        <div class="ip-opts"></div>
        <div class="ip-eg"></div>
      </div>`);
    const $ = sel => root.querySelector(sel);
    const video = $('video');
    const tvRoot = document.getElementById('tv');
    document.body.appendChild(root);
    tvRoot.style.visibility = 'hidden';

    const seekable = () => isFinite(video.duration) && video.duration > 0 && video.seekable && video.seekable.length > 0;
    const clear = name => { clearTimeout(P.timers[name]); clearInterval(P.timers[name]); };

    function message(text) {
      const m = $('.ip-pmsg');
      m.textContent = text;
      m.classList.add('on');
      clear('msg');
      P.timers.msg = setTimeout(() => m.classList.remove('on'), 3500);
    }

    function start(url) {
      try {
        video.src = url;
        video.load();
        const pr = video.play();
        if (pr && pr.catch) pr.catch(() => {});
      } catch {}
    }
    function load(url) {
      P.url = url;
      P.err = false;
      P.retry = 0;
      $('.ip-perr').classList.add('hidden');
      root.classList.remove('err');
      $('.ip-pload').classList.remove('hidden');
      if (P.panel === 'opts') closeOpts(false);
      start(url);
      showOsd();
    }
    video.addEventListener('playing', () => $('.ip-pload').classList.add('hidden'));
    video.addEventListener('error', () => {
      if (!P.url || !video.error) return;
      // Stalled HLS: retry up to 3 times (PlaylistStuckException in Android).
      if (P.retry < 3) {
        P.retry++;
        message(tr(`Stream stalled, retrying (${P.retry}/3)...`, `השידור נתקע, מנסה שוב (${P.retry}/3)...`));
        clear('retry');
        P.timers.retry = setTimeout(() => start(P.url), 800);
        return;
      }
      P.err = true;
      $('.ip-pload').classList.add('hidden');
      $('.ip-perr-s').textContent = `${tr('Stream Error', 'שגיאת שידור')}: ${MEDIA_ERR[video.error.code] || video.error.code}`;
      $('.ip-perr').classList.remove('hidden');
      root.classList.add('err');
      hideOsd();
    });

    /* ── OSD ── */
    function osdProg() {
      const now = Date.now();
      const ps = progsOf(P.ch).filter(p => p[1] > now - DAY).slice(0, 10);
      return ps.find(p => now >= p[0] && now <= p[1]) || ps[0] || null;
    }
    function renderSeek() {
      const box = $('.ip-osd-seek');
      if (!box) return;
      const ok = seekable();
      box.classList.toggle('hidden', !ok);
      const hint = $('.ip-osd-hint');
      if (hint) hint.textContent = hintText(ok);
      if (!ok) return;
      const pos = (video.currentTime || 0) * 1000, d = video.duration * 1000;
      box.querySelector('.a').textContent = fmtDur(pos);
      box.querySelector('.b').textContent = fmtDur(d);
      box.querySelector('i').style.width = `${clamp(pos / d, 0, 1) * 100}%`;
    }
    const hintText = sk => (sk
      ? tr('◄ 5s  •  ► 5s', '◄ 5 שנ׳  •  ► 5 שנ׳')
      : tr('OK for Guide • ◄ EPG • ► Options', 'OK למדריך • ► מדריך • ◄ אפשרויות'));
    function renderOsd() {
      const c = P.ch;
      const p = osdProg();
      $('.ip-osd').innerHTML = `
        <div class="ip-osd-in">
          <div class="ip-osd-row">
            <div class="ip-osd-l">
              <div class="ip-osd-logo">${c.logo ? `<img src="${esc(c.logo)}" alt="">` : `<span>${esc(c.name.slice(0, 1))}</span>`}</div>
              <div class="ip-osd-tx">
                <div class="ip-osd-name">${c.number > 0 ? `<span class="n">${c.number}</span>` : ''}<span class="nm" dir="auto">${esc(c.name)}</span></div>
                ${p ? `<div class="ip-osd-epg" dir="auto">${hhmm(p[0])} - ${hhmm(p[1])}  •  ${esc(p[2])}</div>` : ''}
              </div>
            </div>
            <div class="ip-osd-r"><div class="ip-osd-clock">${TV.clock()}</div><div class="ip-osd-hint">${hintText(seekable())}</div></div>
          </div>
          <div class="ip-osd-seek hidden"><span class="a"></span><div class="bar"><i></i></div><span class="b"></span></div>
        </div>`;
      const img = $('.ip-osd-logo img');
      if (img) img.addEventListener('error', () => { img.replaceWith(el(`<span>${esc(c.name.slice(0, 1))}</span>`)); });
      renderSeek();
    }
    function armOsd() { clear('osd'); P.timers.osd = setTimeout(hideOsd, 5000); }
    function showOsd() {
      if (P.err || P.panel === 'opts') return;
      P.osd = true;
      renderOsd();
      $('.ip-osd').classList.add('on');
      armOsd();
      clear('tick');
      P.timers.tick = setInterval(() => {
        renderSeek();
        const ck = $('.ip-osd-clock');
        if (ck) ck.textContent = TV.clock();
      }, 1000);
    }
    function hideOsd() {
      P.osd = false;
      $('.ip-osd').classList.remove('on');
      clear('osd');
      clear('tick');
    }

    function zap(dir) {
      const L = P.list;
      let i = L.indexOf(P.ch);
      if (i < 0) i = L.findIndex(c => P.url.indexOf(c.url) === 0);
      const n = dir > 0 ? (i >= 0 && i < L.length - 1 ? L[i + 1] : L[0]) : (i > 0 ? L[i - 1] : L[L.length - 1]);
      if (!n) return;
      P.ch = n;
      load(n.url);
    }
    function seek(sec) {
      try { video.currentTime = clamp(video.currentTime + sec, 0, video.duration); } catch {}
      renderSeek();
      armOsd();
    }

    /* ── PlayerSettingsOverlay ── */
    function optItems() {
      const items = [];
      const head = (text, extra) => items.push({ head: text, cls: extra || '' });
      head(tr('ASPECT RATIO', 'יחס תצוגה'));
      [['contain', tr('Original', 'מקורי')], ['fill', tr('Stretch', 'מתיחה')], ['cover', tr('Crop', 'חיתוך')]].forEach(([fit, label]) => {
        items.push({ label, sel: P.fit === fit, cls: 'tight', run: () => { P.fit = fit; video.style.objectFit = fit; } });
      });
      const audio = video.audioTracks;
      if (audio && audio.length) {
        head(tr('AUDIO', 'שמע'), 'sec');
        for (let i = 0; i < audio.length; i++) {
          const t = audio[i];
          const label = t.label || (t.language ? t.language.toUpperCase() : tr('UNKNOWN', 'לא ידוע'));
          items.push({
            label: `${tr('Track', 'רצועה')} ${i + 1}: ${label}`, sel: !!t.enabled,
            run: () => { for (let j = 0; j < audio.length; j++) audio[j].enabled = j === i; },
          });
        }
      }
      const subs = Array.from(video.textTracks || []).filter(t => t.kind === 'subtitles' || t.kind === 'captions');
      if (subs.length) {
        head(tr('SUBTITLES', 'כתוביות'), 'sec');
        items.push({ label: tr('Off', 'כבוי'), sel: !subs.some(t => t.mode === 'showing'), cls: 'tight', run: () => subs.forEach(t => { t.mode = 'disabled'; }) });
        subs.forEach((t, i) => {
          const label = t.label || (t.language ? t.language.toUpperCase() : tr('UNKNOWN', 'לא ידוע'));
          items.push({
            label: `${tr('Subtitle', 'כתובית')} ${i + 1}: ${label}`, sel: t.mode === 'showing',
            run: () => subs.forEach(x => { x.mode = x === t ? 'showing' : 'disabled'; }),
          });
        });
      }
      return items;
    }
    function renderOpts() {
      const items = optItems();
      P.o.items = items.filter(x => !x.head);
      P.o.idx = clamp(P.o.idx, 0, P.o.items.length - 1);
      let bi = 0;
      $('.ip-opts').innerHTML = `
        <div class="ip-op-t">${tr('Options', 'אפשרויות')}</div>
        <div class="ip-op-view"><div class="ip-op-list">${items.map(x => (x.head
          ? `<div class="ip-op-h ${x.cls}">${esc(x.head)}</div>`
          : `<div class="ip-op-b ${x.cls}${x.sel ? ' sel' : ''}" data-i="${bi++}">${x.sel ? '<span class="ck">✓</span>' : ''}<span dir="auto">${esc(x.label)}</span></div>`)).join('')}</div></div>`;
      $('.ip-opts').querySelectorAll('.ip-op-b').forEach(b => TV.pointer(b,
        () => { P.o.idx = +b.dataset.i; paintOpts(); },
        () => { P.o.idx = +b.dataset.i; P.o.items[P.o.idx].run(); renderOpts(); }));
      paintOpts();
    }
    function paintOpts() {
      const btns = [...$('.ip-opts').querySelectorAll('.ip-op-b')];
      btns.forEach((b, i) => b.classList.toggle('f', i === P.o.idx));
      const f = btns[P.o.idx];
      const view = $('.ip-op-view'), listEl = $('.ip-op-list');
      if (!f) return;
      const vh = view.offsetHeight;
      let y = P.o.scroll || 0;
      if (f.offsetTop + f.offsetHeight > y + vh) y = f.offsetTop + f.offsetHeight - vh;
      if (f.offsetTop < y) y = f.offsetTop;
      if (P.o.idx === 0) y = 0;
      P.o.scroll = Math.max(0, y);
      listEl.style.transform = `translateY(${-P.o.scroll}px)`;
    }
    function openOpts() {
      hideOsd();
      P.panel = 'opts';
      P.o = { idx: 0, scroll: 0 };
      renderOpts();
      root.classList.add('opts');
    }
    function closeOpts(thenOsd) {
      if (P.panel !== 'opts') return;
      P.panel = null;
      root.classList.remove('opts');
      if (thenOsd) showOsd();
    }
    function optsKey(key) {
      if (key === 'back' || TV.bwd(key)) { closeOpts(true); return true; }
      if (key === 'down') { P.o.idx = Math.min(P.o.items.length - 1, P.o.idx + 1); paintOpts(); return true; }
      if (key === 'up') { P.o.idx = Math.max(0, P.o.idx - 1); paintOpts(); return true; }
      if (key === 'ok') { const it = P.o.items[P.o.idx]; if (it) { it.run(); renderOpts(); } return true; }
      return true;
    }

    /* ── EpgGuideOverlay ── */
    function dateTabs() {
      return [0, 1, 2, 3, 4, 5, 6].map(back => {
        const d = new Date();
        d.setHours(0, 0, 0, 0);
        d.setDate(d.getDate() - back);
        const startT = d.getTime();
        const label = back === 0 ? tr('Today', 'היום') : back === 1 ? tr('Yesterday', 'אתמול')
          : rtl ? `יום ${HE_DAYS[d.getDay()]}, ${d.getDate()}.${d.getMonth() + 1}` : `${EN_DAYS[d.getDay()]}, ${EN_MONTHS[d.getMonth()]} ${d.getDate()}`;
        return { label, start: startT, end: startT + DAY };
      });
    }
    function openGuide() {
      hideOsd();
      P.panel = 'guide';
      const c = P.ch;
      P.g = { tabs: dateTabs(), day: 0, tab: 0, zone: 'tabs', idx: 0, progs: [] };
      $('.ip-eg').innerHTML = `
        <div class="ip-eg-p">
          <div class="ip-eg-head">
            <div class="ip-eg-logo">${c.logo ? `<img src="${esc(c.logo)}" alt="">` : `<span>${esc(c.name.slice(0, 1))}</span>`}</div>
            <div class="ip-eg-tt"><div class="t">${tr('TV Guide', 'מדריך שידורים')}</div><div class="s" dir="auto">${esc(c.name)}</div></div>
            <div class="ip-eg-sp"></div>
            <div class="ip-eg-cu">${tr('↺ 7-DAY CATCH-UP', '↺ הקלטה 7 ימים')}</div>
          </div>
          <div class="ip-eg-tabs"><div class="ip-eg-tt2">${P.g.tabs.map((t, i) => `<div class="ip-eg-tab" data-i="${i}">${i === 0 ? '<i></i>' : ''}<span>${esc(t.label)}</span></div>`).join('')}</div></div>
          <div class="ip-eg-body"></div>
        </div>`;
      const img = $('.ip-eg-logo img');
      if (img) img.addEventListener('error', () => { img.replaceWith(el(`<span>${esc(c.name.slice(0, 1))}</span>`)); });
      $('.ip-eg').querySelectorAll('.ip-eg-tab').forEach(t => TV.pointer(t,
        () => { P.g.zone = 'tabs'; P.g.tab = +t.dataset.i; paintGuide(); },
        () => { P.g.tab = +t.dataset.i; loadDay(P.g.tab); }));
      root.classList.add('guide');
      loadDay(0);
    }
    function loadDay(d) {
      const G = P.g;
      G.day = d;
      const tab = G.tabs[d];
      const now = Date.now();
      G.progs = dayProgs(P.ch, tab.start, tab.end);
      const body = $('.ip-eg-body');
      if (!G.progs.length) {
        body.innerHTML = `<div class="ip-eg-msg"><div class="a">${tr('No schedule available', 'אין לוח שידורים זמין')}</div><div class="b">${tr('EPG data may not cover this date', 'נתוני EPG לא מכסים תאריך זה')}</div></div>`;
        G.zone = 'tabs';
        G.tab = d;
      } else {
        body.innerHTML = `<div class="ip-eg-row"><div class="ip-eg-track">${G.progs.map((p, i) => {
          const live = now >= p[0] && now <= p[1], past = p[1] < now;
          const mins = Math.max(1, Math.floor((p[1] - p[0]) / 60000));
          const badge = live ? `<span class="bd live">${tr('● LIVE', '● שידור חי')}</span>`
            : past ? `<span class="bd past">${tr('↺ CATCHUP', '↺ הקלטה')}</span>`
              : `<span class="bd next">${tr('UPCOMING', 'בקרוב')}</span>`;
          return `<div class="ip-eg-card${live ? ' live' : past ? ' past' : ''}" data-i="${i}" style="width:${clamp(mins * 5, 200, 500)}px">
            <div class="in">
              <div class="top"><span class="tm">${hhmm(p[0])} - ${hhmm(p[1])}</span>${badge}</div>
              <div class="tt" dir="auto">${esc(p[2] || tr('Unknown Program', 'תוכניה לא ידועה'))}</div>
              ${p[3] ? `<div class="ds" dir="auto">${esc(p[3])}</div>` : ''}
              <div class="mn">${mins}${tr('min', ' דק׳')}</div>
            </div>
            ${live ? `<div class="pr"><i style="width:${pct(now, p[0], p[1])}%"></i></div>` : past ? '<div class="pr past"></div>' : ''}
          </div>`;
        }).join('')}</div></div>`;
        body.querySelectorAll('.ip-eg-card').forEach(cd => TV.pointer(cd,
          () => { G.zone = 'progs'; G.idx = +cd.dataset.i; paintGuide(); },
          () => { G.idx = +cd.dataset.i; pickProg(); }));
        // Android focuses the first programme; on Today start at the one on air.
        const live = d === 0 ? G.progs.findIndex(p => now >= p[0] && now <= p[1]) : -1;
        G.idx = live >= 0 ? live : 0;
        G.zone = 'progs';
      }
      paintGuide();
    }
    function paintGuide() {
      const G = P.g;
      const tabs = [...$('.ip-eg').querySelectorAll('.ip-eg-tab')];
      tabs.forEach((t, i) => {
        t.classList.toggle('sel', i === G.day);
        t.classList.toggle('f', G.zone === 'tabs' && i === G.tab);
      });
      if (G.zone === 'tabs') rowReveal($('.ip-eg-tabs'), $('.ip-eg-tt2'), tabs[G.tab]);
      const cards = [...$('.ip-eg').querySelectorAll('.ip-eg-card')];
      cards.forEach((cd, i) => cd.classList.toggle('f', G.zone === 'progs' && i === G.idx));
      if (cards.length) rowReveal($('.ip-eg-row'), $('.ip-eg-track'), cards[G.idx]);
    }
    function pickProg() {
      const G = P.g;
      const p = G.progs[G.idx];
      if (!p) return;
      const now = Date.now();
      closeGuide();
      if (p[1] < now && settings().catchup) {
        load(catchupUrl(P.ch.url, p[0]));
      } else if (P.url !== P.ch.url) {
        load(P.ch.url);
      }
    }
    function closeGuide() {
      P.panel = null;
      root.classList.remove('guide');
    }
    function guideKey(key) {
      const G = P.g;
      if (key === 'back') { closeGuide(); return true; }
      if (G.zone === 'tabs') {
        if (TV.fwd(key)) { G.tab = Math.min(G.tabs.length - 1, G.tab + 1); paintGuide(); return true; }
        if (TV.bwd(key)) { G.tab = Math.max(0, G.tab - 1); paintGuide(); return true; }
        if (key === 'ok') { loadDay(G.tab); return true; }
        if (key === 'down' && G.progs.length) { G.zone = 'progs'; paintGuide(); return true; }
        return true;
      }
      if (TV.fwd(key)) { G.idx = Math.min(G.progs.length - 1, G.idx + 1); paintGuide(); return true; }
      if (TV.bwd(key)) { G.idx = Math.max(0, G.idx - 1); paintGuide(); return true; }
      if (key === 'up') { G.zone = 'tabs'; G.tab = G.day; paintGuide(); return true; }
      if (key === 'ok') { pickProg(); return true; }
      return true;
    }

    /* ── Keys (the invisible focus anchor of IptvPlayerScreen) ── */
    function onKey(key) {
      if (P.panel === 'guide') return guideKey(key);
      if (P.panel === 'opts') return optsKey(key);
      switch (key) {
        case 'back': if (P.osd) hideOsd(); else close(); return true;
        case 'stop': close(); return true;
        case 'up': zap(1); return true;
        case 'down': zap(-1); return true;
        case 'ok':
          if (P.err) return true;
          if (P.osd) openGuide(); else showOsd();
          return true;
        case 'left':
        case 'right':
          if (seekable() && P.osd) seek(TV.fwd(key) ? 5 : -5);
          else if (TV.fwd(key)) openOpts();
          else if (!P.osd) openGuide();
          return true;
        default: return true;
      }
    }
    const offLayer = TV.pushLayer({ onKey });
    // CH+ / CH− on the LG remote (not in the app's key map).
    const chKeys = e => {
      if (e.keyCode !== 33 && e.keyCode !== 34) return;
      e.preventDefault();
      if (!P.panel) zap(e.keyCode === 33 ? 1 : -1);
    };
    window.addEventListener('keydown', chKeys);
    const vis = () => { try { if (document.hidden) video.pause(); else video.play().catch(() => {}); } catch {} };
    document.addEventListener('visibilitychange', vis);
    TV.pointer(video, () => {}, () => { if (!P.panel) { if (P.osd) hideOsd(); else showOsd(); } });

    function close() {
      Object.keys(P.timers).forEach(clear);
      offLayer();
      window.removeEventListener('keydown', chKeys);
      document.removeEventListener('visibilitychange', vis);
      try { video.pause(); video.removeAttribute('src'); video.load(); } catch {}
      root.remove();
      tvRoot.style.visibility = '';
      PLAYER = null;
      if (onClose) onClose();
    }

    PLAYER = { close };
    load(first.url);
    return PLAYER;
  }

  /* ═══ IptvScreen ══════════════════════════════════════════════════════ */
  TV.register('iptv', () => {
    const root = el(`
      <div class="screen iptv">
        <div class="ip-glow"><i></i><i></i></div>
        <div class="ip-top"><div class="ip-rib"><div class="ip-rib-t"></div></div></div>
        <div class="ip-mid"></div>
        <div class="ip-body"></div>
        <div class="ip-layer"></div>
        <div class="ip-saver"></div>
      </div>`);
    const ribView = TV.$(root, '.ip-rib');
    const ribTrack = TV.$(root, '.ip-rib-t');
    const mid = TV.$(root, '.ip-mid');
    const body = TV.$(root, '.ip-body');
    const layer = TV.$(root, '.ip-layer');
    const saver = TV.$(root, '.ip-saver');
    const glows = [...root.querySelectorAll('.ip-glow i')];

    const s = {
      group: 'All', cat: 'All', zone: 'ribbon', pill: 0,
      list: [], favs: new Set(), recents: [], focusCh: null,
      gi: 0, grid: null, ri: 0, guide: null, set: null,
      editing: false, ed: null, setup: null,
      glowKey: null, glowFlip: false, saverOn: false, saverT: 0, saverTick: 0,
      shown: false, alive: true,
    };

    /* ── Groups (ribbon) ── */
    const SYSTEM = ['EPG Guide', 'Settings'];
    function displayGroups() {
      return [['All', tr('All', 'הכל')], ['Favorites', tr('Favorites', 'מועדפים')]]
        .concat(D.groups.filter(g => g !== 'All' && g !== 'Favorites').map(g => [g, g]))
        .concat([['EPG Guide', tr('EPG Guide', 'מדריך שידורים')], ['Settings', tr('Settings', 'הגדרות')]]);
    }
    function renderRibbon() {
      const groups = displayGroups();
      s.groups = groups;
      s.pill = clamp(s.pill, 0, groups.length - 1);
      ribTrack.innerHTML = groups.map(([k, name]) => `<div class="ip-pill" data-k="${esc(k)}">${esc(name)}</div>`).join('');
      [...ribTrack.children].forEach((p, i) => TV.pointer(p,
        () => { setZone('ribbon'); s.pill = i; paintRibbon(); },
        () => { s.pill = i; selectGroup(groups[i][0]); }));
      ribTrack.dataset.x = '0';
      ribTrack.style.transform = '';
      paintRibbon();
    }
    function paintRibbon() {
      const pills = [...ribTrack.children];
      pills.forEach((p, i) => {
        p.classList.toggle('sel', s.groups[i][0] === s.group);
        p.classList.toggle('f', s.zone === 'ribbon' && i === s.pill);
      });
      rowReveal(ribView, ribTrack, pills[s.pill]);
    }
    function selectGroup(g) {
      s.group = g;
      if (!SYSTEM.includes(g)) {
        s.cat = g;
        St.set(K.lastGroup, g);
      }
      s.list = channelsFor(s.cat);
      s.gi = 0;
      s.editing = false;
      paintRibbon();
      renderMid();
      renderBody(true);
    }

    /* ── Focus info: hero, recent title, ambient glow ── */
    function setFocusCh(c) {
      if (s.focusCh === c) return;
      s.focusCh = c;
      updateInfo();
      setGlow(c);
    }
    function setGlow(c) {
      const col = c ? HUES[Math.abs(jhash(c.name)) % HUES.length] : '';
      if (col === s.glowKey) return;
      s.glowKey = col;
      const next = glows[s.glowFlip ? 0 : 1], prev = glows[s.glowFlip ? 1 : 0];
      s.glowFlip = !s.glowFlip;
      next.style.background = col ? `linear-gradient(to bottom, rgba(${col},0.15), rgba(${col},0))` : 'none';
      next.classList.add('on');
      prev.classList.remove('on');
    }
    const showRecent = () => s.recents.length > 0 && !SYSTEM.includes(s.group);
    function renderMid() {
      if (showRecent()) {
        mid.innerHTML = `
          <div class="ip-rec">
            <div class="ip-rec-name" dir="auto"></div>
            <div class="ip-rec-head">${icon('tv', 22)}<span>${tr('Recently Watched', 'ערוצים אחרונים')}</span></div>
            <div class="ip-rec-view"><div class="ip-rec-t">${s.recents.map(c => `
              <div class="ip-bub">
                <div class="ip-bub-box">
                  <div class="ring"></div>
                  <div class="circ">${c.logo ? `<img src="${esc(c.logo)}" alt="" decoding="async">` : `<span>${esc(c.name.slice(0, 2).toUpperCase())}</span>`}</div>
                  <div class="dot"></div>
                </div>
                <div class="ip-bub-n" dir="auto">${esc(c.name)}</div>
              </div>`).join('')}</div></div>
          </div>`;
        mid.querySelectorAll('.ip-bub').forEach((b, i) => {
          const img = b.querySelector('img');
          if (img) img.addEventListener('error', () => { img.replaceWith(el(`<span>${esc(s.recents[i].name.slice(0, 2).toUpperCase())}</span>`)); });
          TV.pointer(b, () => { setZone('recent'); s.ri = i; paintRecent(); }, () => { s.ri = i; playCh(s.recents[i], true); });
        });
        s.ri = clamp(s.ri, 0, s.recents.length - 1);
        paintRecent();
      } else {
        mid.innerHTML = '<div class="ip-hero"></div>';
        if (s.zone === 'recent') setZone('ribbon');
      }
      updateInfo();
    }
    function paintRecent() {
      const bubs = [...mid.querySelectorAll('.ip-bub')];
      bubs.forEach((b, i) => b.classList.toggle('f', s.zone === 'recent' && i === s.ri));
      if (s.zone === 'recent') {
        rowReveal(TV.$(mid, '.ip-rec-view'), TV.$(mid, '.ip-rec-t'), bubs[s.ri]);
        setFocusCh(s.recents[s.ri]);
      }
    }
    function updateInfo() {
      const c = s.focusCh;
      const recName = TV.$(mid, '.ip-rec-name');
      if (recName) {
        recName.textContent = c ? c.name : '';
        recName.classList.toggle('hidden', !c);
        return;
      }
      const hero = TV.$(mid, '.ip-hero');
      if (!hero) return;
      if (!c) { hero.innerHTML = ''; return; }
      const p = currentProg(c);
      const now = Date.now();
      const live = p && now >= p[0] && now <= p[1];
      hero.innerHTML = `
        <div class="ip-h-name" dir="auto">${esc(c.name)}</div>
        ${p ? `<div class="ip-h-now"><span class="ip-b-now">${tr('NOW PLAYING', 'עכשיו בשידור')}</span><span class="ip-h-prog" dir="auto">${esc(p[2])}</span></div>` : ''}
        ${live && p[0] > 0 ? `<div class="ip-h-bar"><i style="width:${pct(now, p[0], p[1])}%"></i></div>` : ''}`;
    }

    /* ── Body (crossfades on group change) ── */
    function renderBody(fade) {
      s.grid = null;
      s.guide = null;
      s.set = null;
      if (fade) {
        body.classList.remove('in');
        void body.offsetWidth;
        body.classList.add('in');
      }
      if (D.loading) {
        body.innerHTML = '<div class="ip-loading"><i class="ip-spin"></i></div>';
        return;
      }
      if (s.group === 'Settings') renderSettings();
      else if (s.group === 'EPG Guide') renderGuide();
      else if (!s.list.length) renderEmpty();
      else renderGrid();
      if (!['ribbon', 'recent'].includes(s.zone) && s.zone !== bodyZone()) setZone('ribbon');
    }
    const bodyZone = () => (s.group === 'Settings' ? 'set' : s.group === 'EPG Guide' ? 'guide' : s.list.length ? 'grid' : 'empty');

    /* EmptyStateView */
    function renderEmpty() {
      body.innerHTML = `
        <div class="ip-empty">
          <div class="ip-e-ic">${icon('tv', 100)}</div>
          <div class="ip-e-t">${tr('No Channels Found', 'לא נמצאו ערוצים')}</div>
          <div class="ip-e-d">${tr('Configure your playlist to start watching.', 'הגדר את הפלייליסט שלך כדי להתחיל לצפות.')}</div>
          <div class="ip-e-b">${tr('Setup IPTV', 'הגדר IPTV')}</div>
        </div>`;
      TV.pointer(TV.$(body, '.ip-e-b'), () => { setZone('empty'); }, () => openSetup());
      paintEmpty();
    }
    const paintEmpty = () => { const b = TV.$(body, '.ip-e-b'); if (b) b.classList.toggle('f', s.zone === 'empty'); };

    /* Channel grid (virtualised: only the rows around the viewport are in the DOM) */
    function renderGrid() {
      body.innerHTML = '<div class="ip-grid"><div class="ip-grid-in"></div></div>';
      s.grid = { view: TV.$(body, '.ip-grid'), inner: TV.$(body, '.ip-grid-in'), cards: new Map(), scroll: 0 };
      s.gi = clamp(s.gi, 0, s.list.length - 1);
      paintGrid();
    }
    function cardEl(i) {
      const c = s.list[i];
      const row = Math.floor(i / COLS), col = i % COLS;
      const letter = `<span class="ip-c-ltr">${esc(c.name.slice(0, 1).toUpperCase())}</span>`;
      const n = el(`
        <div class="ip-card${s.favs.has(c.id) ? ' fav' : ''}" style="${TV.rtl() ? 'right' : 'left'}:${col * (CELL_W + GAP)}px;top:${GRID_TOP + row * PITCH}px;width:${CELL_W}px;height:${CELL_H}px">
          <div class="ip-c-logo">${c.logo ? `<img src="${esc(c.logo)}" alt="" decoding="async">` : letter}</div>
          <div class="ip-c-heart"><span class="on">${icon('favorite', 16)}</span><span class="off">${icon('favoriteBorder', 16)}</span></div>
          <div class="ip-c-bar"><div class="ip-c-name" dir="auto">${esc(c.name)}</div>${c.number > 0 ? `<div class="ip-c-num">${tr('CH', 'ערוץ')} ${c.number}</div>` : ''}</div>
        </div>`);
      const img = n.querySelector('img');
      if (img) img.addEventListener('error', () => { img.replaceWith(el(letter)); });
      TV.pointer(n, () => { setZone('grid'); s.gi = i; paintGrid(); }, () => { s.gi = i; playCh(s.list[i], true); });
      TV.pointer(n.querySelector('.ip-c-heart'), () => { setZone('grid'); s.gi = i; paintGrid(); }, () => { s.gi = i; favToggle(); });
      return n;
    }
    function paintGrid() {
      const g = s.grid;
      if (!g) return;
      const n = s.list.length;
      const viewH = g.view.offsetHeight || 500;
      const fr = Math.floor(s.gi / COLS);
      const top = GRID_TOP + fr * PITCH, bot = top + CELL_H;
      if (fr === 0) g.scroll = 0;
      else if (top < g.scroll) g.scroll = top;
      else if (bot > g.scroll + viewH) g.scroll = bot - viewH;
      const rows = Math.ceil(n / COLS);
      const r0 = Math.max(0, Math.floor((g.scroll - GRID_TOP) / PITCH) - 1);
      const r1 = Math.min(rows - 1, Math.floor((g.scroll + viewH) / PITCH) + 1);
      const lo = r0 * COLS, hi = Math.min(n - 1, r1 * COLS + COLS - 1);
      g.cards.forEach((node, i) => { if (i < lo || i > hi) { node.remove(); g.cards.delete(i); } });
      for (let i = lo; i <= hi; i++) {
        if (!g.cards.has(i)) { const node = cardEl(i); g.inner.appendChild(node); g.cards.set(i, node); }
      }
      g.cards.forEach((node, i) => node.classList.toggle('f', s.zone === 'grid' && i === s.gi));
      g.inner.style.transform = `translateY(${-g.scroll}px)`;
      if (s.zone === 'grid') setFocusCh(s.list[s.gi]);
    }
    function gridKey(key) {
      const n = s.list.length, col = s.gi % COLS;
      if (TV.fwd(key)) { if (col < COLS - 1 && s.gi + 1 < n) { s.gi++; paintGrid(); } return true; }
      if (TV.bwd(key)) { if (col > 0) { s.gi--; paintGrid(); } return true; }
      if (key === 'down') { if (s.gi + COLS < n) s.gi += COLS; else if (Math.floor(s.gi / COLS) < Math.floor((n - 1) / COLS)) s.gi = n - 1; paintGrid(); return true; }
      if (key === 'up') {
        if (s.gi >= COLS) { s.gi -= COLS; paintGrid(); return true; }
        if (showRecent()) { setZone('recent'); return true; }
        toRibbon();
        return true;
      }
      if (key === 'ok') { playCh(s.list[s.gi], true); return true; }
      return key !== 'back';
    }
    function favToggle() {
      const c = s.list[s.gi];
      if (!c) return;
      const on = toggleFav(c.id);
      s.favs = favSet();
      if (s.cat === 'Favorites') {
        s.list = channelsFor('Favorites');
        if (!s.list.length) { renderBody(false); setZone('ribbon'); return; }
        s.gi = clamp(s.gi, 0, s.list.length - 1);
        renderGrid();
        return;
      }
      const node = s.grid && s.grid.cards.get(s.gi);
      if (node) node.classList.toggle('fav', on);
    }

    /* EpgGuideScreen (in the IPTV screen) */
    function renderGuide() {
      const chans = s.list.slice(0, 50);
      if (!chans.length) { body.innerHTML = ''; return; }
      const now = Date.now();
      const rows = chans.map(c => {
        const ps = guideProgs(c, now);
        const blocks = ps.length ? ps.map(p => ({ p, title: p[2] })) : [{ p: null, title: tr('No Schedule Available', 'אין לוח שידורים') }];
        return { c, blocks };
      });
      body.innerHTML = `<div class="ip-gd"><div class="ip-gd-list">${rows.map(r => `
        <div class="ip-gd-row">
          <div class="ip-gd-ch">${r.c.logo ? `<img src="${esc(r.c.logo)}" alt="" decoding="async">` : ''}<span dir="auto">${esc(r.c.name)}</span></div>
          <div class="ip-gd-view"><div class="ip-gd-t">${r.blocks.map(b => {
            const p = b.p;
            const mins = p ? Math.floor((p[1] - p[0]) / 60000) : 60;
            const live = p && now >= p[0] && now <= p[1], past = p && p[1] < now;
            const badge = live ? `<span class="bd live">${tr('LIVE', 'שידור חי')}</span>` : past ? `<span class="bd past">${tr('↺ CATCHUP', '↺ הקלטה')}</span>` : '';
            return `<div class="ip-pb${live ? ' live' : past ? ' past' : ''}" style="width:${clamp(mins * 6, 240, 600)}px">
              <div class="in"><div class="top"><span class="tm">${p ? `${hhmm(p[0])} - ${hhmm(p[1])}` : '--:--'}</span>${badge}</div><div class="tt" dir="auto">${esc(b.title)}</div></div>
              ${live && p[0] > 0 ? `<div class="pr"><i style="width:${pct(now, p[0], p[1])}%"></i></div>` : ''}
            </div>`;
          }).join('')}</div></div>
        </div>`).join('')}</div></div>`;
      const rowEls = [...body.querySelectorAll('.ip-gd-row')];
      s.guide = { rows, r: 0, b: 0, scroll: 0, list: TV.$(body, '.ip-gd-list'), view: TV.$(body, '.ip-gd'), rowEls };
      rowEls.forEach((re, ri) => {
        const img = re.querySelector('.ip-gd-ch img');
        if (img) img.addEventListener('error', () => img.remove());
        [...re.querySelectorAll('.ip-pb')].forEach((b, bi) => TV.pointer(b,
          () => { setZone('guide'); s.guide.r = ri; s.guide.b = bi; paintGuide(); },
          () => playCh(rows[ri].c, false)));
      });
      paintGuide();
    }
    function blockSpan(r, b) {
      const G = s.guide;
      const blocks = G.rows[r].blocks;
      let x = 0;
      for (let i = 0; i < b; i++) x += blockW(blocks[i]) + 12;
      const re = G.rowEls[r].querySelector('.ip-gd-t');
      const sx = Number(re.dataset.x || 0);
      return [x - sx, x - sx + blockW(blocks[b])];
    }
    const blockW = bl => clamp((bl.p ? Math.floor((bl.p[1] - bl.p[0]) / 60000) : 60) * 6, 240, 600);
    function paintGuide() {
      const G = s.guide;
      if (!G) return;
      G.rowEls.forEach((re, ri) => [...re.querySelectorAll('.ip-pb')].forEach((b, bi) => b.classList.toggle('f', s.zone === 'guide' && ri === G.r && bi === G.b)));
      const re = G.rowEls[G.r];
      rowReveal(re.querySelector('.ip-gd-view'), re.querySelector('.ip-gd-t'), re.querySelectorAll('.ip-pb')[G.b]);
      const viewH = G.view.offsetHeight || 500;
      const top = G.r * GUIDE_ROW, bot = top + 80;
      if (G.r === 0) G.scroll = 0;
      else if (top < G.scroll) G.scroll = top;
      else if (bot > G.scroll + viewH) G.scroll = bot - viewH;
      G.list.style.transform = `translateY(${-G.scroll}px)`;
      if (s.zone === 'guide') setFocusCh(G.rows[G.r].c);
    }
    function guideKey(key) {
      const G = s.guide;
      if (!G) { if (key === 'up') { toRibbon(); return true; } return key !== 'back'; }
      const nb = G.rows[G.r].blocks.length;
      if (TV.fwd(key)) { if (G.b < nb - 1) { G.b++; paintGuide(); } return true; }
      if (TV.bwd(key)) { if (G.b > 0) { G.b--; paintGuide(); } return true; }
      if (key === 'up' || key === 'down') {
        const nr = G.r + (key === 'down' ? 1 : -1);
        if (nr < 0) { toRibbon(); return true; }
        if (nr >= G.rows.length) return true;
        // Geometric focus search: the block in the next row nearest to this one.
        const [a, b] = blockSpan(G.r, G.b);
        const mid0 = (a + b) / 2;
        let best = 0, bestD = Infinity;
        G.rows[nr].blocks.forEach((_, i) => {
          const [x0, x1] = blockSpan(nr, i);
          const dist = mid0 < x0 ? x0 - mid0 : mid0 > x1 ? mid0 - x1 : 0;
          if (dist < bestD) { bestD = dist; best = i; }
        });
        G.r = nr;
        G.b = best;
        paintGuide();
        return true;
      }
      if (key === 'ok') { playCh(G.rows[G.r].c, false); return true; }
      return key !== 'back';
    }

    /* IptvSettingsScreen */
    function renderSettings() {
      const st = settings();
      const pl = D.pl;
      let row = 0;
      const R = () => row++;
      const sec = (t, top) => `<div class="ip-st-sec" style="margin-top:${top}px">${esc(t)}</div>`;
      const desc = d => `<div class="ip-st-d">${esc(d)}</div>`;
      const toggle = (key, title, d) => `
        <div class="ip-st-i" data-row="${R()}" data-col="0" data-k="t:${key}">
          <div class="ip-st-tx"><div class="ip-st-t">${esc(title)}</div>${desc(d)}</div>
          <div class="ip-st-sw${st[key] ? ' on' : ''}"><i></i></div>
        </div>`;
      const cycle = (key, title, d) => `
        <div class="ip-st-i" data-row="${R()}" data-col="0" data-k="c:${key}">
          <div class="ip-st-tx"><div class="ip-st-t">${esc(title)}</div>${desc(d)}</div>
          <div class="ip-st-cy"><span class="ar">◄</span><span class="v">${esc(OPTIONS[key]()[st[key]] || '')}</span><span class="ar">►</span></div>
        </div>`;
      let card = '';
      if (pl && !s.editing) {
        const r = R();
        card = `
          <div class="ip-pl">
            <div class="ip-pl-h"><div class="ip-pl-dot"><i></i></div><div class="ip-pl-n"><div class="a" dir="auto">${esc(pl.name || tr('My Playlist', 'הפלייליסט שלי'))}</div><div class="b">${tr('Active · Connected', 'פעיל · מחובר')}</div></div></div>
            <div class="ip-pl-row">${ico('link', 18)}<div class="tx"><div class="l">${tr('M3U SOURCE', 'קישור M3U')}</div><div class="v" dir="ltr">${esc(pl.url)}</div></div></div>
            <div class="ip-pl-row">${icon('tv', 18)}<div class="tx"><div class="l">${tr('EPG GUIDE', 'קישור EPG')}</div><div class="v" dir="ltr">${esc(pl.epgUrl || tr('Not configured', 'לא הוגדר'))}</div></div></div>
            <div class="ip-pl-btns">
              <div class="ip-pl-b" data-row="${r}" data-col="0" data-k="edit">${ico('edit', 18)}<span>${tr('Edit', 'עריכה')}</span></div>
              <div class="ip-pl-b del" data-row="${r}" data-col="1" data-k="delete">${ico('del', 18)}<span>${tr('Delete', 'מחק')}</span></div>
            </div>
          </div>`;
      } else if (pl) {
        const e = s.ed;
        const r1 = R(), r2 = R(), r3 = R(), r4 = R();
        card = `
          <div class="ip-ed">
            <div class="ip-ed-h"><div class="ip-ed-ic">${ico('edit', 24)}</div><div><div class="a">${tr('Edit Playlist', 'עריכת פלייליסט')}</div><div class="b">${tr('Update your stream links', 'עדכן את הקישורים שלך')}</div></div></div>
            <div class="ip-ed-l g">${tr('NAME', 'שם')}</div>
            ${fieldHtml('name', tr('Playlist Name', 'שם פלייליסט'), e.name, `data-row="${r1}" data-col="0" data-k="f:name"`)}
            <div class="ip-ed-l c">${tr('SOURCE', 'מקור')}</div>
            ${fieldHtml('url', tr('M3U Link', 'קישור M3U'), e.url, `data-row="${r2}" data-col="0" data-k="f:url"`)}
            <div class="ip-ed-l p">${tr('GUIDE', 'לוח שידורים')}</div>
            ${fieldHtml('epg', tr('EPG Link (Optional)', 'קישור EPG (אופציונלי)'), e.epg, `data-row="${r3}" data-col="0" data-k="f:epg"`)}
            <div class="ip-ed-btns">
              <div class="ip-ed-b save" data-row="${r4}" data-col="0" data-k="save">${icon('check', 20)}<span>${tr('Save', 'שמור')}</span></div>
              <div class="ip-ed-b" data-row="${r4}" data-col="1" data-k="cancel">${icon('close', 20)}<span>${tr('Cancel', 'ביטול')}</span></div>
            </div>
          </div>`;
      } else {
        card = `
          <div class="ip-st-i" data-row="${R()}" data-col="0" data-k="add">
            <div class="ip-st-tx"><div class="ip-st-t">${tr('Add Playlist', 'הוספת פלייליסט')}</div>${desc(tr('Configure a new M3U playlist with optional EPG guide.', 'הגדר פלייליסט M3U חדש עם EPG אופציונלי.'))}</div>
            <div class="ip-st-act">${tr('SETUP', 'התחל')}</div>
          </div>`;
      }
      body.innerHTML = `
        <div class="ip-set">
          <div class="ip-set-h">${icon('settings', 36)}<span>${tr('Lumina Settings', 'הגדרות לומינה')}</span></div>
          <div class="ip-set-view"><div class="ip-set-list">
            ${sec(tr('PLAYLIST', 'פלייליסט'), 0)}
            ${card}
            ${sec(tr('GENERAL', 'כללי'), 16)}
            ${toggle('autoStart', tr('Auto-Start on Boot', 'הפעלה אוטומטית'), tr('Automatically launch Lumina and play the last viewed channel.', 'הפעל את לומינה אוטומטית ונגן את הערוץ האחרון.'))}
            ${cycle('cat', tr('Default Launch Category', 'קטגוריה ברירת מחדל'), tr('Choose which category opens first.', 'בחר איזו קטגוריה נפתחת ראשונה.'))}
            ${sec(tr('PLAYBACK & STREAMING', 'ניגון וסטרימינג'), 24)}
            ${cycle('buffer', tr('Network Buffer Size', 'גודל באפר רשת'), tr('Increase buffer if streams are stuttering on Wi-Fi.', 'הגדל את הבאפר אם השידורים נתקעים.'))}
            ${cycle('decoder', tr('Video Decoder Engine', 'מנוע פענוח וידאו'), tr('Switch to Software decoding if you experience black screens.', 'עבור לפענוח תוכנה אם אתה רואה מסך שחור.'))}
            ${toggle('catchup', tr('Time-Shift / Catchup', 'הרצה אחורה / Catchup'), tr('Enable scrubbing back in time for supported channels.', 'אפשר חזרה אחורה בזמן לערוצים נתמכים.'))}
            ${sec(tr('CONTENT & DATA', 'תוכן ונתונים'), 24)}
            ${cycle('epgSync', tr('EPG Auto-Sync Interval', 'סנכרון EPG אוטומטי'), tr('How often should the app download the TV Guide.', 'כל כמה זמן להוריד את מדריך השידורים.'))}
            ${toggle('parental', tr('Parental Control (PIN)', 'בקרת הורים (PIN)'), tr('Lock specific categories with a 4-digit PIN.', 'נעל קטגוריות ספציפיות עם קוד PIN.'))}
          </div></div>
        </div>`;
      const items = [...body.querySelectorAll('[data-k]')];
      const prev = s.set;
      s.set = {
        items, rows: row,
        row: prev ? clamp(prev.row, 0, row - 1) : 0, col: prev ? prev.col : 0,
        scroll: prev ? prev.scroll : 0,
        view: TV.$(body, '.ip-set-view'), list: TV.$(body, '.ip-set-list'),
      };
      items.forEach(n => {
        if (n.dataset.fld) {
          const key = n.dataset.fld;
          const input = wireField(n, v => { s.ed[key] = v; });
          n._input = input;
        }
        TV.pointer(n, () => { setZone('set'); s.set.row = +n.dataset.row; s.set.col = +n.dataset.col; paintSet(); },
          () => { s.set.row = +n.dataset.row; s.set.col = +n.dataset.col; paintSet(); runSet(n); });
      });
      paintSet();
    }
    const setItem = () => {
      const S = s.set;
      const inRow = S.items.filter(n => +n.dataset.row === S.row);
      S.col = clamp(S.col, 0, inRow.length - 1);
      return inRow[S.col];
    };
    function paintSet() {
      const S = s.set;
      if (!S) return;
      const f = setItem();
      S.items.forEach(n => n.classList.toggle('f', s.zone === 'set' && n === f));
      if (!f) return;
      // Keep the focused item in view (LazyColumn bring-into-view).
      const vh = S.view.offsetHeight;
      const top = f.offsetTop, bot = top + f.offsetHeight;
      if (S.row === 0) S.scroll = 0;
      else if (bot + 8 > S.scroll + vh) S.scroll = bot + 8 - vh;
      else if (top - 8 < S.scroll) S.scroll = Math.max(0, top - 8);
      S.list.style.transform = `translateY(${-S.scroll}px)`;
    }
    function runSet(n) {
      const k = n.dataset.k;
      const st = settings();
      if (k.startsWith('t:')) {
        const key = k.slice(2);
        st[key] = !st[key];
        St.set(K.settings, st);
        renderSettings();
      } else if (k.startsWith('c:')) {
        const key = k.slice(2);
        st[key] = (st[key] + 1) % OPTIONS[key]().length;
        St.set(K.settings, st);
        renderSettings();
      } else if (k === 'add') {
        openSetup();
      } else if (k === 'edit') {
        s.editing = true;
        s.ed = { name: D.pl.name || '', url: D.pl.url || '', epg: D.pl.epgUrl || '' };
        s.set.row = 0;
        s.set.col = 0;
        renderSettings();
      } else if (k === 'delete') {
        deletePlaylist();
      } else if (k === 'save') {
        const e = s.ed;
        s.editing = false;
        renderSettings();
        updatePlaylist(e.name, e.url, e.epg);
      } else if (k === 'cancel') {
        s.editing = false;
        s.set.row = 0;
        s.set.col = 0;
        renderSettings();
      } else if (k.startsWith('f:') && n._input) {
        n._input.focus();
      }
    }
    function setKey(key) {
      const S = s.set;
      if (!S) { if (key === 'up') { toRibbon(); return true; } return key !== 'back'; }
      const typing = isInput();
      if (typing) {
        if (key === 'ok' || key === 'down') { typing.blur(); if (S.row < S.rows - 1) S.row++; paintSet(); }
        else if (key === 'up') { typing.blur(); if (S.row > 0) S.row--; paintSet(); }
        else if (key === 'back') typing.blur();
        return true;
      }
      if (key === 'up') { if (S.row === 0) { toRibbon(); return true; } S.row--; paintSet(); return true; }
      if (key === 'down') { if (S.row < S.rows - 1) S.row++; paintSet(); return true; }
      if (TV.fwd(key)) { S.col++; paintSet(); return true; }
      if (TV.bwd(key)) { S.col = Math.max(0, S.col - 1); paintSet(); return true; }
      if (key === 'ok') { const n = setItem(); if (n) runSet(n); return true; }
      return key !== 'back';
    }

    /* ── IptvSetupOverlay (manual setup; the phone/QR half needs Android's web server) ── */
    function openSetup() {
      const v = { name: '', url: '', epg: '' };
      layer.innerHTML = `
        <div class="ip-setup">
          <div class="ip-su">
            <div class="ip-su-l">
              <div class="ip-su-t">${tr('Manual Setup', 'הגדרה ידנית')}</div>
              <div class="ip-su-d">${tr('Enter your IPTV provider details below.', 'הזן את פרטי ספק ה-IPTV שלך למטה.')}</div>
              ${fieldHtml('name', tr('Playlist Name (Optional)', 'שם פלייליסט (אופציונלי)'), '')}
              ${fieldHtml('url', tr('M3U Link (Required)', 'קישור M3U (נדרש)'), '')}
              ${fieldHtml('epg', tr('EPG Link (Optional)', 'קישור EPG (אופציונלי)'), '')}
              <div class="ip-su-btn"><span>${tr('Connect Now', 'התחבר עכשיו')}</span></div>
              <div class="ip-su-err"></div>
            </div>
            <div class="ip-su-div"></div>
            <div class="ip-su-r">
              <div class="ip-su-t">${tr('Remote Setup', 'הגדרה בשלט')}</div>
              <div class="ip-su-d c">${tr('Press OK on a field to type with the TV keyboard.', 'לחץ OK על שדה כדי להקליד במקלדת הטלוויזיה.')}</div>
              <div class="ip-su-box">${icon('tv', 48)}<div>${tr('M3U / M3U8', 'M3U / M3U8')}</div></div>
              <div class="ip-su-s">${tr('For example:', 'לדוגמה:')}</div>
              <div class="ip-su-ex" dir="ltr">http://provider.tv/get.php?username=…&amp;type=m3u_plus</div>
            </div>
          </div>
        </div>`;
      const fields = [...layer.querySelectorAll('.ip-fld')];
      const btn = TV.$(layer, '.ip-su-btn');
      const err = TV.$(layer, '.ip-su-err');
      const items = fields.concat([btn]);
      const S = { idx: 0, busy: false, items, v };
      fields.forEach(f => {
        const key = f.dataset.fld;
        f._input = wireField(f, val => { v[key] = val; err.textContent = ''; });
      });
      items.forEach((n, i) => TV.pointer(n, () => { S.idx = i; paintSetup(); }, () => { S.idx = i; paintSetup(); activate(); }));
      s.setup = S;
      paintSetup();

      async function submit() {
        if (S.busy) return;
        if (!v.url.trim()) { err.textContent = tr('An M3U link is required.', 'נדרש קישור M3U.'); return; }
        S.busy = true;
        btn.classList.add('busy');
        btn.innerHTML = `<i class="ip-spin sm"></i><span>${tr('Connecting…', 'מתחבר…')}</span>`;
        try {
          await addPlaylist(v.name, v.url, v.epg);
          closeSetup();
          s.group = 'All';
          s.cat = 'All';
          s.pill = 0;
          refresh(true);
        } catch (e) {
          err.textContent = tr(`Couldn't load the playlist (${e.message})`, `טעינת הפלייליסט נכשלה (${e.message})`);
          btn.classList.remove('busy');
          btn.innerHTML = `<span>${tr('Connect Now', 'התחבר עכשיו')}</span>`;
        }
        S.busy = false;
      }
      function activate() {
        const n = items[S.idx];
        if (n === btn) submit();
        else n._input.focus();
      }
      S.activate = activate;
    }
    function paintSetup() {
      const S = s.setup;
      if (!S) return;
      S.items.forEach((n, i) => n.classList.toggle('f', i === S.idx));
    }
    function closeSetup() {
      const a = isInput();
      if (a) a.blur();
      s.setup = null;
      layer.innerHTML = '';
    }
    function setupKey(key) {
      const S = s.setup;
      const typing = isInput();
      if (typing) {
        if (key === 'ok' || key === 'down') { typing.blur(); S.idx = Math.min(S.items.length - 1, S.idx + 1); paintSetup(); }
        else if (key === 'up') { typing.blur(); S.idx = Math.max(0, S.idx - 1); paintSetup(); }
        else if (key === 'back') typing.blur();
        return true;
      }
      if (key === 'back') { closeSetup(); paintAll(); return true; }
      if (key === 'down') { S.idx = Math.min(S.items.length - 1, S.idx + 1); paintSetup(); return true; }
      if (key === 'up') { S.idx = Math.max(0, S.idx - 1); paintSetup(); return true; }
      if (key === 'ok') { S.activate(); return true; }
      return true;
    }

    /* ── CinematicScreensaver (after 2 minutes without a key) ── */
    function armSaver() {
      clearTimeout(s.saverT);
      if (!s.shown || PLAYER) return;
      s.saverT = setTimeout(showSaver, 120000);
    }
    function showSaver() {
      if (!s.shown || PLAYER || s.setup) return;
      const c = s.focusCh;
      const col = c ? HUES[Math.abs(jhash(c.name)) % HUES.length] : '';
      // The glow pulses 0.2↔0.5 on Android; static here (no endless animations on the TV).
      saver.innerHTML = `
        <div class="ip-sv-glow" style="background:${c ? `radial-gradient(circle 711px at center, rgba(${col},0.35), rgba(${col},0))` : 'none'}"></div>
        <div class="ip-sv-in">
          <div class="ip-sv-clock">${TV.clock()}</div>
          ${c ? (c.logo ? `<img class="ip-sv-logo" src="${esc(c.logo)}" alt="">` : `<div class="ip-sv-name" dir="auto">${esc(c.name)}</div>`) : ''}
        </div>`;
      const img = TV.$(saver, '.ip-sv-logo');
      if (img) img.addEventListener('error', () => { img.replaceWith(el(`<div class="ip-sv-name" dir="auto">${esc(c.name)}</div>`)); });
      saver.classList.add('on');
      s.saverOn = true;
      clearInterval(s.saverTick);
      s.saverTick = setInterval(() => { const ck = TV.$(saver, '.ip-sv-clock'); if (ck) ck.textContent = TV.clock(); }, 1000);
    }
    function hideSaver() {
      s.saverOn = false;
      saver.classList.remove('on');
      clearInterval(s.saverTick);
    }

    /* ── Focus zones ── */
    function setZone(z) {
      if (s.zone === z) return;
      s.zone = z;
      paintAll();
    }
    function paintAll() {
      paintRibbon();
      paintRecent();
      if (s.grid) paintGrid();
      if (s.guide) paintGuide();
      if (s.set) paintSet();
      paintEmpty();
    }
    function toRibbon() {
      s.pill = Math.max(0, s.groups.findIndex(g => g[0] === s.group));
      setZone('ribbon');
      paintRibbon();
    }
    /** Ribbon ↓ goes straight to the content (focusProperties { down = gridFocusRequester }). */
    function enterBody() {
      const z = bodyZone();
      if (z === 'grid') { s.gi = 0; setZone('grid'); paintGrid(); return; }
      if (z === 'guide') { if (s.guide) { s.guide.r = 0; s.guide.b = 0; setZone('guide'); } return; }
      if (z === 'set') { if (s.set) { s.set.row = 0; s.set.col = 0; setZone('set'); } return; }
      setZone('empty');
    }
    function recentKey(key) {
      if (TV.fwd(key)) { s.ri = Math.min(s.recents.length - 1, s.ri + 1); paintRecent(); return true; }
      if (TV.bwd(key)) { s.ri = Math.max(0, s.ri - 1); paintRecent(); return true; }
      if (key === 'up') { toRibbon(); return true; }
      if (key === 'down') {
        const z = bodyZone();
        if (z === 'grid') {
          const g = s.grid;
          const firstRow = g ? Math.max(0, Math.round((g.scroll - GRID_TOP) / PITCH)) : 0;
          s.gi = Math.min(s.list.length - 1, firstRow * COLS + Math.min(COLS - 1, s.ri));
          setZone('grid');
          paintGrid();
        } else if (z === 'empty') setZone('empty');
        return true;
      }
      if (key === 'ok') { playCh(s.recents[s.ri], true); return true; }
      return key !== 'back';
    }

    /* ── Play ── */
    function playCh(c, mark) {
      if (!c) return;
      if (mark) markWatched(c.id);
      hideSaver();
      clearTimeout(s.saverT);
      openPlayer(c, s.list.length ? s.list : [c], () => {
        if (!s.alive) return;
        const before = s.recents.map(x => x.id).join();
        s.recents = recents();
        if (s.recents.map(x => x.id).join() !== before) {
          s.ri = 0;
          renderMid();
        }
        armSaver();
      });
    }

    /* ── Data refresh ── */
    function refresh(fade) {
      s.favs = favSet();
      s.recents = recents();
      s.list = channelsFor(s.cat);
      if (s.focusCh && !D.byId.has(s.focusCh.id)) setFocusCh(null);
      renderRibbon();
      renderMid();
      if (s.group === 'Settings' && s.zone === 'set' && s.set) {
        renderSettings(); // keep the focused setting
      } else if (s.group === 'EPG Guide' && s.zone === 'guide' && s.guide) {
        const { r, b } = s.guide;
        renderGuide();
        if (s.guide) { s.guide.r = Math.min(r, s.guide.rows.length - 1); s.guide.b = Math.min(b, s.guide.rows[s.guide.r].blocks.length - 1); paintGuide(); }
      } else {
        renderBody(fade);
      }
      paintAll();
    }
    const onData = () => { if (s.alive) refresh(false); };
    D.listeners.add(onData);

    function initialGroup() {
      const st = settings();
      if (st.cat === 1) return 'Favorites';
      if (st.cat === 2) {
        const g = St.get(K.lastGroup, 'All');
        if (g === 'All' || g === 'Favorites' || D.byGroup.has(g)) return g;
      }
      return 'All';
    }
    async function boot() {
      const loading = ensureLoaded(); // sets D.loading first, so the body shows the spinner, not the empty state
      renderRibbon();
      renderMid();
      renderBody(false);
      await loading;
      if (!s.alive) return;
      s.group = s.cat = initialGroup();
      refresh(true);
      s.pill = Math.max(0, s.groups.findIndex(g => g[0] === s.group));
      paintRibbon();
      if (epgDue()) loadEpg(!!D.epg);
    }

    // Colour keys / Menu toggle a channel's favourite (KEYCODE_MENU on Android).
    const extraKeys = e => {
      if (!s.shown || PLAYER || TV.current() !== api || s.setup) return;
      if ([403, 404, 405, 406, 93].indexOf(e.keyCode) < 0 && e.key !== 'ContextMenu') return;
      if (s.zone === 'grid') { e.preventDefault(); favToggle(); armSaver(); }
    };
    window.addEventListener('keydown', extraKeys);

    const api = {
      el: root,
      onShow(first) {
        s.shown = true;
        if (first) boot();
        else refresh(false);
        armSaver();
      },
      onHide() {
        s.shown = false;
        clearTimeout(s.saverT);
        hideSaver();
      },
      destroy() {
        s.alive = false;
        s.shown = false;
        D.listeners.delete(onData);
        window.removeEventListener('keydown', extraKeys);
        clearTimeout(s.saverT);
        clearInterval(s.saverTick);
        if (PLAYER) PLAYER.close();
      },
      onKey(key) {
        armSaver();
        if (s.saverOn) { hideSaver(); return true; }
        if (s.setup) return setupKey(key);
        switch (s.zone) {
          case 'ribbon': {
            if (TV.fwd(key)) { s.pill = Math.min(s.groups.length - 1, s.pill + 1); paintRibbon(); return true; }
            if (TV.bwd(key)) { s.pill = Math.max(0, s.pill - 1); paintRibbon(); return true; }
            if (key === 'ok') { selectGroup(s.groups[s.pill][0]); return true; }
            if (key === 'down') { enterBody(); return true; }
            return key !== 'back';
          }
          case 'recent': return recentKey(key);
          case 'grid': return gridKey(key);
          case 'guide': return guideKey(key);
          case 'set': return setKey(key);
          case 'empty':
            if (key === 'ok') { openSetup(); return true; }
            if (key === 'up') { toRibbon(); return true; }
            return key !== 'back';
          default: return key !== 'back';
        }
      },
    };
    return api;
  });

  /** For other screens (e.g. the home screen) that want to start a channel directly. */
  TV.iptv = { parseM3u, ensureLoaded, channels: () => D.channels };
})();
