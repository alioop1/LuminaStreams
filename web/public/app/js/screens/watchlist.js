/**
 * LuminaStreams — Watchlist Screen
 * Displays favorited movies and TV shows in a filterable grid.
 */
(() => {
  'use strict';

  const { t, tr } = window.I18n;

  /* ── State ─────────────────────────────────────────────────────── */
  let activeTab = 'all'; // 'all' | 'movie' | 'tv'

  /* ── Render ────────────────────────────────────────────────────── */

  /**
   * Render the watchlist screen.
   * @param {HTMLElement} container  #screen-container
   */
  function render(container) {
    const watchlist = window.Storage.getWatchlist();

    container.innerHTML = `
      <div class="watchlist-page page">
        <h1 class="watchlist-page-title">${t('nav.watchlist')}</h1>
        <p class="watchlist-count">${_buildCountText(watchlist)}</p>

        <!-- Tabs -->
        <div class="watchlist-tabs">
          <button class="watchlist-tab ${activeTab === 'all' ? 'active' : ''}" data-tab="all">
            ${tr('All', 'הכל')}
          </button>
          <button class="watchlist-tab ${activeTab === 'movie' ? 'active' : ''}" data-tab="movie">
            ${t('nav.movies')}
          </button>
          <button class="watchlist-tab ${activeTab === 'tv' ? 'active' : ''}" data-tab="tv">
            ${t('nav.series')}
          </button>
        </div>

        <!-- Grid -->
        <div class="watchlist-grid" id="watchlist-grid">
          ${_renderGrid(watchlist, activeTab)}
        </div>
      </div>
    `;

    _attachEventListeners(container);
  }

  /**
   * Build the grid of watchlist cards, filtered by tab.
   * @param {Array} watchlist
   * @param {string} tab
   * @returns {string} HTML
   */
  function _renderGrid(watchlist, tab) {
    const filtered = tab === 'all'
      ? watchlist
      : watchlist.filter(item => item.type === tab);

    if (filtered.length === 0) {
      return _renderEmptyState(tab);
    }

    return filtered.map((item, index) => _renderCard(item, index)).join('');
  }

  /**
   * Render a single watchlist card.
   * @param {Object} item
   * @param {number} index  For staggered animation
   * @returns {string} HTML
   */
  function _renderCard(item, index) {
    const posterUrl = _getPosterUrl(item.poster);
    const title     = window.Helpers.escapeHtml(item.title || item.name || tr('Unknown', 'לא ידוע'));
    const rating    = item.rating ? `★ ${Number(item.rating).toFixed(1)}` : '';
    const typeLabel = item.type === 'movie' ? tr('Movie', 'סרט') : tr('Series', 'סדרה');
    const delay     = Math.min(index * 0.04, 0.6);

    return `
      <div class="card-portrait watchlist-card animate-fadeIn"
           data-id="${item.id}"
           data-type="${item.type || 'movie'}"
           style="animation-delay: ${delay}s; position: relative;">
        
        <!-- Poster -->
        <div style="position: relative; overflow: hidden; border-radius: var(--radius-md);">
          <img class="card-poster"
               src="${posterUrl}"
               alt="${title}"
               loading="lazy"
               onerror="this.src='assets/img/poster-placeholder.svg'">
          
          ${rating ? `<span class="card-rating"><span class="star-icon">★</span> ${item.rating ? Number(item.rating).toFixed(1) : ''}</span>` : ''}
          
          <span class="card-quality" style="font-size: 9px;">${typeLabel}</span>

          <!-- Remove button -->
          <button class="watchlist-remove-btn"
                  data-remove-id="${item.id}"
                  data-remove-type="${item.type || 'movie'}"
                  title="${t('btn.remove_watchlist')}"
                  style="
                    position: absolute; top: 8px; right: 8px; z-index: 10;
                    width: 28px; height: 28px; border-radius: 50%;
                    background: rgba(255, 59, 92, 0.85); color: #fff;
                    display: flex; align-items: center; justify-content: center;
                    backdrop-filter: blur(8px); opacity: 0;
                    transition: opacity 0.2s ease, transform 0.2s ease;
                    font-size: 14px; line-height: 1;
                  ">✕</button>

          <!-- Play overlay -->
          <div class="card-play">
            <svg viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </div>
        </div>

        <!-- Info -->
        <div class="card-info">
          <div class="card-title">${title}</div>
          ${item.year ? `<div class="card-year">${item.year}</div>` : ''}
        </div>
      </div>
    `;
  }

  /**
   * Render empty state message.
   * @param {string} tab
   * @returns {string} HTML
   */
  function _renderEmptyState(tab) {
    let icon, message;

    if (tab === 'movie') {
      icon    = '🎬';
      message = tr('No movies in your watchlist yet', 'אין סרטים ברשימה עדיין');
    } else if (tab === 'tv') {
      icon    = '📺';
      message = tr('No TV shows in your watchlist yet', 'אין סדרות ברשימה עדיין');
    } else {
      icon    = '❤️';
      message = t('status.empty_watchlist');
    }

    return `
      <div class="watchlist-empty animate-fadeIn">
        <div class="watchlist-empty-icon">${icon}</div>
        <p class="watchlist-empty-text">${message}</p>
        <p style="font-size: 13px; color: var(--text-muted); margin-top: 8px;">
          ${tr('Browse movies and series to add them here', 'עיין בסרטים וסדרות כדי להוסיף אותם לרשימה')}
        </p>
        <button class="btn btn-primary" style="margin-top: 20px;"
                onclick="window.Router.navigate('home')">
          ${tr('Explore Content', 'גלה תכנים')}
        </button>
      </div>
    `;
  }

  /* ── Helpers ───────────────────────────────────────────────────── */

  function _getPosterUrl(poster) {
    if (!poster) return 'assets/img/poster-placeholder.svg';
    if (poster.startsWith('http')) return poster;
    return window.CONST.posterUrl(poster);
  }

  function _buildCountText(watchlist) {
    const movies = watchlist.filter(i => i.type === 'movie').length;
    const series = watchlist.filter(i => i.type === 'tv').length;
    return tr(
      `${watchlist.length} items — ${movies} movies, ${series} series`,
      `${watchlist.length} פריטים — ${movies} סרטים, ${series} סדרות`
    );
  }

  /* ── Event Handlers ────────────────────────────────────────────── */

  function _attachEventListeners(container) {
    // Tab switching
    container.querySelectorAll('.watchlist-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        activeTab = tab.dataset.tab;
        render(container);
      });
    });

    // Card click → details
    container.querySelectorAll('.watchlist-card').forEach(card => {
      card.addEventListener('click', (e) => {
        // Don't navigate if clicking the remove button
        if (e.target.closest('.watchlist-remove-btn')) return;

        const id   = parseInt(card.dataset.id, 10);
        const type = card.dataset.type || 'movie';
        window.Router.navigate('details', { id, type });
      });

      // Show remove button on hover
      const removeBtn = card.querySelector('.watchlist-remove-btn');
      if (removeBtn) {
        card.addEventListener('mouseenter', () => {
          removeBtn.style.opacity = '1';
          removeBtn.style.transform = 'scale(1)';
        });
        card.addEventListener('mouseleave', () => {
          removeBtn.style.opacity = '0';
          removeBtn.style.transform = 'scale(0.8)';
        });
      }
    });

    // Remove buttons
    container.querySelectorAll('.watchlist-remove-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id   = btn.dataset.removeId;
        const type = btn.dataset.removeType;

        // Animate card out
        const card = btn.closest('.watchlist-card');
        if (card) {
          card.style.transition = 'transform 0.3s ease, opacity 0.3s ease';
          card.style.transform  = 'scale(0.8)';
          card.style.opacity    = '0';

          setTimeout(() => {
            window.Storage.removeFromWatchlist(id, type);
            render(container);

            if (window.showToast) {
              window.showToast(tr('Removed from watchlist', 'הוסר מרשימת הצפייה'), 'info');
            }
          }, 250);
        } else {
          window.Storage.removeFromWatchlist(id, type);
          render(container);
        }
      });
    });
  }

  /* ── Destroy ───────────────────────────────────────────────────── */

  function destroy() {
    // No timers or listeners to clean up outside the container
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.WatchlistScreen = Object.freeze({ render, destroy });
})();
