/**
 * LuminaStreams TV — My Watchlist, 1:1 with the Android TV WatchlistScreen.kt:
 * header (48 dp back button + 28 sp title), the focused title's backdrop (80 % high, cinematic
 * shadows), WatchlistHeroInfo above a poster grid (GridCells.Adaptive(138 dp), spacing 16 / 24,
 * padding 48) of Home's PosterCard (148×222 dp, radius 10, 3 dp white border + 1.05 when focused).
 * Up from the first grid row goes to the back button, which has focus when the screen opens.
 */
(() => {
  'use strict';
  const { tr, esc, el } = TV;

  /* ── Layout (dp), from the Compose Column: header, hero weight 0.7, grid weight 1 ── */
  const HEADER_H = 40 + 48 + 16;               // padding top 40 + IconButton 48 + padding bottom 16
  const REST = TV.H - HEADER_H;
  const HERO_H = REST * 0.7 / 1.7;
  const GRID_TOP = HEADER_H + HERO_H;
  const GRID_H = REST - HERO_H;
  const PAD = 48, PAD_TOP = 8, H_GAP = 16, V_GAP = 24, MIN_CELL = 138;
  const CARD_W = 148, CARD_H = 222;
  // Column: card 222 + spacer 6 + title (11 sp) + type (10 sp). Single lines are trimmed to the
  // font's own height (Roboto 1.172 em, no font padding); watchlist.css pins the same line heights.
  const LINE = 1.172;
  const ITEM_H = CARD_H + 6 + 11 * LINE + 10 * LINE;
  const AVAIL = TV.W - PAD * 2;
  const COLS = Math.max(1, Math.floor((AVAIL + H_GAP) / (MIN_CELL + H_GAP)));
  const CELL_W = (AVAIL - (COLS - 1) * H_GAP) / COLS;
  const ITEM_W = Math.min(CARD_W, CELL_W);      // Modifier.width(148.dp) inside the cell's constraints

  const full = (p, size) => (!p ? '' : /^https?:/i.test(String(p)) ? String(p) : TV.img(p, size));
  const num = v => (Number(v) || 0);

  function load() {
    const list = window.Storage && window.Storage.getWatchlist ? window.Storage.getWatchlist() : [];
    return (Array.isArray(list) ? list : []).filter(w => w && w.id != null).map(w => ({
      key: `${w.type === 'series' ? 'tv' : w.type}_${w.id}`,
      id: w.id,
      type: w.type === 'series' || w.type === 'tv' ? 'tv' : 'movie',
      title: w.title || w.name || '',
      poster: w.poster || '',
      backdrop: w.backdrop || '',
      overview: w.overview || '',
      rating: num(w.rating),
      year: parseInt(w.year, 10) || 0,
      genre: w.genre || '',
    }));
  }

  TV.register('watchlist', () => {
    const s = { items: [], idx: 0, zone: 'back', scroll: 0, hero: null, bdUrl: null, bdToken: 0, dead: false };

    const root = el(`
      <div class="screen wl">
        <div class="wl-bd"><div class="wl-bimgs"></div><div class="wl-g1"></div><div class="wl-g2"></div><div class="wl-g3"></div></div>
        <div class="wl-head">
          <div class="wl-back">${TV.icon('back', 24)}</div>
          <div class="wl-title">${esc(tr('My Watchlist', 'הרשימה שלי'))}</div>
        </div>
        <div class="wl-herobox" style="top:${HEADER_H}px;height:${HERO_H}px"></div>
        <div class="wl-grid" style="top:${GRID_TOP}px;height:${GRID_H}px"><div class="wl-gin"></div></div>
        <div class="wl-empty hidden" style="top:${GRID_TOP}px">
          <div class="wl-e1">🎬</div>
          <div class="wl-e2">${esc(tr('Your Watchlist is Empty', 'הרשימה שלך ריקה'))}</div>
          <div class="wl-e3">${esc(tr('Save shows and movies to watch later.', 'סרטים וסדרות שתשמור יופיעו כאן.'))}</div>
        </div>
      </div>`);
    const backEl = TV.$(root, '.wl-back');
    const bd = TV.$(root, '.wl-bd');
    const bimgs = TV.$(root, '.wl-bimgs');
    const heroBox = TV.$(root, '.wl-herobox');
    const gin = TV.$(root, '.wl-gin');
    const emptyEl = TV.$(root, '.wl-empty');
    let cards = [];

    /* ── PosterCard ── */
    function cardHtml(m, i) {
      const r = Math.floor(i / COLS);
      const c = i % COLS;
      const side = TV.rtl() ? 'right' : 'left';
      const pos = `top:${PAD_TOP + r * (ITEM_H + V_GAP)}px;${side}:${PAD + c * (CELL_W + H_GAP)}px;width:${ITEM_W}px`;
      const url = full(m.poster, 'w500') || full(m.backdrop, 'w780');
      const art = url
        ? `<img src="${esc(url)}" alt="" decoding="async">`
        : `<div class="wl-ph"><span dir="auto">${esc(m.title)}</span></div>`;
      const fuzer = String(m.id).indexOf('http') === 0;
      const dubbed = fuzer && m.title.indexOf('מדובב') >= 0;
      const badge = fuzer ? `<span class="wl-fz${dubbed ? ' dub' : ''}">${esc(dubbed ? tr('🎤 Dubbed', '🎤 מדובב') : '💎 FUZER')}</span>` : '';
      const rating = m.rating > 0 ? `<span class="wl-rt" dir="ltr">★ ${m.rating.toFixed(1)}</span>` : '';
      return `<div class="wl-card" style="${pos}">
          <div class="wl-surf">${art}${badge}${rating}</div>
          <div class="wl-t" dir="auto">${esc(m.title)}</div>
          <div class="wl-s">${esc(m.type === 'tv' ? tr('TV Show', 'סדרה') : tr('Movie', 'סרט'))}</div>
        </div>`;
    }
    function build() {
      gin.innerHTML = s.items.map(cardHtml).join('');
      cards = [...gin.children];
      const rows = Math.ceil(s.items.length / COLS);
      gin.style.height = `${PAD_TOP + rows * ITEM_H + Math.max(0, rows - 1) * V_GAP + PAD}px`;
      cards.forEach((c, i) => TV.pointer(c,
        () => { s.zone = 'grid'; focusItem(i); },
        () => { s.zone = 'grid'; focusItem(i); open(s.items[i]); }));
      const empty = !s.items.length;
      emptyEl.classList.toggle('hidden', !empty);
      bd.classList.toggle('hidden', empty);
      heroBox.classList.toggle('hidden', empty);
    }

    /* ── Backdrop (AnimatedContent fade 400 ms, 1280×720 image, fillMaxHeight 0.8) ── */
    function setBackdrop(m) {
      const url = m ? (full(m.backdrop, 'w1280') || full(m.poster, 'w780')) : '';
      if (url === s.bdUrl) return;
      s.bdUrl = url;
      const token = ++s.bdToken;
      TV.decode(url).then(ok => {
        if (s.dead || token !== s.bdToken) return;
        const old = [...bimgs.children];
        const img = document.createElement('img');
        img.className = 'wl-bi';
        img.alt = '';
        if (ok) img.src = ok;
        else img.style.visibility = 'hidden';
        bimgs.appendChild(img);
        requestAnimationFrame(() => requestAnimationFrame(() => {
          img.classList.add('on');
          old.forEach(o => o.classList.remove('on'));
          setTimeout(() => old.forEach(o => o.remove()), 450);
        }));
      });
    }

    /* ── WatchlistHeroInfo (AnimatedContent fade 300 ms) ── */
    function heroHtml(m) {
      const len = m.title.length;
      const size = len > 26 ? 32 : len > 16 ? 40 : 48;
      const dot = '<span class="wl-dot">  ·  </span>';
      const meta = [];
      if (m.year > 0) meta.push(`<span>${m.year}</span>`, dot);
      if (m.genre && String(m.genre).trim()) meta.push(`<span dir="auto">${esc(m.genre)}</span>`, dot);
      meta.push(`<span>${esc(m.type === 'tv' ? tr('TV Series', 'סדרה') : tr('Movie', 'סרט'))}</span>`);
      if (m.rating > 0) meta.push(dot, `<span class="wl-imdb"><b>IMDb</b><i>${m.rating.toFixed(1)}</i></span>`);
      return `<div class="wl-hi">
          <div class="wl-ht" dir="auto" style="font-size:${size}px;line-height:${(size * 1.1).toFixed(1)}px">${esc(m.title)}</div>
          <div class="wl-meta">${meta.join('')}</div>
          ${m.overview && m.overview.trim() ? `<div class="wl-ov" dir="auto">${esc(m.overview)}</div>` : ''}
        </div>`;
    }
    function setHero(m) {
      if (m === s.hero || (m && s.hero && m.key === s.hero.key && m.title === s.hero.title)) { s.hero = m; return; }
      s.hero = m;
      setBackdrop(m);
      const old = [...heroBox.children];
      if (!m) { old.forEach(o => o.remove()); return; }
      const layer = el(heroHtml(m));
      heroBox.appendChild(layer);
      requestAnimationFrame(() => requestAnimationFrame(() => {
        layer.classList.add('on');
        old.forEach(o => o.classList.remove('on'));
        setTimeout(() => old.forEach(o => o.remove()), 320);
      }));
    }

    /* ── Focus ── */
    function paint() {
      backEl.classList.toggle('f', s.zone === 'back');
      cards.forEach((c, i) => c.classList.toggle('f', s.zone === 'grid' && i === s.idx));
    }
    function scrollTo(i) {
      // LazyVerticalGrid brings the focused row fully into view (transform only).
      const r = Math.floor(i / COLS);
      const top = PAD_TOP + r * (ITEM_H + V_GAP);
      const bottom = top + ITEM_H;
      let y = s.scroll;
      if (r === 0) y = 0;
      else {
        if (bottom - y > GRID_H) y = bottom - GRID_H;
        if (top - y < 0) y = top;
      }
      if (y !== s.scroll) { s.scroll = y; gin.style.transform = y ? `translateY(${-y}px)` : ''; }
    }
    function focusItem(i) {
      if (!s.items.length) return;
      s.idx = Math.max(0, Math.min(s.items.length - 1, i));
      scrollTo(s.idx);
      paint();
      setHero(s.items[s.idx]);
    }
    function open(m) {
      if (!m) return;
      const id = Number(m.id);
      if (!(id > 0)) { TV.toast(tr('Not available on the TV app yet.', 'עדיין לא זמין באפליקציית הטלוויזיה.')); return; }
      TV.open('details', { id, type: m.type, movie: Object.assign({}, m, { id }) });
    }

    let built = false;
    function refresh() {
      const items = load();
      const sig = list => list.map(m => `${m.key}|${m.title}|${m.poster}|${m.rating}`).join(',');
      const changed = sig(items) !== sig(s.items);
      const prevKey = s.items[s.idx] && s.items[s.idx].key;
      s.items = items;
      // First paint too: an empty list "hasn't changed" but still needs its empty state.
      if (changed || !built) { build(); built = true; }
      if (!items.length) {
        s.zone = 'back'; s.idx = 0; s.scroll = 0; gin.style.transform = '';
        setHero(null);
        paint();
        return;
      }
      const keep = prevKey ? items.findIndex(m => m.key === prevKey) : -1;
      s.idx = keep >= 0 ? keep : Math.min(s.idx, items.length - 1);
      scrollTo(s.idx);
      paint();
      // Android shows the first title until a card takes focus.
      const heroKey = s.hero && s.hero.key;
      const still = heroKey ? items.find(m => m.key === heroKey) : null;
      setHero(s.zone === 'grid' ? items[s.idx] : (still || items[0]));
    }

    TV.pointer(backEl, () => { s.zone = 'back'; paint(); }, () => TV.back());

    function onKey(key) {
      if (s.zone === 'back') {
        if (key === 'down' && s.items.length) { s.zone = 'grid'; focusItem(s.idx); return true; }
        if (key === 'ok') { TV.back(); return true; }
        return key !== 'back';
      }
      const i = s.idx;
      const col = i % COLS;
      if (key === 'up') {
        if (i < COLS) { s.zone = 'back'; paint(); } else focusItem(i - COLS);
        return true;
      }
      if (key === 'down') {
        const nextRowStart = (Math.floor(i / COLS) + 1) * COLS;
        if (nextRowStart < s.items.length) focusItem(Math.min(i + COLS, s.items.length - 1));
        return true;
      }
      if (TV.fwd(key)) { if (col < COLS - 1 && i + 1 < s.items.length) focusItem(i + 1); return true; }
      if (TV.bwd(key)) { if (col > 0) focusItem(i - 1); return true; }
      if (key === 'ok') { open(s.items[i]); return true; }
      return key !== 'back';
    }

    refresh();

    return {
      el: root,
      onKey,
      onShow(first) { if (!first) refresh(); },
      destroy() { s.dead = true; },
    };
  });
})();
