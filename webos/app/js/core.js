/**
 * LuminaStreams TV — core.
 * The LG app is built for the TV alone (not the website): it lays out in the
 * Android TV app's units (1706.67×960 dp: the app forces 180 dpi), moves
 * focus with its own Compose-style engine, and animates only transform/opacity.
 */
(() => {
  'use strict';

  const TV = {};
  window.TV = TV;
  // Shared modules (torrentio.js ranks lossless audio like Android when it sees a TV).
  window.TvMode = Object.freeze({ isTv: true, isWebOS: !!window.PalmSystem || /Web0S|webOS/i.test(navigator.userAgent) });

  /* ── Scale: 1 CSS px = 1 dp ─────────────────────────────────────── */
  // The Android app forces 180 dpi (MainActivity FORCED_DENSITY_DPI): 1 dp = 1.125 px,
  // so a 1080p screen is 1706.67 × 960 dp.
  const DP_PER_PX = 160 / 180;
  const W = 1920 * DP_PER_PX;
  const H = 1080 * DP_PER_PX;
  TV.W = W;
  TV.H = H;
  function fit() {
    const root = document.getElementById('tv');
    const z = Math.min(window.innerWidth / W, window.innerHeight / H);
    root.style.zoom = String(z);
  }
  TV.fit = fit;

  /* ── Language ─────────────────────────────────────────────────────── */
  TV.lang = () => (window.I18n && window.I18n.getLanguage ? window.I18n.getLanguage() : 'he');
  TV.rtl = () => TV.lang() === 'he';
  TV.tr = (en, he) => (TV.rtl() ? he : en);

  /* ── DOM helpers ──────────────────────────────────────────────────── */
  TV.esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  TV.el = (html) => {
    const t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  };
  TV.$ = (root, sel) => root.querySelector(sel);

  /* ── Material icons (24 dp viewBox) ───────────────────────────────── */
  const ICONS = {
    search: 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
    movie: 'M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z',
    liveTv: 'M21 6h-7.59l3.29-3.29L16 2l-4 4-4-4-.71.71L10.59 6H3c-1.1 0-2 .89-2 2v12c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.11-.9-2-2-2zm0 14H3V8h18v12zM9 10v8l7-4z',
    autoAwesome: 'M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z',
    tv: 'M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z',
    cloud: 'M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
    favorite: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
    favoriteBorder: 'M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z',
    settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
    account: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z',
    play: 'M8 5v14l11-7z',
    person: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
    add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
    check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
    back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
    close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
    replay: 'M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z',
    bolt: 'M7 2v11h3v9l7-12h-4l4-8z',
    star: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
    movieFilter: 'M18 4l2 3h-3l-2-3h-2l2 3h-3l-2-3H8l2 3H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4zm-6.75 11.25L10 18l-1.25-2.75L6 14l2.75-1.25L10 10l1.25 2.75L14 14l-2.75 1.25zm5.69-3.31L16 14l-.94-2.06L13 11l2.06-.94L16 8l.94 2.06L19 11l-2.06.94z',
  };
  TV.icon = (name, size = 24, cls = '') =>
    `<svg class="ic ${cls}" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name] || ''}"/></svg>`;

  /* ── Images ───────────────────────────────────────────────────────── */
  const IMG = 'https://image.tmdb.org/t/p/';
  TV.img = (path, size) => (path ? `${IMG}${size}${path}` : '');
  /** Load and decode an image off the main thread; resolves with the URL (or '' on failure). */
  const decoded = new Map();
  TV.decode = url => {
    if (!url) return Promise.resolve('');
    if (decoded.has(url)) return decoded.get(url);
    const p = new Promise(resolve => {
      const im = new Image();
      im.onload = () => (im.decode ? im.decode().catch(() => {}) : Promise.resolve()).then(() => resolve(url));
      im.onerror = () => resolve('');
      im.src = url;
    });
    decoded.set(url, p);
    if (decoded.size > 80) decoded.delete(decoded.keys().next().value);
    return p;
  };

  /* ── TMDB (Android's exact queries) ───────────────────────────────── */
  const TMDB_KEY = (window.CONST && window.CONST.TMDB_API_KEY) || '9ab4a284f0c028007b78925852196b79';
  const cache = new Map();
  TV.tmdb = (path, params = {}) => {
    const q = Object.entries(Object.assign({ api_key: TMDB_KEY, language: TV.rtl() ? 'he-IL' : 'en-US' }, params))
      .filter(([, v]) => v != null && v !== '')
      .map(([k, v]) => `${k}=${encodeURIComponent(v).replace(/%7C/g, '|')}`).join('&');
    const url = `https://api.themoviedb.org/3${path}?${q}`;
    if (cache.has(url)) return cache.get(url);
    const p = fetch(url).then(r => (r.ok ? r.json() : Promise.reject(new Error(`TMDB ${r.status}`))));
    cache.set(url, p);
    p.catch(() => cache.delete(url));
    return p;
  };

  /** Android's Movie model from a TMDB result (skips items without art, like Android). */
  TV.toMovie = (j, mediaType) => {
    const mt = j.media_type || mediaType;
    if (mt !== 'movie' && mt !== 'tv') return null;
    if (!j.backdrop_path || !j.poster_path) return null;
    const date = mt === 'tv' ? j.first_air_date : j.release_date;
    return {
      key: `${mt}_${j.id}`,
      id: j.id,
      type: mt,
      title: (mt === 'tv' ? j.name || j.original_name : j.title || j.original_title) || '',
      poster: j.poster_path,
      backdrop: j.backdrop_path,
      overview: j.overview || '',
      year: date ? Number(String(date).slice(0, 4)) || 0 : 0,
      rating: Number(j.vote_average) || 0,
      genreIds: j.genre_ids || [],
    };
  };
  TV.list = async (path, params, mediaType) => {
    const data = await TV.tmdb(path, params);
    return (data.results || []).map(j => TV.toMovie(j, mediaType)).filter(Boolean);
  };

  /* ── Keys ─────────────────────────────────────────────────────────── */
  const KEYMAP = {
    37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok',
    461: 'back', 8: 'back', 27: 'back', 10009: 'back',
    415: 'play', 19: 'pause', 413: 'stop', 417: 'ff', 412: 'rw', 10252: 'playpause', 179: 'playpause',
  };
  TV.keyOf = e => KEYMAP[e.keyCode] || ({ ArrowLeft: 'left', ArrowUp: 'up', ArrowRight: 'right', ArrowDown: 'down', Enter: 'ok', Escape: 'back', GoBack: 'back', BrowserBack: 'back', MediaPlayPause: 'playpause' }[e.key]) || null;
  /** "Next" along a row: left in Hebrew (rows run right to left), right in English. */
  TV.fwd = key => (TV.rtl() ? key === 'left' : key === 'right');
  TV.bwd = key => (TV.rtl() ? key === 'right' : key === 'left');

  /* ── Screen stack (Android back stack: screens stay alive underneath) ─ */
  const screens = {};
  const stack = [];
  TV.register = (name, factory) => { screens[name] = factory; };
  TV.current = () => stack[stack.length - 1] || null;
  TV.open = (name, params = {}) => {
    const prev = TV.current();
    const screen = screens[name](params);
    screen.name = name;
    document.getElementById('screens').appendChild(screen.el);
    if (prev) { prev.el.classList.add('hidden'); if (prev.onHide) prev.onHide(); }
    stack.push(screen);
    if (screen.onShow) screen.onShow(true);
    return screen;
  };
  TV.back = () => {
    if (stack.length <= 1) { TV.exit(); return; }
    const top = stack.pop();
    if (top.destroy) top.destroy();
    top.el.remove();
    const now = TV.current();
    now.el.classList.remove('hidden');
    if (now.onShow) now.onShow(false);
  };
  /** Replace the whole stack (e.g. switching sections from the top bar). */
  TV.reset = (name, params) => {
    while (stack.length) { const s = stack.pop(); if (s.destroy) s.destroy(); s.el.remove(); }
    return TV.open(name, params);
  };
  TV.exit = () => {
    if (window.PalmSystem && window.PalmSystem.platformBack) window.PalmSystem.platformBack();
    else if (window.webOS && window.webOS.platformBack) window.webOS.platformBack();
  };

  // Full-screen layers above the screens (sheets, the player) get keys first.
  const layers = [];
  TV.pushLayer = layer => { layers.push(layer); return () => { const i = layers.indexOf(layer); if (i >= 0) layers.splice(i, 1); }; };

  /* ── Toast ────────────────────────────────────────────────────────── */
  let toastTimer = 0;
  TV.toast = text => {
    const t = document.getElementById('toast');
    t.textContent = text;
    t.classList.add('on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('on'), 2600);
  };

  /* ── Input ────────────────────────────────────────────────────────── */
  let lastKeyFrame = false;
  window.addEventListener('keydown', e => {
    const key = TV.keyOf(e);
    if (!key) return;
    const typing = /^(INPUT|TEXTAREA)$/.test((document.activeElement || {}).tagName || '');
    if (typing && (key === 'left' || key === 'right' || (key === 'back' && e.keyCode === 8))) return;
    // The player (shared Android player module) handles its own keys.
    if (document.querySelector('.ap-tv')) return;
    e.preventDefault();
    // Held key: at most one step per frame, so focus never runs ahead of the picture.
    if (e.repeat && lastKeyFrame) return;
    lastKeyFrame = true;
    requestAnimationFrame(() => { lastKeyFrame = false; });
    const top = layers[layers.length - 1];
    if (top && top.onKey(key, e)) return;
    const s = TV.current();
    if (s && s.onKey && s.onKey(key, e)) return;
    if (key === 'back') TV.back();
  }, true);

  // Magic Remote pointer: hovering focuses, clicking selects (elements expose _tvFocus/_tvClick).
  document.addEventListener('mouseover', e => {
    const t = e.target.closest && e.target.closest('[data-f]');
    if (t && t._tvFocus) t._tvFocus();
  });
  document.addEventListener('click', e => {
    const t = e.target.closest && e.target.closest('[data-f]');
    if (t && t._tvClick) { e.preventDefault(); t._tvClick(); }
  });

  /** Wire an element to the pointer: hover → focus(), click → click(). */
  TV.pointer = (el, focus, click) => {
    el.setAttribute('data-f', '');
    el._tvFocus = focus;
    el._tvClick = click;
    return el;
  };

  /* ── Row scrolling by transform (Compose LazyRow keeps the focused item in view) ── */
  /**
   * @param {HTMLElement} track   the moving strip (direction-aware flex row)
   * @param {number} index        focused item
   * @param {number} itemW        item width (dp)
   * @param {number} gap          spacing (dp)
   * @param {number} viewW        visible width (dp)
   * @param {number} pad          content padding (dp)
   * @param {{x:number}} state    current scroll, kept by the caller
   */
  TV.scrollRow = (track, index, itemW, gap, viewW, pad, state) => {
    const start = pad + index * (itemW + gap);
    const end = start + itemW;
    let x = state.x;
    if (end > x + viewW - pad) x = end - viewW + pad;
    if (start < x + pad) x = start - pad;
    x = Math.max(0, x);
    if (x !== state.x) {
      state.x = x;
      track.style.transform = `translateX(${TV.rtl() ? x : -x}px)`;
    }
  };

  /**
   * Keep `item` inside `viewport` by translating `track` (measured, so it works for
   * items of any width, in either direction and at any zoom). axis: 'x' | 'y'.
   */
  TV.reveal = (viewport, track, item, pad = 8, axis = 'x') => {
    if (!viewport || !track || !item) return;
    // Layout offsets (not on-screen rects), so a slide still in progress doesn't skew it.
    if (getComputedStyle(viewport).position === 'static') viewport.style.position = 'relative';
    if (getComputedStyle(track).position === 'static') track.style.position = 'relative';
    const x = axis === 'x';
    let shift = Number(track.dataset.shift || 0);
    const start = (x ? track.offsetLeft + item.offsetLeft : track.offsetTop + item.offsetTop) + shift;
    const end = start + (x ? item.offsetWidth : item.offsetHeight);
    const size = x ? viewport.clientWidth : viewport.clientHeight;
    if (start < pad) shift += pad - start;
    else if (end > size - pad) shift -= end - (size - pad);
    else return;
    if (!x) shift = Math.min(0, shift); // lists never scroll above their top padding
    track.dataset.shift = String(shift);
    track.style.transform = x ? `translateX(${shift}px)` : `translateY(${shift}px)`;
  };

  /* ── Clock ────────────────────────────────────────────────────────── */
  TV.clock = () => {
    const d = new Date();
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };
})();
