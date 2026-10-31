/**
 * LuminaStreams TV — Settings, 1:1 with the Android TV SettingsScreen.kt (+ SettingsViewModel.kt).
 * Sidebar 280 dp (back + title, separator, five categories with the gold indicator) and a content
 * column of section headers and cards (padding 48/16/48/64, items 8 dp apart).
 * Keys mirror the Android onPreviewKeyEvent handlers: from a category, "forward" (Android: Left in
 * Hebrew) jumps into the content; from the content, "backward" or Back returns to the selected category.
 */
(() => {
  'use strict';
  const { tr, esc, el } = TV;

  /* ── Material icons used here (24 dp viewBox) ─────────────────────── */
  const P = {
    chevron: 'M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
    surround: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM7.76 16.24l-1.41 1.41C4.78 16.1 4 14.05 4 12c0-2.05.78-4.1 2.34-5.66l1.41 1.41C6.59 8.93 6 10.46 6 12s.59 3.07 1.76 4.24zM12 16c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm5.66 1.66l-1.41-1.41C17.41 15.07 18 13.54 18 12s-.59-3.07-1.76-4.24l1.41-1.41C19.22 7.9 20 9.95 20 12c0 2.05-.78 4.1-2.34 5.66zM12 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
    voice: 'M9 13c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zm6.08-7.95c.84 1.18.84 2.71 0 3.89l1.68 1.69c2.02-2.02 2.02-5.07 0-7.27l-1.68 1.69zM20.07 2l-1.63 1.63c2.77 3.02 2.77 7.56 0 10.74L20.07 16c3.9-3.89 3.91-9.95 0-14z',
    hq: 'M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 11H9.5v-2h-2v2H6V9h1.5v2.5h2V9H11v6zm7-1c0 .55-.45 1-1 1h-.75v1.5h-1.5V15H14c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1h3c.55 0 1 .45 1 1v4zm-3.5-.5h2v-3h-2v3z',
    zip: 'M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-2 6h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z',
    subtitles: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z',
    size: 'M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h3v-7h3V9H3v3z',
    color: 'M2 20h20v4H2v-4zm3.49-3h2.42l1.27-3.58h5.65L16.09 17h2.42L13.25 3h-2.5L5.49 17zm4.42-5.61l2.03-5.79h.12l2.03 5.79H9.91z',
    language: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z',
    speed: 'M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
    storage: 'M2 20h20v-4H2v4zm2-3h2v2H4v-2zM2 4v4h20V4H2zm4 3H4V5h2v2zm-4 7h20v-4H2v4zm2-3h2v2H4v-2z',
    history: 'M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
    sweep: 'M15 16h4v2h-4zm0-8h7v2h-7zm0 4h6v2h-6zM3 18c0 1.1.9 2 2 2h6c1.1 0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6L5 5H2v2h12z',
    manageSearch: 'M7 9H2V7h5v2zm0 3H2v2h5v-2zm13.59 7l-3.83-3.83c-.8.52-1.74.83-2.76.83-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5c0 1.02-.31 1.96-.83 2.75L22 17.59 20.59 19zM17 11c0-1.65-1.35-3-3-3s-3 1.35-3 3 1.35 3 3 3 3-1.35 3-3zM2 19h10v-2H2v2z',
    premium: 'M9.68 13.69L12 11.93l2.31 1.76-.88-2.85L15.75 9h-2.84L12 6.19 11.09 9H8.25l2.31 1.84-.88 2.85zM20 10c0-4.42-3.58-8-8-8s-8 3.58-8 8c0 2.03.76 3.87 2 5.28V23l6-2 6 2v-7.72c1.24-1.41 2-3.25 2-5.28zm-8-6c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6 2.69-6 6-6z',
    phonelink: 'M20.1 7.7l-1 1c1.8 1.8 1.8 4.6 0 6.5l1 1c2.5-2.3 2.5-6.1 0-8.5zM18 9.8l-1 1c.5.7.5 1.6 0 2.3l1 1c1.2-1.2 1.2-3 0-4.3zM14 1H4c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-2-2-2zm0 19H4V4h10v16z',
    vpnKey: 'M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
    cloudSync: 'M21.5 14.98c-.02 0-.03 0-.05.01C21.2 13.3 19.76 12 18 12c-1.4 0-2.6.83-3.16 2.02C13.26 14.1 12 15.4 12 17c0 1.66 1.34 3 3 3l6.5-.02c1.38 0 2.5-1.12 2.5-2.5s-1.12-2.5-2.5-2.5zM10 4.26v2.09C7.67 7.18 6 9.39 6 12c0 1.77.78 3.34 2 4.44V14h2v6H4v-2h2.73C5.06 16.54 4 14.4 4 12c0-3.73 2.55-6.85 6-7.74zM20 6h-2.73c1.43 1.26 2.41 3.01 2.66 5l-2.02-.02C17.68 9.64 16.98 8.45 16 7.56V10h-2V4h6v2z',
  };
  const svg = (name, size, cls = '') =>
    `<svg class="ic ${cls}" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path d="${P[name]}"/></svg>`;

  /* ── Preferences (window.Storage, prefix lumina_) ─────────────────── */
  const KEY = {
    audioLang: 'settings_audio_lang',          // 'original' | 'he' | 'en'        (Android preferred_audio_lang)
    maxRes: 'settings_max_resolution',         // 'Unlimited' | '4K' | '1080p' | '720p'
    maxSize: 'settings_max_file_size',         // 'Unlimited' | '20GB' | '10GB' | '5GB'
    subSize: 'settings_sub_font_size',         // 'small' | 'medium' | 'large' | 'xlarge'
    yellow: 'settings_yellow_subs',            // boolean
    lite: 'settings_lite_ui',                  // boolean (Android lite_ui)
    saveHistory: 'settings_save_history',      // boolean (Android save_history)
  };
  const get = (k, d) => {
    try { const v = window.Storage ? window.Storage.get(k, d) : d; return v == null ? d : v; } catch { return d; }
  };
  const put = (k, v) => { if (window.Storage) window.Storage.set(k, v); };
  const GB = 1024 * 1024 * 1024;

  // Lite UI (Android DeviceProfile.forceLowTier): no focus scaling, glows or transitions anywhere.
  const applyLite = () => document.documentElement.classList.toggle('lite-ui', !!get(KEY.lite, false));
  applyLite();

  /** Read-only view of the settings for the other TV screens (details, player, search). */
  TV.prefs = Object.freeze({
    audioLang: () => get(KEY.audioLang, 'original'),
    maxResolution: () => get(KEY.maxRes, 'Unlimited'),
    maxFileSizeBytes: () => ({ '20GB': 20 * GB, '10GB': 10 * GB, '5GB': 5 * GB }[get(KEY.maxSize, 'Unlimited')] || null),
    /** Android DetailsViewModel filter: quality '4K' | '1080p' | anything else, size in bytes (0 = unknown). */
    allowsStream(quality, sizeBytes) {
      const res = get(KEY.maxRes, 'Unlimited');
      if (res === '1080p' && quality === '4K') return false;
      if (res === '720p' && (quality === '4K' || quality === '1080p')) return false;
      const max = TV.prefs.maxFileSizeBytes();
      return !(max && sizeBytes > max);
    },
    saveSearchHistory: () => get(KEY.saveHistory, true) !== false,
    liteUi: () => !!get(KEY.lite, false),
  });

  /* ── Shared state that outlives the screen ─────────────────────────── */
  // The AllDebrid PIN flow keeps polling after Settings closes, so reopening shows the same PIN.
  let adFlow = { status: 'idle' }; // idle | loading | waiting {pin, url}
  let live = null;                 // the open Settings screen's refresh()
  let authHooked = false;
  const AD_TOKEN_KEY = 'lumina_ad_token';
  const userToken = () => { try { return localStorage.getItem(AD_TOKEN_KEY) || ''; } catch { return ''; } };

  const sleep = ms => new Promise(r => setTimeout(r, ms));
  async function ping(url) {
    const ctl = window.AbortController ? new AbortController() : null;
    const timer = ctl ? setTimeout(() => ctl.abort(), 10000) : 0; // OkHttp's default timeout
    try {
      const r = await fetch(url, { cache: 'no-store', signal: ctl ? ctl.signal : undefined });
      return r.ok;
    } catch { return false; } finally { clearTimeout(timer); }
  }
  function fmtBytes(b) {
    if (b < 1024 * 1024) return `${Math.floor(b / 1024)} KB`;
    if (b < GB) return `${(b / 1024 / 1024).toFixed(1)} MB`;
    return `${(b / GB).toFixed(2)} GB`;
  }
  function deviceInfo() {
    let info = {};
    try { info = JSON.parse((window.PalmSystem && window.PalmSystem.deviceInfo) || '{}') || {}; } catch {}
    const chrome = (/Chrome\/(\d+)/.exec(navigator.userAgent) || [])[1];
    const parts = [info.platformVersion ? `webOS ${info.platformVersion}` : (window.PalmSystem ? 'webOS' : 'Web')];
    parts.push(info.modelName || (chrome ? `Chromium ${chrome}` : 'LG TV'));
    parts.push(navigator.deviceMemory ? `${Math.round(navigator.deviceMemory * 1024)} MB RAM` : `${navigator.hardwareConcurrency || '?'} CPU`);
    return parts.join(' — ');
  }
  let appVersion = '';
  function loadVersion(done) {
    if (appVersion) { done(); return; }
    try {
      const x = new XMLHttpRequest();
      x.open('GET', 'appinfo.json');
      x.onload = () => { try { appVersion = JSON.parse(x.responseText).version || ''; } catch {} done(); };
      x.onerror = () => done();
      x.send();
    } catch { done(); }
  }

  const CATS = ['account', 'playback', 'subtitles', 'system', 'about'];
  const catLabel = c => ({
    account: tr('Account', 'חשבון'), playback: tr('Playback', 'ניגון'), subtitles: tr('Subtitles', 'כתוביות'),
    system: tr('System', 'מערכת'), about: tr('About', 'אודות'),
  }[c]);

  TV.register('settings', () => {
    const AD = window.AllDebrid;
    const FA = window.FirebaseAuth;
    const SM = window.SyncManager;

    const s = {
      cat: 0,
      zone: 'side',     // 'side' | 'content'
      side: 0,          // -1 = back button, 0..4 = category (Android starts on the selected category)
      key: null,        // focused content item
      scroll: 0,
      rd: null,         // AllDebrid account check result {dot, text}
      adSrv: null, trSrv: null, checkingSrv: false,
      sync: '', syncing: false,
      cache: 'Calculating...', history: 'Clear',
      dead: false,
    };
    const handlers = {};

    const root = el(`
      <div class="screen se">
        <div class="se-side">
          <div class="se-head"><div class="se-back">${TV.icon('back', 20)}</div><div class="se-title">${tr('Settings', 'הגדרות')}</div></div>
          <div class="se-sep"></div>
          <div class="se-cats">${CATS.map(c => `<div class="se-cat"><i class="se-ind"></i><span>${esc(catLabel(c))}</span></div>`).join('')}</div>
        </div>
        <div class="se-main"><div class="se-list"></div></div>
      </div>`);
    const backEl = TV.$(root, '.se-back');
    const catEls = [...root.querySelectorAll('.se-cat')];
    const list = TV.$(root, '.se-list');

    /* ── Rows (ToggleRow / CycleRow / ActionRow inside a SettingsCard) ── */
    function row(r) {
      handlers[r.key] = r.run;
      let end;
      if (r.kind === 'toggle') end = `<span class="se-sw${r.on ? ' on' : ''}"><i></i></span>`;
      else if (r.kind === 'cycle') end = `<span class="se-val">${esc(r.value)}</span>${svg('chevron', 20, 'se-chev')}`;
      else end = `<span class="se-desc">${esc(r.desc)}</span>`;
      return `<div class="se-row" data-k="${r.key}"><span class="se-ib">${svg(r.icon, 18)}</span><span class="se-rt">${esc(r.title)}</span><span class="se-end">${end}</span></div>`;
    }
    const card = rows => `<div class="se-card">${rows.map(row).join('<div class="se-div"></div>')}</div>`;
    const header = t => `<div class="se-h">${esc(t)}</div>`;
    const cycle = (listV, cur, fallback) => {
      const i = listV.indexOf(cur);
      return listV[((i >= 0 ? i : fallback) + 1) % listV.length];
    };
    const dot = d => `<i class="se-dot ${d}"></i>`;

    /* ── Account: AllDebrid banner (BuildRdBanner) ── */
    function rdBanner() {
      const token = userToken();
      if (token) {
        handlers.rd = () => { if (AD) AD.logout(); s.rd = null; render(); };
        const result = s.rd
          ? `<span class="se-rd-res">${s.rd.dot ? dot(s.rd.dot) : ''}${esc(s.rd.text)}</span>`
          : `<span class="se-btn">${esc(tr('Check Status', 'בדוק חיבור'))}</span><span class="se-rd-gap"></span>`;
        return `
          <div class="se-rd-on" data-k="rd">
            <div class="se-rd-l">
              <div class="se-rd-tag">${svg('premium', 24)}<span>${esc(tr('AllDebrid ACTIVE', 'חשבון פרימיום פעיל'))}</span></div>
              <div class="se-rd-tok" dir="auto">${esc(tr('Token:', 'טוקן:'))} ${esc(token.slice(0, 5))}•••••••••${esc(token.slice(-4))}</div>
            </div>
            <div class="se-rd-r">${result}<span class="se-rd-dis">${esc(tr('Click to Disconnect', 'לחץ לניתוק'))}</span></div>
          </div>`;
      }
      if (adFlow.status === 'loading') return '<div class="se-rd-load"><i class="se-spin"></i></div>';
      if (adFlow.status === 'waiting') {
        return `
          <div class="se-rd-wait">
            <div class="se-rd-wl">
              <div class="se-rd-wic">${svg('phonelink', 26)}</div>
              <div class="se-rd-wt">${esc(tr('Device Linking', 'חיבור מכשיר'))}</div>
              <div class="se-rd-wd">${esc(tr('Visit the website from your phone or computer and enter the code shown here.', 'הכנס לאתר מהטלפון או מהמחשב והקש את הקוד שמופיע כאן.'))}</div>
            </div>
            <div class="se-rd-wdiv"></div>
            <div class="se-rd-wr">
              <div class="se-rd-step">${esc(tr('1. VISIT WEBSITE', '1. כנס לאתר'))}</div>
              <div class="se-rd-url" dir="ltr">${esc(adFlow.url)}</div>
              <div class="se-rd-step se-rd-step2">${esc(tr('2. ENTER CODE', '2. הקש קוד'))}</div>
              <div class="se-rd-code"><span dir="ltr">${esc(adFlow.pin)}</span></div>
            </div>
          </div>`;
      }
      handlers.rd = startAdAuth;
      return `
        <div class="se-big se-rd-idle" data-k="rd">
          <div class="se-rd-key">${svg('vpnKey', 28)}</div>
          <div class="se-rd-it">
            <div class="se-rd-t">${esc(tr('Connect AllDebrid', 'חבר חשבון AllDebrid'))}</div>
            <div class="se-rd-s">${esc(tr('Unlock 4K streaming and zero-buffering playback.', 'תמיכה ב-4K וסטרימינג ללא עצירות.'))}</div>
          </div>
          <div class="se-pill">${esc(tr('Link Account', 'קשר חשבון'))}</div>
        </div>`;
    }
    function serverCard() {
      handlers.srv = checkServers;
      const st = v => (v == null ? 'Checking...' : `${dot(v ? 'g' : 'r')}${v ? 'Online' : 'Offline'}`);
      return `
        <div class="se-big se-srv" data-k="srv">
          <div class="se-srv-c"><div class="se-srv-l">${esc(tr('AllDebrid API', 'שרתי AllDebrid'))}</div><div class="se-srv-v">${st(s.adSrv)}</div></div>
          <div class="se-srv-div"></div>
          <div class="se-srv-c se-srv-c2"><div class="se-srv-l">${esc(tr('Torrentio Scraper', 'שרתי Torrentio'))}</div><div class="se-srv-v">${st(s.trSrv)}</div></div>
          <span class="se-btn se-srv-btn">${esc(s.checkingSrv ? tr('Checking...', 'בודק...') : tr('Refresh', 'רענן'))}</span>
        </div>`;
    }
    function cloudCard() {
      const user = FA && FA.getCurrentUser ? FA.getCurrentUser() : null;
      handlers.cloud = () => { if (user) runSync(); else TV.open('auth'); };
      const text = {
        PUSHING: tr('Uploading data...', 'מעלה נתונים...'), PULLING: tr('Downloading data...', 'מוריד נתונים...'),
        SUCCESS: tr('Sync Complete ✓', 'סנכרון הושלם ✓'), ERROR: tr('Sync Failed ✗', 'סנכרון נכשל ✗'), NO_USER: tr('Not logged in', 'לא מחובר לחשבון'),
      }[s.sync] || '';
      const color = s.sync === 'SUCCESS' ? 'ok' : (s.sync === 'ERROR' || s.sync === 'NO_USER') ? 'bad' : '';
      let end = '';
      if (text) end = `<span class="se-cl-st ${color}">${esc(text)}</span>`;
      else if (user) end = `<div class="se-pill se-cl-pill">${esc(s.syncing ? tr('Syncing...', 'מסנכרן...') : tr('Sync', 'סנכרן'))}</div>`;
      return `
        <div class="se-big se-cl${s.syncing ? ' busy' : ''}" data-k="cloud">
          <div class="se-cl-ic">${svg('cloudSync', 28)}</div>
          <div class="se-cl-tx">
            <div class="se-cl-t">${esc(user ? tr('Sync Now', 'סנכרן עכשיו') : tr('Not Logged In', 'לא מחובר'))}</div>
            <div class="se-cl-s" dir="${user ? 'ltr' : 'auto'}">${esc(user ? user.email || '' : tr('Login to sync between devices', 'התחבר כדי לסנכרן בין מכשירים'))}</div>
          </div>
          ${end}
        </div>`;
    }
    function infoCard() {
      handlers.info = null;
      const col = (l, v) => `<div class="se-in-c"><div class="se-in-l">${esc(l)}</div><div class="se-in-v" dir="ltr">${esc(v)}</div></div>`;
      return `
        <div class="se-big se-inf" data-k="info">
          ${col(tr('App Version', 'גרסת אפליקציה'), `${appVersion || '1.0.0'}-Lumina`)}
          ${col(tr('Device Capability', 'יכולת מכשיר'), deviceInfo() || 'Detecting...')}
          ${col(tr('Engines', 'מנועים'), 'Torrentio / TMDB / Ktuvit')}
        </div>`;
    }

    function content() {
      const St = window.Storage;
      switch (CATS[s.cat]) {
        case 'account':
          return header(tr('PREMIUM', 'חשבון פרימיום')) + rdBanner()
            + header(tr('SERVICE STATUS', 'סטטוס שרתים')) + serverCard()
            + header(tr('CLOUD SYNC', 'סנכרון ענן')) + cloudCard();
        case 'playback': {
          const lang = get(KEY.audioLang, 'original');
          const res = get(KEY.maxRes, 'Unlimited');
          const size = get(KEY.maxSize, 'Unlimited');
          return header(tr('AUDIO', 'שמע')) + card([
            // Android: ExoPlayer passthrough toggle. The TV's own decoder already passes Dolby/DTS
            // to the sound system (set in the TV's sound settings), so here it is informational.
            { key: 'passthrough', kind: 'action', icon: 'surround', title: tr('Audio Passthrough', 'העברת שמע גולמי'),
              desc: tr('Automatic (TV)', 'אוטומטי (טלוויזיה)'),
              run: () => TV.toast(tr('The TV sends Dolby / DTS audio to your sound system by itself — set it in the TV sound settings.', 'הטלוויזיה מעבירה שמע Dolby / DTS למערכת השמע בעצמה — מגדירים בהגדרות הסאונד של הטלוויזיה.')) },
            { key: 'audioLang', kind: 'cycle', icon: 'voice', title: tr('Preferred Audio', 'שפת שמע מועדפת'),
              value: lang === 'original' ? tr('Original', 'מקור') : lang === 'he' ? 'עברית' : 'English',
              run: () => { put(KEY.audioLang, cycle(['original', 'he', 'en'], lang, 0)); render(); } },
          ]) + header(tr('QUALITY', 'איכות')) + card([
            { key: 'maxRes', kind: 'cycle', icon: 'hq', title: tr('Max Resolution', 'רזולוציה מקסימלית'),
              value: res === 'Unlimited' ? tr('No Limit', 'ללא הגבלה') : res,
              run: () => { put(KEY.maxRes, cycle(['Unlimited', '4K', '1080p', '720p'], res, 0)); render(); } },
            { key: 'maxSize', kind: 'cycle', icon: 'zip', title: tr('Max File Size', 'גודל קובץ מקסימלי'),
              value: size === 'Unlimited' ? tr('No Limit', 'ללא הגבלה') : size,
              run: () => { put(KEY.maxSize, cycle(['Unlimited', '20GB', '10GB', '5GB'], size, 0)); render(); } },
          ]);
        }
        case 'subtitles': {
          const sub = St && St.getSubtitleLang ? St.getSubtitleLang() : 'heb';
          const size = get(KEY.subSize, 'medium');
          const yellow = !!get(KEY.yellow, false);
          return header(tr('LANGUAGE & DISPLAY', 'שפה ותצוגה')) + card([
            { key: 'subLang', kind: 'cycle', icon: 'subtitles', title: tr('Default Subtitles', 'כתוביות ברירת מחדל'),
              value: sub === 'heb' ? tr('Hebrew', 'עברית') : sub === 'eng' ? tr('English', 'אנגלית') : tr('Off', 'כבוי'),
              run: () => { if (St && St.setSubtitleLang) St.setSubtitleLang(cycle(['heb', 'eng', 'none'], sub, 0)); render(); } },
            { key: 'subSize', kind: 'cycle', icon: 'size', title: tr('Subtitle Size', 'גודל כתוביות'),
              value: { small: tr('Small', 'קטן'), large: tr('Large', 'גדול'), xlarge: tr('Extra Large', 'ענק') }[size] || tr('Medium', 'בינוני'),
              run: () => { put(KEY.subSize, cycle(['small', 'medium', 'large', 'xlarge'], size, 1)); render(); } },
            { key: 'yellow', kind: 'toggle', icon: 'color', title: tr('Yellow Subtitles', 'כתוביות צהובות'), on: yellow,
              run: () => { put(KEY.yellow, !yellow); render(); } },
            { key: 'appLang', kind: 'cycle', icon: 'language', title: tr('App Language', 'שפת הממשק'),
              value: TV.rtl() ? 'עברית' : 'English',
              run: () => { if (window.I18n) window.I18n.setLanguage(TV.rtl() ? 'en' : 'he'); location.reload(); } },
          ]);
        }
        case 'system': {
          const lite = !!get(KEY.lite, false);
          const saveHistory = get(KEY.saveHistory, true) !== false;
          return header(tr('PERFORMANCE', 'ביצועים')) + card([
            { key: 'lite', kind: 'toggle', icon: 'speed', title: tr('Lite UI Mode', 'מצב ממשק קל'), on: lite,
              run: () => { put(KEY.lite, !lite); applyLite(); render(); } },
            // Android: ExoPlayer buffer pre-allocation. The TV's media pipeline sizes its own buffer.
            { key: 'preBuffer', kind: 'action', icon: 'storage', title: tr('Pre-allocate Buffer', 'הקצאת זיכרון מראש'),
              desc: tr('Automatic (webOS)', 'אוטומטי (webOS)'),
              run: () => TV.toast(tr('The TV player manages its own buffer.', 'נגן הטלוויזיה מנהל את הזיכרון שלו בעצמו.')) },
          ]) + header(tr('STORAGE', 'אחסון')) + card([
            { key: 'saveHistory', kind: 'toggle', icon: 'history', title: tr('Save Search History', 'שמור היסטוריית חיפוש'), on: saveHistory,
              run: () => { put(KEY.saveHistory, !saveHistory); render(); } },
            { key: 'cache', kind: 'action', icon: 'sweep', title: tr('Clear Image Cache', 'נקה מטמון תמונות'), desc: s.cache, run: clearCache },
            { key: 'history', kind: 'action', icon: 'manageSearch', title: tr('Clear Search History', 'נקה היסטוריית חיפוש'),
              desc: s.history === 'Clear' ? tr('Clear', 'נקה') : s.history,
              run: () => { if (s.history === 'Clear') clearHistory(); } },
          ]);
        }
        default:
          return header(tr('ABOUT', 'אודות')) + infoCard();
      }
    }

    /* ── Render / focus ── */
    let focusables = [];
    function render(fade) {
      for (const k of Object.keys(handlers)) delete handlers[k];
      list.innerHTML = content();
      focusables = [...list.querySelectorAll('[data-k]')];
      focusables.forEach(f => TV.pointer(f,
        () => { s.zone = 'content'; s.key = f.dataset.k; paint(); },
        () => { s.zone = 'content'; s.key = f.dataset.k; paint(); activate(); }));
      if (fade) {
        // AnimatedContent: fadeIn(tween(220))
        list.classList.remove('se-in');
        list.classList.add('se-out');
        void list.offsetWidth;
        list.classList.remove('se-out');
        list.classList.add('se-in');
      }
      paint();
    }
    const keys = () => focusables.map(f => f.dataset.k);
    function paint() {
      backEl.classList.toggle('f', s.zone === 'side' && s.side === -1);
      catEls.forEach((c, i) => {
        c.classList.toggle('sel', i === s.cat);
        c.classList.toggle('f', s.zone === 'side' && s.side === i);
      });
      if (s.zone === 'content' && keys().indexOf(s.key) < 0) s.key = keys()[0] || null;
      let focused = null;
      focusables.forEach(f => {
        const on = s.zone === 'content' && f.dataset.k === s.key;
        f.classList.toggle('f', on);
        if (on) focused = f;
      });
      // LazyColumn: bring the focused item into view (transform only).
      let y = s.scroll;
      if (focused) {
        const top = focused.offsetTop;
        const bottom = top + focused.offsetHeight;
        if (bottom - y > TV.H) y = bottom - TV.H;
        if (top - y < 0) y = top;
      } else if (s.zone === 'side') {
        y = 0;
      }
      y = Math.max(0, Math.min(y, Math.max(0, list.offsetHeight - TV.H)));
      if (y !== s.scroll) { s.scroll = y; list.style.transform = y ? `translateY(${-y}px)` : ''; }
    }
    function selectCat(i) {
      if (i === s.cat) return;
      s.cat = i;
      s.scroll = 0;
      list.style.transform = '';
      render(true);
    }
    function toContent() {
      const k = keys();
      if (!k.length) return;
      s.zone = 'content';
      s.key = k[0]; // contentFR sits on each category's first item
      paint();
    }
    function toMenu() { s.zone = 'side'; s.side = s.cat; paint(); }
    function activate() { const fn = handlers[s.key]; if (fn) fn(); }

    TV.pointer(backEl, () => { s.zone = 'side'; s.side = -1; paint(); }, () => TV.back());
    catEls.forEach((c, i) => TV.pointer(c,
      () => { s.zone = 'side'; s.side = i; selectCat(i); paint(); },
      () => { selectCat(i); toContent(); }));

    /* ── Actions (SettingsViewModel) ── */
    async function checkRd() {
      const token = userToken();
      if (!token) return;
      s.rd = { text: 'Contacting Server...' };
      render();
      const attempt = async () => {
        const r = await fetch('https://api.alldebrid.com/v4/user?agent=LuminaStreams', { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });
        if (r.status !== 200) return { dot: '', text: `⚠ Error (HTTP ${r.status})` };
        const j = await r.json();
        if (j.status !== 'success') return { dot: '', text: '⚠ Token Invalid' };
        const u = (j.data && j.data.user) || {};
        const name = u.username || 'User';
        if (!u.isPremium) return { dot: 'r', text: `Free Account (${name})` };
        const exp = new Date((u.premiumUntil || 0) * 1000).toISOString().slice(0, 10);
        return { dot: 'g', text: `Premium (${name}) | Exp: ${exp}` };
      };
      let result;
      try { result = await attempt(); } catch {
        await sleep(2000); // cold boot: the network may not be up yet
        try { result = await attempt(); } catch { result = { dot: '', text: 'Network Error: Could not reach AllDebrid' }; }
      }
      if (s.dead || userToken() !== token) return;
      s.rd = result;
      render();
    }
    async function checkServers() {
      if (s.checkingSrv) return;
      s.checkingSrv = true; s.adSrv = null; s.trSrv = null;
      render();
      let ad = false;
      let tor = false;
      for (let attempt = 1; attempt <= 3; attempt++) {
        [ad, tor] = await Promise.all([ping('https://api.alldebrid.com/v4/ping'), ping('https://torrentio.strem.fun/manifest.json')]);
        if ((ad && tor) || s.dead) break;
        if (attempt < 3) await sleep(attempt === 1 ? 1500 : 3000);
      }
      if (s.dead) return;
      s.checkingSrv = false; s.adSrv = ad; s.trSrv = tor;
      render();
    }
    function startAdAuth() {
      if (!AD || adFlow.status !== 'idle') return;
      adFlow = { status: 'loading' };
      if (live) live();
      (async () => {
        try {
          const p = await AD.startAuth();
          adFlow = { status: 'waiting', pin: p.pin, url: p.userUrl || p.verificationUrl || '' };
          if (live) live();
          await AD.pollAuth(p.check, p.pin);
          adFlow = { status: 'idle' };
          TV.toast(tr('AllDebrid connected', 'AllDebrid מחובר'));
          if (live) live(true);
        } catch (e) {
          adFlow = { status: 'idle' };
          TV.toast(tr('AllDebrid connection failed', 'החיבור ל-AllDebrid נכשל'));
          if (live) live();
        }
      })();
    }
    async function runSync() {
      if (s.syncing || !SM) return;
      s.syncing = true; s.sync = 'PUSHING';
      render();
      try {
        await SM.flushProgress();
        await SM.push();
        if (s.dead) return;
        s.sync = 'PULLING';
        render();
        await SM.pull();
        s.sync = 'SUCCESS';
      } catch {
        s.sync = 'ERROR';
      }
      if (s.dead) return;
      s.syncing = false;
      render();
      await sleep(3000);
      if (s.dead || s.syncing) return;
      s.sync = '';
      render();
    }
    async function measureCache() {
      let text = 'Unknown';
      try {
        const e = await navigator.storage.estimate();
        text = fmtBytes(e.usage || 0);
      } catch {}
      if (s.dead) return;
      s.cache = text;
      if (CATS[s.cat] === 'system') render();
    }
    async function clearCache() {
      s.cache = 'Clearing...';
      render();
      try {
        if (window.caches) { const names = await caches.keys(); await Promise.all(names.map(n => caches.delete(n))); }
      } catch {}
      await sleep(400);
      measureCache();
    }
    async function clearHistory() {
      s.history = 'Clearing...';
      render();
      try { if (window.Storage) window.Storage.clearSearchHistory(); } catch {}
      await sleep(400);
      if (s.dead) return;
      s.history = 'Cleared ✓';
      if (CATS[s.cat] === 'system') render();
      await sleep(1800);
      if (s.dead) return;
      s.history = 'Clear';
      if (CATS[s.cat] === 'system') render();
    }

    /* ── Keys (SettingsScreen onPreviewKeyEvent) ── */
    function onKey(key) {
      if (s.zone === 'side') {
        if (key === 'up') { if (s.side >= 0) { s.side--; if (s.side >= 0) selectCat(s.side); paint(); } return true; }
        if (key === 'down') { if (s.side < CATS.length - 1) { s.side++; selectCat(s.side); paint(); } return true; }
        if (TV.fwd(key)) { toContent(); return true; }
        if (key === 'ok') {
          if (s.side === -1) TV.back();
          else { selectCat(s.side); toContent(); }
          return true;
        }
        return key !== 'back';
      }
      if (TV.bwd(key) || key === 'back') { toMenu(); return true; }
      const k = keys();
      const i = k.indexOf(s.key);
      if (key === 'up') { if (i > 0) { s.key = k[i - 1]; paint(); } return true; }
      if (key === 'down') { if (i < k.length - 1) { s.key = k[i + 1]; paint(); } return true; }
      if (key === 'ok') { activate(); return true; }
      return true;
    }

    const refresh = rdAgain => { if (s.dead) return; render(); if (rdAgain) checkRd(); };
    live = refresh;
    // The signed-in account shows on the cloud card (Android observes authRepository.currentUser).
    if (FA && FA.onAuthStateChanged && !authHooked) {
      authHooked = true;
      FA.onAuthStateChanged(() => { if (live) live(); });
    }
    // Android init: account check, server check, cache size, device info.
    render();
    checkRd();
    checkServers();
    measureCache();
    loadVersion(() => { if (!s.dead && CATS[s.cat] === 'about') render(); });

    return {
      el: root,
      onKey,
      onShow(first) { if (!first) { live = refresh; render(); } },
      destroy() { s.dead = true; if (live === refresh) live = null; },
    };
  });
})();
