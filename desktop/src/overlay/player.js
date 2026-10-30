/**
 * LuminaStreams desktop player — the Android TV PlayerScreen rebuilt over mpv.
 * Behaviour follows PlayerScreen.kt: controls auto-hide after 5 s, ←/→ queue a
 * ±10 s seek shown as "+10s" (applied 400 ms after the last press, mirrored in
 * RTL), Esc closes menu → controls → player, info badges for 3.5 s, up-next
 * card 45 s before the end with a 10 s countdown, resume dialog.
 * Desktop additions: mouse, ↑/↓ and wheel for volume, F / double-click fullscreen.
 */
(() => {
  'use strict';

  /* ── Icons (Material + the custom vectors from PlayerScreen.kt) ─────── */
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
  const MENU_ICONS = { AUDIO: 'audio', EMBEDDED_SUBS: 'subs', WEB_SUBS: 'webSubs', ASPECT_RATIO: 'aspect', SPEED: 'speed', SYNC: 'sync' };

  const ASPECT_MODES = [
    { id: 'NORMAL',  en: 'Normal', he: 'רגיל', desc: 'Fit to screen' },
    { id: 'ZOOM',    en: 'Zoom', he: 'זום', desc: 'Crop to fill' },
    { id: 'STRETCH', en: 'Stretch', he: 'מתיחה', desc: 'Stretch to fill' },
    { id: '16:9',    en: '16:9', he: '16:9', desc: 'Widescreen' },
    { id: '4:3',     en: '4:3', he: '4:3', desc: 'Classic TV' },
    { id: '21:9',    en: '21:9', he: '21:9', desc: 'CinemaScope' },
  ];
  const SPEEDS = [0.75, 1.0, 1.25, 1.5, 2.0];
  const SYNC_STEPS = [-1000, -500, -250, -100, 100, 250, 500, 1000];
  const FLAGS = { he: '🇮🇱', heb: '🇮🇱', en: '🇺🇸', eng: '🇺🇸', ar: '🇸🇦', ara: '🇸🇦', ru: '🇷🇺', rus: '🇷🇺', fr: '🇫🇷', fre: '🇫🇷', es: '🇪🇸', spa: '🇪🇸', de: '🇩🇪', ger: '🇩🇪' };

  /* ── State ──────────────────────────────────────────────────────────── */
  const $ = id => document.getElementById(id);
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
  let upNext = { shown: false, dismissed: false, countdown: 10, timer: null };

  const isRtl = () => info.lang !== 'en';
  const tr = (en, he) => (info.lang === 'en' ? en : he);
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function fmt(seconds) {
    const s = Math.max(0, Math.floor(seconds || 0));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
  }

  /* ── Controls visibility (5 s auto-hide, like Android) ──────────────── */
  function showControls() {
    controlsVisible = true;
    $('controls').classList.add('visible');
    document.body.classList.remove('hide-cursor');
    scheduleHide();
  }
  function hideControls() {
    if (activeMenu !== 'NONE') return;
    controlsVisible = false;
    $('controls').classList.remove('visible');
    if (!st.paused) document.body.classList.add('hide-cursor');
  }
  function scheduleHide() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      if (!st.paused && activeMenu === 'NONE' && !scrubbing) hideControls();
    }, 5000);
  }

  /* ── Rendering ──────────────────────────────────────────────────────── */
  function renderButtons() {
    $('btn-play').innerHTML = svg(st.paused ? 'play' : 'pause');
    $('btn-skip').innerHTML = svg('skip');
    $('btn-close').innerHTML = svg('close');
    $('btn-play').title = st.paused ? tr('Play', 'נגן') : tr('Pause', 'השהה');
    $('btn-skip').title = tr('Skip 85 s', 'דלג 85 שניות');
    $('btn-close').title = tr('Close', 'סגור');
    const titles = {
      AUDIO: tr('Audio Tracks', 'רצועות שמע'), EMBEDDED_SUBS: tr('Subtitles', 'כתוביות'),
      WEB_SUBS: tr('Web Subtitles', 'כתוביות רשת'), ASPECT_RATIO: tr('Aspect Ratio', 'יחס תצוגה'),
      SPEED: tr('Playback Speed', 'מהירות ניגון'), SYNC: tr('Subtitle Sync', 'סנכרון כתוביות'),
    };
    document.querySelectorAll('[data-menu]').forEach(btn => {
      btn.innerHTML = svg(MENU_ICONS[btn.dataset.menu]);
      btn.title = titles[btn.dataset.menu];
      btn.classList.toggle('active', activeMenu === btn.dataset.menu);
    });
  }

  function renderProgress() {
    const dur = st.duration || 0;
    const pos = pendingSeek != null ? pendingSeek : st.position;
    const pct = dur > 0 ? Math.min(100, Math.max(0, (pos / dur) * 100)) : 0;
    $('bar-fill').style.width = `${pct}%`;
    $('bar-thumb').style.insetInlineStart = `${pct}%`;
    $('elapsed').textContent = fmt(pos);
    $('remaining').textContent = dur > 0 ? fmt(dur - pos) : '';
  }

  function renderPaused() {
    $('center-play').hidden = !(st.paused && st.started && !resumeOpen && !errorOpen);
    if (st.paused) document.body.classList.remove('hide-cursor');
  }

  function renderLoading() {
    const done = st.started || errorOpen || resumeOpen;
    $('loading').classList.toggle('done', done);
    $('loading-status').textContent = tr('Loading and connecting...', 'טוען ומתחבר לזרם...');
  }

  function renderBadges() {
    if (badgesShown || !st.height) return;
    badgesShown = true;
    const h = st.height;
    const res = h >= 2160 ? '4K UHD' : h >= 1080 ? '1080p' : h >= 720 ? '720p' : `${h}p`;
    const fps = st.fps || 0;
    const hz = fps > 59 ? '60Hz' : fps > 49 ? '50Hz' : fps > 29 ? '30Hz' : fps > 24 ? '25Hz' : fps > 0 ? '24Hz' : null;
    const badges = [[res, 'var(--res-gray)']];
    if (hz) badges.push([hz, 'var(--res-gray)']);
    if (info.dolbyVision) badges.push(['DOLBY VISION', 'var(--dv-blue)']);
    if (info.atmos) badges.push(['DOLBY ATMOS', 'var(--atmos-purple)']);
    const el = $('badges');
    el.innerHTML = badges.map(([t, c]) => `<span class="badge" style="background:${c}">${t}</span>`).join('');
    el.classList.remove('fade');
    setTimeout(() => el.classList.add('fade'), 3500);
  }

  function renderAspectBadge() {
    const mode = ASPECT_MODES.find(m => m.id === aspect);
    $('aspect-badge').hidden = aspect === 'NORMAL';
    $('aspect-badge').textContent = `⬛ ${tr(mode.en, mode.he)}`;
  }

  /* ── Popup menus ────────────────────────────────────────────────────── */
  function trackItem(title, subtitle, selected, action, attrs = '') {
    return `<button class="track-item${selected ? ' selected' : ''}" data-action="${esc(action)}" ${attrs}>
      <span class="track-text"><span class="track-title" dir="auto">${esc(title)}</span>${subtitle ? `<span class="track-sub" dir="auto">${esc(subtitle)}</span>` : ''}</span>
      ${selected ? `<span class="track-check">${svg('check')}</span>` : ''}
    </button>`;
  }

  function trackName(t) {
    const atmos = /atmos/i.test(t.title) || (t.codec === 'eac3' && /joc/i.test(t.title)) ? 'ATMOS' : '';
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
          ? list.map(t => trackItem(trackName(t), tr('Internal', 'פנימי'), t.selected, `aid:${t.id}`)).join('')
          : `<div class="popup-empty">${tr('No tracks available', 'אין רצועות זמינות')}</div>`;
        break;
      }
      case 'EMBEDDED_SUBS': {
        title = tr('Subtitles', 'כתוביות');
        const list = st.tracks.filter(t => t.type === 'sub' && !t.external);
        const noneSelected = !st.tracks.some(t => t.type === 'sub' && t.selected);
        items = trackItem(tr('Turn Off', 'כבה'), '', noneSelected, 'sid:no')
          + list.map(t => trackItem(trackName(t), tr('Internal', 'פנימי'), t.selected, `sid:${t.id}`)).join('');
        break;
      }
      case 'WEB_SUBS': {
        title = tr('Web Subtitles', 'כתוביות רשת');
        const subs = info.webSubs;
        if (!subs || !subs.length) {
          items = `<div class="popup-empty">${subs === null ? tr('Searching...', 'מחפש...') : tr('No subtitles found', 'לא נמצאו כתוביות')}</div>`;
        } else {
          const activeExternal = st.tracks.some(t => t.type === 'sub' && t.external && t.selected);
          items = subs.map((s, i) => trackItem(
            `${(s.lang || '').toUpperCase()} ${FLAGS[(s.lang || '').toLowerCase()] || '🌐'}`,
            s.source || '', activeExternal && selectedWebSub === s.url, `web:${i}`)).join('');
        }
        break;
      }
      case 'ASPECT_RATIO':
        title = tr('Aspect Ratio', 'יחס תצוגה');
        items = ASPECT_MODES.map(m => trackItem(tr(m.en, m.he), m.desc, m.id === aspect, `aspect:${m.id}`)).join('');
        break;
      case 'SPEED':
        title = tr('Playback Speed', 'מהירות ניגון');
        items = SPEEDS.map(s => trackItem(`${s}x`, s === 1 ? tr('Normal', 'רגיל') : '', Math.abs(st.speed - s) < 0.01, `speed:${s}`)).join('');
        break;
      case 'SYNC': {
        title = tr('Subtitle Sync', 'סנכרון כתוביות');
        const ms = Math.round((st.subDelay || 0) * 1000);
        extra = `<div class="popup-offset${ms ? ' set' : ''}">${ms > 0 ? '+' : ''}${ms}ms</div>`;
        items = trackItem(tr('Sync to what I hear', 'סנכרן למה שאני שומע'),
          tr('Press when the audio matches the subtitle on screen', 'לחץ כשהדיאלוג תואם לכתובית'), false, 'syncHear')
          + trackItem(tr('Reset to 0', 'אפס'), '', ms === 0, 'delay:0')
          + SYNC_STEPS.map(step => trackItem(`${step > 0 ? '+' : ''}${step}ms`,
            step > 0 ? tr('delay subtitles', 'השהה כתוביות') : tr('advance subtitles', 'הקדם כתוביות'), false, `delayBy:${step}`)).join('');
        break;
      }
      default: break;
    }
    const scroll = popup.querySelector('.popup-list');
    const scrollTop = scroll ? scroll.scrollTop : 0;
    popup.innerHTML = `<div class="popup-title">${title}</div>${extra}<div class="popup-list">${items}</div>`;
    popup.querySelector('.popup-list').scrollTop = scrollTop;
    popup.hidden = false;
    renderButtons();
  }

  function openMenu(name) {
    activeMenu = activeMenu === name ? 'NONE' : name;
    renderPopup();
    showControls();
    const first = $('popup').querySelector('.track-item');
    if (first && activeMenu !== 'NONE') first.focus({ preventScroll: true });
  }

  async function runAction(action) {
    const [kind, value] = action.split(':');
    try {
      switch (kind) {
        case 'aid': await window.player.cmd('setProperty', 'aid', Number(value)); break;
        case 'sid': await window.player.cmd('setProperty', 'sid', value === 'no' ? 'no' : Number(value)); break;
        case 'web': {
          const sub = info.webSubs[Number(value)];
          toast(tr('Loading subtitle…', 'טוען כתוביות…'));
          await window.player.cmd('webSub', sub.url, `${(sub.lang || '').toUpperCase()} • ${sub.source || 'Web'}`, sub.lang || '');
          selectedWebSub = sub.url;
          break;
        }
        case 'aspect': aspect = value; await window.player.cmd('aspect', value); renderAspectBadge(); break;
        case 'speed': await window.player.cmd('setProperty', 'speed', Number(value)); break;
        case 'delay': st.subDelay = 0; await window.player.cmd('setProperty', 'sub-delay', 0); return renderPopup();
        case 'delayBy': {
          st.subDelay = (st.subDelay || 0) + Number(value) / 1000;
          await window.player.cmd('setProperty', 'sub-delay', st.subDelay);
          // Keep the sync menu open for repeated nudges (Android does too), with focus kept.
          renderPopup();
          const again = $('popup').querySelector(`[data-action="${action}"]`);
          if (again) again.focus({ preventScroll: true });
          return;
        }
        case 'syncHear': {
          const ok = await window.player.cmd('syncToHeard');
          toast(ok ? tr('Subtitles synced', 'הכתוביות סונכרנו') : tr('No subtitle on screen right now', 'אין כתובית על המסך כרגע'));
          return;
        }
        default: break;
      }
    } catch (err) {
      toast(err.message || String(err));
    }
    activeMenu = 'NONE';
    renderPopup();
  }

  /* ── Seeking (Android "smart seeking": queue, show, apply after 400 ms) ─ */
  function queueSeek(deltaSeconds) {
    if (!st.duration) return;
    const base = pendingSeek != null ? pendingSeek : st.position;
    pendingSeek = Math.min(st.duration, Math.max(0, base + deltaSeconds));
    const diff = Math.round(pendingSeek - st.position);
    $('seek-diff').textContent = `${diff > 0 ? '+' : ''}${diff}s`;
    $('seek-time').textContent = fmt(pendingSeek);
    $('seek-overlay').hidden = false;
    renderProgress();
    clearTimeout(seekTimer);
    seekTimer = setTimeout(async () => {
      const target = pendingSeek;
      try { await window.player.cmd('seek', target); } catch {}
      st.position = target;
      pendingSeek = null;
      $('seek-overlay').hidden = true;
      renderProgress();
    }, 400);
  }

  function barPositionToTime(clientX) {
    const r = $('bar-area').getBoundingClientRect();
    let frac = (clientX - r.left) / r.width;
    if (isRtl()) frac = 1 - frac;
    return Math.min(1, Math.max(0, frac)) * (st.duration || 0);
  }

  /* ── Overlays: resume, error, up next ───────────────────────────────── */
  function showResume(seconds) {
    resumeOpen = true;
    const el = $('resume');
    el.innerHTML = `
      <div class="resume-card">
        ${svg('play')}
        <div class="resume-title">${tr('Continue Watching?', 'להמשיך צפייה?')}</div>
        <div class="resume-sub">${tr('Stopped at', 'נעצר ב-')} ${fmt(seconds)}</div>
        <div class="resume-actions">
          <button class="pill red" id="resume-go">${tr('▶ Continue', '▶ המשך')}</button>
          <button class="pill dark" id="resume-start">${tr('From Start', 'מהתחלה')}</button>
        </div>
      </div>`;
    el.hidden = false;
    renderLoading();
    $('resume-go').focus();
    const done = async choice => {
      el.hidden = true;
      resumeOpen = false;
      await window.player.cmd('resume', choice);
      renderLoading();
      renderPaused();
    };
    $('resume-go').onclick = () => done('continue');
    $('resume-start').onclick = () => done('restart');
  }

  function showError(message) {
    errorOpen = true;
    const el = $('error');
    el.innerHTML = `
      <div class="error-box">
        <div class="error-icon">${svg('warning')}</div>
        <div class="error-title">${tr('Playback Error', 'שגיאת נגן')}</div>
        <div class="error-msg" dir="auto">${esc(message)}</div>
        <div class="error-actions">
          <button class="pill blue" id="err-retry">${svg('refresh')}${tr('Retry', 'נסה שוב')}</button>
          <button class="pill red" id="err-back">${svg('back')}${tr('Back to Sources', 'חזור למקורות')}</button>
        </div>
      </div>`;
    el.hidden = false;
    renderLoading();
    $('err-retry').focus();
    $('err-retry').onclick = async () => {
      el.hidden = true;
      errorOpen = false;
      await window.player.cmd('retry');
    };
    $('err-back').onclick = () => window.player.cmd('close');
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
      ${info.logo ? `<img class="upnext-logo" src="${esc(info.logo)}" alt="">` : `<div class="upnext-title" dir="auto">${esc(info.showTitle || info.title)}</div>`}
      <div class="upnext-label">${tr('UP NEXT', 'הבא בתור')}</div>
      <div class="upnext-ep">${same ? tr(`Episode ${next.episode}`, `פרק ${next.episode}`) : tr(`S${next.season} • E${next.episode}`, `עונה ${next.season} • פרק ${next.episode}`)}</div>
      <div class="upnext-bar"><div class="upnext-fill" id="upnext-fill"></div></div>
      <div class="upnext-actions">
        <button class="pill red" id="upnext-play">${svg('play')}<span id="upnext-label"></span></button>
        <button class="pill ghost" id="upnext-dismiss">${tr('Dismiss', 'דחה')}</button>
      </div>`;
    el.hidden = false;
    const tick = () => {
      $('upnext-label').textContent = tr(`Play (${upNext.countdown})`, `נגן (${upNext.countdown})`);
      $('upnext-fill').style.width = `${((10 - upNext.countdown) / 10) * 100}%`;
    };
    tick();
    upNext.timer = setInterval(() => {
      upNext.countdown -= 1;
      tick();
      if (upNext.countdown <= 0) { clearInterval(upNext.timer); window.player.cmd('playNext'); }
    }, 1000);
    $('upnext-play').onclick = () => { clearInterval(upNext.timer); window.player.cmd('playNext'); };
    $('upnext-dismiss').onclick = () => {
      clearInterval(upNext.timer);
      upNext.dismissed = true;
      el.hidden = true;
    };
  }

  let toastTimer = null;
  function toast(text) {
    const el = $('toast');
    el.textContent = text;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 2200);
  }

  let volTimer = null;
  function changeVolume(delta) {
    const vol = Math.min(130, Math.max(0, Math.round((st.volume || 0) + delta)));
    window.player.cmd('setProperty', 'volume', vol);
    st.volume = vol;
    const el = $('volume-osd');
    el.textContent = `🔊 ${vol}%`;
    el.hidden = false;
    clearTimeout(volTimer);
    volTimer = setTimeout(() => { el.hidden = true; }, 1200);
  }

  /* ── Input ──────────────────────────────────────────────────────────── */
  function togglePause() {
    window.player.cmd('togglePause');
    showControls();
  }

  document.addEventListener('mousemove', () => showControls());
  $('surface').addEventListener('click', () => {
    if (activeMenu !== 'NONE') { activeMenu = 'NONE'; renderPopup(); return; }
    if (controlsVisible) hideControls(); else showControls();
  });
  $('surface').addEventListener('dblclick', () => window.player.cmd('fullscreen'));
  document.addEventListener('wheel', e => { if (!e.target.closest('.popup')) changeVolume(e.deltaY < 0 ? 5 : -5); }, { passive: true });

  $('btn-play').onclick = togglePause;
  $('btn-skip').onclick = () => queueSeek(85);
  $('btn-close').onclick = () => window.player.cmd('close');
  document.querySelectorAll('[data-menu]').forEach(btn => { btn.onclick = () => openMenu(btn.dataset.menu); });
  $('popup').addEventListener('click', e => {
    const item = e.target.closest('.track-item');
    if (item) runAction(item.dataset.action);
  });

  const bar = $('bar-area');
  bar.addEventListener('pointerdown', e => {
    scrubbing = true;
    bar.classList.add('active');
    bar.setPointerCapture(e.pointerId);
    pendingSeek = barPositionToTime(e.clientX);
    renderProgress();
  });
  bar.addEventListener('pointermove', e => {
    if (!scrubbing) return;
    pendingSeek = barPositionToTime(e.clientX);
    renderProgress();
  });
  bar.addEventListener('pointerup', async () => {
    if (!scrubbing) return;
    scrubbing = false;
    bar.classList.remove('active');
    const target = pendingSeek;
    try { await window.player.cmd('seek', target); } catch {}
    st.position = target;
    pendingSeek = null;
    renderProgress();
    scheduleHide();
  });

  document.addEventListener('keydown', e => {
    if (resumeOpen || errorOpen) return; // dialogs handle their own buttons
    const key = e.key;
    if (activeMenu !== 'NONE' && key !== 'Escape') {
      // Arrow keys move through the menu like the TV remote; ←/→ close it.
      const items = [...$('popup').querySelectorAll('.track-item')];
      const idx = items.indexOf(document.activeElement);
      if (key === 'ArrowDown') { (items[idx + 1] || items[0])?.focus(); e.preventDefault(); return; }
      if (key === 'ArrowUp') { (items[idx - 1] || items[items.length - 1])?.focus(); e.preventDefault(); return; }
      if (key === 'ArrowLeft' || key === 'ArrowRight') { activeMenu = 'NONE'; renderPopup(); e.preventDefault(); return; }
      return;
    }
    switch (key) {
      case ' ':
      case 'Enter':
        if (document.activeElement && document.activeElement.tagName === 'BUTTON') return;
        togglePause(); e.preventDefault(); break;
      case 'ArrowRight': queueSeek(isRtl() ? -10 : 10); showControls(); e.preventDefault(); break;
      case 'ArrowLeft': queueSeek(isRtl() ? 10 : -10); showControls(); e.preventDefault(); break;
      case 'ArrowUp': changeVolume(5); e.preventDefault(); break;
      case 'ArrowDown': changeVolume(-5); e.preventDefault(); break;
      case 'm': case 'M': window.player.cmd('setProperty', 'mute', !st.muted); toast(st.muted ? '🔊' : '🔇'); break;
      case 'f': case 'F': case 'F11': window.player.cmd('fullscreen'); e.preventDefault(); break;
      case 'Escape':
        // Android Back: menu → controls → leave the player.
        if (activeMenu !== 'NONE') { activeMenu = 'NONE'; renderPopup(); }
        else if (controlsVisible && !st.paused) hideControls();
        else window.player.cmd('close');
        break;
      default: break;
    }
  });

  /* ── Bridge ─────────────────────────────────────────────────────────── */
  window.player.onInit(data => {
    info = { ...info, ...data };
    document.documentElement.lang = info.lang === 'en' ? 'en' : 'he';
    document.documentElement.dir = isRtl() ? 'rtl' : 'ltr';
    const bd = $('loading-backdrop');
    bd.style.backgroundImage = info.backdrop ? `url("${info.backdrop.replace(/"/g, '%22')}")` : '';
    $('loading-title').innerHTML = info.logo
      ? `<img class="loading-logo" src="${esc(info.logo)}" alt="${esc(info.title)}">`
      : `<div class="loading-name" dir="auto">${esc(info.title)}</div>`;
    renderButtons();
    renderLoading();
    renderAspectBadge();
    if (info.resumeSeconds > 30) showResume(info.resumeSeconds);
    showControls();
  });

  window.player.onState(data => {
    const wasPaused = st.paused;
    st = { ...st, ...data };
    if (!scrubbing && pendingSeek == null) renderProgress();
    if (wasPaused !== st.paused) { renderButtons(); renderPaused(); if (st.paused) showControls(); else scheduleHide(); }
    renderLoading();
    renderBadges();
    checkUpNext();
    if (activeMenu === 'AUDIO' || activeMenu === 'EMBEDDED_SUBS' || activeMenu === 'WEB_SUBS' || activeMenu === 'SPEED' || activeMenu === 'SYNC') {
      if (!$('popup').contains(document.activeElement)) renderPopup();
    }
  });

  window.player.onEvent(ev => {
    if (ev.type === 'error') showError(ev.message);
    else if (ev.type === 'subtitles') { info.webSubs = ev.list; if (ev.selected) selectedWebSub = ev.selected; if (activeMenu === 'WEB_SUBS') renderPopup(); }
    else if (ev.type === 'toast') toast(ev.text);
  });

  renderButtons();
  window.player.ready();
})();
