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

  /*
   * Android TV proportions: the Android app lays out at 960×540 dp, every dp two
   * pixels on a 1080p TV. The web layout gets the same couch-sized proportions by
   * laying out 1280 units across the screen: the app's own layers are CSS-zoomed
   * (1.5 on a 1920-wide TV), which keeps full-resolution text and images. The
   * player is not zoomed; it is pixel-identical to the Windows player.
   * Zoom also scales viewport units on these engines, so vh/vw in the app's
   * stylesheet are divided by the same factor to still mean "the screen".
   */
  const TV_ZOOM = Math.max(1, Math.round((window.innerWidth / 1280) * 100) / 100);
  document.documentElement.style.setProperty('--tv-zoom', String(TV_ZOOM));
  // The player is laid out in Android dp: 960 dp across the screen, as on Android TV.
  document.documentElement.style.setProperty('--tv-player-zoom', String(Math.max(1, window.innerWidth / 960)));
  function compensateViewportUnits() {
    if (TV_ZOOM === 1) return;
    const UNIT = /(\d*\.?\d+)(dvh|svh|lvh|vh|vw)\b/g;
    const UNZOOMED = /^(html|body|#player-overlay)$/;
    const visit = rules => {
      for (const rule of rules) {
        if (rule.cssRules && !rule.selectorText) { visit(rule.cssRules); continue; }
        if (!rule.style || UNZOOMED.test((rule.selectorText || '').trim())) continue;
        for (let i = 0; i < rule.style.length; i++) {
          const prop = rule.style[i];
          const value = rule.style.getPropertyValue(prop);
          if (!UNIT.test(value)) continue;
          UNIT.lastIndex = 0;
          rule.style.setProperty(prop, value.replace(UNIT, (_m, n, u) => `calc(${n}${u} / ${TV_ZOOM})`), rule.style.getPropertyPriority(prop));
        }
      }
    };
    for (const sheet of document.styleSheets) {
      if (!sheet.href || !/\/css\/app\.css/.test(sheet.href)) continue;
      try { visit(sheet.cssRules); } catch { /* not readable */ }
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', compensateViewportUnits);
  else compensateViewportUnits();

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

  // Rect-only check (no style reads): display:none and empty boxes have no size.
  function isVisible(el) {
    const r = el.getBoundingClientRect();
    return r.width >= 2 && r.height >= 2 && !el.closest('[hidden], .hidden');
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

  const NAV_DOWN = ['#detail-play-btn', '#hero-btn-play', '.ps5-landscape-card.active'];

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

  /** The horizontal row (a sideways-scrolling track) an item belongs to, if any. */
  const scrollsX = new WeakMap(); // element → overflow-x allows scrolling (style read once)
  function rowOf(el) {
    let node = el.parentElement;
    for (let depth = 0; node && node !== document.body && depth < 5; depth++, node = node.parentElement) {
      let can = scrollsX.get(node);
      if (can === undefined) {
        const ox = getComputedStyle(node).overflowX;
        can = ox === 'auto' || ox === 'scroll';
        scrollsX.set(node, can);
      }
      // A row scrolls sideways only (a vertical list or page that scrolls is not a row).
      if (can && node.scrollWidth > node.clientWidth + 1 && node.scrollHeight <= node.clientHeight + 1) return node;
    }
    return null;
  }

  // The item last focused in each row: coming back to a row returns to it (Android TV).
  const lastInRow = new WeakMap();

  function focusEl(el, instant) {
    el.focus({ preventScroll: true });
    const row = rowOf(el);
    if (row) lastInRow.set(row, el);
    // Rows keep a steady place on screen; holding a key scrolls instantly.
    el.scrollIntoView({ block: row ? 'center' : 'nearest', inline: 'nearest', behavior: instant ? 'auto' : 'smooth' });
  }

  /**
   * Move focus one step (left/right/up/down), like Android TV's focus search:
   * items in line with the current one (its "beam") win, then the closest.
   * Left/right inside a row stays in that row and stops at its ends.
   */
  function move(dir, instant) {
    const current = document.activeElement;
    if (!current || current === document.body || !scope().contains(current)) { focusFirst(); return; }
    // Down from the top bar: into the screen's main action, not whatever sits below.
    if (dir === 'down' && current.closest('.ps5-nav-group')) {
      for (const sel of NAV_DOWN) {
        const el = [...scope().querySelectorAll(sel)].find(isVisible);
        if (el) { focusEl(el, instant); return; }
      }
    }
    const from = current.getBoundingClientRect();
    const horizontal = dir === 'left' || dir === 'right';
    const row = rowOf(current);
    const pool = horizontal && row
      ? [...row.querySelectorAll(FOCUSABLE)].filter(el => el !== current && isVisible(el))
      : candidates();

    let best = null;
    let bestKey = null;
    for (const el of pool) {
      if (el === current || current.contains(el) || el.contains(current)) continue;
      const r = el.getBoundingClientRect();
      let gap;
      switch (dir) {
        case 'left': gap = from.left - r.right; if (r.left + r.width / 2 >= from.left + from.width / 2) continue; break;
        case 'right': gap = r.left - from.right; if (r.left + r.width / 2 <= from.left + from.width / 2) continue; break;
        case 'up': gap = from.top - r.bottom; if (r.top + r.height / 2 >= from.top + from.height / 2) continue; break;
        default: gap = r.top - from.bottom; if (r.top + r.height / 2 <= from.top + from.height / 2) continue; break;
      }
      const inBeam = horizontal
        ? r.bottom > from.top && r.top < from.bottom
        : r.right > from.left && r.left < from.right;
      // Left/right stay on the same line (a row's end is an end); up/down may move
      // diagonally, but only to items entirely above/below.
      if (!inBeam && (horizontal || gap < 0)) continue;
      gap = Math.max(0, gap);
      const off = horizontal
        ? Math.abs((r.top + r.height / 2) - (from.top + from.height / 2))
        : Math.abs((r.left + r.width / 2) - (from.left + from.width / 2));
      const key = [inBeam ? 0 : 1, inBeam ? gap : gap + off * 2, off];
      if (!bestKey || key[0] < bestKey[0] || (key[0] === bestKey[0] && (key[1] < bestKey[1] || (key[1] === bestKey[1] && key[2] < bestKey[2])))) {
        best = el;
        bestKey = key;
      }
    }
    if (!best) return; // edge of a row or of the screen: stay put
    // Entering another row: go back to where you were in it.
    const targetRow = !horizontal && rowOf(best);
    if (targetRow && targetRow !== row) {
      const remembered = lastInRow.get(targetRow);
      if (remembered && remembered.isConnected && isVisible(remembered)) best = remembered;
    }
    focusEl(best, instant);
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
  let moving = false;

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
      // Held key: one step per frame at most, so focus never runs ahead of the screen.
      if (e.repeat && moving) return;
      moving = true;
      requestAnimationFrame(() => { moving = false; });
      move(dir, e.repeat);
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
