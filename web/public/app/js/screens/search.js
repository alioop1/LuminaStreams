/**
 * LuminaStreams — Search Screen
 * Multi-search with TMDB, advanced filters, search history,
 * debounced input, responsive result grid, and pagination.
 */
(() => {
  'use strict';

  const { tr, debounce, escapeHtml, formatYear, formatRating, truncate } = window.Helpers;

  /* ── State ────────────────────────────────────────────────────────── */
  let currentQuery     = '';
  let currentPage      = 1;
  let totalPages       = 1;
  let results          = [];
  let isLoading        = false;
  let filtersVisible   = false;
  let historyVisible   = false;
  let destroyed        = false;

  const filters = {
    type: 'all',       // 'all' | 'movie' | 'tv'
    genre: null,
    yearMin: null,
    yearMax: null,
    ratingMin: null,
    language: null,
    sortBy: 'popularity.desc',
  };

  /* ── Genre list ──────────────────────────────────────────────────── */
  const ALL_GENRES = [
    { id: 28,    name: 'אקשן',              nameEn: 'Action' },
    { id: 12,    name: 'הרפתקאות',           nameEn: 'Adventure' },
    { id: 16,    name: 'אנימציה',            nameEn: 'Animation' },
    { id: 35,    name: 'קומדיה',             nameEn: 'Comedy' },
    { id: 80,    name: 'פשע',               nameEn: 'Crime' },
    { id: 99,    name: 'דוקומנטרי',          nameEn: 'Documentary' },
    { id: 18,    name: 'דרמה',              nameEn: 'Drama' },
    { id: 10751, name: 'משפחה',             nameEn: 'Family' },
    { id: 14,    name: 'פנטזיה',             nameEn: 'Fantasy' },
    { id: 36,    name: 'היסטוריה',            nameEn: 'History' },
    { id: 27,    name: 'אימה',              nameEn: 'Horror' },
    { id: 10402, name: 'מוזיקה',             nameEn: 'Music' },
    { id: 9648,  name: 'מסתורין',            nameEn: 'Mystery' },
    { id: 10749, name: 'רומנטיקה',           nameEn: 'Romance' },
    { id: 878,   name: 'מדע בדיוני',          nameEn: 'Sci-Fi' },
    { id: 53,    name: 'מותחן',             nameEn: 'Thriller' },
    { id: 10752, name: 'מלחמה',             nameEn: 'War' },
    { id: 37,    name: 'מערבון',             nameEn: 'Western' },
  ];

  const SORT_OPTIONS = [
    { value: 'popularity.desc',    label: tr('Popularity', 'פופולריות') },
    { value: 'vote_average.desc',  label: tr('Rating', 'דירוג') },
    { value: 'release_date.desc',  label: tr('Release Date', 'תאריך יציאה') },
    { value: 'original_title.asc', label: tr('Title', 'שם') },
  ];

  const LANGUAGES = [
    { code: null, label: tr('All', 'הכל') },
    { code: 'en', label: tr('English', 'אנגלית') },
    { code: 'he', label: tr('Hebrew', 'עברית') },
    { code: 'ar', label: tr('Arabic', 'ערבית') },
    { code: 'fr', label: tr('French', 'צרפתית') },
    { code: 'es', label: tr('Spanish', 'ספרדית') },
    { code: 'de', label: tr('German', 'גרמנית') },
    { code: 'ja', label: tr('Japanese', 'יפנית') },
    { code: 'ko', label: tr('Korean', 'קוריאנית') },
  ];

  /* ── Container ref ───────────────────────────────────────────────── */
  let containerEl = null;

  /* ── Debounced search ────────────────────────────────────────────── */
  const debouncedSearch = debounce((query) => {
    if (destroyed) return;
    currentQuery = query;
    currentPage = 1;
    results = [];
    performSearch();
  }, 300);

  /* ── Perform search / discover ───────────────────────────────────── */
  async function performSearch() {
    if (isLoading || destroyed) return;
    isLoading = true;
    renderLoadingState();

    try {
      let data;
      const hasFilters = filters.type !== 'all' || filters.genre || filters.yearMin ||
                         filters.ratingMin || filters.language || filters.sortBy !== 'popularity.desc';

      if (currentQuery && !hasFilters) {
        // Simple multi-search
        data = await window.TMDB.searchMulti(currentQuery, currentPage);

        // Save to history
        if (currentPage === 1 && currentQuery.trim()) {
          window.Storage.addSearchHistory(currentQuery.trim());
        }
      } else if (currentQuery && hasFilters) {
        // Search with type filter
        if (filters.type === 'movie') {
          data = await window.TMDB.searchMovies(currentQuery, currentPage);
        } else if (filters.type === 'tv') {
          data = await window.TMDB.searchTv(currentQuery, currentPage);
        } else {
          data = await window.TMDB.searchMulti(currentQuery, currentPage);
        }
        if (currentPage === 1 && currentQuery.trim()) {
          window.Storage.addSearchHistory(currentQuery.trim());
        }
      } else if (hasFilters) {
        // Discover with filters (no query)
        data = await window.TMDB.discoverWithFilters({
          type: filters.type === 'all' ? 'movie' : filters.type,
          genre: filters.genre,
          year: filters.yearMin,
          rating: filters.ratingMin,
          language: filters.language,
          sortBy: filters.sortBy,
          page: currentPage,
        });
      } else {
        // No query and no filters — show trending
        data = await window.TMDB.getTrending('all', currentPage);
      }

      const newResults = (data?.results || []).filter(item =>
        item.media_type !== 'person' && (item.poster_path || item.backdrop_path)
      );

      if (currentPage === 1) {
        results = newResults;
      } else {
        results = [...results, ...newResults];
      }

      totalPages = data?.total_pages || 1;
    } catch (err) {
      console.error('[Search] Error:', err);
    } finally {
      isLoading = false;
      renderResults();
    }
  }

  /* ── Build page HTML ─────────────────────────────────────────────── */
  function buildHTML() {
    return `
      <div class="search-page" style="animation:fadeIn 0.3s ease">
        <!-- Search bar -->
        <div class="search-bar-wrap" id="search-bar-wrap">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
          </svg>
          <input type="text" class="search-input" id="search-input"
            placeholder="${tr('Search movies, TV shows...', 'חפש סרטים, סדרות...')}"
            autocomplete="off" spellcheck="false" />
          <button class="search-clear" id="search-clear" aria-label="Clear">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>

          <!-- Search history dropdown -->
          <div class="search-history-dropdown hidden" id="search-history">
            <div class="search-history-header">
              <span style="font-size:12px;font-weight:600;color:rgba(238,242,255,0.4);text-transform:uppercase;letter-spacing:1px">
                ${tr('Recent Searches', 'חיפושים אחרונים')}
              </span>
              <button id="clear-history-btn" style="font-size:11px;color:var(--red);cursor:pointer;background:none;border:none">
                ${tr('Clear', 'נקה')}
              </button>
            </div>
            <div id="search-history-list"></div>
          </div>
        </div>

        <!-- Filter toggle + active filter pills -->
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:20px;flex-wrap:wrap">
          <button class="btn btn-secondary btn-sm" id="toggle-filters-btn" style="gap:6px">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/><line x1="8" y1="12" x2="20" y2="12"/><line x1="12" y1="18" x2="20" y2="18"/>
              <circle cx="6" cy="12" r="2"/><circle cx="10" cy="18" r="2"/><circle cx="18" cy="6" r="2"/>
            </svg>
            ${tr('Filters', 'מסננים')}
          </button>
          <div id="active-filters" style="display:flex;gap:6px;flex-wrap:wrap"></div>
        </div>

        <!-- Main layout -->
        <div id="search-layout">
          <!-- Filters sidebar (hidden by default) -->
          <div class="search-filters hidden" id="filters-panel" style="
            background:var(--glass-bg);backdrop-filter:blur(20px);
            border:1px solid var(--glass-border);border-radius:var(--radius-lg);
            padding:20px;position:sticky;top:20px;align-self:start;
            animation:fadeIn 0.2s ease;
          ">
            <!-- Type filter -->
            <div>
              <div class="filter-group-title">${tr('Type', 'סוג')}</div>
              ${['all', 'movie', 'tv'].map(t => `
                <div class="filter-option ${filters.type === t ? 'active' : ''}" data-filter="type" data-value="${t}">
                  <div class="filter-checkbox">${filters.type === t ? '✓' : ''}</div>
                  <span>${t === 'all' ? tr('All', 'הכל') : t === 'movie' ? tr('Movies', 'סרטים') : tr('TV Shows', 'סדרות')}</span>
                </div>
              `).join('')}
            </div>

            <!-- Genre filter -->
            <div>
              <div class="filter-group-title">${tr('Genre', 'ז\'אנר')}</div>
              <select class="settings-select" id="filter-genre" style="width:100%">
                <option value="">${tr('All Genres', 'כל הז\'אנרים')}</option>
                ${ALL_GENRES.map(g => `
                  <option value="${g.id}" ${filters.genre == g.id ? 'selected' : ''}>
                    ${tr(g.nameEn, g.name)}
                  </option>
                `).join('')}
              </select>
            </div>

            <!-- Year range -->
            <div>
              <div class="filter-group-title">${tr('Year', 'שנה')}</div>
              <div style="display:flex;gap:8px;align-items:center">
                <input type="number" class="form-input" id="filter-year-min"
                  placeholder="${tr('From', 'מ-')}" min="1900" max="2030"
                  value="${filters.yearMin || ''}" style="padding:8px 10px;font-size:13px;width:50%" />
                <span style="color:var(--text-muted)">—</span>
                <input type="number" class="form-input" id="filter-year-max"
                  placeholder="${tr('To', 'עד')}" min="1900" max="2030"
                  value="${filters.yearMax || ''}" style="padding:8px 10px;font-size:13px;width:50%" />
              </div>
            </div>

            <!-- Rating -->
            <div>
              <div class="filter-group-title">${tr('Min Rating', 'דירוג מינימלי')}</div>
              <div style="display:flex;align-items:center;gap:10px">
                <input type="range" min="0" max="9" step="0.5" value="${filters.ratingMin || 0}"
                  id="filter-rating" style="flex:1;accent-color:var(--cyan)" />
                <span id="filter-rating-label" style="font-size:13px;font-weight:600;min-width:28px;color:var(--gold)">
                  ${filters.ratingMin || 0}
                </span>
              </div>
            </div>

            <!-- Language -->
            <div>
              <div class="filter-group-title">${tr('Language', 'שפה')}</div>
              <select class="settings-select" id="filter-language" style="width:100%">
                ${LANGUAGES.map(l => `
                  <option value="${l.code || ''}" ${filters.language === l.code ? 'selected' : ''}>
                    ${l.label}
                  </option>
                `).join('')}
              </select>
            </div>

            <!-- Sort -->
            <div>
              <div class="filter-group-title">${tr('Sort By', 'מיין לפי')}</div>
              <select class="settings-select" id="filter-sort" style="width:100%">
                ${SORT_OPTIONS.map(s => `
                  <option value="${s.value}" ${filters.sortBy === s.value ? 'selected' : ''}>
                    ${s.label}
                  </option>
                `).join('')}
              </select>
            </div>

            <!-- Apply button -->
            <button class="btn btn-primary" id="apply-filters-btn" style="width:100%;margin-top:8px">
              ${tr('Apply Filters', 'החל מסננים')}
            </button>
            <button class="btn btn-ghost btn-sm" id="reset-filters-btn" style="width:100%;margin-top:4px">
              ${tr('Reset', 'אפס')}
            </button>
          </div>

          <!-- Results area -->
          <div id="search-results-area">
            <div class="search-results" id="search-results-grid"></div>
            <div id="search-load-more" style="text-align:center;padding:32px 0"></div>
          </div>
        </div>
      </div>
    `;
  }

  /* ── Render a single card ────────────────────────────────────────── */
  function renderCard(item) {
    const type = item.media_type || (item.first_air_date || (item.name && !item.title) ? 'tv' : 'movie');
    const title = item.title || item.name || '';
    const year = formatYear(item.release_date || item.first_air_date);
    const rating = item.vote_average;
    const poster = item.poster_path
      ? window.CONST.posterUrl(item.poster_path)
      : null;

    const card = document.createElement('div');
    card.className = 'card-portrait';
    card.style.animation = 'fadeIn 0.3s ease';
    card.innerHTML = `
      ${poster
        ? `<img class="card-poster" src="${poster}" alt="${escapeHtml(title)}" loading="lazy"
            onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 200 300%22><rect fill=%22%230D1525%22 width=%22200%22 height=%22300%22/><text x=%2250%25%22 y=%2250%25%22 fill=%22%23EEF2FF20%22 font-size=%2240%22 text-anchor=%22middle%22 dominant-baseline=%22middle%22>🎬</text></svg>'" />`
        : `<div class="card-poster" style="width:100%;height:222px;background:var(--navy);display:flex;align-items:center;justify-content:center;border-radius:var(--radius-md)">
            <span style="font-size:40px;opacity:0.15">🎬</span>
          </div>`
      }
      ${rating > 0 ? `
        <div class="card-rating">
          <span class="star-icon">★</span>
          ${Number(rating).toFixed(1)}
        </div>
      ` : ''}
      <div class="card-play">
        <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21"/></svg>
      </div>
      <span style="
        position:absolute;top:8px;right:8px;padding:2px 7px;font-size:10px;
        font-weight:700;border-radius:4px;letter-spacing:0.5px;z-index:2;
        background:${type === 'movie' ? 'var(--cyan)' : 'var(--purple)'};
        color:var(--bg);
      ">${type === 'movie' ? tr('Movie', 'סרט') : tr('TV', 'סדרה')}</span>
      <div class="card-info">
        <div class="card-title">${escapeHtml(title)}</div>
        <div class="card-year">${year}</div>
      </div>
    `;

    card.addEventListener('click', () => {
      window.Router.navigate('details', {
        id: item.id,
        type: type === 'movie' ? 'movie' : 'tv',
      });
    });

    return card;
  }

  /* ── Render results ──────────────────────────────────────────────── */
  function renderResults() {
    if (destroyed) return;
    const grid = document.getElementById('search-results-grid');
    const loadMore = document.getElementById('search-load-more');
    if (!grid) return;

    grid.innerHTML = '';

    if (results.length === 0 && !isLoading) {
      grid.innerHTML = `
        <div class="search-empty">
          <div class="search-empty-icon">🔍</div>
          <div class="search-empty-text">${tr('No results found', 'לא נמצאו תוצאות')}</div>
          <div class="search-empty-hint">
            ${currentQuery
              ? tr('Try different keywords or adjust filters', 'נסה מילות חיפוש אחרות או שנה מסננים')
              : tr('Start typing to search', 'התחל להקליד כדי לחפש')
            }
          </div>
        </div>
      `;
      if (loadMore) loadMore.innerHTML = '';
      return;
    }

    results.forEach(item => grid.appendChild(renderCard(item)));

    // Load more button
    if (loadMore) {
      if (currentPage < totalPages && results.length > 0) {
        loadMore.innerHTML = `
          <button class="btn btn-secondary" id="load-more-btn">
            ${tr('Load More', 'טען עוד')}
          </button>
        `;
        document.getElementById('load-more-btn')?.addEventListener('click', () => {
          currentPage++;
          performSearch();
        });
      } else {
        loadMore.innerHTML = '';
      }
    }
  }

  /* ── Loading state ───────────────────────────────────────────────── */
  function renderLoadingState() {
    if (destroyed) return;
    const grid = document.getElementById('search-results-grid');
    if (!grid || currentPage > 1) return; // Don't clear grid on "load more"

    grid.innerHTML = '';
    for (let i = 0; i < 12; i++) {
      const skel = document.createElement('div');
      skel.className = 'card-skeleton portrait';
      skel.style.animation = `fadeIn 0.15s ease ${i * 0.03}s both`;
      grid.appendChild(skel);
    }
  }

  /* ── Search history ──────────────────────────────────────────────── */
  function renderHistory() {
    const listEl = document.getElementById('search-history-list');
    const dropdown = document.getElementById('search-history');
    if (!listEl || !dropdown) return;

    const history = window.Storage.getSearchHistory();
    if (history.length === 0) {
      dropdown.classList.add('hidden');
      return;
    }

    listEl.innerHTML = '';
    history.slice(0, 10).forEach(term => {
      const item = document.createElement('div');
      item.style.cssText = `
        padding:10px 16px;cursor:pointer;display:flex;align-items:center;gap:10px;
        font-size:13px;color:var(--text-dim);transition:background 0.15s ease;
      `;
      item.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity:0.4;flex-shrink:0">
          <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
        </svg>
        <span style="flex:1">${escapeHtml(term)}</span>
      `;
      item.addEventListener('mouseenter', () => item.style.background = 'rgba(238,242,255,0.05)');
      item.addEventListener('mouseleave', () => item.style.background = 'none');
      item.addEventListener('click', () => {
        const input = document.getElementById('search-input');
        if (input) input.value = term;
        dropdown.classList.add('hidden');
        updateSearchBarState(term);
        currentQuery = term;
        currentPage = 1;
        results = [];
        performSearch();
      });
      listEl.appendChild(item);
    });

    if (historyVisible) dropdown.classList.remove('hidden');
  }

  /* ── Update search bar UI state ──────────────────────────────────── */
  function updateSearchBarState(value) {
    const wrap = document.getElementById('search-bar-wrap');
    if (wrap) {
      wrap.classList.toggle('has-value', !!value);
    }
  }

  /* ── Active filter pills ─────────────────────────────────────────── */
  function renderActiveFilters() {
    const container = document.getElementById('active-filters');
    if (!container) return;
    container.innerHTML = '';

    const pills = [];
    if (filters.type !== 'all') {
      pills.push({
        label: filters.type === 'movie' ? tr('Movies', 'סרטים') : tr('TV Shows', 'סדרות'),
        clear: () => { filters.type = 'all'; }
      });
    }
    if (filters.genre) {
      const g = ALL_GENRES.find(g => g.id == filters.genre);
      pills.push({
        label: g ? tr(g.nameEn, g.name) : `Genre ${filters.genre}`,
        clear: () => { filters.genre = null; }
      });
    }
    if (filters.yearMin) {
      pills.push({
        label: `${tr('From', 'מ-')} ${filters.yearMin}`,
        clear: () => { filters.yearMin = null; }
      });
    }
    if (filters.ratingMin && filters.ratingMin > 0) {
      pills.push({
        label: `★ ≥ ${filters.ratingMin}`,
        clear: () => { filters.ratingMin = null; }
      });
    }
    if (filters.language) {
      const l = LANGUAGES.find(l => l.code === filters.language);
      pills.push({
        label: l ? l.label : filters.language,
        clear: () => { filters.language = null; }
      });
    }

    pills.forEach(pill => {
      const el = document.createElement('span');
      el.style.cssText = `
        display:inline-flex;align-items:center;gap:6px;padding:4px 12px;
        font-size:12px;font-weight:500;border-radius:20px;
        background:rgba(0,212,255,0.1);color:var(--cyan);
        border:1px solid rgba(0,212,255,0.2);cursor:pointer;
        transition:all 0.15s ease;
      `;
      el.innerHTML = `${escapeHtml(pill.label)} <span style="font-size:14px;line-height:1">×</span>`;
      el.addEventListener('click', () => {
        pill.clear();
        currentPage = 1;
        results = [];
        renderActiveFilters();
        performSearch();
        rebuildFilterPanel();
      });
      container.appendChild(el);
    });
  }

  /* ── Rebuild filter panel values ─────────────────────────────────── */
  function rebuildFilterPanel() {
    const genreSelect = document.getElementById('filter-genre');
    const yearMin = document.getElementById('filter-year-min');
    const rating = document.getElementById('filter-rating');
    const langSelect = document.getElementById('filter-language');
    const sortSelect = document.getElementById('filter-sort');

    if (genreSelect) genreSelect.value = filters.genre || '';
    if (yearMin) yearMin.value = filters.yearMin || '';
    if (rating) rating.value = filters.ratingMin || 0;
    if (langSelect) langSelect.value = filters.language || '';
    if (sortSelect) sortSelect.value = filters.sortBy;

    // Update type filter options
    const typeOptions = document.querySelectorAll('[data-filter="type"]');
    typeOptions.forEach(opt => {
      const isActive = opt.dataset.value === filters.type;
      opt.classList.toggle('active', isActive);
      opt.querySelector('.filter-checkbox').textContent = isActive ? '✓' : '';
    });

    const ratingLabel = document.getElementById('filter-rating-label');
    if (ratingLabel) ratingLabel.textContent = filters.ratingMin || 0;
  }

  /* ── Bind events ─────────────────────────────────────────────────── */
  function bindEvents() {
    const input = document.getElementById('search-input');
    const clearBtn = document.getElementById('search-clear');
    const toggleBtn = document.getElementById('toggle-filters-btn');
    const applyBtn = document.getElementById('apply-filters-btn');
    const resetBtn = document.getElementById('reset-filters-btn');
    const clearHistoryBtn = document.getElementById('clear-history-btn');
    const historyDropdown = document.getElementById('search-history');

    // Input
    input?.addEventListener('input', (e) => {
      const val = e.target.value;
      updateSearchBarState(val);
      debouncedSearch(val);
    });

    input?.addEventListener('focus', () => {
      historyVisible = true;
      renderHistory();
    });

    // Hide history on click outside
    document.addEventListener('click', (e) => {
      if (!e.target.closest('#search-bar-wrap')) {
        historyVisible = false;
        historyDropdown?.classList.add('hidden');
      }
    });

    // Clear input
    clearBtn?.addEventListener('click', () => {
      if (input) input.value = '';
      updateSearchBarState('');
      currentQuery = '';
      currentPage = 1;
      results = [];
      performSearch();
    });

    // Toggle filters
    toggleBtn?.addEventListener('click', () => {
      filtersVisible = !filtersVisible;
      const panel = document.getElementById('filters-panel');
      const layout = document.getElementById('search-layout');
      if (panel) panel.classList.toggle('hidden', !filtersVisible);
      if (layout) {
        layout.style.display = 'grid';
        layout.style.gridTemplateColumns = filtersVisible ? '240px 1fr' : '1fr';
        layout.style.gap = '24px';
      }
    });

    // Type filter clicks
    document.querySelectorAll('[data-filter="type"]').forEach(opt => {
      opt.addEventListener('click', () => {
        filters.type = opt.dataset.value;
        rebuildFilterPanel();
      });
    });

    // Rating slider
    const ratingSlider = document.getElementById('filter-rating');
    const ratingLabel = document.getElementById('filter-rating-label');
    ratingSlider?.addEventListener('input', (e) => {
      if (ratingLabel) ratingLabel.textContent = e.target.value;
    });

    // Apply filters
    applyBtn?.addEventListener('click', () => {
      const genreEl = document.getElementById('filter-genre');
      const yearMinEl = document.getElementById('filter-year-min');
      const yearMaxEl = document.getElementById('filter-year-max');
      const ratingEl = document.getElementById('filter-rating');
      const langEl = document.getElementById('filter-language');
      const sortEl = document.getElementById('filter-sort');

      filters.genre = genreEl?.value || null;
      filters.yearMin = yearMinEl?.value ? parseInt(yearMinEl.value) : null;
      filters.yearMax = yearMaxEl?.value ? parseInt(yearMaxEl.value) : null;
      filters.ratingMin = ratingEl?.value ? parseFloat(ratingEl.value) : null;
      filters.language = langEl?.value || null;
      filters.sortBy = sortEl?.value || 'popularity.desc';

      currentPage = 1;
      results = [];
      renderActiveFilters();
      performSearch();
    });

    // Reset filters
    resetBtn?.addEventListener('click', () => {
      filters.type = 'all';
      filters.genre = null;
      filters.yearMin = null;
      filters.yearMax = null;
      filters.ratingMin = null;
      filters.language = null;
      filters.sortBy = 'popularity.desc';

      currentPage = 1;
      results = [];
      rebuildFilterPanel();
      renderActiveFilters();
      performSearch();
    });

    // Clear history
    clearHistoryBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      window.Storage.clearSearchHistory();
      renderHistory();
    });

    // Auto-focus
    requestAnimationFrame(() => input?.focus());
  }

  /* ── Render ──────────────────────────────────────────────────────── */
  async function render(container, p) {
    destroyed = false;
    containerEl = container;
    currentPage = 1;
    results = [];

    container.innerHTML = buildHTML();

    // Inject styles for search history dropdown
    let styleEl = document.getElementById('search-screen-styles');
    if (!styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = 'search-screen-styles';
      styleEl.textContent = `
        .search-history-dropdown {
          position: absolute;
          top: calc(100% + 4px);
          left: 0;
          right: 0;
          background: rgba(13,21,37,0.95);
          backdrop-filter: blur(20px);
          border: 1px solid var(--glass-border);
          border-radius: var(--radius-md);
          z-index: 50;
          box-shadow: var(--shadow-heavy);
          overflow: hidden;
          max-height: 360px;
          overflow-y: auto;
        }
        .search-history-dropdown.hidden { display: none; }
        .search-history-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 12px 16px 8px;
        }
        #search-layout {
          display: grid;
          grid-template-columns: 1fr;
          gap: 24px;
        }
      `;
      document.head.appendChild(styleEl);
    }

    bindEvents();

    // If params include a query, pre-fill
    if (p?.query) {
      const input = document.getElementById('search-input');
      if (input) input.value = p.query;
      currentQuery = p.query;
      updateSearchBarState(p.query);
      performSearch();
    } else {
      // Show trending by default
      performSearch();
    }
  }

  /* ── Destroy ─────────────────────────────────────────────────────── */
  function destroy() {
    destroyed = true;
    debouncedSearch.cancel?.();
    currentQuery = '';
    currentPage = 1;
    results = [];
    filtersVisible = false;
    historyVisible = false;
    containerEl = null;

    // Clean up injected styles
    const styleEl = document.getElementById('search-screen-styles');
    if (styleEl) styleEl.remove();
  }

  /* ── Register ────────────────────────────────────────────────────── */
  window.SearchScreen = { render, destroy };
  window.Router.register('search', window.SearchScreen);
})();
