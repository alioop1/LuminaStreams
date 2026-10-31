/**
 * LuminaStreams — TV mode (LG webOS).
 * Detects the TV and turns the shared web app into a remote-controlled TV app,
 * like the Android TV version: D-pad spatial navigation with Android TV focus
 * states, OK = select, Back = close sheet → previous screen → exit app.
 * Playback goes to the TV's own player (player/tvPlayback.js).
 * Outside the TV (or without ?tv=1 for testing) nothing here runs.
 */
(() => {
  'use strict';

  const ua = navigator.userAgent || '';
  const forced = /[?&]tv=1\b/.test(location.search);
  const isTv = forced || /Web0S|webOS/i.test(ua) || !!window.PalmSystem;
  window.TvMode = Object.freeze({ isTv, isWebOS: /Web0S|webOS/i.test(ua) || !!window.PalmSystem });
  if (!isTv) return;

  document.documentElement.classList.add('is-tv');

  const FOCUSABLE = 'button:not([disabled]), a[href], input:not([type="hidden"]), select, textarea, [tabindex]:not([tabindex="-1"])';
  // Things the app makes clickable without making them focusable.
  const CLICKABLE = [
    '.ps5-landscape-card[data-idx]', '.card-portrait', '.ps5-source-card', '.ps5-episode-card',
    '.watchlist-card', '.studio-pill', '.toggle[data-setting]', '.ps5-details-actor',
    '.fuzer-desktop-row', '.iptv-channel', '.season-tab', '.auth-tab', '.watchlist-tab', '.stream-card',
  ].join(', ');

  function makeFocusable(root) {
    if (!root.querySelectorAll) return;
    const els = root.matches && root.matches(CLICKABLE) ? [root] : [];
    root.querySelectorAll(CLICKABLE).forEach(el => els.push(el));
    els.forEach(el => { if (!el.hasAttribute('tabindex')) el.tabIndex = 0; });
  }

  function isVisible(el) {
    if (el.closest('[hidden], .hidden')) return false;
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    const cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none' && Number(cs.opacity) > 0.05;
  }

  /** The area navigation is confined to: an open sheet, or the whole page. */
  function scope() {
    const modal = document.getElementById('modal-overlay');
    if (modal && !modal.classList.contains('hidden') && getComputedStyle(modal).display !== 'none') return modal;
    return document.body;
  }

  function candidates() {
    return [...scope().querySelectorAll(FOCUSABLE)].filter(el => !el.closest('.ap-root') && isVisible(el));
  }

  // Where focus lands when a screen or sheet opens (Android TV's initial focus).
  const INITIAL = [
    '.ps5-source-card', '.ps5-episode-card', '#detail-play-btn',
    '.ps5-landscape-card.active', '#hero-btn-play', '.ps5-nav-btn.active',
  ];

  function focusFirst() {
    const area = scope();
    for (const sel of INITIAL) {
      const el = [...area.querySelectorAll(sel)].find(isVisible);
      if (el) { focusEl(el); return; }
    }
    const first = candidates()[0];
    if (first) focusEl(first);
  }

  // Focus lost (screen changed, sheet closed, item removed) → initial focus again.
  let refocusTimer = 0;
  function refocusSoon() {
    clearTimeout(refocusTimer);
    refocusTimer = setTimeout(() => {
      const a = document.activeElement;
      if ((!a || a === document.body || !a.isConnected || !scope().contains(a)) && !document.querySelector('.ap-tv')) focusFirst();
    }, 450);
  }

  function focusEl(el) {
    el.focus({ preventScroll: true });
    el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }

  /** Move focus to the nearest element in a screen direction (left/right/up/down). */
  function move(dir) {
    const current = document.activeElement;
    if (!current || current === document.body || !scope().contains(current)) { focusFirst(); return; }
    const from = current.getBoundingClientRect();
    const cx = from.left + from.width / 2;
    const cy = from.top + from.height / 2;
    let best = null;
    let bestScore = Infinity;
    for (const el of candidates()) {
      if (el === current || current.contains(el) || el.contains(current)) continue;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      let primary;
      let ortho;
      switch (dir) {
        case 'left': primary = from.left - r.right; ortho = Math.abs(y - cy); if (x >= cx) continue; break;
        case 'right': primary = r.left - from.right; ortho = Math.abs(y - cy); if (x <= cx) continue; break;
        case 'up': primary = from.top - r.bottom; ortho = Math.abs(x - cx); if (y >= cy) continue; break;
        default: primary = r.top - from.bottom; ortho = Math.abs(x - cx); if (y <= cy) continue; break;
      }
      const score = Math.max(0, primary) + ortho * 2;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    if (best) focusEl(best);
  }

  function isBackKey(e) {
    return e.keyCode === 461 || e.key === 'GoBack' || e.key === 'BrowserBack' || e.key === 'Escape' || e.key === 'Backspace' && !/^(INPUT|TEXTAREA)$/.test(document.activeElement && document.activeElement.tagName);
  }

  function back() {
    const modal = document.getElementById('modal-overlay');
    if (modal && !modal.classList.contains('hidden')) {
      if (window.closeModal) window.closeModal();
      else modal.classList.add('hidden');
      focusFirst();
      return;
    }
    const screen = window.Router && window.Router.getCurrentScreen();
    if (screen && screen !== 'home') {
      window.Router.back(); // empty history → home
      return;
    }
    // Home: leave the app the webOS way.
    if (window.PalmSystem && window.PalmSystem.platformBack) window.PalmSystem.platformBack();
    else if (window.webOS && window.webOS.platformBack) window.webOS.platformBack();
  }

  const DIRS = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };

  // Window/bubble phase: screens with their own handling (home rows, the player)
  // run first and mark the event handled.
  window.addEventListener('keydown', e => {
    if (document.querySelector('.ap-tv')) return; // the player has the remote
    if (e.defaultPrevented) return;
    const active = document.activeElement;
    const typing = active && /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName);

    if (isBackKey(e)) {
      if (typing && e.key !== 'Escape' && e.keyCode !== 461) return;
      e.preventDefault();
      back();
      return;
    }
    const dir = DIRS[e.key];
    if (dir) {
      if (typing && (dir === 'left' || dir === 'right')) return; // move the caret
      e.preventDefault();
      move(dir);
      return;
    }
    if (e.key === 'Enter' && active && active !== document.body && !/^(BUTTON|A|INPUT|TEXTAREA|SELECT)$/.test(active.tagName)) {
      e.preventDefault();
      active.click();
    }
  });

  // webOS: keep the app's own Back handling (appinfo.json disableBackHistoryAPI).
  document.addEventListener('webOSRelaunch', () => focusFirst());

  const start = () => {
    document.body.classList.add('is-tv');
    makeFocusable(document.body);
    new MutationObserver(ms => {
      ms.forEach(m => m.addedNodes.forEach(n => n.nodeType === 1 && makeFocusable(n)));
      const a = document.activeElement;
      if (!a || a === document.body || !a.isConnected || !scope().contains(a)) refocusSoon();
    }).observe(document.body, { childList: true, subtree: true });
    document.addEventListener('focusout', e => { if (!e.relatedTarget) refocusSoon(); });
    setTimeout(focusFirst, 1500);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
