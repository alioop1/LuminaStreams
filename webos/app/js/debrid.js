/**
 * LuminaStreams TV — Debrid Cloud: the user's AllDebrid library, 1:1 with the Android TV
 * DebridCloudScreen + DebridCloudViewModel (presentation/debrid). Laid out in Android dp
 * (1706.67×960). Focus follows Compose: the header back/refresh buttons, the poster grid
 * (Adaptive 142 dp, TV pivot scrolling at 30%), the not-connected "Connect" button, and
 * the files dialog for multi-file torrents.
 */
(() => {
  'use strict';
  const { tr, esc, el } = TV;

  const AD_V4 = 'https://api.alldebrid.com/v4';
  const AD_V41 = 'https://api.alldebrid.com/v4.1';
  const IMG = 'https://image.tmdb.org/t/p';
  const VIDEO_EXT = ['mkv', 'mp4', 'avi', 'ts', 'mov', 'm4v', 'webm', 'flv', 'wmv', 'mpg', 'mpeg'];

  /* ── Material icons used here (24 dp viewBox) ── */
  const PATHS = {
    refresh: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
    checkCircle: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
    download: 'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
    warning: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
    videoLibrary: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z',
    delete: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    playCircle: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z',
  };
  const ic = (name, size) => (PATHS[name]
    ? `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path d="${PATHS[name]}"/></svg>`
    : TV.icon(name, size));
  /** Material3 CircularProgressIndicator (40 dp, cyan). */
  const spinner = (stroke = 4) => {
    const r = 20 - stroke / 2;
    const c = 2 * Math.PI * r;
    return `<svg class="dbc-spin" width="40" height="40" viewBox="0 0 40 40" aria-hidden="true"><circle cx="20" cy="20" r="${r}" stroke-width="${stroke}" stroke-dasharray="${(c * 0.75).toFixed(2)} ${(c * 0.25).toFixed(2)}"/></svg>`;
  };

  /* ── DebridCloudViewModel helpers (companion object) ── */
  const trimTags = s => s.replace(/^[ \-_]+|[ \-_]+$/g, '');
  function cleanTorrentTitle(raw) {
    let name = String(raw || '');
    name = name.replace(/^\s*\[[^\]]+\]\s*/, '');
    name = name.replace(/^\s*\([^)]+\)\s*/, '');
    const dot = name.lastIndexOf('.');
    if (dot >= 0) name = name.slice(0, dot);
    name = name.replace(/[._]/g, ' ');
    const m = /\b(2160p|1080p|720p|480p|4k|uhd|bluray|web-dl|webrip|hdrip|dvdrip|h265|x265|h264|x264|hevc|ddp5\.1|atmos|truehd|aac|ac3|flac|multi|heb|hebdub)\b/i.exec(name);
    if (m) {
      const before = trimTags(name.slice(0, m.index));
      if (before.trim()) return before;
    }
    return trimTags(name);
  }
  function cleanSearchQuery(title) {
    let clean = title.replace(/\b(s\d{1,2}e\d{1,2}|s\d{1,2}|season\s*\d{1,2})\b/gi, '');
    clean = clean.replace(/\(\s*s\d{1,2}\s*\)/gi, '');
    clean = clean.replace(/\b(tvs|pl|ai)\b/gi, '');
    return trimTags(clean);
  }
  function formatBytes(bytes) {
    if (!(bytes > 0)) return '0 MB';
    const gb = bytes / (1024 * 1024 * 1024);
    return gb >= 1 ? `${gb.toFixed(2)} GB` : `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
  }
  function formatTimestamp(sec) {
    if (!(sec > 0)) return '';
    const d = new Date(sec * 1000);
    if (isNaN(d.getTime())) return '';
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
  }

  /* ── AllDebrid (AllDebridManager) ── */
  const AD = () => window.AllDebrid;
  /** AllDebridTokenKeeper.getValidToken(): the stored key, or '' when not connected. */
  const adToken = () => {
    const a = AD();
    if (!a || !a.isAuthenticated()) return '';
    return String(a.getToken() || '').trim();
  };
  async function adPost(url, params) {
    const token = adToken();
    if (!token) throw new Error(tr('AllDebrid token missing', 'טוקן AllDebrid חסר'));
    const form = new URLSearchParams();
    form.append('apikey', token);
    Object.keys(params || {}).forEach(k => [].concat(params[k]).forEach(v => form.append(k, String(v))));
    const res = await fetch(`${url}?agent=LuminaStreams`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form.toString(),
    });
    if (!res.ok) throw new Error(`AllDebrid HTTP ${res.status}`);
    const json = await res.json();
    if (json.status !== 'success') throw new Error((json.error && (json.error.message || json.error.code)) || tr('AllDebrid error', 'שגיאה ב-AllDebrid'));
    return json.data || {};
  }
  /** getUserMagnets(): every magnet on the account (/v4.1/magnet/status without an id). */
  async function getUserMagnets() {
    const data = await adPost(`${AD_V41}/magnet/status`, {});
    let list = data.magnets || [];
    if (!Array.isArray(list)) list = typeof list === 'object' ? Object.keys(list).map(k => list[k]) : [];
    return list.filter(o => o && typeof o === 'object').map(o => ({
      id: Number(o.id) || 0,
      filename: String(o.filename || ''),
      size: Number(o.size) || 0,
      status: String(o.status || ''),
      statusCode: Number(o.statusCode) || 0,
      downloaded: Number(o.downloaded) || 0,
      uploadDate: Number(o.uploadDate) || 0,
      nbLinks: Number(o.nbLinks) || 0,
    }));
  }
  /** extractFiles(): the file tree flattened to files that have a link. */
  function extractFiles(node, out = []) {
    if (Array.isArray(node)) { node.forEach(n => extractFiles(n, out)); return out; }
    if (node && typeof node === 'object') {
      if (Array.isArray(node.e)) node.e.forEach(n => extractFiles(n, out));
      const link = node.l || node.link || '';
      if (link) out.push({ name: String(node.n || node.filename || ''), size: Number(node.s || node.size) || 0, link });
    } else if (typeof node === 'string' && node) {
      out.push({ name: node.slice(node.lastIndexOf('/') + 1), size: 0, link: node });
    }
    return out;
  }
  async function getMagnetFiles(id) {
    const files = await AD().getMagnetFiles(id);
    return extractFiles(files);
  }
  const deleteMagnet = id => adPost(`${AD_V4}/magnet/delete`, { id });

  /* ── Grid geometry: LazyVerticalGrid(Adaptive(142), padding 48/8/48/48, spacing 16 × 20) ── */
  const PAD_X = 48, PAD_TOP = 8, PAD_BOTTOM = 48, HGAP = 16, VGAP = 20, MIN_CELL = 142;
  const AVAIL = TV.W - 2 * PAD_X;
  const COLS = Math.max(1, Math.floor((AVAIL + HGAP) / (MIN_CELL + HGAP)));
  const CW = (AVAIL - (COLS - 1) * HGAP) / COLS;
  const CH = CW * 1.5; // aspectRatio(2/3)
  // Header 36 + 44 + 12 (taller in Hebrew); hero and grid share the rest 0.65 : 1.22.
  const GRID_H = (TV.H - 92) * 1.22 / 1.87;
  /** Android TV's PivotBringIntoViewSpec: the focused item's leading edge at 30% of the viewport. */
  const pivot = (top, size, viewH, contentH) => {
    let target = 0.3 * viewH;
    if (size <= viewH && viewH - target < size) target = viewH - size;
    return Math.max(0, Math.min(Math.max(0, contentH - viewH), top - target));
  };

  function debrid() {
    const root = el(`
      <div class="screen dbc">
        <div class="dbc-bd"><img class="dbc-bdi" alt=""><img class="dbc-bdi" alt=""><div class="dbc-bdg"></div></div>
        <div class="dbc-col">
        <div class="dbc-head">
          <div class="dbc-ib dbc-back">${TV.icon('back', 22)}</div>
          <div class="dbc-logo">${TV.icon('cloud', 24)}</div>
          <div class="dbc-tt">
            <div class="dbc-tr"><span class="dbc-title">${tr('AllDebrid Cloud', 'ספריית ענן AllDebrid')}</span><span class="dbc-count hidden"></span></div>
            <div class="dbc-sub">${tr('Content uploaded from computer / browser', 'התוכן שהעלת מהמחשב או הדפדפן')}</div>
          </div>
          <div class="dbc-flex"></div>
          <div class="dbc-ib dbc-refresh">${ic('refresh', 22)}</div>
        </div>
        <div class="dbc-hero"></div>
        <div class="dbc-grid"><div class="dbc-gin"></div><div class="dbc-gmsg"></div></div>
        </div>
        <div class="dbc-busy hidden"><div class="dbc-busy-box">${spinner(3)}<div class="dbc-busy-t"></div></div></div>
        <div class="dbc-errw hidden"><div class="dbc-err"></div></div>
        <div class="dbc-dl"></div>
      </div>`);
    const $ = s => TV.$(root, s);
    const backEl = $('.dbc-back');
    const refreshEl = $('.dbc-refresh');
    const countEl = $('.dbc-count');
    const heroEl = $('.dbc-hero');
    const gridEl = $('.dbc-grid');
    const ginEl = $('.dbc-gin');
    const gmsgEl = $('.dbc-gmsg');
    const busyEl = $('.dbc-busy');
    const errWrap = $('.dbc-errw');
    const errEl = $('.dbc-err');
    const dlEl = $('.dbc-dl');
    const bdImgs = [...root.querySelectorAll('.dbc-bdi')];

    const st = {
      loading: true, notAuth: false, items: [], error: null,
      zone: 'back', idx: 0, scroll: 0, hero: null,
      resolving: false, dlg: null, gen: 0, op: 0, alive: true,
    };
    const tmdbCache = new Map();
    /** The grid viewport's height (measured: the header is taller in Hebrew). */
    const gridH = () => gridEl.clientHeight || GRID_H;

    /* ── Load (loadTorrents) ── */
    async function load(isRefresh) {
      const gen = ++st.gen;
      if (!adToken()) {
        Object.assign(st, { loading: false, notAuth: true, error: null, items: [] });
        renderAll();
        return;
      }
      st.error = null;
      if (!isRefresh) { st.loading = true; st.notAuth = false; }
      renderAll();
      const hadItems = st.items.length > 0;
      try {
        const raw = await getUserMagnets();
        if (gen !== st.gen || !st.alive) return;
        // Ready (4) first, then newest upload first.
        raw.sort((a, b) => ((b.statusCode === 4) - (a.statusCode === 4)) || (b.uploadDate - a.uploadDate));
        const old = new Map(st.items.map(i => [i.id, i]));
        st.items = raw.map(m => {
          const title = cleanTorrentTitle(m.filename);
          const percent = m.statusCode === 4 ? 100
            : m.size > 0 ? Math.max(0, Math.min(99, Math.floor((m.downloaded / m.size) * 100))) : 0;
          const prev = old.get(m.id);
          return {
            id: m.id, rawFilename: m.filename, displayTitle: title, cleanSearchTitle: cleanSearchQuery(title),
            sizeFormatted: formatBytes(m.size), status: m.status, statusCode: m.statusCode,
            progressPercent: percent, uploadDateFormatted: formatTimestamp(m.uploadDate), nbLinks: m.nbLinks,
            posterUrl: prev ? prev.posterUrl : null, backdropUrl: prev ? prev.backdropUrl : null,
            overview: prev ? prev.overview : null, rating: prev ? prev.rating : 0, year: prev ? prev.year : '',
          };
        });
        Object.assign(st, { loading: false, notAuth: false, error: null });
        // LaunchedEffect(items.isNotEmpty()): focus moves to the grid when items appear, to Back when they go.
        const has = st.items.length > 0;
        if (has && !hadItems) { st.zone = 'grid'; st.idx = 0; st.scroll = 0; }
        else if (!has && hadItems) st.zone = 'back';
        else if (st.zone === 'grid') {
          const keep = st.hero ? st.items.findIndex(i => i.id === st.hero.id) : -1;
          st.idx = keep >= 0 ? keep : Math.min(st.idx, st.items.length - 1);
        }
        st.idx = Math.max(0, Math.min(st.idx, st.items.length - 1));
        if (st.hero) st.hero = st.items.find(i => i.id === st.hero.id) || st.hero;
        if (!st.hero && has) st.hero = st.items[0];
        renderAll();
        if (st.zone === 'grid') focusGrid(st.idx);
        enrich(gen);
      } catch (e) {
        if (gen !== st.gen || !st.alive) return;
        st.loading = false;
        st.error = tr(`Failed to load AllDebrid library: ${e.message}`, `שגיאה בטעינת הענן מ-AllDebrid: ${e.message}`);
        renderAll();
      }
    }

    /** enrichWithTmdb(): posters, backdrops and overviews, one search after another. */
    async function enrich(gen) {
      for (const item of st.items.slice()) {
        if (gen !== st.gen || !st.alive) return;
        const q = item.cleanSearchTitle;
        if (!q.trim()) continue;
        let meta = tmdbCache.get(q);
        if (meta === undefined) {
          try {
            const data = await TV.tmdb('/search/multi', { query: q, page: 1 });
            const r = (data.results || []).find(x => (x.media_type === 'movie' || x.media_type === 'tv') && x.poster_path);
            if (r) {
              const poster = `${IMG}/w500${r.poster_path}`;
              const back = r.backdrop_path ? `${IMG}/original${r.backdrop_path}` : poster;
              const date = r.media_type === 'tv' ? r.first_air_date : r.release_date;
              meta = {
                posterUrl: poster,
                backdropUrl: back.replace(/\/w\d+\//, '/original/'),
                overview: String(r.overview || '').trim() ? r.overview : null,
                rating: Number(r.vote_average) || 0,
                year: date ? String(date).slice(0, 4) : '',
              };
              tmdbCache.set(q, meta);
            } else meta = null;
          } catch { meta = null; }
        }
        if (gen !== st.gen || !st.alive) return;
        if (!meta) continue;
        Object.assign(item, meta);
        const i = st.items.indexOf(item);
        if (i >= 0) updateCard(i);
        if (st.hero === item) renderHero();
      }
    }

    /* ── Rendering ── */
    function renderAll() {
      countEl.classList.toggle('hidden', !st.items.length);
      countEl.textContent = TV.rtl() ? `${st.items.length} טורנטים` : `${st.items.length} Torrents`;
      renderGrid();
      renderHero();
      renderBusy();
      renderError();
      paintFocus();
    }

    function currentItem() {
      if (!st.hero) return st.items[0] || null;
      return st.items.find(i => i.id === st.hero.id) || st.hero;
    }

    /* Backdrop (CloudBackdropLayer): 400 ms crossfade between the focused item's art. */
    let bdUrl, bdTok = 0, bdFront = 0;
    function setBackdrop(url) {
      if (url === bdUrl) return;
      bdUrl = url;
      const t = ++bdTok;
      if (!url) { bdImgs.forEach(i => i.classList.remove('on')); return; }
      TV.decode(url).then(ok => {
        if (t !== bdTok || !st.alive) return;
        const cur = bdImgs[bdFront];
        const next = bdImgs[1 - bdFront];
        cur.classList.remove('on');
        if (!ok) return;
        next.src = ok;
        next.classList.add('on');
        bdFront = 1 - bdFront;
      });
    }

    function badge(text, bg, color, icon) {
      return `<span class="dbc-badge" style="background:${bg};color:${color}">${icon ? ic(icon, 13) : ''}<span>${esc(text)}</span></span>`;
    }
    function renderHero() {
      const item = currentItem();
      const showBd = !!item && !st.notAuth;
      root.classList.toggle('has-bd', showBd);
      setBackdrop(showBd ? ((item.backdropUrl || item.posterUrl || '').replace(/\/w\d+\//, '/original/') || null) : null);
      if (st.notAuth) {
        heroEl.innerHTML = `
          <div class="dbc-ua">
            <div class="dbc-ua-t">${tr('Connect AllDebrid', 'חבר חשבון AllDebrid')}</div>
            <div class="dbc-ua-d">${tr('You need to link your AllDebrid account to browse and play your cloud torrents directly on LuminaStreams.', 'עליך לחבר את חשבון ה-AllDebrid שלך כדי לצפות בטורנטים שהעלת ישירות מהאפליקציה בטלוויזיה.')}</div>
            <div class="dbc-connect"><span>${tr('Connect Now', 'התחבר עכשיו')}</span></div>
          </div>`;
        TV.pointer(TV.$(heroEl, '.dbc-connect'), () => { st.zone = 'connect'; paintFocus(); }, () => TV.open('settings'));
        return;
      }
      if (!item) { heroEl.innerHTML = ''; return; }
      const badges = [];
      if (item.statusCode === 4) badges.push(badge(tr('Ready to Stream', 'מוכן לצפייה'), 'rgba(16,185,129,0.2)', '#10B981', 'checkCircle'));
      else if (item.statusCode >= 1 && item.statusCode <= 3) badges.push(badge(`${tr('Downloading', 'בהורדה')} ${item.progressPercent}%`, 'rgba(245,158,11,0.2)', '#F59E0B', 'download'));
      else badges.push(badge(item.status || tr('Pending', 'בהמתנה'), 'rgba(239,68,68,0.2)', '#EF4444', 'warning'));
      badges.push(badge(item.sizeFormatted, 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0.9)'));
      if (item.nbLinks > 1) badges.push(badge(TV.rtl() ? `${item.nbLinks} קבצים / פרקים` : `${item.nbLinks} Files / Episodes`, 'rgba(0,229,255,0.15)', '#00E5FF', 'videoLibrary'));
      if (item.uploadDateFormatted) badges.push(badge(item.uploadDateFormatted, 'rgba(255,255,255,0.08)', 'rgba(255,255,255,0.6)'));
      if (item.rating > 0) badges.push(`<span class="dbc-imdb"><b>IMDb</b><i>${item.rating.toFixed(1)}</i></span>`);
      const len = item.displayTitle.length;
      const size = len > 30 ? 28 : len > 18 ? 34 : 40;
      heroEl.innerHTML = `
        <div class="dbc-hi">
          <div class="dbc-badges">${badges.join('')}</div>
          <div class="dbc-ht" style="font-size:${size}px">${esc(item.displayTitle)}</div>
          <div class="dbc-hf">${esc(item.rawFilename)}</div>
          ${item.overview ? `<div class="dbc-ho">${esc(item.overview)}</div>` : ''}
        </div>`;
    }

    function pill(item) {
      if (item.statusCode === 4) return `<span class="dbc-pill ok">${ic('checkCircle', 10)}<span>${tr('READY', 'מוכן')}</span></span>`;
      if (item.statusCode >= 1 && item.statusCode <= 3) return `<span class="dbc-pill dl">${ic('download', 10)}<span>${item.progressPercent}%</span></span>`;
      return `<span class="dbc-pill er">${ic('warning', 10)}<span>${tr('ERROR', 'שגיאה')}</span></span>`;
    }
    function cardInner(item) {
      const img = item.posterUrl || item.backdropUrl;
      const end = item.nbLinks > 1
        ? `<span class="dbc-multi">${ic('videoLibrary', 10)}<span>${item.nbLinks}</span></span>`
        : item.rating > 0 ? `<span class="dbc-star"><b>★</b><span>${item.rating.toFixed(1)}</span></span>` : '';
      const meta = item.year ? `<span class="dbc-yr">${esc(item.year)}</span>`
        : item.uploadDateFormatted ? `<span class="dbc-dt">${esc(item.uploadDateFormatted)}</span>` : '';
      return `<i class="dbc-glow"></i>
        <div class="dbc-surf">
          ${img ? `<img class="dbc-pi" data-src="${esc(img)}" alt="" decoding="async">` : `<div class="dbc-fb">${TV.icon('cloud', 48)}</div>`}
          <div class="dbc-gt"></div><div class="dbc-gb"></div>
          <div class="dbc-tb">${pill(item)}${end}</div>
          <div class="dbc-cb"><div class="dbc-ct">${esc(item.displayTitle)}</div><div class="dbc-cm"><span class="dbc-sz">${esc(item.sizeFormatted)}</span>${meta}</div></div>
          <i class="dbc-ring"></i>
        </div>`;
    }
    const contentH = () => {
      const rows = Math.ceil(st.items.length / COLS);
      return PAD_TOP + rows * CH + Math.max(0, rows - 1) * VGAP + PAD_BOTTOM;
    };
    function renderGrid() {
      ginEl.innerHTML = '';
      gmsgEl.innerHTML = '';
      gmsgEl.className = 'dbc-gmsg';
      if (st.loading) {
        gmsgEl.classList.add('load');
        gmsgEl.innerHTML = `<div>${spinner(4)}<div class="dbc-lt">${tr('Loading your AllDebrid library...', 'טוען את ספריית AllDebrid שלך...')}</div></div>`;
        return;
      }
      if (st.notAuth) return;
      if (!st.items.length) {
        gmsgEl.classList.add('empty');
        gmsgEl.innerHTML = `<div class="dbc-ee">☁️</div><div class="dbc-et">${tr('No Torrents In AllDebrid', 'אין טורנטים בחשבון AllDebrid שלך')}</div>
          <div class="dbc-ed">${tr('Torrents you upload from your computer to AllDebrid will appear here automatically.', 'טורנטים שתעלה ל-AllDebrid מהמחשב יופיעו כאן ויהיו מוכנים להפעלה ישירה.')}</div>`;
        return;
      }
      const side = TV.rtl() ? 'right' : 'left';
      const html = st.items.map((item, i) => {
        const col = i % COLS;
        const row = Math.floor(i / COLS);
        return `<div class="dbc-card" style="${side}:${(PAD_X + col * (CW + HGAP)).toFixed(2)}px;top:${(PAD_TOP + row * (CH + VGAP)).toFixed(2)}px;width:${CW.toFixed(2)}px;height:${CH.toFixed(2)}px">${cardInner(item)}</div>`;
      }).join('');
      ginEl.innerHTML = html;
      ginEl.style.height = `${contentH().toFixed(2)}px`;
      st.scroll = Math.min(st.scroll, Math.max(0, contentH() - gridH()));
      ginEl.style.transform = `translateY(${-st.scroll}px)`;
      [...ginEl.children].forEach((c, i) => TV.pointer(c,
        () => { focusGrid(i); },
        () => { focusGrid(i); onTorrentClick(st.items[i]); }));
      loadImages();
    }
    function updateCard(i) {
      const c = ginEl.children[i];
      if (!c) return;
      c.innerHTML = cardInner(st.items[i]);
      loadImages();
    }
    /** Only cards near the viewport get their poster (Compose's lazy grid composes the visible ones). */
    function loadImages() {
      const pitch = CH + VGAP;
      const first = Math.max(0, Math.floor((st.scroll - PAD_TOP) / pitch) - 1);
      const last = Math.ceil((st.scroll + gridH()) / pitch) + 1;
      const cards = ginEl.children;
      for (let r = first; r <= last; r++) {
        for (let c = 0; c < COLS; c++) {
          const card = cards[r * COLS + c];
          const im = card && card.querySelector('img[data-src]');
          if (im) { im.src = im.getAttribute('data-src'); im.removeAttribute('data-src'); }
        }
      }
    }

    function renderBusy() {
      busyEl.classList.toggle('hidden', !st.resolving);
      if (st.resolving) TV.$(busyEl, '.dbc-busy-t').textContent = st.resolvingMsg || tr('Loading stream...', 'טוען קישור להזרמה...');
    }
    function renderError() {
      errWrap.classList.toggle('hidden', !st.error);
      errEl.textContent = st.error || '';
    }

    /* ── Focus ── */
    function paintFocus() {
      backEl.classList.toggle('f', st.zone === 'back');
      refreshEl.classList.toggle('f', st.zone === 'refresh');
      const connect = TV.$(heroEl, '.dbc-connect');
      if (connect) connect.classList.toggle('f', st.zone === 'connect');
      const cards = ginEl.children;
      for (let i = 0; i < cards.length; i++) cards[i].classList.toggle('f', st.zone === 'grid' && i === st.idx);
    }
    function focusGrid(i) {
      if (!st.items.length) return;
      st.zone = 'grid';
      st.idx = Math.max(0, Math.min(st.items.length - 1, i));
      const top = PAD_TOP + Math.floor(st.idx / COLS) * (CH + VGAP);
      const s = pivot(top, CH, gridH(), contentH());
      if (s !== st.scroll) {
        st.scroll = s;
        ginEl.style.transform = `translateY(${-s}px)`;
        loadImages();
      }
      const item = st.items[st.idx];
      if (st.hero !== item) { st.hero = item; renderHero(); }
      paintFocus();
    }
    function enterFromHeader() {
      if (st.items.length && !st.loading && !st.notAuth) { focusGrid(st.idx); return; } // focusRestorer
      if (st.notAuth) { st.zone = 'connect'; paintFocus(); }
    }

    TV.pointer(backEl, () => { st.zone = 'back'; paintFocus(); }, () => TV.back());
    TV.pointer(refreshEl, () => { st.zone = 'refresh'; paintFocus(); }, () => { st.zone = 'refresh'; paintFocus(); load(true); });

    /* ── Actions (handleTorrentClick / unlockAndPlay / deleteTorrent) ── */
    function setBusy(on, msg) {
      st.resolving = on;
      st.resolvingMsg = msg || '';
      renderBusy();
    }
    function showError(msg) { st.error = msg; renderError(); }

    async function onTorrentClick(item) {
      if (!item || st.resolving) return;
      if (item.statusCode !== 4) {
        showError(item.statusCode >= 1 && item.statusCode <= 3
          ? tr(`Torrent is still downloading on AllDebrid (${item.progressPercent}%)`, `הטורנט עדיין בהורדה ב-AllDebrid (${item.progressPercent}%)`)
          : tr(`Torrent is not ready (${item.status})`, `הטורנט אינו מוכן לצפייה (${item.status})`));
        return;
      }
      if (!adToken()) return;
      const op = ++st.op;
      st.error = null;
      renderError();
      setBusy(true, tr('Inspecting files...', 'בודק קבצים בטורנט...'));
      try {
        const all = await getMagnetFiles(item.id);
        if (op !== st.op || !st.alive) return;
        const videos = all.filter(f => {
          const n = f.name.toLowerCase();
          const dot = n.lastIndexOf('.');
          return dot >= 0 && VIDEO_EXT.indexOf(n.slice(dot + 1)) >= 0 && n.indexOf('sample') < 0;
        });
        if (!videos.length) {
          setBusy(false);
          showError(tr('No video files found in this torrent', 'לא נמצאו קבצי וידאו בטורנט זה'));
        } else if (videos.length === 1) {
          unlockAndPlay(videos[0], item);
        } else {
          setBusy(false);
          openFiles(item, videos);
        }
      } catch (e) {
        if (op !== st.op || !st.alive) return;
        setBusy(false);
        showError(tr(`Failed to open torrent: ${e.message}`, `שגיאה בפתיחת הטורנט: ${e.message}`));
      }
    }

    async function unlockAndPlay(file, torrent) {
      if (!adToken()) return;
      const op = ++st.op;
      st.error = null;
      renderError();
      setBusy(true, tr('Unlocking direct stream...', 'פותח קישור להזרמה...'));
      try {
        const url = await AD().unlockLink(file.link);
        if (op !== st.op || !st.alive) return;
        if (!url) throw new Error('no link');
        setBusy(false);
        closeFiles();
        const title = cleanTorrentTitle(file.name) || torrent.displayTitle;
        TV.play({ url, title, showTitle: title, type: 'movie', backdrop: torrent.backdropUrl || '', poster: torrent.posterUrl || '' });
      } catch (e) {
        if (op !== st.op || !st.alive) return;
        setBusy(false);
        showError(tr(`Failed to unlock stream: ${e.message}`, `שגיאה בפתיחת הקישור: ${e.message}`));
      }
    }

    async function deleteTorrent(id) {
      if (!adToken() || st.resolving) return;
      const op = ++st.op;
      setBusy(true, tr('Deleting from AllDebrid...', 'מוחק מ-AllDebrid...'));
      try {
        await deleteMagnet(id);
        if (op !== st.op || !st.alive) return;
        setBusy(false);
        closeFiles();
        const at = st.items.findIndex(i => i.id === id);
        st.items = st.items.filter(i => i.id !== id);
        countEl.classList.toggle('hidden', !st.items.length);
        countEl.textContent = TV.rtl() ? `${st.items.length} טורנטים` : `${st.items.length} Torrents`;
        if (!st.items.length) { st.zone = 'back'; st.hero = null; }
        else if (st.zone === 'grid' || at >= 0) { st.idx = Math.min(st.idx, st.items.length - 1); st.hero = st.items[st.idx]; }
        renderGrid();
        renderHero();
        if (st.zone === 'grid') focusGrid(st.idx); else paintFocus();
      } catch (e) {
        if (op !== st.op || !st.alive) return;
        setBusy(false);
        showError(tr(`Failed to delete torrent: ${e.message}`, `שגיאה במחיקת טורנט: ${e.message}`));
      }
    }

    /* ── MultiFilesDialog ── */
    function openFiles(torrent, files) {
      const box = el(`
        <div class="dbc-scrim">
          <div class="dbc-dlg">
            <div class="dbc-dh">
              <div class="dbc-dht">
                <div class="dbc-dtitle">${esc(torrent.displayTitle)}</div>
                <div class="dbc-dsub">${tr('Select episode / file to play', 'בחר פרק / קובץ להפעלה')}</div>
              </div>
              <div class="dbc-btn dbc-del">${ic('delete', 16)}<span>${tr('Delete', 'מחק')}</span></div>
            </div>
            <div class="dbc-dlist"><div class="dbc-dlin"></div></div>
            <div class="dbc-dfoot"><div class="dbc-btn dbc-close"><span>${tr('Close', 'סגור')}</span></div></div>
          </div>
        </div>`);
      const listEl = TV.$(box, '.dbc-dlist');
      const linEl = TV.$(box, '.dbc-dlin');
      const delEl = TV.$(box, '.dbc-del');
      const closeEl = TV.$(box, '.dbc-close');
      if (!files.length) {
        linEl.innerHTML = `<div class="dbc-dmsg">${tr('No video files found', 'לא נמצאו קבצי וידאו בטורנט זה')}</div>`;
      } else {
        linEl.innerHTML = files.map(f => {
          const clean = cleanTorrentTitle(f.name);
          return `<div class="dbc-frow">
            <span class="dbc-fic">${ic('playCircle', 20)}</span>
            <div class="dbc-ftx"><div class="dbc-fn">${esc(clean.trim() ? clean : f.name)}</div><div class="dbc-fr">${esc(f.name)}</div></div>
            <span class="dbc-fs">${esc(formatBytes(f.size))}</span>
          </div>`;
        }).join('');
      }
      const rows = [...linEl.querySelectorAll('.dbc-frow')];
      const d = { torrent, files, zone: files.length ? 'files' : 'close', idx: 0, scroll: 0, el: box };
      st.dlg = d;

      const viewH = () => listEl.clientHeight || 351;
      const contentHeight = () => linEl.scrollHeight || 0;
      function paintDlg() {
        delEl.classList.toggle('f', d.zone === 'delete');
        closeEl.classList.toggle('f', d.zone === 'close');
        rows.forEach((r, i) => r.classList.toggle('f', d.zone === 'files' && i === d.idx));
        if (d.zone === 'files' && rows[d.idx]) {
          const r = rows[d.idx];
          const s = pivot(r.offsetTop, r.offsetHeight, viewH(), contentHeight());
          if (s !== d.scroll) { d.scroll = s; linEl.style.transform = `translateY(${-s}px)`; }
        }
      }
      /** Compose's 2D focus search from the buttons lands on the nearest visible row. */
      function visibleRow(fromTop) {
        const top = d.scroll;
        const bottom = d.scroll + viewH();
        const vis = rows.map((r, i) => [r, i]).filter(([r]) => r.offsetTop + r.offsetHeight > top && r.offsetTop < bottom);
        if (!vis.length) return 0;
        return fromTop ? vis[0][1] : vis[vis.length - 1][1];
      }
      d.paint = paintDlg;
      d.onKey = key => {
        if (key === 'back') { closeFiles(); return; }
        if (d.zone === 'files') {
          if (key === 'down') { if (d.idx < rows.length - 1) d.idx++; else d.zone = 'close'; paintDlg(); }
          else if (key === 'up') { if (d.idx > 0) d.idx--; else d.zone = 'delete'; paintDlg(); }
          else if (key === 'ok') unlockAndPlay(files[d.idx], torrent);
          return;
        }
        if (d.zone === 'delete') {
          if (key === 'down') { if (rows.length) { d.zone = 'files'; d.idx = visibleRow(true); } else d.zone = 'close'; paintDlg(); }
          else if (key === 'ok') deleteTorrent(torrent.id);
          return;
        }
        // close
        if (key === 'up') { if (rows.length) { d.zone = 'files'; d.idx = visibleRow(false); } else d.zone = 'delete'; paintDlg(); }
        else if (key === 'ok') closeFiles();
      };
      rows.forEach((r, i) => TV.pointer(r,
        () => { d.zone = 'files'; d.idx = i; paintDlg(); },
        () => { d.zone = 'files'; d.idx = i; paintDlg(); unlockAndPlay(files[i], torrent); }));
      TV.pointer(delEl, () => { d.zone = 'delete'; paintDlg(); }, () => { d.zone = 'delete'; paintDlg(); deleteTorrent(torrent.id); });
      TV.pointer(closeEl, () => { d.zone = 'close'; paintDlg(); }, () => closeFiles());

      dlEl.innerHTML = '';
      dlEl.appendChild(box);
      requestAnimationFrame(() => box.classList.add('on'));
      paintDlg();
    }
    function closeFiles() {
      if (!st.dlg) return;
      st.dlg = null;
      dlEl.innerHTML = '';
      paintFocus();
    }

    /* ── Keys ── */
    function onKey(key) {
      if (st.dlg) { st.dlg.onKey(key); return true; }
      if (key === 'back') return false; // BackHandler → previous screen
      if (st.resolving && key !== 'up' && key !== 'down' && !TV.fwd(key) && !TV.bwd(key)) return true;
      if (st.zone === 'grid') {
        const n = st.items.length;
        const i = st.idx;
        if (TV.fwd(key)) { if ((i + 1) % COLS && i + 1 < n) focusGrid(i + 1); return true; }
        if (TV.bwd(key)) { if (i % COLS) focusGrid(i - 1); return true; }
        if (key === 'down') {
          if (i + COLS < n) focusGrid(i + COLS);
          else if (Math.floor(i / COLS) < Math.floor((n - 1) / COLS)) focusGrid(n - 1);
          return true;
        }
        if (key === 'up') {
          // Android: Up while the grid sits at its very top goes to the Back button.
          if (st.scroll <= 0) { st.zone = 'back'; paintFocus(); } else if (i - COLS >= 0) focusGrid(i - COLS);
          return true;
        }
        if (key === 'ok') { onTorrentClick(st.items[i]); return true; }
        return true;
      }
      if (st.zone === 'back') {
        if (TV.fwd(key)) { st.zone = 'refresh'; paintFocus(); return true; }
        if (key === 'down') { enterFromHeader(); return true; }
        if (key === 'ok') { TV.back(); return true; }
        return true;
      }
      if (st.zone === 'refresh') {
        if (TV.bwd(key)) { st.zone = 'back'; paintFocus(); return true; }
        if (key === 'down') { enterFromHeader(); return true; }
        if (key === 'ok') { load(true); return true; }
        return true;
      }
      if (st.zone === 'connect') {
        if (key === 'up') { st.zone = 'back'; paintFocus(); return true; }
        if (TV.fwd(key)) { st.zone = 'refresh'; paintFocus(); return true; }
        if (key === 'ok') { TV.open('settings'); return true; }
        return true;
      }
      return true;
    }

    renderAll();
    load(false);

    return {
      el: root,
      onKey,
      onShow(first) {
        // Back from Settings after connecting AllDebrid: load the library.
        if (!first && st.notAuth && adToken()) load(false);
      },
      destroy() {
        st.alive = false;
        st.gen++;
        st.op++;
      },
    };
  }

  TV.register('debrid', debrid);
})();
