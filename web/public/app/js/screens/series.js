/**
 * LuminaStreams — Series Screen
 * Studio filter ribbon, TV genre rows, studio-specific TV content.
 * Uses TMDB.discoverTv() and TMDB.discoverByStudio(studio, 'tv').
 */
(() => {
  'use strict';

  const { posterUrl, STUDIOS, TV_GENRES } = window.CONST;
  const { escapeHtml, formatYear, throttle } = window.Helpers;
  const { t, tr } = window.I18n;

  /* ── State ────────────────────────────────────────────────────────── */
  let container = null;
  let activeStudio = 'all';
  let rowObserver = null;
  let loadedRowIds = new Set();
  let activeRequests = new Map();

  /* ── TV Genre Rows ───────────────────────────────────────────────── */
  const TV_GENRE_ROWS = [
    { id: 'tv_18',    genreId: 18,    title: tr('Drama', 'דרמה') },
    { id: 'tv_35',    genreId: 35,    title: tr('Comedy', 'קומדיה') },
    { id: 'tv_53',    genreId: 80,    title: tr('Crime', 'פשע') },
    { id: 'tv_10765', genreId: 10765, title: tr('Sci-Fi & Fantasy', 'מדע בדיוני ופנטזיה') },
    { id: 'tv_16',    genreId: 16,    title: tr('Animation', 'אנימציה') },
    { id: 'tv_9648',  genreId: 9648,  title: tr('Mystery', 'מסתורין') },
    { id: 'tv_99',    genreId: 99,    title: tr('Documentary', 'דוקומנטרי') },
  ];

  /* ── Studio list for ribbon ──────────────────────────────────────── */
  const STUDIO_LIST = [
    { key: 'all',       name: tr('All', 'הכל'),    icon: '📺' },
    { key: 'netflix',   name: 'Netflix',            icon: '🔴' },
    { key: 'hbo',       name: 'HBO / Max',          icon: '💜' },
    { key: 'amazon',    name: 'Amazon',             icon: '📦' },
    { key: 'apple',     name: 'Apple TV+',          icon: '🍎' },
    { key: 'disney',    name: 'Disney+',            icon: '🏰' },
    { key: 'paramount', name: 'Paramount+',         icon: '⛰️' },
    { key: 'hulu',      name: 'Hulu',               icon: '💚' },
  ];

  /* ── Builders ────────────────────────────────────────────────────── */
  function skeletonCards(count = 8) {
    return Array.from({ length: count }, () =>
      `<div class="card-skeleton portrait"></div>`
    ).join('');
  }

  function buildCard(item) {
    const title  = escapeHtml(item.name || item.title || '');
    const year   = formatYear(item.first_air_date || item.release_date);
    const rating = item.vote_average ? item.vote_average.toFixed(1) : '';
    const poster = posterUrl(item.poster_path);

    return `
      <div class="card-portrait" data-id="${item.id}" data-type="tv" tabindex="0">
        <img class="card-poster" src="${poster}" alt="${title}" loading="lazy"
             onerror="this.src='assets/img/poster-placeholder.png'">
        ${rating ? `<div class="card-rating"><span class="star-icon">★</span> ${rating}</div>` : ''}
        <div class="card-play">
          <svg viewBox="0 0 24 24"><polygon points="8,5 19,12 8,19"/></svg>
        </div>
        <div class="card-info">
          <div class="card-title">${title}</div>
          <div class="card-year">${year}</div>
        </div>
      </div>`;
  }

  function buildRowShell(id, title) {
    return `
      <section class="content-section" id="srow-${id}" data-row-id="${id}">
        <div class="section-header">
          <h2 class="section-title">${escapeHtml(title)}</h2>
        </div>
        <div class="content-row">
          <button class="row-arrow left" aria-label="Scroll left">‹</button>
          <div class="content-row-scroll" data-page="1" data-total-pages="1">
            ${skeletonCards()}
          </div>
          <button class="row-arrow right" aria-label="Scroll right">›</button>
        </div>
      </section>`;
  }

  function buildRibbon() {
    const pills = STUDIO_LIST.map(s => `
      <button class="studio-pill${s.key === activeStudio ? ' active' : ''}"
              data-studio="${s.key}" tabindex="0">
        <span class="studio-pill-icon">${s.icon}</span>
        <span class="studio-pill-name">${escapeHtml(s.name)}</span>
      </button>
    `).join('');

    return `
      <div class="studio-ribbon">
        <div class="studio-ribbon-scroll">${pills}</div>
      </div>`;
  }

  /* ── Data Fetchers ───────────────────────────────────────────────── */
  async function fetchTvGenreRow(genreId, page = 1) {
    try {
      return await window.TMDB.discoverTv(genreId, page);
    } catch (e) {
      console.error('[Series] Genre fetch error:', e);
      return { results: [], total_pages: 1 };
    }
  }

  async function fetchStudioTvRow(studioKey, page = 1) {
    try {
      return await window.TMDB.discoverByStudio(studioKey, 'tv', page);
    } catch (e) {
      console.error('[Series] Studio fetch error:', e);
      return { results: [], total_pages: 1 };
    }
  }

  /* ── Row Population ──────────────────────────────────────────────── */
  async function populateGenreRow(def) {
    const rowId = def.id;
    if (loadedRowIds.has(rowId)) return;
    loadedRowIds.add(rowId);

    const section = document.getElementById(`srow-${rowId}`);
    if (!section) return;
    const scrollEl = section.querySelector('.content-row-scroll');
    if (!scrollEl) return;

    const data = await fetchTvGenreRow(def.genreId);
    if (!data.results?.length) { section.style.display = 'none'; return; }

    scrollEl.innerHTML = data.results
      .filter(r => r.poster_path)
      .map(item => buildCard(item))
      .join('');
    scrollEl.dataset.page = '1';
    scrollEl.dataset.totalPages = String(data.total_pages || 1);

    attachCardListeners(section);
    attachPagination(section, { type: 'genre', genreId: def.genreId, id: rowId });
  }

  async function populateStudioRow(studioKey, rowId) {
    if (loadedRowIds.has(rowId)) return;
    loadedRowIds.add(rowId);

    const section = document.getElementById(`srow-${rowId}`);
    if (!section) return;
    const scrollEl = section.querySelector('.content-row-scroll');
    if (!scrollEl) return;

    const data = await fetchStudioTvRow(studioKey);
    if (!data.results?.length) { section.style.display = 'none'; return; }

    scrollEl.innerHTML = data.results
      .filter(r => r.poster_path)
      .map(item => buildCard(item))
      .join('');
    scrollEl.dataset.page = '1';
    scrollEl.dataset.totalPages = String(data.total_pages || 1);

    attachCardListeners(section);
    attachPagination(section, { type: 'studio', studio: studioKey, id: rowId });
  }

  /* ── Pagination ──────────────────────────────────────────────────── */
  function attachPagination(section, def) {
    const scrollEl = section.querySelector('.content-row-scroll');
    if (!scrollEl) return;

    const onScroll = throttle(async () => {
      const { scrollLeft, scrollWidth, clientWidth } = scrollEl;
      if (Math.abs(scrollLeft) + clientWidth < scrollWidth - 300) return;
      const page  = parseInt(scrollEl.dataset.page, 10) || 1;
      const total = parseInt(scrollEl.dataset.totalPages, 10) || 1;
      if (page >= total || page >= 5) return;

      const reqKey = `${def.id}-${page + 1}`;
      if (activeRequests.has(reqKey)) return;
      activeRequests.set(reqKey, true);

      try {
        let data;
        if (def.type === 'genre') data = await fetchTvGenreRow(def.genreId, page + 1);
        else                      data = await fetchStudioTvRow(def.studio, page + 1);

        if (data.results?.length) {
          scrollEl.insertAdjacentHTML('beforeend',
            data.results.filter(r => r.poster_path).map(item => buildCard(item)).join(''));
          scrollEl.dataset.page = String(page + 1);
          attachCardListeners(section);
        }
      } catch (e) {
        console.warn('[Series] Pagination error:', e);
      } finally {
        activeRequests.delete(reqKey);
      }
    }, 600);

    scrollEl.addEventListener('scroll', onScroll);
  }

  /* ── Row Controls ────────────────────────────────────────────────── */
  function attachRowControls(section) {
    const scrollEl = section.querySelector('.content-row-scroll');
    const leftBtn  = section.querySelector('.row-arrow.left');
    const rightBtn = section.querySelector('.row-arrow.right');
    if (!scrollEl) return;

    if (leftBtn)  leftBtn.addEventListener('click', () => scrollEl.scrollBy({ left: -600, behavior: 'smooth' }));
    if (rightBtn) rightBtn.addEventListener('click', () => scrollEl.scrollBy({ left: 600, behavior: 'smooth' }));
  }

  /* ── Card Listeners ──────────────────────────────────────────────── */
  function attachCardListeners(parent) {
    parent.querySelectorAll('.card-portrait[data-id]').forEach(card => {
      if (card._serClick) return;
      card._serClick = true;
      card.addEventListener('click', () => {
        window.Router.navigate('details', {
          id: parseInt(card.dataset.id, 10),
          type: 'tv',
        });
      });
    });
  }

  /* ── Lazy Loading ────────────────────────────────────────────────── */
  function setupLazyRows(populateFn) {
    if (rowObserver) rowObserver.disconnect();

    rowObserver = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          populateFn(entry.target.dataset.rowId);
          rowObserver.unobserve(entry.target);
        }
      });
    }, { rootMargin: '300px 0px' });

    document.querySelectorAll('#series-rows .content-section[data-row-id]').forEach(el => {
      rowObserver.observe(el);
    });
  }

  /* ── Render "All" genres ─────────────────────────────────────────── */
  function renderAllGenres() {
    const rowsEl = document.getElementById('series-rows');
    if (!rowsEl) return;
    loadedRowIds.clear();

    rowsEl.innerHTML = TV_GENRE_ROWS.map(g => buildRowShell(g.id, g.title)).join('');
    rowsEl.querySelectorAll('.content-section').forEach(attachRowControls);

    TV_GENRE_ROWS.slice(0, 3).forEach(g => populateGenreRow(g));

    setupLazyRows((rowId) => {
      const def = TV_GENRE_ROWS.find(g => g.id === rowId);
      if (def) populateGenreRow(def);
    });
  }

  /* ── Render Studio Content ───────────────────────────────────────── */
  function renderStudioContent(studioKey) {
    const rowsEl = document.getElementById('series-rows');
    if (!rowsEl) return;
    loadedRowIds.clear();

    const studioName = STUDIOS[studioKey]?.name || studioKey;

    const studioRows = [
      { id: `${studioKey}_trending`, title: `${studioName} — ${tr('Trending', 'פופולרי')}` },
      { id: `${studioKey}_top`,     title: `${studioName} — ${tr('Top Rated', 'מדורגים')}` },
      { id: `${studioKey}_new`,     title: `${studioName} — ${tr('New Releases', 'חדשים')}` },
    ];

    rowsEl.innerHTML = studioRows.map(r => buildRowShell(r.id, r.title)).join('');
    rowsEl.querySelectorAll('.content-section').forEach(attachRowControls);

    studioRows.forEach(r => populateStudioRow(studioKey, r.id));
  }

  /* ── Studio Switch ───────────────────────────────────────────────── */
  function switchStudio(studioKey) {
    if (studioKey === activeStudio) return;
    activeStudio = studioKey;

    document.querySelectorAll('.studio-pill').forEach(pill => {
      pill.classList.toggle('active', pill.dataset.studio === studioKey);
    });

    const rowsEl = document.getElementById('series-rows');
    if (rowsEl) {
      rowsEl.style.opacity = '0';
      rowsEl.style.transform = 'translateY(10px)';

      setTimeout(() => {
        if (studioKey === 'all') renderAllGenres();
        else renderStudioContent(studioKey);

        requestAnimationFrame(() => {
          rowsEl.style.opacity = '1';
          rowsEl.style.transform = 'translateY(0)';
        });
      }, 200);
    }
  }

  /* ── Inject Shared Ribbon Styles ─────────────────────────────────── */
  function injectStyles() {
    // Reuse movies-screen-styles if already present
    if (document.getElementById('movies-screen-styles')) return;
    const style = document.createElement('style');
    style.id = 'movies-screen-styles';
    style.textContent = `
      .studio-ribbon {
        padding: 20px 48px 8px;
        position: sticky;
        top: 0;
        z-index: 10;
        background: linear-gradient(to bottom, var(--bg) 70%, transparent);
      }
      .studio-ribbon-scroll {
        display: flex;
        gap: 10px;
        overflow-x: auto;
        padding-bottom: 8px;
      }
      .studio-ribbon-scroll::-webkit-scrollbar { display: none; }
      .studio-pill {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 22px;
        border-radius: 30px;
        background: rgba(238,242,255,0.04);
        border: 1px solid var(--glass-border);
        font-size: 13px;
        font-weight: 600;
        color: var(--text-dim);
        white-space: nowrap;
        cursor: pointer;
        transition: all 0.25s ease;
        flex-shrink: 0;
      }
      .studio-pill:hover {
        background: rgba(238,242,255,0.08);
        color: var(--text);
        border-color: rgba(238,242,255,0.15);
      }
      .studio-pill.active {
        background: rgba(0,212,255,0.1);
        border-color: var(--cyan);
        color: var(--cyan);
        box-shadow: var(--glow-cyan);
      }
      .studio-pill-icon { font-size: 16px; }
      #series-rows {
        transition: opacity 0.2s ease, transform 0.2s ease;
      }
    `;
    document.head.appendChild(style);
  }

  /* ── Main Render ─────────────────────────────────────────────────── */
  async function render(el) {
    container = el;
    activeStudio = 'all';
    loadedRowIds.clear();
    activeRequests.clear();

    injectStyles();

    container.innerHTML = `
      <div class="series-page page">
        ${buildRibbon()}
        <div id="series-rows"></div>
      </div>
    `;

    container.querySelector('.studio-ribbon').addEventListener('click', (e) => {
      const pill = e.target.closest('.studio-pill');
      if (pill) switchStudio(pill.dataset.studio);
    });

    renderAllGenres();
  }

  /* ── Destroy ─────────────────────────────────────────────────────── */
  function destroy() {
    if (rowObserver) { rowObserver.disconnect(); rowObserver = null; }
    loadedRowIds.clear();
    activeRequests.clear();
    container = null;
  }

  /* ── Export ───────────────────────────────────────────────────────── */
  window.SeriesScreen = { render, destroy };
  window.Router.register('series', window.SeriesScreen);
})();
