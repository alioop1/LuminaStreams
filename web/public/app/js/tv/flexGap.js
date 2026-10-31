/**
 * LuminaStreams — `gap` for flex layouts on older TV engines.
 * webOS 6 (2021 TVs, Chromium 79) supports `gap` only in grids, so every flex
 * row/column in the app would lose its spacing. Where that is the case, this
 * turns each flex container's gap into margins on its children.
 * On any engine that supports flex gap it does nothing.
 *
 * Kept cheap for slow TV CPUs: only elements matched by a stylesheet rule that
 * sets a gap are looked at, all styles are read before anything is written
 * (one style pass, no layout), and wrapping rows use the negative-margin trick
 * instead of measuring where lines break. The first time a plain row or column
 * of a CSS rule is seen, the spacing becomes a stylesheet rule
 * (`selector > * + * { margin… }`), so later screens need no script work.
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

  /** Selectors of every CSS rule that sets a gap (including inside media queries). */
  function gapSelectors() {
    const found = new Set();
    const visit = rules => {
      for (const rule of rules) {
        if (rule.cssRules && !rule.selectorText) { visit(rule.cssRules); continue; }
        const s = rule.style;
        if (s && rule.selectorText && (s.columnGap || s.rowGap)) found.add(rule.selectorText);
      }
    };
    for (const sheet of document.styleSheets) {
      try { visit(sheet.cssRules); } catch { /* cross-origin sheet (fonts): no gaps of ours */ }
    }
    // One entry per selector (rules may list several, comma-separated).
    const list = [];
    found.forEach(text => text.split(/,(?![^(]*\))/).forEach(sel => list.push(sel.trim())));
    return [...new Set(list)].filter(Boolean);
  }

  const px = v => (v && v !== 'normal' ? parseFloat(v) || 0 : 0);
  let pendingSelectors = []; // gap rules still handled per element
  let selector = '';
  let sheet = null;          // rules generated for plain rows/columns

  /** Turn a plain row/column into a stylesheet rule for its selector; true if done. */
  function compile(box, job) {
    const { colGap, rowGap, column, reverse, wraps, kids, absKids } = job;
    if (reverse || wraps || absKids) return false;
    const gap = column ? rowGap : colGap;
    const side = column ? 'top' : 'start';
    if (!gap || kids.some(k => k.ks[side] !== 0)) return false;
    const sel = pendingSelectors.find(x => { try { return box.matches(x); } catch { return false; } });
    if (!sel) return false;
    if (!sheet) {
      const style = document.createElement('style');
      style.id = 'flexgap-rules';
      document.head.appendChild(style);
      sheet = style.sheet;
    }
    const prop = column ? 'margin-top' : 'margin-inline-start';
    sheet.insertRule(`${sel} > * + * { ${prop}: ${gap}px !important; }`, sheet.cssRules.length);
    pendingSelectors = pendingSelectors.filter(x => x !== sel);
    selector = pendingSelectors.join(',');
    return true;
  }

  /** Read phase: what each container needs. No writes here. */
  function plan(box, jobs) {
    if (box.dataset.fgap) return;
    const cs = getComputedStyle(box);
    if (cs.display !== 'flex' && cs.display !== 'inline-flex') return;
    const colGap = px(cs.columnGap);
    const rowGap = px(cs.rowGap);
    if (!colGap && !rowGap) return;
    const column = cs.flexDirection.indexOf('column') === 0;
    const reverse = cs.flexDirection.slice(-7) === 'reverse';
    const wraps = cs.flexWrap !== 'nowrap';
    const kids = [];
    let absKids = false;
    for (const kid of box.children) {
      const ks = getComputedStyle(kid);
      if (ks.position === 'absolute' || ks.position === 'fixed') { absKids = true; continue; }
      if (ks.display === 'none') continue;
      kids.push({ kid, done: kid.dataset.fgk === '1', ks: { top: px(ks.marginTop), bottom: px(ks.marginBottom), start: px(ks.marginInlineStart), end: px(ks.marginInlineEnd) } });
    }
    jobs.push({ box, cs: { top: px(cs.marginTop), start: px(cs.marginInlineStart) }, colGap, rowGap, column, reverse, wraps, kids, absKids });
  }

  /** Write phase. Children handled before (container re-planned for a new child) are skipped. */
  function apply(job) {
    if (!job.kids.some(k => k.done) && compile(job.box, job)) { job.box.dataset.fgap = '1'; return; }
    const { box, colGap, rowGap, column, reverse, wraps, kids } = job;
    const firstTime = !box.dataset.fgapWrap;
    box.dataset.fgap = '1';
    const each = fn => kids.forEach((k, i) => { if (!k.done) { k.kid.dataset.fgk = '1'; fn(k, i); } });
    if (column) {
      const prop = reverse ? 'marginBottom' : 'marginTop';
      const key = reverse ? 'bottom' : 'top';
      each(({ kid, ks }, i) => { if (i && rowGap) kid.style[prop] = `${ks[key] + rowGap}px`; });
      return;
    }
    const prop = reverse ? 'marginInlineEnd' : 'marginInlineStart';
    const key = reverse ? 'end' : 'start';
    if (wraps) {
      // Every item gets the gap before/above it; the container pulls back by the same amount.
      if (firstTime) {
        box.dataset.fgapWrap = '1';
        if (colGap) box.style.marginInlineStart = `${job.cs.start - colGap}px`;
        if (rowGap) box.style.marginTop = `${job.cs.top - rowGap}px`;
      }
      each(({ kid, ks }) => {
        if (colGap) kid.style[prop] = `${ks[key] + colGap}px`;
        if (rowGap) kid.style.marginTop = `${ks.top + rowGap}px`;
      });
    } else {
      each(({ kid, ks }, i) => { if (i && colGap) kid.style[prop] = `${ks[key] + colGap}px`; });
    }
  }

  function scan(roots) {
    const boxes = new Set();
    for (const root of roots) {
      if (!root.isConnected || root.nodeType !== 1) continue;
      if (!selector) return;
      if (root.matches(selector)) boxes.add(root);
      if (root.parentElement && root.parentElement.matches(selector)) {
        root.parentElement.removeAttribute('data-fgap'); // new child in a known container
        boxes.add(root.parentElement);
      }
      root.querySelectorAll(selector).forEach(b => boxes.add(b));
    }
    const jobs = [];
    boxes.forEach(b => plan(b, jobs));
    jobs.forEach(apply);
  }

  function start() {
    if (flexGapSupported()) return;
    pendingSelectors = gapSelectors();
    selector = pendingSelectors.join(',');
    if (!selector) return;
    scan([document.body]);
    const pending = new Set();
    let queued = false;
    new MutationObserver(ms => {
      ms.forEach(m => m.addedNodes.forEach(n => n.nodeType === 1 && pending.add(n)));
      if (queued || !pending.size) return;
      queued = true;
      // Before the next frame, so new content never paints without its spacing.
      requestAnimationFrame(() => {
        queued = false;
        const roots = [...pending];
        pending.clear();
        scan(roots);
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
