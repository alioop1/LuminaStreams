/**
 * LuminaStreams — PS5 Cinematic Home Screen
 * Matches 1:1 the Android TV Compose Home UI (HomeScreen.kt, HomeHero.kt, HomeRows.kt, HomeCards.kt).
 * Features:
 *  - Full-bleed cinematic original TMDB backdrop with directional gradients
 *  - Real-time enriched Hero section: transparent movie logo, runtime, age rating, year, genre,
 *    HBO Max-style dynamic vertical gradient bar next to Hebrew synopsis, and circular cast headshots
 *  - Bottom Console Cycler: Right category column ("סרטים פופולריים", HBO, Netflix, Apple TV+, etc.)
 *    and horizontal 16:9 landscape cards carousel with 3px solid white glowing border on active item.
 *  - Responsive fluid adaptation for iPhone 15 Pro Max (both portrait & landscape).
 */
(() => {
  'use strict';

  const { posterUrl, backdropUrl, profileUrl, originalUrl, MOVIE_GENRES, TV_GENRES } = window.CONST;
  const { escapeHtml, truncate, formatYear } = window.Helpers;
  const { tr } = window.I18n;

  /* ── Category Definitions ────────────────────────────────────────── */
  const CATEGORIES = [
    { id: 'trending_m',   title: 'סרטים פופולריים',   titleEn: 'Popular Movies', type: 'trending_movie' },
    { id: 'hbo',          title: 'בלעדי ל-HBO',       titleEn: 'HBO Exclusives', type: 'studio', studio: 'hbo', mediaType: 'tv' },
    { id: 'netflix',      title: 'מקור של נטפליקס',   titleEn: 'Netflix Originals', type: 'studio', studio: 'netflix', mediaType: 'movie' },
    { id: 'trending_tv',  title: 'סדרות פופולריות',    titleEn: 'Popular Series', type: 'trending_tv' },
    { id: 'amazon',       title: 'מקור של אמזון',      titleEn: 'Amazon Originals', type: 'studio', studio: 'amazon', mediaType: 'movie' },
    { id: 'apple',        title: 'מקור של אפל TV',    titleEn: 'Apple TV+ Originals', type: 'studio', studio: 'apple', mediaType: 'tv' },
    { id: 'genre_28',     title: 'סרטי אקשן',          titleEn: 'Action Movies', type: 'genre', genreId: 28 },
    { id: 'genre_35',     title: 'קומדיה',             titleEn: 'Comedy', type: 'genre', genreId: 35 },
  ];

  /* ── State ────────────────────────────────────────────────────────── */
  let currentCategoryIndex = 0;
  let categoryCache = new Map();
  let activeMoviesList = [];
  let currentActiveMovieId = null;
  let enrichAbortController = null;
  let rootContainer = null;

  /* ── Helper: Format runtime in Hebrew (matches Android tr("h", "שע")) ── */
  function formatHebrewRuntime(minutes) {
    if (!minutes || minutes <= 0) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    const isEn = (window.I18n && window.I18n.getLanguage ? window.I18n.getLanguage() : 'he') !== 'he';
    if (h > 0 && m > 0) return isEn ? `${h}h ${m}m` : `${h}שע ${m}ד`;
    if (h > 0) return isEn ? `${h}h` : `${h}שע`;
    return isEn ? `${m}m` : `${m}ד`;
  }

  /* ── Fetch Category Movies ────────────────────────────────────────── */
  async function fetchCategoryItems(cat) {
    if (categoryCache.has(cat.id)) {
      return categoryCache.get(cat.id);
    }

    let items = [];
    try {
      if (cat.type === 'trending_movie') {
        const res = await window.TMDB.getTrending('movie', 1);
        items = (res.results || []).map(m => ({ ...m, media_type: 'movie' }));
      } else if (cat.type === 'trending_tv') {
        const res = await window.TMDB.getTrending('tv', 1);
        items = (res.results || []).map(m => ({ ...m, media_type: 'tv' }));
      } else if (cat.type === 'studio') {
        const res = await window.TMDB.discoverByStudio(cat.studio, cat.mediaType || 'movie', 1);
        items = (res.results || []).map(m => ({ ...m, media_type: cat.mediaType || 'movie' }));
      } else if (cat.type === 'genre') {
        const res = await window.TMDB.discoverMovies(cat.genreId, 1);
        items = (res.results || []).map(m => ({ ...m, media_type: 'movie' }));
      }
    } catch (err) {
      console.warn('[HomeScreen] Failed to fetch category:', cat.id, err);
      items = [];
    }

    // Filter items with backdrops or posters
    items = items.filter(m => (m.backdrop_path || m.poster_path));
    categoryCache.set(cat.id, items);
    return items;
  }

  /* ── Build Shell HTML ─────────────────────────────────────────────── */
  function buildHomeShell() {
    return `
      <div class="ps5-home-layout">
        <!-- 1. Full-bleed Background Backdrop Layer -->
        <div class="ps5-backdrop-layer">
          <div id="ps5-backdrop-img-prev" class="ps5-backdrop-img"></div>
          <div id="ps5-backdrop-img" class="ps5-backdrop-img active"></div>
          <div class="ps5-vignette-horiz"></div>
          <div class="ps5-vignette-vert"></div>
          <div class="ps5-vignette-top"></div>
        </div>

        <!-- 2. Hero Content Section (Right side in RTL) -->
        <div class="ps5-hero-container">
          <div class="ps5-hero-overlay" id="ps5-hero-overlay">
            <!-- Title / Logo Area -->
            <div class="ps5-hero-title-box" id="ps5-hero-title-box">
              <h1 class="ps5-hero-title skeleton-text" style="width: 320px; height: 50px;"></h1>
            </div>

            <!-- Metadata Row -->
            <div class="ps5-hero-meta" id="ps5-hero-meta">
              <span class="ps5-meta-item skeleton-text" style="width: 180px; height: 20px;"></span>
            </div>

            <!-- Synopsis with Dynamic Vertical Gradient Bar -->
            <div class="ps5-hero-synopsis-box" id="ps5-hero-synopsis-box">
              <div class="ps5-gradient-accent-line"></div>
              <p class="ps5-hero-synopsis" id="ps5-hero-synopsis"></p>
            </div>

            <!-- Cast Row -->
            <div class="ps5-hero-cast-box" id="ps5-hero-cast-box"></div>

            <!-- Hero Action Buttons (צפה עכשיו / פרטים) -->
            <div class="ps5-hero-actions" id="ps5-hero-actions">
              <button class="ps5-btn-hero-play" id="hero-btn-play" title="${tr('Watch Now', 'צפה עכשיו')}">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="6,3 20,12 6,21"/>
                </svg>
                <span>${tr('Watch Now', 'צפה עכשיו')}</span>
              </button>
              <button class="ps5-btn-hero-info" id="hero-btn-info" title="${tr('More Info', 'פרטים נוספים')}">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="16" x2="12" y2="12"></line>
                  <line x1="12" y1="8" x2="12.01" y2="8"></line>
                </svg>
                <span>${tr('Details', 'פרטים')}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 3. Bottom Console Cycler (Categories on Right, 16:9 Landscape Carousel on Left) -->
        <div class="ps5-cycler-container">
          <!-- Categories Sidebar -->
          <div class="ps5-cycler-categories" id="ps5-cycler-categories">
            ${CATEGORIES.map((cat, idx) => `
              <button class="ps5-category-btn${idx === 0 ? ' active' : ''}" data-cat-idx="${idx}">
                ${escapeHtml(tr(cat.titleEn, cat.title))}
              </button>
            `).join('')}
          </div>

          <!-- Divider Line -->
          <div class="ps5-cycler-divider"></div>

          <!-- Horizontal 16:9 Landscape Cards Carousel -->
          <div class="ps5-cycler-cards-viewport" id="ps5-cycler-cards-viewport">
            <div class="ps5-cycler-cards-track" id="ps5-cycler-cards-track">
              <!-- Cards rendered here -->
            </div>
          </div>
        </div>
      </div>
    `;
  }

  // On the TV, heavy hero work (4K backdrop, details request) waits for the
  // remote to rest on a card this long.
  const IS_TV = !!(window.TvMode && window.TvMode.isTv);
  const SETTLE_MS = 220;

  /* ── Render 16:9 Landscape Card ──────────────────────────────────── */
  function renderLandscapeCard(movie, index, isActive = false) {
    const title = escapeHtml(movie.title || movie.name || '');
    const isTv = movie.media_type === 'tv' || (movie.first_air_date && !movie.release_date);
    const typeLabel = isTv ? tr('Series', 'סדרה') : tr('Movie', 'סרט');
    const rating = movie.vote_average ? movie.vote_average.toFixed(1) : '';

    // Backdrop prefered for 16:9; fallback to poster
    const imgPath = movie.backdrop_path || movie.poster_path;
    const thumbUrl = imgPath ? `https://image.tmdb.org/t/p/w780${imgPath}` : 'assets/img/backdrop-placeholder.svg';

    return `
      <div class="ps5-landscape-card${isActive ? ' active' : ''}" 
           data-id="${movie.id}" 
           data-idx="${index}" 
           data-type="${isTv ? 'tv' : 'movie'}" 
           tabindex="0"
           role="button"
           aria-label="${title}">
        <div class="ps5-card-media">
          <img class="ps5-card-img" src="${thumbUrl}" alt="${title}" loading="lazy" />
          ${rating ? `<div class="ps5-card-rating">★ ${rating}</div>` : ''}
          <div class="ps5-card-overlay">
            <div class="ps5-card-title">${title}</div>
            <div class="ps5-card-sub">${typeLabel}</div>
          </div>
        </div>
      </div>
    `;
  }

  /* ── Update Hero in Real-Time ─────────────────────────────────────── */
  async function updateHero(movie) {
    if (!movie || movie.id === currentActiveMovieId) return;
    currentActiveMovieId = movie.id;

    if (enrichAbortController) {
      enrichAbortController.abort();
    }
    enrichAbortController = new AbortController();
    const signal = enrichAbortController.signal;

    const title = movie.title || movie.name || '';
    const isTv = movie.media_type === 'tv' || (movie.first_air_date && !movie.release_date);
    const mediaType = isTv ? 'tv' : 'movie';
    const year = formatYear(movie.release_date || movie.first_air_date);
    const rating = movie.vote_average ? movie.vote_average.toFixed(1) : '';
    const initialOverview = movie.overview || '';

    // 1. Fast update: backdrop image
    const bgImgEl = document.getElementById('ps5-backdrop-img');
    const bgImgPrevEl = document.getElementById('ps5-backdrop-img-prev');
    const bgPath = movie.backdrop_path || movie.poster_path;
    if (bgImgEl && bgPath) {
      if (bgImgPrevEl) {
        bgImgPrevEl.style.backgroundImage = bgImgEl.style.backgroundImage;
      }
      // Background images have no <img> error event, so fall back to the proxy here.
      const showBackdrop = (url, isRetry) => {
        const preloader = new Image();
        preloader.onload = () => {
          // Decode off the main thread first, so the swap never stalls a frame.
          const decoded = preloader.decode ? preloader.decode().catch(() => {}) : Promise.resolve();
          decoded.then(() => {
            if (currentActiveMovieId === movie.id && bgImgEl) {
              bgImgEl.style.backgroundImage = `url("${url}")`;
            }
          });
        };
        preloader.onerror = () => {
          if (!isRetry && url.includes('image.tmdb.org')) {
            window.CONST.markTmdbBlocked();
            showBackdrop(window.CONST.heroUrl(bgPath), true);
          }
        };
        preloader.src = url;
      };
      // TV: only for the card the remote stops on, not every card it passes.
      if (IS_TV) setTimeout(() => { if (currentActiveMovieId === movie.id) showBackdrop(window.CONST.heroUrl(bgPath), false); }, SETTLE_MS);
      else showBackdrop(window.CONST.heroUrl(bgPath), false);
    }

    // 2. Fast update: Title fallback
    const titleBox = document.getElementById('ps5-hero-title-box');
    if (titleBox) {
      titleBox.innerHTML = `<h1 class="ps5-hero-title">${escapeHtml(title)}</h1>`;
    }

    // 3. Fast update: Metadata
    const metaBox = document.getElementById('ps5-hero-meta');
    if (metaBox) {
      const parts = [];
      if (rating) parts.push(`<span class="ps5-meta-rating">★ ${rating}</span>`);
      if (year && year !== '—') parts.push(`<span class="ps5-meta-year">${year}</span>`);
      parts.push(`<span class="ps5-meta-age">16+</span>`);
      const genreName = (movie.genre_ids && movie.genre_ids[0])
        ? (isTv ? TV_GENRES[movie.genre_ids[0]] : MOVIE_GENRES[movie.genre_ids[0]]) || ''
        : '';
      if (genreName) parts.push(`<span class="ps5-meta-genre">${escapeHtml(genreName)}</span>`);

      metaBox.innerHTML = parts.join(`<span class="ps5-meta-sep">|</span>`);
    }

    // 4. Fast update: Synopsis
    const synopsisBox = document.getElementById('ps5-hero-synopsis-box');
    const synopsisEl = document.getElementById('ps5-hero-synopsis');
    if (synopsisBox && synopsisEl) {
      if (initialOverview) {
        synopsisEl.textContent = initialOverview;
        synopsisBox.style.display = 'flex';
      } else {
        synopsisEl.textContent = '';
        synopsisBox.style.display = 'none';
      }
    }

    // 5. Async enrichment: fetch full details for Hebrew logo, exact runtime, and cast
    if (IS_TV) {
      await new Promise(r => setTimeout(r, SETTLE_MS));
      if (signal.aborted || currentActiveMovieId !== movie.id) return;
    }
    try {
      const details = mediaType === 'tv'
        ? await window.TMDB.getTvDetails(movie.id)
        : await window.TMDB.getMovieDetails(movie.id);

      if (signal.aborted || currentActiveMovieId !== movie.id) return;

      // Logo handling: transparent PNG logo from TMDB
      if (details.images && details.images.logos && details.images.logos.length > 0) {
        const logos = details.images.logos;
        const logoObj = logos.find(l => l.iso_639_1 === 'he')
          || logos.find(l => l.iso_639_1 === 'en')
          || logos[0];

        if (logoObj && logoObj.file_path && titleBox) {
          const logoUrl = `https://image.tmdb.org/t/p/w500${logoObj.file_path}`;
          titleBox.innerHTML = `
            <img class="ps5-hero-logo" src="${logoUrl}" alt="${escapeHtml(title)}" />
          `;
        }
      }

      // Enriched Runtime
      const runtimeMinutes = details.runtime || (details.episode_run_time && details.episode_run_time[0]) || 0;
      const runtimeStr = formatHebrewRuntime(runtimeMinutes);

      // Rebuild Enriched Meta Row
      if (metaBox) {
        const parts = [];
        if (rating) parts.push(`<span class="ps5-meta-rating">★ ${rating}</span>`);
        if (runtimeStr) parts.push(`<span class="ps5-meta-runtime">${runtimeStr}</span>`);
        parts.push(`<span class="ps5-meta-age">16+</span>`);
        if (year && year !== '—') parts.push(`<span class="ps5-meta-year">${year}</span>`);
        const fullGenre = (details.genres && details.genres[0]) ? details.genres[0].name : '';
        if (fullGenre) parts.push(`<span class="ps5-meta-genre">${escapeHtml(fullGenre)}</span>`);

        metaBox.innerHTML = parts.join(`<span class="ps5-meta-sep">|</span>`);
      }

      // Enriched Hebrew Synopsis (if details overview has more text)
      if (details.overview && synopsisEl && synopsisBox) {
        synopsisEl.textContent = details.overview;
        synopsisBox.style.display = 'flex';
      }

      // Enriched Cast Row (Top 4 actors with circular photos)
      const castBox = document.getElementById('ps5-hero-cast-box');
      if (castBox && details.credits && details.credits.cast && details.credits.cast.length > 0) {
        const topCast = details.credits.cast.slice(0, 4);
        castBox.innerHTML = `
          <span class="ps5-cast-label">${tr('Starring:', 'בכיכובם של:')}</span>
          <div class="ps5-cast-list">
            ${topCast.map(actor => {
              const photo = actor.profile_path
                ? `https://image.tmdb.org/t/p/w185${actor.profile_path}`
                : 'assets/img/avatar-placeholder.svg';
              return `
                <div class="ps5-cast-member">
                  <img class="ps5-cast-avatar" src="${photo}" alt="${escapeHtml(actor.name)}" />
                  <span class="ps5-cast-name">${escapeHtml(actor.name)}</span>
                </div>
              `;
            }).join('')}
          </div>
        `;
        castBox.style.display = 'flex';
      } else if (castBox) {
        castBox.innerHTML = '';
        castBox.style.display = 'none';
      }
    } catch (err) {
      if (!signal.aborted) {
        console.warn('[HomeScreen] Enrichment warning:', err);
      }
    }
  }

  /* ── Switch Category ──────────────────────────────────────────────── */
  async function selectCategory(catIndex) {
    currentCategoryIndex = catIndex;
    const cat = CATEGORIES[catIndex];
    if (!cat) return;

    // Update Category Sidebar Buttons
    const catButtons = document.querySelectorAll('.ps5-category-btn');
    catButtons.forEach(btn => {
      const idx = parseInt(btn.dataset.catIdx, 10);
      if (idx === catIndex) {
        btn.classList.add('active');
        btn.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      } else {
        btn.classList.remove('active');
      }
    });

    // Remote focus stays on the row while it reloads (skeleton, then the first card).
    const keepFocus = !!(document.activeElement && document.activeElement.closest &&
      document.activeElement.closest('.ps5-landscape-card'));

    // Show skeletons in carousel track
    const track = document.getElementById('ps5-cycler-cards-track');
    if (track) {
      track.innerHTML = Array.from({ length: 6 }, (_, i) => `
        <div class="ps5-landscape-card skeleton${i === 0 ? ' active' : ''}"${i === 0 ? ' tabindex="-1"' : ''}>
          <div class="ps5-card-media"></div>
        </div>
      `).join('');
      if (keepFocus) track.firstElementChild.focus({ preventScroll: true });
    }

    // Fetch items
    const movies = await fetchCategoryItems(cat);
    activeMoviesList = movies;

    if (!track) return;

    if (movies.length === 0) {
      track.innerHTML = `<div class="ps5-empty-row">${tr('No items found', 'לא נמצאו פריטים')}</div>`;
      return;
    }

    // Render cards
    track.innerHTML = movies.map((m, i) => renderLandscapeCard(m, i, i === 0)).join('');

    // Setup first item as active
    currentActiveMovieId = null;
    updateHero(movies[0]);

    // Scroll track back to start (via the first card: in RTL, older engines such as
    // webOS 6's put scrollLeft 0 at the far end of the row)
    const firstCard = track.querySelector('.ps5-landscape-card[data-idx]');
    if (firstCard) firstCard.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' });

    // Attach card event listeners
    _setupCardListeners();
    // (Replacing the skeleton dropped focus to <body> — unless the user moved on meanwhile.)
    const lost = !document.activeElement || document.activeElement === document.body || document.activeElement.classList.contains('skeleton');
    if (keepFocus && lost) {
      const first = track.querySelector('.ps5-landscape-card[data-idx]');
      if (first) first.focus({ preventScroll: true });
    }
  }

  /* ── Setup Card Listeners ─────────────────────────────────────────── */
  function _setupCardListeners() {
    const cards = document.querySelectorAll('.ps5-landscape-card[data-idx]');
    cards.forEach(card => {
      const idx = parseInt(card.dataset.idx, 10);
      const movie = activeMoviesList[idx];
      if (!movie) return;

      const activateCard = () => {
        cards.forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        updateHero(movie);
      };

      // Hover / Focus / Click (the remote handler calls it directly too)
      card._activate = activateCard;
      card.addEventListener('mouseenter', activateCard);
      card.addEventListener('focus', activateCard);

      // Tap / Click
      card.addEventListener('click', (e) => {
        if (!card.classList.contains('active')) {
          activateCard();
        } else {
          // Already active -> Navigate directly to Details screen!
          const type = movie.media_type || (movie.first_air_date ? 'tv' : 'movie');
          window.Router.navigate('details', { id: movie.id, type });
        }
      });
    });
  }

  /* ── Setup Category Button Listeners ──────────────────────────────── */
  function _setupCategoryListeners() {
    const catButtons = document.querySelectorAll('.ps5-category-btn');
    catButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const idx = parseInt(btn.dataset.catIdx, 10);
        selectCategory(idx);
      });
    });
  }

  /* ── Keyboard / Remote DPAD Navigation ────────────────────────────── */
  function _handleKeyDown(e) {
    // Focus in the top bar or on the hero buttons: the global remote/keyboard
    // navigation (tv/tvMode.js) moves between those, not the card rows.
    const focused = document.activeElement;
    // Down from the hero's buttons goes to the selected card of the row (Android TV).
    if (e.key === 'ArrowDown' && focused && focused.closest('.ps5-hero-actions, #hero-btn-play, #hero-btn-info')) {
      const card = document.querySelector('.ps5-landscape-card.active') || document.querySelector('.ps5-landscape-card[data-idx]');
      if (card) {
        e.preventDefault();
        card.focus();
        card.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
      return;
    }
    if (focused && focused !== document.body && !focused.closest('.ps5-landscape-card')) return;
    const isRtl = document.documentElement.getAttribute('dir') === 'rtl';
    const activeCard = document.querySelector('.ps5-landscape-card.active');
    const cards = Array.from(document.querySelectorAll('.ps5-landscape-card[data-idx]'));
    const currentIdx = activeCard ? cards.indexOf(activeCard) : 0;

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      const targetIdx = isRtl ? currentIdx - 1 : currentIdx + 1;
      if (targetIdx >= 0 && targetIdx < cards.length) {
        // Select and focus the card; a click would open it.
        if (cards[targetIdx]._activate) cards[targetIdx]._activate();
        cards[targetIdx].focus({ preventScroll: true });
        cards[targetIdx].scrollIntoView({ behavior: e.repeat ? 'auto' : 'smooth', inline: 'center', block: 'nearest' });
      }
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      const targetIdx = isRtl ? currentIdx + 1 : currentIdx - 1;
      if (targetIdx >= 0 && targetIdx < cards.length) {
        // Select and focus the card; a click would open it.
        if (cards[targetIdx]._activate) cards[targetIdx]._activate();
        cards[targetIdx].focus({ preventScroll: true });
        cards[targetIdx].scrollIntoView({ behavior: e.repeat ? 'auto' : 'smooth', inline: 'center', block: 'nearest' });
      }
    } else if (e.key === 'ArrowDown') {
      // Down arrow -> next category
      e.preventDefault();
      if (currentCategoryIndex < CATEGORIES.length - 1) {
        selectCategory(currentCategoryIndex + 1);
      }
    } else if (e.key === 'ArrowUp') {
      // Up arrow -> prev category, or from the first row to the hero's Play button
      e.preventDefault();
      if (currentCategoryIndex > 0) {
        selectCategory(currentCategoryIndex - 1);
      } else {
        const play = document.getElementById('hero-btn-play');
        if (play) play.focus();
      }
    } else if (e.key === 'Enter') {
      if (activeCard) {
        e.preventDefault();
        activeCard.click();
      }
    }
  }

  /* ── Setup Hero Action Buttons Listeners ─────────────────────────── */
  function _setupHeroActionListeners() {
    const playBtn = document.getElementById('hero-btn-play');
    const infoBtn = document.getElementById('hero-btn-info');

    const openHeroDetails = () => {
      const activeMovie = activeMoviesList.find(m => m.id === currentActiveMovieId) || activeMoviesList[0];
      if (activeMovie && window.Router) {
        const type = activeMovie.media_type || (activeMovie.first_air_date ? 'tv' : 'movie');
        window.Router.navigate('details', { id: activeMovie.id, type });
      }
    };

    if (playBtn) playBtn.addEventListener('click', openHeroDetails);
    if (infoBtn) infoBtn.addEventListener('click', openHeroDetails);
  }

  /* ── Screen Lifecycle ─────────────────────────────────────────────── */
  const HomeScreen = {
    async render(container, params = {}) {
      rootContainer = container;
      container.innerHTML = buildHomeShell();

      _setupCategoryListeners();
      _setupHeroActionListeners();
      document.addEventListener('keydown', _handleKeyDown);

      // Load initial category (Popular Movies)
      await selectCategory(0);
    },

    destroy() {
      document.removeEventListener('keydown', _handleKeyDown);
      if (enrichAbortController) {
        enrichAbortController.abort();
        enrichAbortController = null;
      }
      rootContainer = null;
    }
  };

  window.HomeScreen = HomeScreen;
})();
