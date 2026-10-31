/**
 * LuminaStreams TV — Details (1:1 with the Android TV DetailsScreen, which runs at
 * 0.85 density: laid out on a 2007.84×1129.41 dp canvas scaled to the screen).
 * Includes the sources dialog (filters + DetailedSourceRow) and the season popup.
 */
(() => {
  'use strict';
  const { tr, esc, icon, el, img } = TV;

  const GOLD = '#FFD700';
  const TRACKER_COLORS = { rutracker: '#7C3AED', rutor: '#F97316', yts: '#22C55E', rarbg: '#EF4444', torrentgalaxy: '#3B82F6', '1337x': '#E879F9', thepiratebay: '#EAB308', tpb: '#EAB308', eztv: '#06B6D4', nyaa: '#F472B6' };
  const LANGS = { rus: ['RU', 'Русский', '#5C7CFA'], eng: ['EN', 'English', '#69DB7C'], heb: ['HE', 'עברית', '#748FFC'] };

  /** Torrentio stream → Android's AdvancedStreamSource fields. */
  function describe(s) {
    const text = `${s.title || ''} ${s.name || ''}`;
    const up = text.toUpperCase().replace(/\./g, ' ');
    const tracker = ((/⚙️\s*([^\n]+)/.exec(s.title || '') || [])[1] || '').trim() || 'Torrentio';
    const seeders = (/👤\s*(\d+)/.exec(s.title || '') || [])[1] || String(s.seeders || 0);
    const leechers = (/🏃\s*(\d+)/.exec(s.title || '') || [])[1] || '0';
    const is4k = /4K|2160P/.test(up) || s.quality === '4K';
    const is1080 = /1080P/.test(up) || s.quality === '1080p';
    const isDv = / DV |DOVI|DOLBY VISION|\bDV\b/.test(up) || s.hdr === 'DV';
    const isHdr = / HDR|HDR10/.test(up) || (s.hdr && s.hdr !== 'DV');
    const isAtmos = /ATMOS/.test(up);
    return {
      tracker,
      group: (s.group || '').toUpperCase() || 'UNKNOWN',
      filename: s.filename || s.title || '',
      quality: is4k ? '4K' : is1080 ? '1080p' : 'HD',
      isDv, isHdr,
      audio: isAtmos ? 'ATMOS' : /7 1/.test(up) ? '7.1' : /5 1/.test(up) ? '5.1' : 'STEREO',
      langs: (s.languages || []).filter(l => LANGS[l]),
      size: s.sizeBytes ? (s.sizeBytes >= 1073741824 ? `${(s.sizeBytes / 1073741824).toFixed(1)} GB` : `${Math.round(s.sizeBytes / 1048576)} MB`) : (s.size || ''),
      seeders, leechers,
    };
  }

  const EXTRA_ICONS = {
    schedule: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
    bell: 'M7.58 4.08L6.15 2.65C3.75 4.48 2.17 7.3 2.03 10.5h2c.15-2.65 1.51-4.97 3.55-6.42zm12.39 6.42h2c-.15-3.2-1.73-6.02-4.12-7.85l-1.42 1.43c2.02 1.45 3.39 3.77 3.54 6.42zM18 11c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2v-5zm-6 11c.14 0 .27-.01.4-.04.65-.14 1.18-.58 1.44-1.18.1-.24.15-.5.15-.78h-4c.01 1.1.9 2 2.01 2z',
    verified: 'M23 12l-2.44-2.79.34-3.69-3.61-.82-1.89-3.2L12 2.96 8.6 1.5 6.71 4.69 3.1 5.5l.34 3.7L1 12l2.44 2.79-.34 3.7 3.61.82L8.6 22.5l3.4-1.47 3.4 1.46 1.89-3.19 3.61-.82-.34-3.69L23 12zm-12.91 4.72l-3.8-3.81 1.48-1.48 2.32 2.33 5.85-5.87 1.48 1.48-7.33 7.35z',
    checkCircle: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  };
  const xicon = (name, size) => `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24"><path d="${EXTRA_ICONS[name]}"/></svg>`;

  /* ── Series release state (SeriesReleaseInfo.kt + NextEpisodeStatusCard.kt) ── */
  const DAY = 86400000;
  const today0 = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const parseDay = iso => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || ''); return m ? new Date(+m[1], +m[2] - 1, +m[3]).getTime() : null; };
  function countdownHeadline(days, he) {
    if (days == null) return he ? 'בקרוב' : 'SOON';
    if (days <= 0) return he ? 'עכשיו' : 'NOW';
    if (days === 1) return he ? 'מחר' : 'TOMORROW';
    if (days < 7) return he ? `בעוד ${days} ימים` : `IN ${days} DAYS`;
    if (days < 14) return he ? 'בעוד שבוע' : 'IN 1 WEEK';
    if (days < 31) return he ? `בעוד ${Math.floor(days / 7)} שבועות` : `IN ${Math.floor(days / 7)} WEEKS`;
    if (days < 365) { const m = Math.max(1, Math.floor(days / 30)); return he ? `בעוד ${m} חודשים` : `IN ${m} MONTHS`; }
    return he ? 'בעוד שנה+' : 'IN 1+ YEAR';
  }
  function airDateLine(ms, he) {
    if (ms == null) return '';
    const d = new Date(ms);
    const loc = he ? 'he' : 'en';
    return `${d.toLocaleDateString(loc, { weekday: 'short' })} · ${d.toLocaleDateString(loc, { month: 'short' })} ${d.getDate()}`;
  }
  /** "AIRS Mar 14" pill for an episode that hasn't aired (airsPillText). */
  function airsPillText(iso, he) {
    const ms = parseDay(iso);
    if (ms == null) return he ? 'טרם שודר' : 'NOT AIRED';
    const d = new Date(ms);
    return `${he ? 'משודר' : 'AIRS'} ${d.toLocaleDateString(he ? 'he' : 'en', { month: 'short' })} ${d.getDate()}`;
  }
  function releaseStrip(d) {
    const he = TV.rtl();
    const next = d.next_episode_to_air;
    const nextMs = next ? parseDay(next.air_date) : null;
    const days = nextMs != null ? Math.round((nextMs - today0()) / DAY) : null;
    const lastMs = d.last_episode_to_air ? parseDay(d.last_episode_to_air.air_date) : null;
    const lastAgo = lastMs != null ? Math.round((today0() - lastMs) / DAY) : null;
    const seasons = d.number_of_seasons || 0;
    let st;
    if (next) st = days == null ? 'RETURNING' : days > 0 ? 'SOON' : days >= -3 ? 'NOW' : 'RETURNING';
    else if (lastAgo != null && lastAgo >= 0 && lastAgo <= 3) st = 'NOW';
    else {
      const t = String(d.status || '').trim().toLowerCase();
      st = t === 'ended' ? 'ENDED' : (t === 'canceled' || t === 'cancelled') ? 'CANCELED'
        : ['returning series', 'in production', 'planned', 'pilot'].includes(t) ? 'RETURNING' : 'UNKNOWN';
    }
    if (st === 'UNKNOWN') return '';
    const label = next ? `S${next.season_number} · E${next.episode_number}` : '';
    const V = {
      SOON: ['#E2B258', he ? 'פרק חדש' : 'NEXT EPISODE', countdownHeadline(days, he), [label, airDateLine(nextMs, he)].filter(Boolean).join('   ·   '), 'schedule', true],
      NOW: ['#32D74B', he ? 'פרק חדש זמין' : 'NEW EPISODE OUT', he ? 'עכשיו' : 'AVAILABLE NOW', next ? `${label}   ·   ${next.name || ''}` : '', 'bell', true],
      RETURNING: ['#3B82F6', he ? 'מתחדש' : 'RENEWED', he ? 'טרם נקבע תאריך' : 'DATE TBA', he ? 'העונה הבאה בהפקה' : 'Next season in production', 'verified', false],
      ENDED: ['#8A8A93', he ? 'הסתיים' : 'SERIES COMPLETE', he ? 'כל הפרקים שודרו' : 'ALL EPISODES AIRED', seasons ? (he ? `${seasons} עונות` : `${seasons} seasons`) : '', 'checkCircle', false],
      CANCELED: ['#E5484D', he ? 'בוטלה' : 'CANCELED', seasons ? (he ? `הסתיים אחרי ${seasons} עונות` : `ENDED AFTER ${seasons} SEASONS`) : (he ? 'לא תחודש' : 'WILL NOT RETURN'), '', 'checkCircle', false],
    }[st];
    const [accent, lbl, headline, sub, ic, pulse] = V;
    const still = st === 'SOON' && next && next.still_path ? img(next.still_path, 'w300') : '';
    return `<div class="d-rel" style="--a:${accent}">
      <i class="d-rel-spine"></i><i class="d-rel-dot${pulse ? ' pulse' : ''}"></i>
      <div class="d-rel-tx"><div class="d-rel-l1">${xicon(ic, 16)}<b>${esc(lbl)}</b><span>${esc(headline)}</span></div>${sub ? `<div class="d-rel-sub" dir="auto">${esc(sub)}</div>` : ''}</div>
      ${still ? `<div class="d-rel-still"><img src="${still}" alt=""><span>${tr('SOON', 'בקרוב')}</span></div>` : ''}
    </div>`;
  }

  function launchTrailer(key, title) {
    if (!key) { TV.toast(tr('No trailer available', 'אין טריילר זמין')); return; }
    try {
      // The TV's own YouTube app (Android launches the native player too).
      const bridge = new window.PalmServiceBridge();
      bridge.call('luna://com.webos.applicationManager/launch', JSON.stringify({
        id: 'youtube.leanback.v4', params: { contentTarget: `https://www.youtube.com/tv?v=${key}` },
      }));
    } catch {
      TV.toast(tr(`Trailer: ${title}`, `טריילר: ${title}`));
    }
  }

  function details(params) {
    const type = params.type === 'series' ? 'tv' : params.type;
    const isSeries = type === 'tv';
    const root = el(`
      <div class="screen details">
        <div class="dcanvas">
          <div class="d-bd"><img class="bd-img" alt=""><div class="d-gh"></div><div class="d-gv"></div></div>
          <div class="d-main">
            <div class="d-back">${icon('back', 28)}</div>
            <div class="d-top">
              <div class="d-info"></div>
              <div class="d-poster-box"><div class="d-poster"></div></div>
            </div>
            <div class="d-cast"></div>
          </div>
          <div class="d-layer"></div>
        </div>
      </div>`);
    const canvas = TV.$(root, '.dcanvas');
    const infoEl = TV.$(root, '.d-info');
    const posterEl = TV.$(root, '.d-poster');
    const castEl = TV.$(root, '.d-cast');
    const layerEl = TV.$(root, '.d-layer');
    const backEl = TV.$(root, '.d-back');

    const s = { data: null, imdbId: null, zone: 'actions', idx: { actions: 0, seasons: 0, cast: 0 }, castScroll: { x: 0 }, seasonScroll: { x: 0 } };
    const WP = window.WatchProgress;

    /* ── Load ── */
    async function load() {
      const m = params.movie;
      if (m) paintBackdrop(m.backdrop);
      try {
        const d = await TV.tmdb(`/${type}/${params.id}`, {
          append_to_response: 'credits,videos,external_ids,images,release_dates,content_ratings',
          include_image_language: `${TV.lang()},en,null`,
        });
        s.data = d;
        s.imdbId = (d.external_ids && d.external_ids.imdb_id) || d.imdb_id || null;
        paintBackdrop(d.backdrop_path);
        render();
      } catch (e) {
        infoEl.innerHTML = `<div class="d-error">${tr('Could not load details', 'לא ניתן לטעון את הפרטים')}</div>`;
      }
    }
    function paintBackdrop(path) {
      const url = img(path, 'original');
      if (!url) return;
      const im = TV.$(root, '.d-bd .bd-img');
      TV.decode(url).then(ok => { if (ok) { im.src = ok; im.classList.add('on'); } });
    }

    function resumeTarget() {
      if (!s.imdbId || !WP) return { season: 1, episode: 1, inProgress: false };
      if (!isSeries) {
        const p = WP.get(WP.keyFor('movie', s.imdbId));
        return { inProgress: !!p && p.percentage >= 2 && p.percentage < 90 };
      }
      const last = WP.getLatestEpisode(s.imdbId);
      if (!last) return { season: 1, episode: 1, inProgress: false };
      if (last.percentage < 90) return { season: last.season, episode: last.episode, inProgress: last.percentage >= 2 };
      return { season: last.season, episode: last.episode + 1, inProgress: false };
    }
    const inList = () => !!(window.Storage && window.Storage.isInWatchlist(params.id, type));
    const ageRating = d => {
      if (isSeries) {
        const r = ((d.content_ratings && d.content_ratings.results) || []);
        const x = r.find(c => c.iso_3166_1 === 'IL') || r.find(c => c.iso_3166_1 === 'US');
        return x ? x.rating : '';
      }
      const r = ((d.release_dates && d.release_dates.results) || []);
      const x = r.find(c => c.iso_3166_1 === 'IL') || r.find(c => c.iso_3166_1 === 'US');
      return x ? ((x.release_dates || []).map(z => z.certification).find(Boolean) || '') : '';
    };
    const runtimeText = min => {
      if (!min) return '';
      const h = Math.floor(min / 60);
      const m = min % 60;
      return h > 0 ? `${h}${tr('h', 'שע')} ${m}${tr('m', 'ד')}` : `${m}${tr('m', 'ד')}`;
    };

    function render() {
      const d = s.data;
      const title = d.title || d.name || '';
      const logos = (d.images && d.images.logos) || [];
      const logo = logos.find(l => l.iso_639_1 === TV.lang()) || logos.find(l => l.iso_639_1 === 'en') || logos[0];
      const year = String(d.release_date || d.first_air_date || '').slice(0, 4);
      const runtime = runtimeText(d.runtime || (d.episode_run_time && d.episode_run_time[0]));
      const age = ageRating(d);
      const genres = (d.genres || []).slice(0, 2).map(g => g.name).join(' • ');
      const dot = '<span class="dot">•</span>';
      const meta = [];
      if (d.vote_average > 0) meta.push(`<span class="d-rt">${icon('star', 20)}<b>${Number(d.vote_average).toFixed(1)}</b></span>`);
      if (year) meta.push(`<span class="d-m">${year}</span>`);
      if (runtime) meta.push(`<span class="d-m">${runtime}</span>`);
      if (age) meta.push(`<span class="d-age">${esc(age.toUpperCase())}</span>`);
      if (genres) meta.push(`<span class="d-g">${esc(genres)}</span>`);
      const rt = resumeTarget();
      const watchText = isSeries
        ? (rt.inProgress ? `${tr('WATCH', 'צפה')} S${rt.season} E${rt.episode}` : tr('WATCH S1 E1', 'צפה ע1 פ1'))
        : (rt.inProgress ? tr('CONTINUE', 'המשך צפייה') : tr('WATCH NOW', 'צפה עכשיו'));
      const fav = inList();
      const overview = String(d.overview || '').trim().replace(/^\.\s*/, '');
      const seasons = isSeries ? (d.seasons || []).filter(x => x.season_number > 0) : [];
      infoEl.innerHTML = `
        ${logo ? `<img class="d-logo" src="${img(logo.file_path, 'w500')}" alt="">` : `<div class="d-title" dir="auto">${esc(title)}</div>`}
        <div class="d-meta">${meta.join(dot)}</div>
        <div class="d-actions">
          <div class="d-play" data-a="play">${icon('play', 26)}<span>${esc(watchText)}</span></div>
          <div class="d-btn" data-a="trailer">${icon('play', 24)}<span>${tr('TRAILER', 'טריילר')}</span></div>
          <div class="d-btn" data-a="fav">${icon(fav ? 'check' : 'add', 24)}<span>${fav ? tr('MY LIST', 'הרשימה שלי') : tr('ADD', 'הוסף')}</span></div>
        </div>
        ${overview ? `<p class="d-syn" dir="auto">${esc(overview)}</p>` : ''}
        ${isSeries ? releaseStrip(d) : ''}
        ${seasons.length ? `<div class="d-seasons"><div class="d-strack">${seasons.map(x => `
          <div class="d-season" data-s="${x.season_number}"><span>${tr('Season', 'עונה')}</span><b>${x.season_number}</b></div>`).join('')}</div></div>` : ''}`;
      posterEl.innerHTML = d.poster_path ? `<img src="${img(d.poster_path, 'w780')}" alt="">` : '';
      const cast = ((d.credits && d.credits.cast) || []).slice(0, 12);
      castEl.innerHTML = cast.length ? `
        <div class="d-cast-h">${tr('Cast', 'שחקנים')}</div>
        <div class="d-cast-row"><div class="d-ctrack">${cast.map(c => `
          <div class="d-actor">${c.profile_path ? `<img src="${img(c.profile_path, 'w185')}" alt="">` : `<span class="ph">${icon('person', 40)}</span>`}</div>`).join('')}</div></div>
        <div class="d-cast-name" dir="auto">${esc(cast.slice(0, 4).map(c => c.name).join(', '))}</div>` : '';
      s.cast = cast;
      s.seasons = seasons;
      wirePointer();
      paintFocus();
      fitSynopsis();
      requestAnimationFrame(() => { posterEl.style.width = `${Math.round(posterEl.offsetHeight * 2 / 3)}px`; });
    }
    /** Show only the synopsis lines that fit (28 dp each), like Compose's maxLines in the space left. */
    function fitSynopsis() {
      const syn = TV.$(infoEl, '.d-syn');
      if (!syn) return;
      const fit = () => {
        syn.style.webkitLineClamp = '4';
        syn.style.maxHeight = '';
        const others = [...infoEl.children].filter(c => c !== syn).reduce((h, c) => h + c.offsetHeight + 16, 0);
        const lines = Math.max(0, Math.min(4, Math.floor((infoEl.clientHeight - others - 16) / 28)));
        syn.style.display = lines ? '' : 'none';
        syn.style.webkitLineClamp = String(lines || 1);
        syn.style.maxHeight = `${lines * 28}px`;
      };
      fit();
      // Logos load after the first layout.
      const logo = TV.$(infoEl, '.d-logo');
      if (logo && !logo.complete) logo.addEventListener('load', fit, { once: true });
    }

    /* ── Focus (zones: back / actions / seasons / cast) ── */
    const zones = () => ['back', 'actions'].concat(s.seasons && s.seasons.length ? ['seasons'] : [], s.cast && s.cast.length ? ['cast'] : []);
    const items = z => (z === 'back' ? [backEl] : z === 'actions' ? [...infoEl.querySelectorAll('[data-a]')] : z === 'seasons' ? [...infoEl.querySelectorAll('.d-season')] : [...castEl.querySelectorAll('.d-actor')]);
    function paintFocus() {
      ['back', 'actions', 'seasons', 'cast'].forEach(z => items(z).forEach((it, i) => it.classList.toggle('f', s.zone === z && (z === 'back' || i === s.idx[z]))));
      if (s.zone === 'seasons') {
        const tr0 = TV.$(infoEl, '.d-strack');
        if (tr0) TV.reveal(tr0.parentElement, tr0, items('seasons')[s.idx.seasons], 8);
      }
      if (s.zone === 'cast') {
        const t = TV.$(castEl, '.d-ctrack');
        TV.reveal(t.parentElement, t, items('cast')[s.idx.cast], 8);
        const name = TV.$(castEl, '.d-cast-name');
        if (name && s.cast[s.idx.cast]) name.textContent = s.cast[s.idx.cast].name;
      }
    }
    function wirePointer() {
      TV.pointer(backEl, () => { s.zone = 'back'; paintFocus(); }, () => TV.back());
      ['actions', 'seasons', 'cast'].forEach(z => items(z).forEach((it, i) => TV.pointer(it,
        () => { s.zone = z; s.idx[z] = i; paintFocus(); },
        () => { s.zone = z; s.idx[z] = i; paintFocus(); activate(); })));
    }

    function activate() {
      if (s.zone === 'back') { TV.back(); return; }
      if (!s.data) return;
      if (s.zone === 'actions') {
        const a = items('actions')[s.idx.actions].dataset.a;
        if (a === 'play') {
          const rt = resumeTarget();
          if (isSeries) openSources(rt.season || 1, rt.episode || 1); else openSources(null, null);
        } else if (a === 'trailer') {
          const v = ((s.data.videos && s.data.videos.results) || []).find(x => x.site === 'YouTube' && x.type === 'Trailer') || ((s.data.videos && s.data.videos.results) || []).find(x => x.site === 'YouTube');
          launchTrailer(v && v.key, s.data.title || s.data.name);
        } else if (a === 'fav') toggleFav();
      } else if (s.zone === 'seasons') {
        openSeason(s.seasons[s.idx.seasons].season_number);
      } else if (s.zone === 'cast') {
        openActor(s.cast[s.idx.cast]);
      }
    }
    function toggleFav() {
      const St = window.Storage;
      if (!St) return;
      const d = s.data;
      if (inList()) { St.removeFromWatchlist(params.id, type); TV.toast(tr('Removed from My List', 'הוסר מהרשימה שלי')); }
      else {
        St.addToWatchlist({ id: d.id, type, title: d.title || d.name, poster_path: d.poster_path, backdrop_path: d.backdrop_path, overview: d.overview, vote_average: d.vote_average, release_date: d.release_date, first_air_date: d.first_air_date });
        TV.toast(tr('Added to My List', 'נוסף לרשימה שלי'));
      }
      const keep = s.idx.actions;
      render();
      s.idx.actions = keep;
      paintFocus();
    }

    function onKey(key) {
      if (layer && layer.onKey(key)) return true;
      const zs = zones();
      const zi = zs.indexOf(s.zone);
      if (key === 'up') { if (zi > 0) { s.zone = zs[zi - 1]; paintFocus(); } return true; }
      if (key === 'down') { if (zi < zs.length - 1) { s.zone = zs[zi + 1]; paintFocus(); } return true; }
      if (TV.fwd(key) || TV.bwd(key)) {
        if (s.zone === 'back') return true;
        const n = items(s.zone).length;
        s.idx[s.zone] = Math.max(0, Math.min(n - 1, s.idx[s.zone] + (TV.fwd(key) ? 1 : -1)));
        paintFocus();
        return true;
      }
      if (key === 'ok') { activate(); return true; }
      if (key === 'play' || key === 'playpause') { s.zone = 'actions'; s.idx.actions = 0; paintFocus(); activate(); return true; }
      return false; // back → previous screen
    }

    /* ═══ Layers inside the details canvas (sources dialog, season popup) ═══ */
    let layer = null;
    function showLayer(l) {
      if (layer && layer.close) layer.close(true);
      layer = l;
      layerEl.innerHTML = '';
      if (l) { layerEl.appendChild(l.el); requestAnimationFrame(() => l.el.classList.add('on')); }
    }
    function closeLayer() { showLayer(null); paintFocus(); }

    /* ── Sources dialog ── */
    function openSources(season, episode) {
      const logoUrl = (() => {
        const logos = (s.data.images && s.data.images.logos) || [];
        const lg = logos.find(l => l.iso_639_1 === TV.lang()) || logos.find(l => l.iso_639_1 === 'en') || logos[0];
        return lg ? img(lg.file_path, 'w500') : img(s.data.backdrop_path, 'w780');
      })();
      const box = el(`
        <div class="src-scrim">
          <div class="src-dlg">
            <div class="src-head">
              <div class="src-title">${tr('Available Sources', 'מקורות זמינים')}</div>
              <div class="src-count hidden"></div>
              <div class="src-flex"></div>
              <div class="src-hint">${tr('Select a stream to begin playback', 'בחר מקור כדי להתחיל')}</div>
              <div class="src-refresh" data-z="head">${icon('replay', 20)}<span>${tr('Refresh', 'רענן')}</span></div>
            </div>
            <div class="src-sep"></div>
            <div class="src-filters"></div>
            <div class="src-body"><div class="src-loading"><i class="spin"></i></div></div>
          </div>
        </div>`);
      const bodyEl = TV.$(box, '.src-body');
      const filtersEl = TV.$(box, '.src-filters');
      const countEl = TV.$(box, '.src-count');
      const refreshEl = TV.$(box, '.src-refresh');
      const st = { streams: [], shown: [], zone: 'list', idx: 0, frow: 0, fidx: 0, scroll: 0, q: null, dv: false, trackers: new Set(), langs: new Set(), busy: false, token: 0 };

      const filterRows = () => {
        const rows = [[{ k: 'q', v: '4K', label: '4K', color: '#FFA000' }, { k: 'q', v: '1080p', label: '1080p', color: '#FFA000' }, { k: 'dv', label: 'Dolby Vision', color: '#FF3B30' }]];
        const trackers = [...new Set(st.streams.map(x => x.d.tracker))].sort().slice(0, 8);
        const langs = ['rus', 'eng', 'heb'].filter(l => st.streams.some(x => x.d.langs.includes(l)));
        const r2 = [];
        if (trackers.length > 1) trackers.forEach(t => r2.push({ k: 't', v: t, label: t, color: TRACKER_COLORS[t.toLowerCase()] || '#94A3B8' }));
        if (langs.length > 1) langs.forEach(l => r2.push({ k: 'l', v: l, label: `${LANGS[l][0]} ${LANGS[l][1]}`, color: LANGS[l][2] }));
        if (st.q || st.dv || st.trackers.size || st.langs.size) r2.push({ k: 'clear', label: `✕ ${tr('Clear', 'נקה')}` });
        if (r2.length) rows.push(r2);
        return rows;
      };
      const isOn = f => (f.k === 'q' ? st.q === f.v : f.k === 'dv' ? st.dv : f.k === 't' ? st.trackers.has(f.v) : f.k === 'l' ? st.langs.has(f.v) : false);
      function renderFilters() {
        const rows = filterRows();
        filtersEl.innerHTML = rows.map((r, ri) => `<div class="src-frow"><div class="src-ftrack">
          ${ri === 0 ? `<span class="src-flbl">${tr('Quality', 'איכות')}</span>` : ''}
          ${r.map((f, i) => `${ri === 0 && i === 2 ? '<i class="src-vsep"></i>' : ''}<div class="chip${f.k === 'clear' ? ' clear' : ''}${isOn(f) ? ' on' : ''}" style="--c:${f.color || '#fff'}" data-r="${ri}" data-i="${i}">${esc(f.label)}</div>`).join('')}
        </div></div>`).join('');
        st.frows = rows;
        filtersEl.querySelectorAll('.chip').forEach(ch => TV.pointer(ch,
          () => { st.zone = 'filters'; st.frow = +ch.dataset.r; st.fidx = +ch.dataset.i; paint(); },
          () => { st.zone = 'filters'; st.frow = +ch.dataset.r; st.fidx = +ch.dataset.i; toggleFilter(); }));
      }
      function toggleFilter() {
        const f = st.frows[st.frow][st.fidx];
        if (f.k === 'q') st.q = st.q === f.v ? null : f.v;
        else if (f.k === 'dv') st.dv = !st.dv;
        else if (f.k === 't') { if (st.trackers.has(f.v)) st.trackers.delete(f.v); else st.trackers.add(f.v); }
        else if (f.k === 'l') { if (st.langs.has(f.v)) st.langs.delete(f.v); else st.langs.add(f.v); }
        else if (f.k === 'clear') { st.q = null; st.dv = false; st.trackers.clear(); st.langs.clear(); }
        renderFilters();
        st.fidx = Math.min(st.fidx, st.frows[st.frow] ? st.frows[st.frow].length - 1 : 0);
        if (!st.frows[st.frow]) { st.frow = 0; st.fidx = 0; }
        renderList();
        paint();
      }
      function visible() {
        return st.streams.filter(x =>
          (!st.q || x.d.quality === st.q) && (!st.dv || x.d.isDv) &&
          (!st.trackers.size || st.trackers.has(x.d.tracker)) &&
          (!st.langs.size || x.d.langs.some(l => st.langs.has(l))));
      }
      function rowHtml(x) {
        const d = x.d;
        const tc = TRACKER_COLORS[d.tracker.toLowerCase()] || '#94A3B8';
        return `<div class="srow">
          ${logoUrl ? `<div class="sr-logo"><img src="${esc(logoUrl)}" alt=""></div><i class="sr-div"></i>` : ''}
          <div class="sr-main" dir="ltr">
            <div class="sr-l1"><span class="sr-trk" style="--c:${tc}">${esc(d.tracker.toUpperCase())}</span><span class="sr-grp">${esc(d.group)}</span><span class="sr-q">${d.quality}</span></div>
            <div class="sr-file">${esc(d.filename)}</div>
          </div>
          <div class="sr-tags" dir="ltr">
            ${d.langs.slice(0, 4).map(l => `<span class="sr-lang" style="--c:${LANGS[l][2]}"><i>${LANGS[l][0]}</i>${LANGS[l][1]}</span>`).join('')}
            ${d.isHdr ? '<span class="tag" style="--c:#FF9500">HDR</span>' : ''}${d.isDv ? '<span class="tag" style="--c:#E50914">DV</span>' : ''}
            <span class="tag" style="--c:#0A84FF">${d.audio}</span>
          </div>
          <div class="sr-size" dir="ltr"><b>${esc(d.size)}</b><span class="sr-sl"><span class="up">▲ ${d.seeders}</span><i></i><span class="dn">▼ ${d.leechers}</span></span></div>
          <div class="sr-play">${icon('play', 24)}</div>
        </div>`;
      }
      function renderList() {
        st.shown = visible();
        countEl.textContent = `${st.streams.length} ${tr('streams', 'מקורות')}`;
        countEl.classList.toggle('hidden', !st.streams.length);
        if (!st.shown.length) {
          bodyEl.innerHTML = `<div class="src-msg">${st.streams.length ? tr('No sources match these filters.', 'אין מקורות שמתאימים לסינון.') : tr('No sources available right now.', 'לא נמצאו מקורות זמינים כרגע.')}</div>`;
          return;
        }
        bodyEl.innerHTML = `<div class="src-list">${st.shown.map(rowHtml).join('')}</div>`;
        st.idx = Math.min(st.idx, st.shown.length - 1);
        st.scroll = 0;
        [...bodyEl.querySelectorAll('.srow')].forEach((r, i) => TV.pointer(r,
          () => { st.zone = 'list'; st.idx = i; paint(); },
          () => { st.zone = 'list'; st.idx = i; paint(); play(); }));
      }
      function paint() {
        refreshEl.classList.toggle('f', st.zone === 'head');
        filtersEl.querySelectorAll('.chip').forEach(ch => ch.classList.toggle('f', st.zone === 'filters' && +ch.dataset.r === st.frow && +ch.dataset.i === st.fidx));
        // Chip rows scroll sideways to keep the focused chip visible (Compose horizontalScroll).
        if (st.zone === 'filters') {
          const chip = filtersEl.querySelector('.chip.f');
          const row = chip && chip.closest('.src-frow');
          if (row) TV.reveal(row, row.firstElementChild, chip, 8);
        }
        const rows = [...bodyEl.querySelectorAll('.srow')];
        rows.forEach((r, i) => r.classList.toggle('f', st.zone === 'list' && i === st.idx));
        const list = TV.$(bodyEl, '.src-list');
        // Keep the focused row in view (LazyColumn contentPadding 24).
        if (list && rows[st.idx]) TV.reveal(bodyEl, list, rows[st.idx], 24, 'y');
      }
      async function scan(force) {
        const token = ++st.token;
        bodyEl.innerHTML = '<div class="src-loading"><i class="spin"></i></div>';
        refreshEl.querySelector('span').textContent = tr('Refreshing…', 'מרענן…');
        try {
          if (!s.imdbId) throw new Error(tr('No IMDb ID for this title', 'לא נמצא מזהה IMDb'));
          const T = window.Torrentio;
          const list = season != null ? await T.getEpisodeStreams(s.imdbId, season, episode) : await T.getStreams('movie', s.imdbId);
          if (token !== st.token) return;
          // Max resolution / file size from Settings (Android DetailsViewModel filter).
          st.streams = (list || []).map(x => ({ s: x, d: describe(x) }))
            .filter(x => !TV.prefs || TV.prefs.allowsStream(x.d.quality, x.s.sizeBytes || 0));
          renderFilters();
          renderList();
          st.zone = st.shown.length ? 'list' : 'filters';
          paint();
        } catch (e) {
          if (token !== st.token) return;
          bodyEl.innerHTML = `<div class="src-msg err">${esc(e.message || tr('Error scanning sources', 'שגיאה בסריקת המקורות'))}</div>`;
        }
        refreshEl.querySelector('span').textContent = tr('Refresh', 'רענן');
      }
      async function play() {
        const x = st.shown[st.idx];
        if (!x || st.busy) return;
        st.busy = true;
        const row = bodyEl.querySelectorAll('.srow')[st.idx];
        if (row) row.classList.add('busy');
        try {
          let url = x.s.url;
          if (!url && x.s.infoHash && window.AllDebrid) url = await window.AllDebrid.resolveStream(x.s.infoHash, () => {}, season, episode);
          if (!url) throw new Error('no url');
          closeLayer();
          const d = s.data;
          const title = d.title || d.name || '';
          TV.play({
            url,
            title: season != null ? `${title} — ${tr('S', 'ע')}${season} ${tr('E', 'פ')}${episode}` : title,
            showTitle: title,
            type: isSeries ? 'series' : 'movie',
            id: d.id, imdbId: s.imdbId,
            backdrop: img(d.backdrop_path, 'original'), poster: img(d.poster_path, 'w500'),
            logo: logoUrl && logoUrl.indexOf('/w500/') > 0 ? logoUrl : '',
            season, episode, hdr: x.s.hdr || null,
            onNext: next => openSources(next.season, next.episode),
            onEnd: () => { render(); },
          });
        } catch {
          TV.toast(tr('Failed to launch stream', 'שגיאה בהפעלת המקור'));
        }
        st.busy = false;
        if (row) row.classList.remove('busy');
      }
      TV.pointer(refreshEl, () => { st.zone = 'head'; paint(); }, () => scan(true));

      const l = {
        el: box,
        onKey(key) {
          if (key === 'back') { closeLayer(); return true; }
          if (st.zone === 'list') {
            if (key === 'down') { if (st.idx < st.shown.length - 1) { st.idx++; paint(); } return true; }
            if (key === 'up') { if (st.idx > 0) { st.idx--; } else { st.zone = 'filters'; st.frow = st.frows.length - 1; st.fidx = 0; } paint(); return true; }
            if (key === 'ok') { play(); return true; }
            return true;
          }
          if (st.zone === 'filters') {
            const row = st.frows[st.frow];
            if (TV.fwd(key)) { st.fidx = Math.min(row.length - 1, st.fidx + 1); paint(); return true; }
            if (TV.bwd(key)) { st.fidx = Math.max(0, st.fidx - 1); paint(); return true; }
            if (key === 'up') { if (st.frow > 0) { st.frow--; st.fidx = Math.min(st.fidx, st.frows[st.frow].length - 1); } else st.zone = 'head'; paint(); return true; }
            if (key === 'down') { if (st.frow < st.frows.length - 1) { st.frow++; st.fidx = Math.min(st.fidx, st.frows[st.frow].length - 1); } else if (st.shown.length) { st.zone = 'list'; } paint(); return true; }
            if (key === 'ok') { toggleFilter(); return true; }
            return true;
          }
          // head (refresh)
          if (key === 'down') { st.zone = 'filters'; st.frow = 0; st.fidx = 0; paint(); return true; }
          if (key === 'ok') { scan(true); return true; }
          return true;
        },
        close() { st.token++; },
      };
      showLayer(l);
      renderFilters();
      scan(false);
    }

    /* ── Actor popup (ActorDetailsPopup) ── */
    async function openActor(actor) {
      if (!actor) return;
      const box = el('<div class="ac-scrim"><div class="src-loading"><i class="spin"></i></div></div>');
      const st = { idx: 0, items: [], credits: [] };
      const l = {
        el: box,
        onKey(key) {
          if (key === 'back') { closeLayer(); return true; }
          if (!st.items.length) return true;
          if (key === 'down') { st.idx = Math.min(st.items.length - 1, st.idx + 1); paint(); return true; }
          if (key === 'up') { st.idx = Math.max(0, st.idx - 1); paint(); return true; }
          if (key === 'ok') {
            const c = st.credits[st.idx];
            closeLayer();
            TV.open('details', { id: c.id, type: c.isSeries ? 'tv' : 'movie' });
            return true;
          }
          return true;
        },
      };
      function paint() {
        st.items.forEach((it, i) => it.classList.toggle('f', i === st.idx));
        const list = TV.$(box, '.ac-list');
        if (list && st.items[st.idx]) TV.reveal(list.parentElement, list, st.items[st.idx], 8, 'y');
      }
      showLayer(l);
      try {
        let p = await TV.tmdb(`/person/${actor.id}`, { append_to_response: 'combined_credits' });
        if (!p.biography && TV.rtl()) {
          const en = await TV.tmdb(`/person/${actor.id}`, { language: 'en-US' }).catch(() => null);
          if (en && en.biography) p = Object.assign({}, p, { biography: en.biography });
        }
        const seen = new Set();
        st.credits = ((p.combined_credits && p.combined_credits.cast) || []).map(c => {
          const title = c.title || c.name;
          if (!title) return null;
          const date = c.release_date || c.first_air_date || '';
          return { year: Number(date.slice(0, 4)) || 0, title, isSeries: c.media_type === 'tv' || !!c.first_air_date, id: c.id };
        }).filter(Boolean).sort((a, b) => b.year - a.year).filter(c => (seen.has(c.title) ? false : seen.add(c.title)));
        let lastYear = null;
        const rows = st.credits.map(c => {
          const head = c.year !== lastYear ? `<div class="ac-year">${c.year ? c.year : tr('Upcoming', 'בקרוב')}</div>` : '';
          lastYear = c.year;
          return `${head}<div class="ac-item"><i class="ac-dot${c.isSeries ? ' s' : ''}"></i><span class="ac-t" dir="auto">${esc(c.title)}</span><span class="ac-k${c.isSeries ? ' s' : ''}">${c.isSeries ? tr('Series', 'סדרה') : tr('Movie', 'סרט')}</span></div>`;
        }).join('');
        const badges = [p.birthday, p.place_of_birth].filter(Boolean).map(b => `<span class="ac-badge" dir="auto">${esc(b)}</span>`).join('')
          + (p.deathday ? `<span class="ac-badge death">Died: ${esc(p.deathday)}</span>` : '');
        box.innerHTML = `
          <div class="ac-modal">
            ${p.profile_path ? `<img class="ac-glow" src="${img(p.profile_path, 'w45')}" alt="">` : ''}
            <div class="ac-row">
              <div class="ac-portrait">${p.profile_path ? `<img src="${img(p.profile_path, 'original')}" alt="">` : `<span class="ph">${icon('person', 64)}</span>`}</div>
              <div class="ac-info">
                <div class="ac-name" dir="auto">${esc(p.name || actor.name)}</div>
                ${badges ? `<div class="ac-badges">${badges}</div>` : ''}
                ${p.biography ? `<p class="ac-bio" dir="auto">${esc(p.biography)}</p>` : ''}
                ${st.credits.length ? `<div class="ac-fh">${tr('Filmography', 'פילמוגרפיה')}</div><div class="ac-view"><div class="ac-list">${rows}</div></div>` : ''}
              </div>
            </div>
          </div>`;
        const portrait = TV.$(box, '.ac-portrait');
        portrait.style.width = `${Math.round(portrait.offsetHeight * 2 / 3)}px`;
        st.items = [...box.querySelectorAll('.ac-item')];
        st.items.forEach((it, i) => TV.pointer(it, () => { st.idx = i; paint(); }, () => { st.idx = i; l.onKey('ok'); }));
        paint();
      } catch (e) {
        box.innerHTML = `<div class="src-msg">${tr('Could not load this actor', 'לא ניתן לטעון את השחקן')}</div>`;
      }
    }

    /* ── Season popup (SeasonEpisodesPopup) ── */
    async function openSeason(num) {
      const box = el(`
        <div class="ep-pop">
          <img class="ep-bg" alt="">
          <div class="ep-shade"></div>
          <div class="ep-wrap">
            <div class="ep-prev"><div class="ep-prev-in"></div></div>
            <div class="ep-listbox"><div class="ep-h">${tr('SEASON', 'עונה')} ${num}</div><div class="ep-view"><div class="ep-list"><div class="src-loading"><i class="spin"></i></div></div></div></div>
          </div>
        </div>`);
      const listEl = TV.$(box, '.ep-list');
      const prevEl = TV.$(box, '.ep-prev-in');
      const bgEl = TV.$(box, '.ep-bg');
      const st = { eps: [], idx: 0, scroll: 0 };
      const l = {
        el: box,
        onKey(key) {
          if (key === 'back') { closeLayer(); return true; }
          if (key === 'down') { if (st.idx < st.eps.length - 1) { st.idx++; paint(); } return true; }
          if (key === 'up') { if (st.idx > 0) { st.idx--; paint(); } return true; }
          if (key === 'ok') { playEp(); return true; }
          return true;
        },
      };
      showLayer(l);
      const today = new Date().toISOString().slice(0, 10);
      function preview(ep) {
        const still = ep && ep.still_path ? img(ep.still_path, 'w780') : '';
        // A tiny still stretched over the screen gives Android's blurred-aurora backdrop
        // without a full-screen blur filter.
        bgEl.src = ep && ep.still_path ? img(ep.still_path, 'w92') : img(s.data.backdrop_path, 'w300');
        prevEl.innerHTML = ep ? `
          <div class="ep-still" style="height:${Math.round(prevEl.clientWidth * 9 / 16)}px">${still ? `<img src="${still}" alt="">` : ''}${ep.progress > 0 ? `<span class="ep-pbar"><i style="width:${Math.round(ep.progress * 100)}%"></i></span>` : ''}</div>
          <div class="ep-no"><b>${tr('EPISODE', 'פרק')} ${ep.episode_number}</b>${ep.watched ? `<span class="ep-w">${tr('WATCHED', 'נצפה')}</span>` : ''}</div>
          <div class="ep-t" dir="auto">${esc(ep.name || '')}</div>
          ${ep.overview ? `<div class="ep-o" dir="auto">${esc(ep.overview)}</div>` : ''}` : '';
      }
      function paint() {
        const rows = [...listEl.querySelectorAll('.ep-row')];
        rows.forEach((r, i) => r.classList.toggle('f', i === st.idx));
        if (rows[st.idx]) TV.reveal(listEl.parentElement, listEl, rows[st.idx], 10, 'y');
        preview(st.eps[st.idx]);
      }
      function playEp() {
        const ep = st.eps[st.idx];
        if (!ep) return;
        if (ep.air_date && ep.air_date > today) { TV.toast(tr("This episode hasn't aired yet", 'הפרק הזה עדיין לא שודר')); return; }
        openSources(num, ep.episode_number);
      }
      try {
        const data = await TV.tmdb(`/tv/${params.id}/season/${num}`);
        st.eps = (data.episodes || []).map(ep => {
          const p = s.imdbId && WP ? WP.get(WP.keyFor('series', s.imdbId, num, ep.episode_number)) : null;
          return Object.assign(ep, { progress: p ? Math.min(1, (p.percentage || 0) / 100) : 0, watched: p ? p.percentage >= 90 : false });
        });
        listEl.innerHTML = st.eps.map(ep => {
          const up = ep.air_date && ep.air_date > today;
          return `<div class="ep-row${up ? ' up' : ''}">
            <div class="ep-th">${ep.still_path ? `<img src="${img(ep.still_path, 'w300')}" alt="">` : ''}
              ${ep.progress > 0 && !up ? `<span class="ep-pr${ep.watched ? ' done' : ''}"><i style="width:${Math.round(ep.progress * 100)}%"></i></span>` : ''}
              <span class="ep-ov">${up ? '🗓' : `<span class="pl">${icon('play', 36)}</span>${ep.watched ? `<span class="ck">${icon('check', 28)}</span>` : ''}`}</span></div>
            <div class="ep-info"><div class="ep-rt" dir="auto">${ep.episode_number}. ${esc(ep.name || '')}</div>
              ${up ? `<span class="ep-air">🗓  ${esc(airsPillText(ep.air_date, TV.rtl()).toUpperCase())}</span>` : (ep.overview ? `<div class="ep-ro" dir="auto">${esc(ep.overview)}</div>` : '')}</div>
          </div>`;
        }).join('');
        [...listEl.querySelectorAll('.ep-row')].forEach((r, i) => TV.pointer(r, () => { st.idx = i; paint(); }, () => { st.idx = i; paint(); playEp(); }));
        paint();
      } catch {
        listEl.innerHTML = `<div class="src-msg">${tr('Could not load episodes', 'לא ניתן לטעון את הפרקים')}</div>`;
      }
    }

    load();
    return {
      el: root,
      onKey,
      onShow(first) { if (!first && s.data) { const keep = Object.assign({}, s.idx); render(); s.idx = keep; paintFocus(); } },
      destroy() { if (layer && layer.close) layer.close(true); },
    };
  }

  TV.register('details', details);
})();
