/**
 * LuminaStreams — the Android TV player UI (PlayerScreen.kt), shared by:
 *  - the Windows app: drawn in a transparent window over mpv (backend = IPC);
 *  - the LG webOS app: drawn over the TV's native <video> (backend = tvBackend.js),
 *    so the TV's own Dolby Vision / Atmos popups and decoders do the playback.
 *
 * Behaviour follows PlayerScreen.kt: controls auto-hide after 5 s; on the seek
 * bar ←/→ queue a ±10 s seek shown as "+10s" (applied 400 ms after the last
 * press, mirrored in RTL); Up/Down move between seek bar and buttons; Back/Esc
 * closes menu → controls → player; up-next card 45 s before the end; resume dialog.
 *
 * backend: { cmd(name, ...args) → Promise, onInit(cb), onState(cb), onEvent(cb), ready() }
 * (the on* functions return an unsubscribe function).
 */
(() => {
  'use strict';

  const ICONS = {
    play: 'M8 5v14l11-7z',
    pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
    skip: 'M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z',
    close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
    audio: 'M12 3L12 14.5C11.6 14.2 11.1 14 10.5 14C9.1 14 8 15.1 8 16.5C8 17.9 9.1 19 10.5 19C11.9 19 13 17.9 13 16.5L13 6L17 6L17 3L12 3Z',
    subs: 'M20 4L4 4C2.9 4 2 4.9 2 6L2 18C2 19.1 2.9 20 4 20L20 20C21.1 20 22 19.1 22 18L22 6C22 4.9 21.1 4 20 4ZM4 12L8 12L8 14L4 14ZM10 12L20 12L20 14L10 14ZM14 16L20 16L20 18L14 18ZM4 16L12 16L12 18L4 18Z',
    webSubs: 'M19 4L5 4C3.89 4 3 4.9 3 6L3 18C3 19.1 3.89 20 5 20L14 20L14 18L5 18L5 6L19 6L19 13L21 13L21 6C21 4.9 20.1 4 19 4ZM7 14L13 14L13 16L7 16ZM7 10L17 10L17 12L7 12ZM20.49 19.08L22.96 21.54L21.54 22.96L19.08 20.49C18.41 20.81 17.68 21 16.9 21C14.19 21 12 18.81 12 16.1C12 13.39 14.19 11.2 16.9 11.2C19.61 11.2 21.8 13.39 21.8 16.1C21.8 16.88 21.61 17.61 21.29 18.28L20.49 19.08ZM16.9 13.2C15.29 13.2 14 14.49 14 16.1C14 17.71 15.29 19 16.9 19C18.51 19 19.8 17.71 19.8 16.1C19.8 14.49 18.51 13.2 16.9 13.2Z',
    aspect: 'M19 12h-2v3h-3v2h5v-5zM7 9h3V7H5v5h2V9zm14-6H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z',
    speed: 'M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
    sync: 'M11 17c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1-1 .45-1 1zm0-14v4h2V5.08c3.39.49 6 3.39 6 6.92 0 3.87-3.13 7-7 7s-7-3.13-7-7c0-1.68.59-3.22 1.58-4.42L12 13l1.41-1.41-6.8-6.8v.02C4.42 6.45 3 9.05 3 12c0 4.97 4.02 9 9 9 4.97 0 9-4.03 9-9s-4.03-9-9-9h-1zm7 9c0-.55-.45-1-1-1s-1 .45-1 1 .45 1 1 1 1-.45 1-1zM6 12c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1-1 .45-1 1z',
    check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
    warning: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
    back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
    refresh: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  };
  const svg = name => `<svg viewBox="0 0 24 24"><path d="${ICONS[name]}"/></svg>`;
  const MENUS = [
    { id: 'AUDIO', icon: 'audio' }, { id: 'EMBEDDED_SUBS', icon: 'subs' }, { id: 'WEB_SUBS', icon: 'webSubs' },
    { id: 'ASPECT_RATIO', icon: 'aspect' }, { id: 'SPEED', icon: 'speed' }, { id: 'SYNC', icon: 'sync' },
  ];
  const ASPECT_MODES = [
    { id: 'NORMAL', en: 'Normal', he: 'רגיל', desc: 'Fit to screen' },
    { id: 'ZOOM', en: 'Zoom', he: 'זום', desc: 'Crop to fill' },
    { id: 'STRETCH', en: 'Stretch', he: 'מתיחה', desc: 'Stretch to fill' },
    { id: '16:9', en: '16:9', he: '16:9', desc: 'Widescreen' },
    { id: '4:3', en: '4:3', he: '4:3', desc: 'Classic TV' },
    { id: '21:9', en: '21:9', he: '21:9', desc: 'CinemaScope' },
  ];
  const SPEEDS = [0.75, 1.0, 1.25, 1.5, 2.0];
  const SYNC_STEPS = [-1000, -500, -250, -100, 100, 250, 500, 1000];
  const FLAGS = { he: '🇮🇱', heb: '🇮🇱', en: '🇺🇸', eng: '🇺🇸', ar: '🇸🇦', ara: '🇸🇦', ru: '🇷🇺', rus: '🇷🇺', fr: '🇫🇷', fre: '🇫🇷', es: '🇪🇸', spa: '🇪🇸', de: '🇩🇪', ger: '🇩🇪' };
  // webOS remote Back key and its aliases.
  const isBackKey = e => e.key === 'Escape' || e.key === 'GoBack' || e.key === 'BrowserBack' || e.keyCode === 461;

  const TEMPLATE = `
    <div class="ap-surface" data-ap="surface"></div>
    <div class="ap-badges" data-ap="badges"></div>
    <div class="ap-aspect-badge" data-ap="aspect-badge" hidden></div>
    <div class="ap-center-play" data-ap="center-play" hidden>${svg('play')}</div>
    <div class="ap-seek-overlay" data-ap="seek" hidden>
      <div class="ap-seek-diff" data-ap="seek-diff"></div>
      <div class="ap-seek-time" data-ap="seek-time"></div>
    </div>
    <div class="ap-controls" data-ap="controls">
      <div class="ap-gradient"></div>
      <div class="ap-popup" data-ap="popup" hidden></div>
      <div class="ap-bottom">
        <div class="ap-progress-row">
          <span class="ap-time" data-ap="elapsed">0:00</span>
          <div class="ap-bar" data-ap="bar" tabindex="0">
            <div class="ap-bar-track"></div>
            <div class="ap-bar-fill" data-ap="fill"></div>
            <div class="ap-bar-thumb" data-ap="thumb"></div>
          </div>
          <span class="ap-time ap-dim" data-ap="remaining"></span>
        </div>
        <div class="ap-buttons-row">
          <div class="ap-group">
            <button class="ap-icon-btn" data-ap="play"></button>
            <button class="ap-icon-btn" data-ap="skip"></button>
            <button class="ap-icon-btn" data-ap="close"></button>
          </div>
          <div class="ap-group">
            ${MENUS.map(m => `<button class="ap-icon-btn" data-menu="${m.id}">${svg(m.icon)}</button>`).join('')}
          </div>
        </div>
      </div>
    </div>
    <div class="ap-upnext" data-ap="upnext" hidden></div>
    <div class="ap-osd" data-ap="osd" hidden></div>
    <div class="ap-modal" data-ap="resume" hidden></div>
    <div class="ap-modal ap-error" data-ap="error" hidden></div>
    <div class="ap-loading" data-ap="loading">
      <div class="ap-loading-backdrop" data-ap="loading-backdrop"></div>
      <div class="ap-loading-fade"></div>
      <div class="ap-loading-center">
        <div data-ap="loading-title"></div>
        <div class="ap-spinner"></div>
        <div class="ap-loading-status" data-ap="loading-status"></div>
      </div>
    </div>`;

  /**
   * @param {HTMLElement} root    container (gets .ap-root); may already hold a <video>
   * @param {Object} backend
   * @param {{tv?: boolean}} [options]  tv: LG remote behaviour, no own Dolby badges
   * @returns {{destroy: Function}}
   */
  function mount(root, backend, options = {}) {
    const TV = !!options.tv;
    root.classList.add('ap-root');
    root.insertAdjacentHTML('beforeend', TEMPLATE);
    const $ = name => root.querySelector(`[data-ap="${name}"]`);

    let info = { lang: 'he', title: '', webSubs: null };
    let st = { position: 0, duration: 0, paused: false, buffering: true, started: false, tracks: [], subDelay: 0, speed: 1, volume: 100, muted: false };
    let activeMenu = 'NONE';
    let aspect = 'NORMAL';
    let selectedWebSub = null;
    let controlsVisible = false;
    let hideTimer = null;
    let pendingSeek = null;
    let seekTimer = null;
    let scrubbing = false;
    let badgesShown = false;
    let resumeOpen = false;
    let errorOpen = false;
    const upNext = { shown: false, dismissed: false, countdown: 10, timer: null };
    const timers = new Set();
    const later = (fn, ms) => { const t = setTimeout(() => { timers.delete(t); fn(); }, ms); timers.add(t); return t; };

    const isRtl = () => info.lang !== 'en';
    const tr = (en, he) => (info.lang === 'en' ? en : he);
    const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const fmt = seconds => {
      const s = Math.max(0, Math.floor(seconds || 0));
      const h = Math.floor(s / 3600);
      const m = Math.floor((s % 3600) / 60);
      const sec = String(s % 60).padStart(2, '0');
      return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
    };

    /* ── Controls visibility (5 s auto-hide) ────────────────────────── */
    function showControls(focusBar) {
      const wasHidden = !controlsVisible;
      controlsVisible = true;
      $('controls').classList.add('ap-visible');
      root.classList.add('ap-controls-on');
      root.classList.remove('ap-hide-cursor');
      // Android: controls appear with the seek bar focused (remote control).
      if (TV && wasHidden && focusBar !== false && !root.contains(document.activeElement)) $('bar').focus({ preventScroll: true });
      scheduleHide();
    }
    function hideControls() {
      if (activeMenu !== 'NONE') return;
      controlsVisible = false;
      $('controls').classList.remove('ap-visible');
      root.classList.remove('ap-controls-on');
      if (root.contains(document.activeElement)) document.activeElement.blur();
      if (!st.paused) root.classList.add('ap-hide-cursor');
    }
    function scheduleHide() {
      clearTimeout(hideTimer);
      hideTimer = later(() => {
        if (!st.paused && activeMenu === 'NONE' && !scrubbing) hideControls();
      }, 5000);
    }

    /* ── Rendering ──────────────────────────────────────────────────── */
    function renderButtons() {
      $('play').innerHTML = svg(st.paused ? 'play' : 'pause');
      $('skip').innerHTML = svg('skip');
      $('close').innerHTML = svg('close');
      $('play').title = st.paused ? tr('Play', 'נגן') : tr('Pause', 'השהה');
      $('skip').title = tr('Skip 85 s', 'דלג 85 שניות');
      $('close').title = tr('Close', 'סגור');
      const titles = {
        AUDIO: tr('Audio Tracks', 'רצועות שמע'), EMBEDDED_SUBS: tr('Subtitles', 'כתוביות'),
        WEB_SUBS: tr('Web Subtitles', 'כתוביות רשת'), ASPECT_RATIO: tr('Aspect Ratio', 'יחס תצוגה'),
        SPEED: tr('Playback Speed', 'מהירות ניגון'), SYNC: tr('Subtitle Sync', 'סנכרון כתוביות'),
      };
      root.querySelectorAll('[data-menu]').forEach(btn => {
        btn.title = titles[btn.dataset.menu];
        btn.classList.toggle('ap-on', activeMenu === btn.dataset.menu);
      });
    }

    function renderProgress() {
      const dur = st.duration || 0;
      const pos = pendingSeek != null ? pendingSeek : st.position;
      const pct = dur > 0 ? Math.min(100, Math.max(0, (pos / dur) * 100)) : 0;
      $('fill').style.width = `${pct}%`;
      // left/right rather than inset-inline-start: older TV engines (webOS 6) lack it.
      const thumb = $('thumb');
      const rtl = root.getAttribute('dir') === 'rtl';
      thumb.style.left = rtl ? 'auto' : `${pct}%`;
      thumb.style.right = rtl ? `${pct}%` : 'auto';
      $('elapsed').textContent = fmt(pos);
      $('remaining').textContent = dur > 0 ? fmt(dur - pos) : '';
    }

    function renderPaused() {
      $('center-play').hidden = !(st.paused && st.started && !resumeOpen && !errorOpen);
      if (st.paused) root.classList.remove('ap-hide-cursor');
    }

    function renderLoading() {
      $('loading').classList.toggle('ap-done', st.started || errorOpen || resumeOpen);
      $('loading-status').textContent = tr('Loading and connecting...', 'טוען ומתחבר לזרם...');
    }

    function renderBadges() {
      // On the TV the panel announces Dolby Vision / Atmos / HDR itself (native popup).
      if (TV || badgesShown || !st.height) return;
      badgesShown = true;
      const h = st.height;
      const res = h >= 2160 ? '4K UHD' : h >= 1080 ? '1080p' : h >= 720 ? '720p' : `${h}p`;
      const fps = st.fps || 0;
      const hz = fps > 59 ? '60Hz' : fps > 49 ? '50Hz' : fps > 29 ? '30Hz' : fps > 24 ? '25Hz' : fps > 0 ? '24Hz' : null;
      const badges = [[res, 'var(--ap-res-gray)']];
      if (hz) badges.push([hz, 'var(--ap-res-gray)']);
      if (info.dolbyVision) badges.push(['DOLBY VISION', 'var(--ap-dv-blue)']);
      if (info.atmos) badges.push(['DOLBY ATMOS', 'var(--ap-atmos-purple)']);
      const el = $('badges');
      el.innerHTML = badges.map(([t, c]) => `<span class="ap-badge" style="background:${c}">${t}</span>`).join('');
      el.classList.remove('ap-fade');
      later(() => el.classList.add('ap-fade'), 3500);
    }

    function renderAspectBadge() {
      const mode = ASPECT_MODES.find(m => m.id === aspect);
      $('aspect-badge').hidden = aspect === 'NORMAL';
      $('aspect-badge').textContent = `⬛ ${tr(mode.en, mode.he)}`;
    }

    /* ── Popup menus ────────────────────────────────────────────────── */
    function item(title, subtitle, selected, action) {
      return `<button class="ap-item${selected ? ' ap-selected' : ''}" data-action="${esc(action)}">
        <span class="ap-item-text"><span class="ap-item-title" dir="auto">${esc(title)}</span>${subtitle ? `<span class="ap-item-sub" dir="auto">${esc(subtitle)}</span>` : ''}</span>
        ${selected ? `<span class="ap-item-check">${svg('check')}</span>` : ''}
      </button>`;
    }

    function trackName(t) {
      const atmos = /atmos/i.test(t.title) || /joc/i.test(t.codec) ? 'ATMOS' : '';
      return [(t.lang || tr('Unknown', 'לא ידוע')).toUpperCase(), t.title, t.channels ? `${t.channels}Ch` : '', (t.codec || '').toUpperCase(), atmos]
        .filter(Boolean).join(' • ');
    }

    function renderPopup() {
      const popup = $('popup');
      if (activeMenu === 'NONE') { popup.hidden = true; renderButtons(); return; }
      let title = '';
      let extra = '';
      let items = '';
      switch (activeMenu) {
        case 'AUDIO': {
          title = tr('Audio Tracks', 'רצועות שמע');
          const list = st.tracks.filter(t => t.type === 'audio');
          items = list.length
            ? list.map(t => item(trackName(t), tr('Internal', 'פנימי'), t.selected, `aid:${t.id}`)).join('')
            : `<div class="ap-popup-empty">${tr('No tracks available', 'אין רצועות זמינות')}</div>`;
          break;
        }
        case 'EMBEDDED_SUBS': {
          title = tr('Subtitles', 'כתוביות');
          const list = st.tracks.filter(t => t.type === 'sub' && !t.external);
          const noneSelected = !st.tracks.some(t => t.type === 'sub' && t.selected);
          items = item(tr('Turn Off', 'כבה'), '', noneSelected, 'sid:no')
            + list.map(t => item(trackName(t), tr('Internal', 'פנימי'), t.selected, `sid:${t.id}`)).join('');
          break;
        }
        case 'WEB_SUBS': {
          title = tr('Web Subtitles', 'כתוביות רשת');
          const subs = info.webSubs;
          if (!subs || !subs.length) {
            items = `<div class="ap-popup-empty">${subs === null ? tr('Searching...', 'מחפש...') : tr('No subtitles found', 'לא נמצאו כתוביות')}</div>`;
          } else {
            const activeExternal = st.tracks.some(t => t.type === 'sub' && t.external && t.selected);
            items = subs.map((s, i) => item(
              `${(s.lang || '').toUpperCase()} ${FLAGS[(s.lang || '').toLowerCase()] || '🌐'}`,
              s.source || '', activeExternal && selectedWebSub === s.url, `web:${i}`)).join('');
          }
          break;
        }
        case 'ASPECT_RATIO':
          title = tr('Aspect Ratio', 'יחס תצוגה');
          items = ASPECT_MODES.map(m => item(tr(m.en, m.he), m.desc, m.id === aspect, `aspect:${m.id}`)).join('');
          break;
        case 'SPEED':
          title = tr('Playback Speed', 'מהירות ניגון');
          items = SPEEDS.map(s => item(`${s}x`, s === 1 ? tr('Normal', 'רגיל') : '', Math.abs(st.speed - s) < 0.01, `speed:${s}`)).join('');
          break;
        case 'SYNC': {
          title = tr('Subtitle Sync', 'סנכרון כתוביות');
          const ms = Math.round((st.subDelay || 0) * 1000);
          extra = `<div class="ap-popup-offset${ms ? ' ap-set' : ''}">${ms > 0 ? '+' : ''}${ms}ms</div>`;
          items = item(tr('Sync to what I hear', 'סנכרן למה שאני שומע'),
            tr('Press when the audio matches the subtitle on screen', 'לחץ כשהדיאלוג תואם לכתובית'), false, 'syncHear')
            + item(tr('Reset to 0', 'אפס'), '', ms === 0, 'delay:0')
            + SYNC_STEPS.map(step => item(`${step > 0 ? '+' : ''}${step}ms`,
              step > 0 ? tr('delay subtitles', 'השהה כתוביות') : tr('advance subtitles', 'הקדם כתוביות'), false, `delayBy:${step}`)).join('');
          break;
        }
        default: break;
      }
      const scroll = popup.querySelector('.ap-popup-list');
      const scrollTop = scroll ? scroll.scrollTop : 0;
      popup.innerHTML = `<div class="ap-popup-title">${title}</div>${extra}<div class="ap-popup-list">${items}</div>`;
      popup.querySelector('.ap-popup-list').scrollTop = scrollTop;
      popup.hidden = false;
      renderButtons();
    }

    function openMenu(name) {
      activeMenu = activeMenu === name ? 'NONE' : name;
      renderPopup();
      showControls(false);
      const first = $('popup').querySelector('.ap-item');
      if (first && activeMenu !== 'NONE') first.focus({ preventScroll: true });
      else root.querySelector(`[data-menu="${name}"]`)?.focus({ preventScroll: true });
    }

    function closeMenu() {
      const menu = activeMenu;
      activeMenu = 'NONE';
      renderPopup();
      root.querySelector(`[data-menu="${menu}"]`)?.focus({ preventScroll: true });
    }

    async function runAction(action) {
      const [kind, value] = action.split(':');
      try {
        switch (kind) {
          case 'aid': await backend.cmd('setProperty', 'aid', Number(value)); break;
          case 'sid': await backend.cmd('setProperty', 'sid', value === 'no' ? 'no' : Number(value)); break;
          case 'web': {
            const sub = info.webSubs[Number(value)];
            toast(tr('Loading subtitle…', 'טוען כתוביות…'));
            await backend.cmd('webSub', sub.url, `${(sub.lang || '').toUpperCase()} • ${sub.source || 'Web'}`, sub.lang || '');
            selectedWebSub = sub.url;
            break;
          }
          case 'aspect': aspect = value; await backend.cmd('aspect', value); renderAspectBadge(); break;
          case 'speed': await backend.cmd('setProperty', 'speed', Number(value)); break;
          case 'delay': st.subDelay = 0; await backend.cmd('setProperty', 'sub-delay', 0); return renderPopup();
          case 'delayBy': {
            st.subDelay = (st.subDelay || 0) + Number(value) / 1000;
            await backend.cmd('setProperty', 'sub-delay', st.subDelay);
            // Keep the sync menu open for repeated nudges (Android does too), focus kept.
            renderPopup();
            $('popup').querySelector(`[data-action="${action}"]`)?.focus({ preventScroll: true });
            return;
          }
          case 'syncHear': {
            const ok = await backend.cmd('syncToHeard');
            toast(ok ? tr('Subtitles synced', 'הכתוביות סונכרנו') : tr('No subtitle on screen right now', 'אין כתובית על המסך כרגע'));
            return;
          }
          default: break;
        }
      } catch (err) {
        toast(err.message || String(err));
      }
      closeMenu();
    }

    /* ── Seeking ────────────────────────────────────────────────────── */
    function queueSeek(deltaSeconds) {
      if (!st.duration) return;
      const base = pendingSeek != null ? pendingSeek : st.position;
      pendingSeek = Math.min(st.duration, Math.max(0, base + deltaSeconds));
      const diff = Math.round(pendingSeek - st.position);
      $('seek-diff').textContent = `${diff > 0 ? '+' : ''}${diff}s`;
      $('seek-time').textContent = fmt(pendingSeek);
      $('seek').hidden = false;
      renderProgress();
      clearTimeout(seekTimer);
      seekTimer = later(async () => {
        const target = pendingSeek;
        try { await backend.cmd('seek', target); } catch {}
        st.position = target;
        pendingSeek = null;
        $('seek').hidden = true;
        renderProgress();
      }, 400);
    }

    function barPositionToTime(clientX) {
      const r = $('bar').getBoundingClientRect();
      let frac = (clientX - r.left) / r.width;
      if (isRtl()) frac = 1 - frac;
      return Math.min(1, Math.max(0, frac)) * (st.duration || 0);
    }

    /* ── Resume, error, up next ─────────────────────────────────────── */
    function showResume(seconds) {
      resumeOpen = true;
      const el = $('resume');
      el.innerHTML = `
        <div class="ap-resume-card">
          ${svg('play')}
          <div class="ap-resume-title">${tr('Continue Watching?', 'להמשיך צפייה?')}</div>
          <div class="ap-resume-sub">${tr('Stopped at', 'נעצר ב-')} ${fmt(seconds)}</div>
          <div class="ap-resume-actions">
            <button class="ap-pill ap-red" data-choice="continue">${tr('▶ Continue', '▶ המשך')}</button>
            <button class="ap-pill ap-dark" data-choice="restart">${tr('From Start', 'מהתחלה')}</button>
          </div>
        </div>`;
      el.hidden = false;
      renderLoading();
      el.querySelector('[data-choice="continue"]').focus();
      el.querySelectorAll('[data-choice]').forEach(btn => {
        btn.onclick = async () => {
          el.hidden = true;
          resumeOpen = false;
          await backend.cmd('resume', btn.dataset.choice);
          renderLoading();
          renderPaused();
        };
      });
    }

    function showError(message) {
      errorOpen = true;
      const el = $('error');
      el.innerHTML = `
        <div class="ap-error-box">
          <div class="ap-error-icon">${svg('warning')}</div>
          <div class="ap-error-title">${tr('Playback Error', 'שגיאת נגן')}</div>
          <div class="ap-error-msg" dir="auto">${esc(message)}</div>
          <div class="ap-error-actions">
            <button class="ap-pill ap-blue" data-err="retry">${svg('refresh')}${tr('Retry', 'נסה שוב')}</button>
            <button class="ap-pill ap-red" data-err="back">${svg('back')}${tr('Back to Sources', 'חזור למקורות')}</button>
          </div>
        </div>`;
      el.hidden = false;
      renderLoading();
      el.querySelector('[data-err="retry"]').focus();
      el.querySelector('[data-err="retry"]').onclick = async () => {
        el.hidden = true;
        errorOpen = false;
        await backend.cmd('retry');
      };
      el.querySelector('[data-err="back"]').onclick = () => backend.cmd('close');
    }

    function checkUpNext() {
      const next = info.nextEpisode;
      if (!next || upNext.shown || upNext.dismissed || !st.duration) return;
      if (st.duration - st.position > 45 || st.position < 60) return;
      upNext.shown = true;
      upNext.countdown = 10;
      const same = next.season === info.season;
      const el = $('upnext');
      el.innerHTML = `
        ${info.logo ? `<img class="ap-upnext-logo" src="${esc(info.logo)}" alt="">` : `<div class="ap-upnext-title" dir="auto">${esc(info.showTitle || info.title)}</div>`}
        <div class="ap-upnext-label">${tr('UP NEXT', 'הבא בתור')}</div>
        <div class="ap-upnext-ep">${same ? tr(`Episode ${next.episode}`, `פרק ${next.episode}`) : tr(`S${next.season} • E${next.episode}`, `עונה ${next.season} • פרק ${next.episode}`)}</div>
        <div class="ap-upnext-bar"><div class="ap-upnext-fill" data-ap="upnext-fill"></div></div>
        <div class="ap-upnext-actions">
          <button class="ap-pill ap-red" data-up="play">${svg('play')}<span data-ap="upnext-label"></span></button>
          <button class="ap-pill ap-ghost" data-up="dismiss">${tr('Dismiss', 'דחה')}</button>
        </div>`;
      el.hidden = false;
      const tick = () => {
        $('upnext-label').textContent = tr(`Play (${upNext.countdown})`, `נגן (${upNext.countdown})`);
        $('upnext-fill').style.width = `${((10 - upNext.countdown) / 10) * 100}%`;
      };
      tick();
      el.querySelector('[data-up="play"]').focus();
      upNext.timer = setInterval(() => {
        upNext.countdown -= 1;
        tick();
        if (upNext.countdown <= 0) { clearInterval(upNext.timer); backend.cmd('playNext'); }
      }, 1000);
      el.querySelector('[data-up="play"]').onclick = () => { clearInterval(upNext.timer); backend.cmd('playNext'); };
      el.querySelector('[data-up="dismiss"]').onclick = () => {
        clearInterval(upNext.timer);
        upNext.dismissed = true;
        el.hidden = true;
      };
    }

    let osdTimer = null;
    function toast(text) {
      const el = $('osd');
      el.textContent = text;
      el.hidden = false;
      clearTimeout(osdTimer);
      osdTimer = later(() => { el.hidden = true; }, 2200);
    }

    function changeVolume(delta) {
      const vol = Math.min(130, Math.max(0, Math.round((st.volume || 0) + delta)));
      backend.cmd('setProperty', 'volume', vol);
      st.volume = vol;
      toast(`🔊 ${vol}%`);
    }

    /* ── Input ──────────────────────────────────────────────────────── */
    function togglePause() {
      backend.cmd('togglePause');
      showControls(false);
    }

    const listeners = [];
    const on = (target, type, fn, opts) => { target.addEventListener(type, fn, opts); listeners.push(() => target.removeEventListener(type, fn, opts)); };

    on(root, 'mousemove', () => showControls(false));
    on($('surface'), 'click', () => {
      if (activeMenu !== 'NONE') { closeMenu(); return; }
      if (controlsVisible) hideControls(); else showControls(false);
    });
    if (!TV) {
      on($('surface'), 'dblclick', () => backend.cmd('fullscreen'));
      on(root, 'wheel', e => { if (!e.target.closest('.ap-popup')) changeVolume(e.deltaY < 0 ? 5 : -5); }, { passive: true });
    }

    $('play').onclick = togglePause;
    $('skip').onclick = () => queueSeek(85);
    $('close').onclick = () => backend.cmd('close');
    root.querySelectorAll('[data-menu]').forEach(btn => { btn.onclick = () => openMenu(btn.dataset.menu); });
    on($('popup'), 'click', e => {
      const el = e.target.closest('.ap-item');
      if (el) runAction(el.dataset.action);
    });

    const bar = $('bar');
    on(bar, 'pointerdown', e => {
      scrubbing = true;
      bar.classList.add('ap-active');
      bar.setPointerCapture(e.pointerId);
      pendingSeek = barPositionToTime(e.clientX);
      renderProgress();
    });
    on(bar, 'pointermove', e => {
      if (!scrubbing) return;
      pendingSeek = barPositionToTime(e.clientX);
      renderProgress();
    });
    on(bar, 'pointerup', async () => {
      if (!scrubbing) return;
      scrubbing = false;
      bar.classList.remove('ap-active');
      const target = pendingSeek;
      try { await backend.cmd('seek', target); } catch {}
      st.position = target;
      pendingSeek = null;
      renderProgress();
      scheduleHide();
    });

    const buttons = () => [...root.querySelectorAll('.ap-buttons-row .ap-icon-btn')];
    // Visual order follows the layout direction (RTL mirrors the row).
    const visualButtons = () => buttons().sort((a, b) => a.getBoundingClientRect().left - b.getBoundingClientRect().left);

    on(document, 'keydown', e => {
      if (resumeOpen || errorOpen || !$('upnext').hidden) {
        // Dialogs: ←/→ move between their buttons, OK clicks, Back dismisses up-next.
        const scope = resumeOpen ? $('resume') : errorOpen ? $('error') : $('upnext');
        const pills = [...scope.querySelectorAll('button')].sort((a, b) => a.getBoundingClientRect().left - b.getBoundingClientRect().left);
        const i = pills.indexOf(document.activeElement);
        if (e.key === 'ArrowLeft') { (pills[i - 1] || pills[0]).focus(); e.preventDefault(); }
        else if (e.key === 'ArrowRight') { (pills[i + 1] || pills[pills.length - 1]).focus(); e.preventDefault(); }
        else if (isBackKey(e) && !resumeOpen && !errorOpen) { scope.querySelector('[data-up="dismiss"]')?.click(); e.preventDefault(); }
        else if (isBackKey(e) && errorOpen) { backend.cmd('close'); e.preventDefault(); }
        return;
      }

      // Menus: ↑/↓ walk the list; ←/→ and Back close it (Android TV).
      if (activeMenu !== 'NONE') {
        const items = [...$('popup').querySelectorAll('.ap-item')];
        const idx = items.indexOf(document.activeElement);
        if (e.key === 'ArrowDown') { (items[idx + 1] || items[0])?.focus(); e.preventDefault(); }
        else if (e.key === 'ArrowUp') { (items[idx - 1] || items[items.length - 1])?.focus(); e.preventDefault(); }
        else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || isBackKey(e)) { closeMenu(); e.preventDefault(); }
        return;
      }

      if (isBackKey(e)) {
        e.preventDefault();
        if (controlsVisible && !st.paused) hideControls();
        else backend.cmd('close');
        return;
      }

      const focused = document.activeElement;
      const onBar = focused === bar;
      const onButton = buttons().includes(focused);

      // Hidden controls: OK toggles pause, ←/→ seek, anything else shows them.
      if (!controlsVisible) {
        if (e.key === 'Enter' || e.key === ' ' || e.key === 'MediaPlayPause') { togglePause(); e.preventDefault(); return; }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          queueSeek((e.key === 'ArrowRight') !== isRtl() ? 10 : -10);
          showControls();
          e.preventDefault();
          return;
        }
        if (TV) { showControls(); e.preventDefault(); return; }
      }

      switch (e.key) {
        case 'MediaPlayPause': togglePause(); break;
        case 'MediaPlay': if (st.paused) togglePause(); break;
        case 'MediaPause': if (!st.paused) togglePause(); break;
        case 'MediaFastForward': queueSeek(10); break;
        case 'MediaRewind': queueSeek(-10); break;
        case ' ':
          if (!onButton) { togglePause(); e.preventDefault(); }
          break;
        case 'Enter':
          if (onBar || !onButton) { togglePause(); e.preventDefault(); }
          break;
        case 'ArrowLeft':
        case 'ArrowRight': {
          const forward = (e.key === 'ArrowRight') !== isRtl();
          if (onButton) {
            const row = visualButtons();
            const i = row.indexOf(focused);
            const next = row[i + (e.key === 'ArrowRight' ? 1 : -1)];
            if (next) next.focus({ preventScroll: true });
          } else {
            queueSeek(forward ? 10 : -10);
          }
          showControls(false);
          e.preventDefault();
          break;
        }
        case 'ArrowUp':
          if (onButton) bar.focus({ preventScroll: true });
          else if (!TV) changeVolume(5);
          showControls(false);
          e.preventDefault();
          break;
        case 'ArrowDown':
          if (onBar) $('play').focus({ preventScroll: true });
          else if (!TV) changeVolume(-5);
          showControls(false);
          e.preventDefault();
          break;
        case 'm': case 'M': if (!TV) { backend.cmd('setProperty', 'mute', !st.muted); toast(st.muted ? '🔊' : '🔇'); } break;
        case 'f': case 'F': case 'F11': if (!TV) { backend.cmd('fullscreen'); e.preventDefault(); } break;
        default: break;
      }
    });

    /* ── Backend wiring ─────────────────────────────────────────────── */
    const unsubs = [];
    unsubs.push(backend.onInit(data => {
      info = { ...info, ...data };
      root.setAttribute('dir', isRtl() ? 'rtl' : 'ltr');
      root.setAttribute('lang', info.lang === 'en' ? 'en' : 'he');
      $('loading-backdrop').style.backgroundImage = info.backdrop ? `url("${info.backdrop.replace(/"/g, '%22')}")` : '';
      $('loading-title').innerHTML = info.logo
        ? `<img class="ap-loading-logo" src="${esc(info.logo)}" alt="${esc(info.title)}">`
        : `<div class="ap-loading-name" dir="auto">${esc(info.title)}</div>`;
      renderButtons();
      renderLoading();
      renderAspectBadge();
      if (info.resumeSeconds > 30) showResume(info.resumeSeconds);
      else showControls();
    }));

    unsubs.push(backend.onState(data => {
      const wasPaused = st.paused;
      st = { ...st, ...data };
      if (!scrubbing && pendingSeek == null) renderProgress();
      if (wasPaused !== st.paused) { renderButtons(); renderPaused(); if (st.paused) showControls(false); else scheduleHide(); }
      renderLoading();
      renderBadges();
      checkUpNext();
      if (['AUDIO', 'EMBEDDED_SUBS', 'WEB_SUBS', 'SPEED', 'SYNC'].includes(activeMenu) && !$('popup').contains(document.activeElement)) {
        renderPopup();
      }
    }));

    unsubs.push(backend.onEvent(ev => {
      if (ev.type === 'error') showError(ev.message);
      else if (ev.type === 'subtitles') {
        info.webSubs = ev.list;
        if (ev.selected) selectedWebSub = ev.selected;
        if (activeMenu === 'WEB_SUBS') renderPopup();
      } else if (ev.type === 'nextEpisode') info.nextEpisode = ev.value;
      else if (ev.type === 'toast') toast(ev.text);
    }));

    renderButtons();
    backend.ready();

    return {
      destroy() {
        unsubs.forEach(off => { try { off(); } catch {} });
        listeners.forEach(off => off());
        timers.forEach(t => clearTimeout(t));
        clearInterval(upNext.timer);
        root.innerHTML = '';
        root.classList.remove('ap-root', 'ap-controls-on', 'ap-hide-cursor');
      },
    };
  }

  window.AndroidPlayer = Object.freeze({ mount });
})();
