/**
 * LuminaStreams TV — Home (1:1 with the Android TV HomeScreen):
 * top bar with the Home / Movies / Series / Fuzer tabs, full-screen backdrop,
 * hero at the bottom start, and the console row cycler (category list + one row
 * at a time, crossfading as you move up and down).
 */
(() => {
  'use strict';
  const { tr, esc, icon, el, img } = TV;

  // HomeConstants.kt (dp)
  const LAND_W = 280, LAND_H = 158, PORT_W = 148, PORT_H = 222;
  const LAND_GAP = 20, PORT_GAP = 16, BRAND_W = 180, BRAND_GAP = 16, ROW_PAD = 32;
  const CONTENT_W = 960 - 260 - 2;
  const SIDE_ITEM_H = 44;

  const BRANDS = {
    HBO: { label: ['HBO Max Exclusives', 'בלעדי ל-HBO'], movie: { with_watch_providers: '1899|384', watch_region: 'US' }, tv: { with_networks: '49|3186' }, logo: '/tuomPhY2UtuPTqqFnKMVHvSb724.png', bg: ['#1A0A2E', '#2D1B4E'] },
    NETFLIX: { label: ['Netflix Originals', 'מקור של נטפליקס'], movie: { with_watch_providers: '8', watch_region: 'US' }, tv: { with_networks: '213' }, logo: '/wwemzKWzjKYJFfCeiB57q3r4Bcm.png', bg: ['#141414', '#1C1C1C'] },
    AMAZON: { label: ['Amazon Originals', 'מקור של אמאזון'], movie: { with_watch_providers: '119', watch_region: 'US' }, tv: { with_networks: '1024' }, logo: '/w7HfLNm9CWwRmAMU58udl2L7We7.png', bg: ['#0A1E2C', '#0F2B3D'] },
    DISNEY: { label: ['Disney+ Exclusives', 'בלעדי לדיסני+'], movie: { with_companies: '2|3|420' }, tv: { with_networks: '2739' }, logo: '/1edZOYAfoyZyZ3rklNSiUpXX30Q.png', bg: ['#0A1628', '#142240'] },
    APPLE_TV: { label: ['Apple TV+ Originals', 'מקור של אפל TV'], movie: { with_watch_providers: '350', watch_region: 'US' }, tv: { with_networks: '2552' }, logo: '/bngHRFi794mnMq34gfVcm9nDxN1.png', bg: ['#1A1A2E', '#2A2A40'] },
    PARAMOUNT: { label: ['Paramount+ Exclusives', 'בלעדי לפרמאונט'], movie: { with_companies: '4' }, tv: { with_networks: '4330|67' }, logo: '/fi83B1oztoS47xxcemFdPMhIzK.png', bg: ['#0A0F1E', '#141E35'] },
    HULU: { label: ['Hulu Originals', 'מקור של הולו'], movie: { with_watch_providers: '15', watch_region: 'US' }, tv: { with_networks: '453' }, logo: '/pqUTCleNUiTLAVlelGxUgWn1ELh.png', bg: ['#0A1A14', '#0F2A1E'] },
    SONY: { label: ['Sony Pictures', 'תוכן של סוני'], movie: { with_companies: '5|34|559' }, tv: { with_companies: '5|34|559' }, logo: '', bg: ['#050A14', '#152238'] },
  };
  const RIBBON = ['HBO', 'NETFLIX', 'AMAZON', 'DISNEY', 'APPLE_TV', 'PARAMOUNT', 'HULU', 'SONY'];
  const HOME_STUDIOS = ['HBO', 'NETFLIX', null, 'AMAZON', 'APPLE_TV', 'DISNEY', 'PARAMOUNT', 'HULU', 'SONY'];

  const POP = { sort_by: 'popularity.desc' };
  const SOURCES = {
    movieTrending: ['/trending/movie/week', {}, 'movie'],
    tvTrending: ['/trending/tv/week', {}, 'tv'],
    moviePremieres: ['/movie/now_playing', {}, 'movie'],
    tvPremieres: ['/tv/on_the_air', {}, 'tv'],
    movieAction: ['/discover/movie', Object.assign({ with_genres: '28' }, POP), 'movie'],
    movieAnimation: ['/discover/movie', Object.assign({ with_genres: '16' }, POP), 'movie'],
    tvDrama: ['/discover/tv', Object.assign({ with_genres: '18' }, POP), 'tv'],
    tvAnimation: ['/discover/tv', Object.assign({ with_genres: '16' }, POP), 'tv'],
  };
  const STUDIO_CATS = [
    ['top', 'Most Popular', 'הכי פופולרי', {}],
    ['action', 'Action & Adventure', 'פעולה והרפתקאות', { with_genres: '28|10759' }],
    ['ani', 'Animation', 'אנימציה', { with_genres: '16' }],
    ['comedy', 'Comedy', 'קומדיה', { with_genres: '35' }],
    ['drama', 'Drama', 'דרמה', { with_genres: '18' }],
    ['scifi', 'Sci-Fi & Fantasy', 'מדע בדיוני ופנטזיה', { with_genres: '878|14|10765' }],
    ['crime', 'Crime & Mystery', 'פשע ומסתורין', { with_genres: '80|9648' }],
  ];

  const NAV = [
    ['search', 'search'], ['home', 'home'], ['movie', 'movies'], ['liveTv', 'series'], ['autoAwesome', 'Fuzer'],
    ['tv', 'iptv'], ['cloud', 'debrid'], ['favorite', 'watchlist'], ['settings', 'settings'], ['account', 'auth'],
  ];

  const genreName = (ids, type) => {
    const C = window.CONST || {};
    const map = type === 'tv' ? C.TV_GENRES : C.MOVIE_GENRES;
    return (map && ids && ids[0] && map[ids[0]]) || '';
  };

  function home() {
    const root = el(`
      <div class="screen home">
        <div class="bd"><img class="bd-img" alt=""><img class="bd-img" alt=""><div class="bd-h"></div><div class="bd-v"></div><div class="bd-top"></div></div>
        <div class="topnav">
          <div class="logo" dir="ltr"><b>LUMINA&nbsp;</b><span>STREAMS</span></div>
          <div class="clock"></div>
          <div class="nav">${NAV.map(([ic, id]) => `<div class="nav-btn" data-nav="${id}"><i class="glow"></i>${icon(ic, 24)}</div>`).join('')}</div>
        </div>
        <div class="hero-area"><div class="hero"></div></div>
        <div class="cycler">
          <div class="side"><div class="side-list"></div></div>
          <div class="divider"></div>
          <div class="rows"></div>
        </div>
        <div class="home-loading hidden"></div>
      </div>`);

    const bdImgs = root.querySelectorAll('.bd-img');
    const heroEl = TV.$(root, '.hero');
    const sideList = TV.$(root, '.side-list');
    const rowsEl = TV.$(root, '.rows');
    const navBtns = [...root.querySelectorAll('.nav-btn')];
    const clockEl = TV.$(root, '.clock');

    const s = {
      tab: 'home', filter: null,
      rows: [],          // { id, title, kind: 'land'|'port'|'ribbon', items, page, src, el?, track?, scroll }
      row: 0, item: 0,
      zone: 'rows',      // 'rows' | 'side' | 'nav'
      nav: 0,
      hero: null, heroToken: 0,
      loading: false,
    };

    /* ── Clock ── */
    const tick = () => { clockEl.textContent = TV.clock(); };
    tick();
    const clockTimer = setInterval(tick, 15000);

    /* ── Backdrop (original size, crossfaded once decoded, like Coil's crossfade) ── */
    let bdFront = 0;
    let bdUrl = '';
    function setBackdrop(path) {
      const url = img(path, 'original');
      if (!url || url === bdUrl) return;
      bdUrl = url;
      TV.decode(url).then(ok => {
        if (!ok || url !== bdUrl) return;
        const back = bdImgs[1 - bdFront];
        back.src = url;
        back.classList.add('on');
        bdImgs[bdFront].classList.remove('on');
        bdFront = 1 - bdFront;
      });
    }

    /* ── Hero (HeroOverlay) ── */
    const runtimeText = m => {
      if (!m) return '';
      const h = Math.floor(m / 60);
      const mm = m % 60;
      return h > 0 ? `${h}${tr('h', 'שע')} ${mm}${tr('m', 'ד')}` : `${mm}${tr('m', 'ד')}`;
    };
    function heroHtml(m, x) {
      const sep = '<span class="sep">|</span>';
      const meta = [];
      if (m.rating > 0) meta.push(`<span class="rt"><i>★</i>${m.rating.toFixed(1)}</span>`);
      if (x && x.runtime) meta.push(`<span>${runtimeText(x.runtime)}</span>`);
      if (m.year > 0) meta.push(`<span>${m.year}</span>`);
      meta.push('<span>16+</span>');
      const g = (x && x.genre) || genreName(m.genreIds, m.type);
      if (g) meta.push(`<span class="up">${esc(String(g).split(/[,·•/|]/)[0].trim())}</span>`);
      const overview = (x && x.overview) || m.overview;
      const title = x && x.logo
        ? `<img class="hero-logo" src="${esc(x.logo)}" alt="">`
        : (x || s.heroEnriched === false ? `<div class="hero-title ${m.title.length > 26 ? 's' : m.title.length > 16 ? 'm' : ''}" dir="auto">${esc(m.title)}</div>` : '');
      const cast = x && x.cast && x.cast.length ? `
        <div class="hero-cast"><span class="lbl">${tr('Starring:', 'בכיכובם של:')}</span>${x.cast.map(c => `
          <span class="actor">${c.photo ? `<img src="${esc(c.photo)}" alt="">` : `<span class="ph">${icon('person', 24)}</span>`}<span dir="auto">${esc(c.name)}</span></span>`).join('')}</div>` : '';
      return `
        <div class="hero-in">
          <div class="hero-titlebox">${title}</div>
          <div class="hero-meta">${meta.join(sep)}</div>
          ${overview ? `<div class="hero-syn"><i class="accent"></i><p dir="auto">${esc(overview)}</p></div>` : ''}
          ${cast}
        </div>`;
    }
    // The hero and its 4K backdrop follow the card the remote rests on (Android's
    // MID-tier hero delay), so moving fast along a row only moves the focus ring.
    const HERO_DWELL = 300;
    let heroTimer = 0;
    const enrichCache = new Map();
    function setHero(m) {
      if (!m) return;
      clearTimeout(heroTimer);
      if (s.hero && s.hero.key === m.key) return;
      heroTimer = setTimeout(() => applyHero(m), s.hero ? HERO_DWELL : 0);
    }
    async function applyHero(m) {
      s.hero = m;
      const token = ++s.heroToken;
      // The 4K backdrop (decode + crossfade is the heaviest thing the TV does) waits a little longer.
      setTimeout(() => { if (token === s.heroToken) setBackdrop(m.backdrop); }, 400);
      let x = enrichCache.get(m.key);
      if (!x) {
        // Details (logo, runtime, cast), fetched before drawing so the hero changes once.
        try {
          const d = await TV.tmdb(`/${m.type}/${m.id}`, { append_to_response: 'images,credits', include_image_language: `${TV.lang()},en,null` });
          const logos = (d.images && d.images.logos) || [];
          const logo = logos.find(l => l.iso_639_1 === TV.lang()) || logos.find(l => l.iso_639_1 === 'en') || logos[0];
          x = {
            logo: logo ? img(logo.file_path, 'w500') : '',
            runtime: d.runtime || (d.episode_run_time && d.episode_run_time[0]) || 0,
            genre: d.genres && d.genres[0] ? d.genres[0].name : '',
            overview: d.overview || '',
            cast: ((d.credits && d.credits.cast) || []).slice(0, 3).map(c => ({ name: c.name, photo: c.profile_path ? img(c.profile_path, 'w185') : '' })),
          };
          enrichCache.set(m.key, x);
          if (x.logo) await TV.decode(x.logo);
        } catch { x = {}; }
      }
      if (token === s.heroToken) renderHero(m, x);
    }
    function renderHero(m, x) {
      // Crossfade (Compose Crossfade 400 ms).
      const next = el(heroHtml(m, x));
      const old = heroEl.querySelector('.hero-in.on');
      heroEl.appendChild(next);
      requestAnimationFrame(() => next.classList.add('on'));
      if (old) { old.classList.remove('on'); setTimeout(() => old.remove(), 420); }
    }

    /* ── Cards ── */
    const typeLabel = m => (m.type === 'tv' ? tr('TV Show', 'סדרה') : tr('Movie', 'סרט'));
    const badge = m => (m.rating > 0 ? `<span class="rating">★ ${m.rating.toFixed(1)}</span>` : '');
    const progress = m => (m.progress >= 0.02 ? `<span class="prog"><i style="width:${Math.min(100, Math.round(m.progress * 100))}%"></i></span>` : '');
    function cardHtml(row, m) {
      if (row.kind === 'land') {
        return `<div class="lcard"><div class="surf"><img src="${img(m.backdrop, 'w780')}" alt="" decoding="async">
          <div class="lc-txt"><div class="t" dir="auto">${esc(m.title)}</div><div class="s">${typeLabel(m)}</div></div>${badge(m)}${progress(m)}</div></div>`;
      }
      if (row.kind === 'port') {
        return `<div class="pcard"><div class="surf"><img src="${img(m.poster, 'w500')}" alt="" decoding="async">${badge(m)}${progress(m)}</div>
          <div class="t" dir="auto">${esc(m.title)}</div><div class="s">${typeLabel(m)}</div></div>`;
      }
      const b = BRANDS[m];
      return `<div class="bcard${s.filter === m ? ' sel' : ''}"><div class="surf" style="background:linear-gradient(135deg,${b.bg[0]},${b.bg[1]})">${b.logo
        ? `<img src="https://image.tmdb.org/t/p/w300${b.logo}" alt="">` : '<span class="sony">SONY</span>'}</div></div>`;
    }
    const itemW = row => (row.kind === 'land' ? LAND_W : row.kind === 'port' ? PORT_W : BRAND_W);
    const itemGap = row => (row.kind === 'land' ? LAND_GAP : row.kind === 'port' ? PORT_GAP : BRAND_GAP);

    function buildRow(row, index) {
      if (row.el) return row.el;
      row.el = el(`<div class="row ${row.kind}"><div class="track"></div></div>`);
      row.track = row.el.firstChild;
      row.scroll = { x: 0 };
      appendCards(row, 0, index);
      return row.el;
    }
    function appendCards(row, from, rowIndex) {
      const html = row.items.slice(from).map(m => cardHtml(row, m)).join('');
      row.track.insertAdjacentHTML('beforeend', html);
      [...row.track.children].slice(from).forEach((card, k) => {
        const i = from + k;
        TV.pointer(card, () => { setZone('rows'); setRow(row.idx); focusItem(i); }, () => { setZone('rows'); setRow(row.idx); focusItem(i); activate(); });
      });
    }

    /* ── Rows & sidebar ── */
    function renderSide() {
      sideList.innerHTML = s.rows.map((r, i) => `<div class="side-item${i === s.row ? ' act' : ''}" dir="auto">${esc(r.title)}</div>`).join('');
      [...sideList.children].forEach((it, i) => TV.pointer(it, () => { setZone('side'); setRow(i); }, () => { setRow(i); setZone('rows'); focusItem(0); }));
    }
    function updateSide() {
      [...sideList.children].forEach((it, i) => {
        it.classList.toggle('act', i === s.row);
        it.classList.toggle('f', s.zone === 'side' && i === s.row);
      });
      sideList.style.transform = `translateY(${-Math.max(0, s.row - 1) * SIDE_ITEM_H}px)`;
    }
    let neighborTimer = 0;
    function showRows() {
      // Active row ± 1 in the DOM (Android composes only "isNear" rows). The active row
      // now; its neighbours a moment later, so the key press itself stays light.
      const place = (r, i) => {
        if (!r.el) rowsEl.appendChild(buildRow(r, i));
        else if (!r.el.isConnected) rowsEl.appendChild(r.el);
      };
      s.rows.forEach((r, i) => {
        if (i === s.row) place(r, i);
        else if (Math.abs(i - s.row) > 2 && r.el && r.el.isConnected) r.el.remove();
        if (r.el) r.el.classList.toggle('on', i === s.row);
      });
      clearTimeout(neighborTimer);
      const at = s.row;
      neighborTimer = setTimeout(() => {
        if (at !== s.row) return;
        [at - 1, at + 1].forEach(i => { if (s.rows[i]) place(s.rows[i], i); });
      }, 350);
    }
    function setRow(i) {
      i = Math.max(0, Math.min(s.rows.length - 1, i));
      if (i === s.row && s.rows[i] && s.rows[i].el) { updateSide(); return; }
      const prev = s.rows[s.row];
      if (prev && prev.track) { prev.track.children[s.item] && prev.track.children[s.item].classList.remove('f'); }
      s.row = i;
      s.item = 0;
      const row = s.rows[i];
      showRows();
      // Rows restart at their first item when they become active (Android scrollToItem(0)).
      if (row && row.track) { row.scroll.x = 1; TV.scrollRow(row.track, 0, itemW(row), itemGap(row), CONTENT_W, ROW_PAD, row.scroll); }
      updateSide();
      if (row && row.kind !== 'ribbon' && row.items[0]) setHero(row.items[0]);
    }
    function focusItem(i) {
      const row = s.rows[s.row];
      if (!row || !row.track) return;
      i = Math.max(0, Math.min(row.items.length - 1, i));
      const cards = row.track.children;
      if (cards[s.item]) cards[s.item].classList.remove('f');
      s.item = i;
      if (s.zone === 'rows' && cards[i]) cards[i].classList.add('f');
      TV.scrollRow(row.track, i, itemW(row), itemGap(row), CONTENT_W, ROW_PAD, row.scroll);
      if (row.kind !== 'ribbon') setHero(row.items[i]);
      // Paging (RememberPagedRowLoad): next page near the end.
      if (row.src && i >= row.items.length - 5 && !row.loadingMore) loadMore(row);
    }
    function setZone(z) {
      s.zone = z;
      root.classList.toggle('nav-on', z === 'nav');
      navBtns.forEach((b, i) => b.classList.toggle('f', z === 'nav' && i === s.nav));
      const row = s.rows[s.row];
      if (row && row.track) [...row.track.children].forEach((c, i) => c.classList.toggle('f', z === 'rows' && i === s.item));
      updateSide();
    }

    async function loadMore(row) {
      row.loadingMore = true;
      try {
        const [path, params, mt] = row.src;
        const more = await TV.list(path, Object.assign({}, params, { page: (row.page || 1) + 1 }), mt);
        const known = new Set(row.items.map(m => m.key));
        const add = more.filter(m => !known.has(m.key));
        if (!add.length) return; // end of the list: stop paging
        row.page = (row.page || 1) + 1;
        const from = row.items.length;
        row.items.push(...add);
        if (row.track) appendCards(row, from, row.idx);
        row.loadingMore = false;
      } catch { row.loadingMore = false; }
    }

    /* ── Tabs (HomeViewModel.recalculateRows) ── */
    const merge = (a, b) => {
      const seen = new Set();
      return a.concat(b).sort((x, y) => y.rating - x.rating).filter(m => (seen.has(m.key) ? false : seen.add(m.key)));
    };
    const fetchSrc = (id) => { const [p, q, mt] = SOURCES[id]; return TV.list(p, q, mt).catch(() => []); };
    async function continueWatching() {
      const WP = window.WatchProgress;
      if (!WP) return [];
      const entries = WP.getContinueWatching();
      const out = await Promise.all(entries.map(async e => {
        const tmdb = e.tmdb || (e.mediaId ? `${e.type === 'series' ? 'tv' : 'movie'}_${e.mediaId}` : '');
        const m = /^(movie|tv)_(\d+)$/.exec(tmdb || '');
        if (!m) return null;
        try {
          const d = await TV.tmdb(`/${m[1]}/${m[2]}`);
          const mv = TV.toMovie(Object.assign({ media_type: m[1] }, d), m[1]);
          if (mv) { mv.progress = (e.percentage || 0) / 100; mv.genreIds = (d.genres || []).map(g => g.id); }
          return mv;
        } catch { return null; }
      }));
      return out.filter(Boolean);
    }
    async function rowsFor(tab) {
      const T = (en, he) => tr(en, he);
      const rows = [];
      const add = (id, title, items, src) => { if (items && items.length) rows.push({ id, title, items, src, kind: 'port', page: 1 }); };
      if (tab === 'home') {
        const studio = (b) => Promise.all([
          TV.list('/discover/movie', Object.assign({}, BRANDS[b].movie, POP), 'movie').catch(() => []),
          TV.list('/discover/tv', Object.assign({}, BRANDS[b].tv, POP), 'tv').catch(() => []),
        ]).then(([a, c]) => merge(a, c));
        const [cw, mT, tT, mP, ...studios] = await Promise.all([
          continueWatching(), fetchSrc('movieTrending'), fetchSrc('tvTrending'), fetchSrc('moviePremieres'),
          ...HOME_STUDIOS.filter(Boolean).map(studio),
        ]);
        const st = {};
        HOME_STUDIOS.filter(Boolean).forEach((b, i) => { st[b] = studios[i]; });
        add('continueWatching', T('▶  Continue Watching', '▶  המשך צפייה'), cw);
        add('movieTrending', T('Trending Movies', 'סרטים פופולריים'), mT, SOURCES.movieTrending);
        add('homeHBO', T(...BRANDS.HBO.label), st.HBO);
        add('homeNetflix', T(...BRANDS.NETFLIX.label), st.NETFLIX);
        add('tvTrending', T('Popular Shows', 'סדרות פופולריות'), tT, SOURCES.tvTrending);
        ['AMAZON', 'APPLE_TV', 'DISNEY', 'PARAMOUNT', 'HULU', 'SONY'].forEach(b => add(`home${b}`, T(...BRANDS[b].label), st[b]));
        add('moviePremieres', T('New in Theaters', 'בקולנוע'), mP, SOURCES.moviePremieres);
      } else if (tab === 'movies' || tab === 'series') {
        rows.push({ id: 'ribbon', title: T('Studios', 'אולפנים'), items: RIBBON.slice(), kind: 'ribbon' });
        const isM = tab === 'movies';
        if (s.filter) {
          const b = BRANDS[s.filter];
          const base = isM ? b.movie : b.tv;
          const cats = await Promise.all(STUDIO_CATS.map(([id, en, he, q]) => {
            const src = [isM ? '/discover/movie' : '/discover/tv', Object.assign({}, base, q, POP), isM ? 'movie' : 'tv'];
            return TV.list(...src).catch(() => []).then(items => ({ id, en, he, items, src }));
          }));
          cats.forEach(c => add(`studio_${c.id}`, T(c.en, c.he), c.items, c.src));
        } else if (isM) {
          const [a, t, p, n] = await Promise.all(['movieAction', 'movieTrending', 'moviePremieres', 'movieAnimation'].map(fetchSrc));
          add('movieAction', T('Action & Adventure', 'פעולה והרפתקאות'), a, SOURCES.movieAction);
          add('movieTrending', T('Trending Now', 'פופולרי עכשיו'), t, SOURCES.movieTrending);
          add('moviePremieres', T('In Theaters', 'בקולנוע'), p, SOURCES.moviePremieres);
          add('movieAnimation', T('Animations', 'אנימציה'), n, SOURCES.movieAnimation);
        } else {
          const [d, t, p, n] = await Promise.all(['tvDrama', 'tvTrending', 'tvPremieres', 'tvAnimation'].map(fetchSrc));
          add('tvDrama', T('Drama', 'דרמה'), d, SOURCES.tvDrama);
          add('tvTrending', T('Trending Shows', 'סדרות פופולריות'), t, SOURCES.tvTrending);
          add('tvPremieres', T('New Episodes', 'פרקים חדשים'), p, SOURCES.tvPremieres);
          add('tvAnimation', T('Animations', 'אנימציה'), n, SOURCES.tvAnimation);
        }
      }
      // The first content row is landscape (Android: index 0 && not the studio ribbon).
      if (rows[0] && rows[0].kind !== 'ribbon') rows[0].kind = 'land';
      rows.forEach((r, i) => { r.idx = i; });
      return rows;
    }

    async function selectTab(tab, keepRow) {
      if (tab === 'Fuzer') { TV.toast(tr('Fuzer needs a sign-in with a security check — available in the Windows and iPhone apps.', 'Fuzer דורש התחברות עם אימות — זמין באפליקציות Windows ו-iPhone.')); return; }
      s.tab = tab;
      navBtns.forEach(b => b.classList.toggle('sel', b.dataset.nav === tab));
      s.loading = true;
      root.classList.add('loading');
      const rows = await rowsFor(tab);
      if (s.tab !== tab) return;
      s.loading = false;
      root.classList.remove('loading');
      rowsEl.innerHTML = '';
      s.rows = rows;
      s.row = -1;
      renderSide();
      const target = keepRow != null ? keepRow : (tab !== 'home' && s.filter ? 1 : 0);
      setRow(Math.min(target, rows.length - 1));
      if (s.zone !== 'nav') setZone('rows');
      focusItem(0);
    }

    /* ── Actions ── */
    function activate() {
      const row = s.rows[s.row];
      if (!row) return;
      const m = row.items[s.item];
      if (row.kind === 'ribbon') {
        s.filter = s.filter === m ? null : m;
        selectTab(s.tab, s.filter ? 1 : 0);
        return;
      }
      TV.open('details', { id: m.id, type: m.type, movie: m });
    }
    function navAction(id) {
      if (id === 'home' || id === 'movies' || id === 'series' || id === 'Fuzer') {
        if (id !== s.tab) { s.filter = null; selectTab(id); }
        setZone('rows');
        return;
      }
      if (id === 'iptv' || id === 'debrid') { TV.toast(tr('Coming to the TV app soon.', 'יגיע בקרוב לאפליקציית הטלוויזיה.')); return; }
      TV.open(id);
    }
    navBtns.forEach((b, i) => TV.pointer(b, () => { s.nav = i; setZone('nav'); }, () => { s.nav = i; navAction(b.dataset.nav); }));

    /* ── Keys (HomeScreen + ConsoleRowCycler onPreviewKeyEvent) ── */
    function onKey(key) {
      if (s.zone === 'nav') {
        if (TV.fwd(key)) { s.nav = Math.min(NAV.length - 1, s.nav + 1); setZone('nav'); return true; }
        if (TV.bwd(key)) { s.nav = Math.max(0, s.nav - 1); setZone('nav'); return true; }
        if (key === 'down' || key === 'back') { setZone('rows'); focusItem(s.item); return true; }
        if (key === 'ok') { navAction(NAV[s.nav][1]); return true; }
        return true;
      }
      if (s.zone === 'side') {
        if (key === 'up') { if (s.row === 0) { s.nav = 0; setZone('nav'); } else setRow(s.row - 1); return true; }
        if (key === 'down') { setRow(s.row + 1); return true; }
        if (TV.fwd(key) || key === 'ok') { setZone('rows'); focusItem(0); return true; }
        return key === 'back' ? false : true;
      }
      // rows
      if (key === 'up') {
        if (s.row > 0) { setRow(s.row - 1); setZone('rows'); focusItem(0); } else { s.nav = 0; setZone('nav'); }
        return true;
      }
      if (key === 'down') { if (s.row < s.rows.length - 1) { setRow(s.row + 1); setZone('rows'); focusItem(0); } return true; }
      if (TV.fwd(key)) { focusItem(s.item + 1); return true; }
      if (TV.bwd(key)) { if (s.item > 0) focusItem(s.item - 1); else setZone('side'); return true; }
      if (key === 'ok') { activate(); return true; }
      if (key === 'back') { setZone('side'); return true; }
      return false;
    }

    selectTab('home');

    return {
      el: root,
      onKey,
      onShow(first) {
        if (first) return;
        // Back from another screen: refresh Continue Watching, keep the place (Android ON_RESUME).
        if (s.tab === 'home') {
          continueWatching().then(cw => {
            const row = s.rows.find(r => r.id === 'continueWatching');
            const sig = list => list.map(m => `${m.key}:${Math.round((m.progress || 0) * 100)}`).join(',');
            if (sig(cw) === sig(row ? row.items : [])) return;
            selectTab('home', s.row);
          });
        }
      },
      destroy() { clearInterval(clockTimer); },
    };
  }

  TV.register('home', home);
})();
