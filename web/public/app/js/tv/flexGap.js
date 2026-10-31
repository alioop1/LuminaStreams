/**
 * LuminaStreams — `gap` for flex layouts on older TV engines.
 * webOS 6 (2021 TVs, Chromium 79) supports `gap` only in grids, so every flex
 * row/column in the app would lose its spacing. Where that is the case, this
 * turns each flex container's gap into margins on its children.
 * On any engine that supports flex gap it does nothing.
 */
(() => {
  'use strict';

  function flexGapSupported() {
    const probe = document.createElement('div');
    probe.style.cssText = 'display:flex;flex-direction:column;row-gap:1px;position:absolute;visibility:hidden';
    probe.appendChild(document.createElement('div'));
    probe.appendChild(document.createElement('div'));
    document.documentElement.appendChild(probe);
    const ok = probe.scrollHeight === 1;
    probe.remove();
    return ok;
  }

  const px = v => (v && v !== 'normal' ? parseFloat(v) || 0 : 0);

  function inFlow(el) {
    const cs = getComputedStyle(el);
    return cs.display !== 'none' && cs.position !== 'absolute' && cs.position !== 'fixed';
  }

  /** Give the children of one flex container the spacing its gap asks for. */
  function applyTo(box) {
    const cs = getComputedStyle(box);
    if (cs.display !== 'flex' && cs.display !== 'inline-flex') return;
    const colGap = px(cs.columnGap);
    const rowGap = px(cs.rowGap);
    if (!colGap && !rowGap) return;
    const column = cs.flexDirection.startsWith('column');
    const reverse = cs.flexDirection.endsWith('reverse');
    const wraps = cs.flexWrap !== 'nowrap';
    const kids = [...box.children].filter(inFlow);
    kids.forEach((kid, i) => {
      if (kid.dataset.fgap) return;
      kid.dataset.fgap = '1';
      if (i === 0) return;
      const ks = getComputedStyle(kid);
      if (column) {
        const prop = reverse ? 'marginBottom' : 'marginTop';
        if (rowGap) kid.style[prop] = `${px(ks[prop]) + rowGap}px`;
      } else {
        const prop = reverse ? 'marginInlineEnd' : 'marginInlineStart';
        const base = px(ks[reverse ? 'webkitMarginEnd' : 'webkitMarginStart'] || ks[prop]);
        if (colGap) kid.style[prop] = `${base + colGap}px`;
        // Wrapped rows: items that start a new line get the row gap above them.
        if (wraps && rowGap && kid.offsetTop > kids[0].offsetTop) {
          kid.style.marginTop = `${px(ks.marginTop) + rowGap}px`;
        }
      }
    });
  }

  function scan(root) {
    if (root.nodeType !== 1) return;
    applyTo(root);
    root.querySelectorAll('*').forEach(applyTo);
  }

  function start() {
    if (flexGapSupported()) return;
    scan(document.body);
    // New content: re-check the containers that changed (children not seen yet).
    const pending = new Set();
    let timer = 0;
    new MutationObserver(ms => {
      ms.forEach(m => {
        if (m.target.nodeType === 1) pending.add(m.target);
        m.addedNodes.forEach(n => n.nodeType === 1 && pending.add(n));
      });
      clearTimeout(timer);
      timer = setTimeout(() => {
        const roots = [...pending];
        pending.clear();
        roots.forEach(n => n.isConnected && scan(n));
      }, 120);
    }).observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
