/**
 * LuminaStreams TV — Search, My List, Settings and Sign-in, in the Android TV
 * app's style. They share the D-pad poster grid below.
 */
(() => {
  'use strict';
  const { tr, esc, icon, el, img } = TV;

  /* ── Poster grid (Compose LazyVerticalGrid, Adaptive 150 dp) ── */
  const CELL_W = 148, CELL_H = 268, GAP = 16;
  function grid(container, onOpen) {
    const g = { items: [], idx: 0, cols: 1, scroll: 0, el: el('<div class="grid"><div class="grid-in"></div></div>') };
    const inner = g.el.firstChild;
    container.appendChild(g.el);
    g.set = items => {
      g.items = items;
      g.idx = 0;
      g.scroll = 0;
      g.cols = Math.max(1, Math.floor((g.el.clientWidth + GAP) / (CELL_W + GAP)) || 5);
      inner.style.transform = '';
      inner.innerHTML = items.map(m => `<div class="pcard"><div class="surf"><img src="${img(m.poster, 'w500')}" alt="" decoding="async">${m.rating > 0 ? `<span class="rating">★ ${m.rating.toFixed(1)}</span>` : ''}</div>
        <div class="t" dir="auto">${esc(m.title)}</div><div class="s">${m.type === 'tv' ? tr('TV Show', 'סדרה') : tr('Movie', 'סרט')}</div></div>`).join('');
      [...inner.children].forEach((c, i) => TV.pointer(c, () => { g.focusAt(i); if (g.onPointer) g.onPointer(); }, () => onOpen(g.items[i])));
    };
    g.paint = on => {
      [...inner.children].forEach((c, i) => c.classList.toggle('f', on && i === g.idx));
      const rowTop = Math.floor(g.idx / g.cols) * (CELL_H + GAP);
      const viewH = g.el.clientHeight || 400;
      if (rowTop + CELL_H > g.scroll + viewH) g.scroll = rowTop + CELL_H - viewH + 8;
      if (rowTop < g.scroll) g.scroll = rowTop;
      inner.style.transform = `translateY(${-g.scroll}px)`;
    };
    g.focusAt = i => { g.idx = Math.max(0, Math.min(g.items.length - 1, i)); g.paint(true); };
    /** Returns false at the top edge (so the caller can move focus up). */
    g.key = key => {
      if (!g.items.length) return false;
      if (TV.fwd(key)) { if ((g.idx + 1) % g.cols && g.idx + 1 < g.items.length) g.focusAt(g.idx + 1); return true; }
      if (TV.bwd(key)) { if (g.idx % g.cols) g.focusAt(g.idx - 1); return true; }
      if (key === 'down') { if (g.idx + g.cols < g.items.length) g.focusAt(g.idx + g.cols); return true; }
      if (key === 'up') { if (g.idx - g.cols >= 0) { g.focusAt(g.idx - g.cols); return true; } return false; }
      if (key === 'ok') { onOpen(g.items[g.idx]); return true; }
      return false;
    };
    return g;
  }
  const openDetails = m => TV.open('details', { id: m.id, type: m.type, movie: m });

  /* ═══ Search ═══ */
  TV.register('search', () => {
    const root = el(`
      <div class="screen page has-bar">
        <div class="pg-head"><div class="pg-back">${icon('back', 28)}</div><div class="pg-title">${tr('Search', 'חיפוש')}</div></div>
        <div class="sr-bar"><span class="sr-ic">${icon('search', 24)}</span><input class="sr-input" type="text" dir="auto" placeholder="${tr('Search movies, series...', 'חיפוש סרטים, סדרות...')}"></div>
        <div class="pg-sub"></div>
        <div class="pg-body"></div>
      </div>`);
    const input = TV.$(root, '.sr-input');
    const bar = TV.$(root, '.sr-bar');
    const back = TV.$(root, '.pg-back');
    const sub = TV.$(root, '.pg-sub');
    const g = grid(TV.$(root, '.pg-body'), openDetails);
    let zone = 'bar';
    const paint = () => { bar.classList.toggle('f', zone === 'bar'); back.classList.toggle('f', zone === 'back'); g.paint(zone === 'grid'); };
    g.onPointer = () => { zone = 'grid'; paint(); };
    TV.pointer(back, () => { zone = 'back'; paint(); }, () => TV.back());
    TV.pointer(bar, () => { zone = 'bar'; paint(); }, () => input.focus());

    async function trending() {
      sub.textContent = tr('Trending now', 'פופולרי עכשיו');
      g.set(await TV.list('/trending/all/week', {}).catch(() => []));
      paint();
    }
    let timer = 0;
    let token = 0;
    async function search(q) {
      const t = ++token;
      if (!q.trim()) { trending(); return; }
      sub.textContent = tr('Results', 'תוצאות');
      const items = await TV.list('/search/multi', { query: q, include_adult: false }).catch(() => []);
      if (t !== token) return;
      g.set(items);
      if (!items.length) sub.textContent = tr('No results found', 'לא נמצאו תוצאות');
      paint();
    }
    input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => search(input.value), 400); });
    input.addEventListener('keydown', e => {
      const k = TV.keyOf(e);
      // LG keyboard "Enter"/down: done typing → results.
      if (k === 'ok' || k === 'down') { e.preventDefault(); input.blur(); if (g.items.length) { zone = 'grid'; g.focusAt(0); paint(); } }
      if (k === 'back') { e.preventDefault(); input.blur(); }
    });
    trending();
    paint();
    return {
      el: root,
      onKey(key) {
        if (zone === 'grid') { if (g.key(key)) return true; if (key === 'up') { zone = 'bar'; paint(); return true; } return key !== 'back'; }
        if (zone === 'bar') {
          if (key === 'ok') { input.focus(); return true; }
          if (key === 'down' && g.items.length) { zone = 'grid'; g.focusAt(0); paint(); return true; }
          if (key === 'up') { zone = 'back'; paint(); return true; }
          return key !== 'back';
        }
        if (key === 'down') { zone = 'bar'; paint(); return true; }
        if (key === 'ok') { TV.back(); return true; }
        return key !== 'back';
      },
    };
  });

  /* ═══ My List ═══ */
  TV.register('watchlist', () => {
    const root = el(`
      <div class="screen page">
        <div class="pg-head"><div class="pg-back">${icon('back', 28)}</div><div class="pg-title">${tr('My List', 'הרשימה שלי')}</div></div>
        <div class="pg-sub"></div>
        <div class="pg-body"></div>
      </div>`);
    const back = TV.$(root, '.pg-back');
    const sub = TV.$(root, '.pg-sub');
    const g = grid(TV.$(root, '.pg-body'), openDetails);
    let zone = 'grid';
    const paint = () => { back.classList.toggle('f', zone === 'back'); g.paint(zone === 'grid'); };
    g.onPointer = () => { zone = 'grid'; paint(); };
    TV.pointer(back, () => { zone = 'back'; paint(); }, () => TV.back());
    function load() {
      const list = (window.Storage ? window.Storage.getWatchlist() : []).map(w => ({
        key: `${w.type}_${w.id}`, id: w.id, type: w.type === 'series' ? 'tv' : w.type, title: w.title || '',
        poster: w.poster || '', backdrop: w.backdrop || '', rating: Number(w.rating) || 0, year: w.year || 0, overview: w.overview || '',
      }));
      g.set(list);
      sub.textContent = list.length ? `${list.length} ${tr('titles', 'פריטים')}` : tr('Your list is empty — add titles with ＋ on their page.', 'הרשימה ריקה — הוסף כותרים עם ＋ בדף שלהם.');
      if (!list.length) zone = 'back';
      paint();
    }
    load();
    return {
      el: root,
      onShow(first) { if (!first) load(); },
      onKey(key) {
        if (zone === 'grid') { if (g.key(key)) return true; if (key === 'up') { zone = 'back'; paint(); return true; } return key !== 'back'; }
        if (key === 'down' && g.items.length) { zone = 'grid'; paint(); return true; }
        if (key === 'ok') { TV.back(); return true; }
        return key !== 'back';
      },
    };
  });

  /* ═══ Settings (Android: categories on the start side, items beside them) ═══ */
  TV.register('settings', () => {
    const St = window.Storage;
    const AD = window.AllDebrid;
    const CATS = [
      ['account', tr('Account', 'חשבון')], ['playback', tr('Playback', 'ניגון')],
      ['subtitles', tr('Subtitles', 'כתוביות')], ['system', tr('System', 'מערכת')], ['about', tr('About', 'אודות')],
    ];
    const SUB_SIZES = ['small', 'medium', 'large', 'xlarge'];
    const sizeName = v => ({ small: tr('Small', 'קטן'), medium: tr('Medium', 'בינוני'), large: tr('Large', 'גדול'), xlarge: tr('Extra Large', 'ענק') }[v] || tr('Medium', 'בינוני'));
    const SUB_LANGS = ['heb', 'eng', 'none'];
    const subLangName = v => ({ heb: tr('Hebrew', 'עברית'), eng: tr('English', 'אנגלית'), none: tr('Off', 'כבוי') }[v] || v);
    const root = el(`
      <div class="screen page settings">
        <div class="pg-head"><div class="pg-back">${icon('back', 28)}</div><div class="pg-title">${tr('Settings', 'הגדרות')}</div></div>
        <div class="st-wrap"><div class="st-cats"></div><div class="st-items"></div></div>
      </div>`);
    const catsEl = TV.$(root, '.st-cats');
    const itemsEl = TV.$(root, '.st-items');
    const back = TV.$(root, '.pg-back');
    const s = { cat: 0, item: 0, zone: 'cats', adUser: null, pin: null };

    function items() {
      const c = CATS[s.cat][0];
      if (c === 'account') {
        const user = window.FirebaseAuth && window.FirebaseAuth.getCurrentUser && window.FirebaseAuth.getCurrentUser();
        return [
          { title: 'AllDebrid', value: s.pin ? `PIN ${s.pin.pin} — alldebrid.com/pin` : (AD && AD.isAuthenticated() ? (s.adUser ? `${s.adUser.username} · ${tr('Premium until', 'פרימיום עד')} ${s.adUser.expiration ? new Date(s.adUser.expiration).toLocaleDateString() : '—'}` : tr('Connected', 'מחובר')) : tr('Not connected', 'לא מחובר')),
            action: AD && AD.isAuthenticated() ? tr('Disconnect', 'נתק') : tr('Connect', 'חבר'), run: toggleAD },
          { title: tr('Cloud Sync', 'סנכרון ענן'), value: user ? user.email : tr('Not signed in', 'לא מחובר'), action: user ? tr('Sign out', 'התנתק') : tr('Sign in', 'התחבר'),
            run: () => { if (user) { window.FirebaseAuth.logout().then(paint); } else TV.open('auth'); } },
        ];
      }
      if (c === 'playback') {
        const next = St ? St.get('settings_autoplay_next', true) : true;
        return [{ title: tr('Autoplay Next Episode', 'הפעל אוטומטית פרק הבא'), toggle: next, run: () => { St.set('settings_autoplay_next', !next); paint(); } }];
      }
      if (c === 'subtitles') {
        const lang = St && St.getSubtitleLang ? St.getSubtitleLang() : 'heb';
        const size = (St && St.get('settings_sub_font_size', 'medium')) || 'medium';
        const yellow = !!(St && St.get('settings_yellow_subs', false));
        return [
          { title: tr('Default Subtitles', 'כתוביות ברירת מחדל'), value: subLangName(lang), run: () => { St.setSubtitleLang(SUB_LANGS[(SUB_LANGS.indexOf(lang) + 1) % SUB_LANGS.length]); paint(); } },
          { title: tr('Subtitle Size', 'גודל כתוביות'), value: sizeName(size), run: () => { St.set('settings_sub_font_size', SUB_SIZES[(SUB_SIZES.indexOf(size) + 1) % SUB_SIZES.length]); paint(); } },
          { title: tr('Yellow Subtitles', 'כתוביות צהובות'), toggle: yellow, run: () => { St.set('settings_yellow_subs', !yellow); paint(); } },
        ];
      }
      if (c === 'system') {
        return [{ title: tr('App Language', 'שפת הממשק'), value: TV.rtl() ? 'עברית' : 'English',
          run: () => { window.I18n.setLanguage(TV.rtl() ? 'en' : 'he'); location.reload(); } }];
      }
      return [{ title: 'LuminaStreams', value: `LG webOS · ${tr('version', 'גרסה')} 1.0.0` }];
    }
    async function toggleAD() {
      if (!AD) return;
      if (AD.isAuthenticated()) { AD.logout(); s.adUser = null; paint(); return; }
      try {
        s.pin = await AD.startAuth();
        paint();
        await AD.pollAuth(s.pin.check, s.pin.pin);
        s.pin = null;
        s.adUser = await AD.getUser().catch(() => null);
        TV.toast(tr('AllDebrid connected', 'AllDebrid מחובר'));
      } catch { s.pin = null; TV.toast(tr('AllDebrid connection failed', 'החיבור ל-AllDebrid נכשל')); }
      paint();
    }
    function paint() {
      catsEl.innerHTML = CATS.map(([, name], i) => `<div class="st-cat${i === s.cat ? ' act' : ''}${s.zone === 'cats' && i === s.cat ? ' f' : ''}">${esc(name)}</div>`).join('');
      const list = items();
      s.item = Math.min(s.item, list.length - 1);
      itemsEl.innerHTML = list.map((it, i) => `
        <div class="st-item${s.zone === 'items' && i === s.item ? ' f' : ''}">
          <div class="st-tx"><div class="st-t">${esc(it.title)}</div>${it.value ? `<div class="st-v" dir="auto">${esc(it.value)}</div>` : ''}</div>
          ${it.toggle != null ? `<span class="st-sw${it.toggle ? ' on' : ''}"><i></i></span>` : it.action ? `<span class="st-act">${esc(it.action)}</span>` : it.run ? `<span class="st-chev">›</span>` : ''}
        </div>`).join('');
      back.classList.toggle('f', s.zone === 'back');
      [...catsEl.children].forEach((c, i) => TV.pointer(c, () => { s.zone = 'cats'; s.cat = i; s.item = 0; paint(); }, () => { s.cat = i; s.zone = 'items'; s.item = 0; paint(); }));
      [...itemsEl.children].forEach((c, i) => TV.pointer(c, () => { s.zone = 'items'; s.item = i; paint(); }, () => { s.item = i; const it = items()[i]; if (it.run) it.run(); }));
    }
    TV.pointer(back, () => { s.zone = 'back'; paint(); }, () => TV.back());
    if (AD && AD.isAuthenticated()) AD.getUser().then(u => { s.adUser = u; paint(); }).catch(() => {});
    paint();
    return {
      el: root,
      onShow(first) { if (!first) paint(); },
      onKey(key) {
        const list = items();
        if (s.zone === 'cats') {
          if (key === 'up') { if (s.cat > 0) { s.cat--; s.item = 0; } else s.zone = 'back'; paint(); return true; }
          if (key === 'down') { s.cat = Math.min(CATS.length - 1, s.cat + 1); s.item = 0; paint(); return true; }
          if (TV.fwd(key) || key === 'ok') { s.zone = 'items'; s.item = 0; paint(); return true; }
          return key !== 'back';
        }
        if (s.zone === 'items') {
          if (key === 'up') { s.item = Math.max(0, s.item - 1); paint(); return true; }
          if (key === 'down') { s.item = Math.min(list.length - 1, s.item + 1); paint(); return true; }
          if (TV.bwd(key) || key === 'back') { s.zone = 'cats'; paint(); return true; }
          if (key === 'ok') { const it = list[s.item]; if (it && it.run) it.run(); return true; }
          return true;
        }
        if (key === 'down') { s.zone = 'cats'; paint(); return true; }
        if (key === 'ok') { TV.back(); return true; }
        return key !== 'back';
      },
    };
  });

  /* ═══ Sign in (cloud sync with the same account as every other platform) ═══ */
  TV.register('auth', () => {
    const FA = window.FirebaseAuth;
    const root = el(`
      <div class="screen page auth">
        <div class="pg-head"><div class="pg-back">${icon('back', 28)}</div><div class="pg-title">${tr('Account', 'חשבון')}</div></div>
        <div class="au-card">
          <div class="au-logo" dir="ltr"><b>LUMINA </b><span>STREAMS</span></div>
          <div class="au-sub">${tr('Sign in to sync your list and progress', 'התחבר כדי לסנכרן את הרשימה וההתקדמות')}</div>
          <div class="au-user hidden"></div>
          <input class="au-in" type="email" dir="ltr" placeholder="${tr('Email', 'אימייל')}">
          <input class="au-in" type="password" dir="ltr" placeholder="${tr('Password', 'סיסמה')}">
          <div class="au-btn">${tr('Sign in', 'התחבר')}</div>
          <div class="au-err"></div>
        </div>
      </div>`);
    const back = TV.$(root, '.pg-back');
    const [email, pass] = root.querySelectorAll('.au-in');
    const btn = TV.$(root, '.au-btn');
    const err = TV.$(root, '.au-err');
    const userEl = TV.$(root, '.au-user');
    const fields = [back, email, pass, btn];
    let idx = 1;
    const signedIn = () => !!(FA && FA.getCurrentUser && FA.getCurrentUser());
    function paint() {
      const u = signedIn() ? FA.getCurrentUser() : null;
      userEl.classList.toggle('hidden', !u);
      userEl.textContent = u ? u.email : '';
      email.classList.toggle('hidden', !!u);
      pass.classList.toggle('hidden', !!u);
      btn.textContent = u ? tr('Sign out', 'התנתק') : tr('Sign in', 'התחבר');
      if (u && (idx === 1 || idx === 2)) idx = 3;
      fields.forEach((f, i) => f.classList.toggle('f', i === idx));
    }
    async function submit() {
      err.textContent = '';
      if (signedIn()) { await FA.logout(); paint(); return; }
      try {
        btn.classList.add('busy');
        await FA.login(email.value.trim(), pass.value);
        TV.toast(tr('Signed in', 'מחובר'));
        TV.back();
      } catch (e) {
        err.textContent = tr('Sign-in failed — check the email and password.', 'ההתחברות נכשלה — בדוק את האימייל והסיסמה.');
      }
      btn.classList.remove('busy');
    }
    [email, pass].forEach(inp => inp.addEventListener('keydown', e => {
      const k = TV.keyOf(e);
      if (k === 'ok' || k === 'down') { e.preventDefault(); inp.blur(); idx = inp === email ? 2 : 3; paint(); }
      if (k === 'up') { e.preventDefault(); inp.blur(); idx = Math.max(0, fields.indexOf(inp) - 1); paint(); }
      if (k === 'back') { e.preventDefault(); inp.blur(); }
    }));
    fields.forEach((f, i) => TV.pointer(f, () => { idx = i; paint(); }, () => { idx = i; paint(); activate(); }));
    function activate() {
      const f = fields[idx];
      if (f === back) TV.back();
      else if (f === btn) submit();
      else f.focus();
    }
    paint();
    return {
      el: root,
      onKey(key) {
        if (key === 'up') { do { idx = Math.max(0, idx - 1); } while (idx > 0 && fields[idx].classList.contains('hidden')); paint(); return true; }
        if (key === 'down') { do { idx = Math.min(fields.length - 1, idx + 1); } while (idx < fields.length - 1 && fields[idx].classList.contains('hidden')); paint(); return true; }
        if (key === 'ok') { activate(); return true; }
        return key !== 'back';
      },
    };
  });
})();
