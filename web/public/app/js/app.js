/**
 * LuminaStreams — Application Entry Point
 * Initialises all modules, registers screens, sets up navigation,
 * keyboard shortcuts, titlebar, toast/modal utilities, and boots the app.
 * Loaded LAST after all other scripts.
 */
(() => {
  'use strict';

  const { t, tr } = window.I18n;

  /* ── Toast Utility ─────────────────────────────────────────────── */

  /**
   * Show a toast notification.
   * @param {string} message  Text to display
   * @param {'success'|'error'|'info'|'warning'} [type='info']
   * @param {number} [duration=3500]  Auto-dismiss in ms
   */
  window.showToast = function showToast(message, type = 'info', duration = 3500) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const icons = {
      success: '✓',
      error:   '✕',
      info:    'ℹ',
      warning: '⚠',
    };

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.style.position = 'relative'; // for progress bar
    toast.innerHTML = `
      <div class="toast-icon">${icons[type] || icons.info}</div>
      <div class="toast-content">
        <div class="toast-title">${window.Helpers.escapeHtml(message)}</div>
      </div>
      <button class="toast-close" aria-label="${t('btn.close')}">✕</button>
      <div class="toast-progress" style="width: 100%; transition: width ${duration}ms linear;"></div>
    `;

    container.appendChild(toast);

    // Start progress bar animation
    requestAnimationFrame(() => {
      const progressBar = toast.querySelector('.toast-progress');
      if (progressBar) progressBar.style.width = '0%';
    });

    // Close button
    const closeBtn = toast.querySelector('.toast-close');
    closeBtn.addEventListener('click', () => _dismissToast(toast));

    // Auto-dismiss
    const timer = setTimeout(() => _dismissToast(toast), duration);
    toast._timer = timer;
  };

  function _dismissToast(toast) {
    if (!toast || !toast.parentNode) return;
    clearTimeout(toast._timer);
    toast.classList.add('leaving');
    toast.addEventListener('animationend', () => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    });
  }

  /* ── Modal Utility ─────────────────────────────────────────────── */

  /**
   * Show a modal overlay with custom content.
   * @param {string} htmlContent  Inner HTML for the modal body
   * @returns {HTMLElement} The modal element
   */
  window.showModal = function showModal(htmlContent) {
    // Use the existing modal overlay in the DOM
    const overlay = document.getElementById('modal-overlay');
    const content = document.getElementById('modal-content');

    if (!overlay || !content) {
      // Fallback: create a modal
      return _createFallbackModal(htmlContent);
    }

    content.innerHTML = `
      <div class="modal animate-slideUp">
        <div class="modal-header">
          <span></span>
          <button class="modal-close" id="modal-close-btn" aria-label="${t('btn.close')}">✕</button>
        </div>
        <div class="modal-body">
          ${htmlContent}
        </div>
      </div>
    `;

    overlay.classList.remove('hidden');
    overlay.style.display = 'flex';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    overlay.style.position = 'fixed';
    overlay.style.inset = '0';
    overlay.style.background = 'rgba(0,0,0,0.6)';
    overlay.style.backdropFilter = 'blur(8px)';
    overlay.style.zIndex = 'var(--z-modal, 500)';
    overlay.style.animation = 'fadeIn 0.2s ease';

    // Close on clicking the button
    const closeBtn = content.querySelector('#modal-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => _closeModal());
    }

    // Close on clicking backdrop
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) _closeModal();
    });

    return overlay;
  };

  function _closeModal() {
    const overlay = document.getElementById('modal-overlay');
    if (overlay) {
      overlay.classList.add('hidden');
      overlay.style.display = 'none';
      const content = document.getElementById('modal-content');
      if (content) content.innerHTML = '';
    }
  }

  function _createFallbackModal(htmlContent) {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop active';
    backdrop.innerHTML = `
      <div class="modal animate-slideUp">
        <div class="modal-header">
          <span></span>
          <button class="modal-close" aria-label="${t('btn.close')}">✕</button>
        </div>
        <div class="modal-body">${htmlContent}</div>
      </div>
    `;

    document.body.appendChild(backdrop);

    const closeBtn = backdrop.querySelector('.modal-close');
    closeBtn.addEventListener('click', () => {
      backdrop.remove();
    });

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) backdrop.remove();
    });

    return backdrop;
  }

  /* ── Titlebar Controls ─────────────────────────────────────────── */

  function _setupTitlebar() {
    const api = window.electronAPI;
    if (api) {
      document.body.classList.add('is-electron');
    } else {
      document.body.classList.add('is-web');
      const titlebar = document.getElementById('titlebar');
      if (titlebar) titlebar.style.display = 'none';
      return;
    }

    const btnMin   = document.getElementById('btn-minimize');
    const btnMax   = document.getElementById('btn-maximize');
    const btnClose = document.getElementById('btn-close');

    if (btnMin)   btnMin.addEventListener('click', () => api.minimize());
    if (btnMax)   btnMax.addEventListener('click', () => api.maximize());
    if (btnClose) btnClose.addEventListener('click', () => api.close());

    // Update maximize button icon on fullscreen change
    if (api.onFullscreenChanged) {
      api.onFullscreenChanged((isMaximized) => {
        if (btnMax) {
          btnMax.innerHTML = isMaximized ? '&#9723;' : '&#9744;';
        }
      });
    }
  }

  /* ── PS5 Navigation & Clock ───────────────────────────────────── */

  function _setupSidebar() {
    const navItems = document.querySelectorAll('.ps5-nav-btn[data-screen], .nav-item[data-screen], [data-screen]');

    navItems.forEach(item => {
      item.addEventListener('click', () => {
        const screen = item.dataset.screen;
        if (screen && window.Router) {
          window.Router.navigate(screen);
        }
      });
    });
  }

  function _setupClock() {
    const clockEl = document.getElementById('ps5-clock');
    if (!clockEl) return;
    function update() {
      const now = new Date();
      const h = String(now.getHours()).padStart(2, '0');
      const m = String(now.getMinutes()).padStart(2, '0');
      clockEl.textContent = `${h}:${m}`;
    }
    update();
    setInterval(update, 10000);
  }

  /* ── Keyboard Shortcuts ────────────────────────────────────────── */

  function _setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
      // Ctrl+F or Cmd+F → focus search
      if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        window.Router.navigate('search');
      }

      // Escape → go back (handled by Router, but also close modals)
      if (e.key === 'Escape') {
        _closeModal();
      }
    });
  }

  /* ── Screen Registration ───────────────────────────────────────── */

  function _registerScreens() {
    const screenMap = {
      home:      window.HomeScreen,
      movies:    window.MoviesScreen,
      series:    window.SeriesScreen,
      details:   window.DetailsScreen,
      player:    window.PlayerScreen,
      search:    window.SearchScreen,
      iptv:      window.IptvScreen,
      fuzer:     window.FuzerScreen,
      watchlist: window.WatchlistScreen,
      settings:  window.SettingsScreen,
      auth:      window.AuthScreen,
    };

    for (const [name, module] of Object.entries(screenMap)) {
      if (module) {
        window.Router.register(name, module);
      } else {
        // Register a placeholder if the screen module isn't loaded
        window.Router.register(name, {
          render(container) {
            container.innerHTML = `
              <div class="page-loader">
                <div style="font-size: 48px; opacity: 0.2; margin-bottom: 16px;">🚧</div>
                <p style="font-size: 16px; color: var(--text-dim);">
                  ${tr(`${name} screen is not available yet`, `מסך ${name} אינו זמין עדיין`)}
                </p>
                <button class="btn btn-secondary" onclick="window.Router.navigate('home')">
                  ${tr('Go Home', 'חזרה לדף הבית')}
                </button>
              </div>
            `;
          },
          destroy() {},
        });
      }
    }
  }

  /* ── Language & Direction ───────────────────────────────────────── */

  function _setupLanguage() {
    const lang = window.I18n.getLanguage();
    document.documentElement.setAttribute('lang', lang);
    document.documentElement.setAttribute('dir', lang === 'he' ? 'rtl' : 'ltr');
  }

  /* ── Auth State Sync ───────────────────────────────────────────── */

  function _setupAuthState() {
    if (!window.FirebaseAuth) return;

    window.FirebaseAuth.onAuthStateChanged((user) => {
      _updateProfileNavItem(user);

      // Start/stop realtime sync and pull initial cloud data
      if (user && window.SyncManager) {
        window.SyncManager.fullSync().catch(err => {
          console.warn('[App] Initial fullSync warning:', err);
        });
        window.SyncManager.startRealtimeSync();
      } else if (!user && window.SyncManager) {
        window.SyncManager.stopRealtimeSync();
      }
    });
  }

  function _updateProfileNavItem(user) {
    const profileBtn  = document.querySelector('#nav-profile');
    const profileSpan = profileBtn ? profileBtn.querySelector('span') : null;

    if (profileSpan) {
      if (user && user.email) {
        profileSpan.textContent = user.email.split('@')[0];
      } else {
        profileSpan.textContent = tr('Account', 'חשבון');
      }
    }
  }

  /* ── Splash Screen ─────────────────────────────────────────────── */

  function _hideSplash() {
    const splash = document.getElementById('splash-loader');
    if (!splash) return;

    splash.style.transition = 'opacity 0.5s ease';
    splash.style.opacity    = '0';

    setTimeout(() => {
      splash.style.display = 'none';
    }, 500);
  }

  /* ── App Initialization ────────────────────────────────────────── */

  async function init() {
    console.log('[LuminaStreams] Initializing...');

    try {
      // 1. Set up language direction
      _setupLanguage();

      // 2. Initialize Firebase (async, non-blocking)
      if (window.FirebaseAuth) {
        window.FirebaseAuth.init().catch(err => {
          console.warn('[App] Firebase init error:', err);
        });
      }

      // 3. Set up titlebar
      _setupTitlebar();

      // 4. Set up sidebar navigation & clock
      _setupSidebar();
      _setupClock();

      // 5. Register all screens with the router
      _registerScreens();

      // 6. Set up keyboard shortcuts
      _setupKeyboardShortcuts();

      // 7. Set up auth state listener
      _setupAuthState();

      // 8. Navigate to the home screen
      await window.Router.init('home');

      // 9. Hide the splash loader
      _hideSplash();

      console.log('[LuminaStreams] App ready.');
    } catch (err) {
      console.error('[LuminaStreams] Initialization error:', err);
      _hideSplash();
    }
  }

  /* ── Boot ──────────────────────────────────────────────────────── */

  // Wait for DOM if needed, then initialize
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
