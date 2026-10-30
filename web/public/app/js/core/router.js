/**
 * LuminaStreams — SPA Router
 * Screen navigation with history stack, lifecycle hooks, and sidebar sync.
 */
(() => {
  'use strict';

  /* ── Screen Registry ──────────────────────────────────────────────── */
  const screens = {};
  const VALID_SCREENS = [
    'home', 'movies', 'series', 'search', 'details',
    'player', 'iptv', 'fuzer', 'watchlist', 'settings', 'auth',
  ];

  /* ── State ────────────────────────────────────────────────────────── */
  let currentScreen = null;
  let currentParams = null;
  const historyStack = [];
  let isTransitioning = false;

  /* ── Helpers ──────────────────────────────────────────────────────── */

  function getContainer() {
    return document.getElementById('screen-container');
  }

  /**
   * Register a screen module.
   * Each screen must expose: render(container, params) and optionally destroy().
   * @param {string} name
   * @param {Object} screenModule  { render, destroy?, onResize? }
   */
  function register(name, screenModule) {
    if (!VALID_SCREENS.includes(name) && !name.startsWith('_')) {
      console.warn(`[Router] Unknown screen name: "${name}" — registering anyway.`);
    }
    screens[name] = screenModule;
  }

  /**
   * Update sidebar active state to reflect the current screen.
   * @param {string} screenName
   */
  function updateSidebar(screenName) {
    const sidebarLinks = document.querySelectorAll('.sidebar-nav-item, [data-screen]');
    sidebarLinks.forEach(link => {
      const target = link.dataset.screen || link.getAttribute('href');
      if (target === screenName) {
        link.classList.add('active');
        link.setAttribute('aria-current', 'page');
      } else {
        link.classList.remove('active');
        link.removeAttribute('aria-current');
      }
    });
  }

  /**
   * Animate the screen transition.
   * @param {HTMLElement} container
   * @param {'in'|'out'} direction
   * @returns {Promise<void>}
   */
  function animateTransition(container, direction) {
    return new Promise(resolve => {
      if (direction === 'out') {
        container.style.transition = 'opacity 0.15s ease, transform 0.15s ease';
        container.style.opacity = '0';
        container.style.transform = 'translateY(8px)';
      } else {
        container.style.opacity = '0';
        container.style.transform = 'translateY(-8px)';
        // Force reflow
        void container.offsetHeight;
        container.style.transition = 'opacity 0.25s ease, transform 0.25s ease';
        container.style.opacity = '1';
        container.style.transform = 'translateY(0)';
      }
      const duration = direction === 'out' ? 150 : 250;
      setTimeout(resolve, duration);
    });
  }

  /* ── Navigation ───────────────────────────────────────────────────── */

  /**
   * Navigate to a screen.
   * @param {string} screenName  One of VALID_SCREENS
   * @param {Object} [params]    Optional parameters passed to render()
   * @param {Object} [options]   { replace: bool, skipHistory: bool }
   */
  async function navigate(screenName, params = {}, options = {}) {
    if (isTransitioning) return;
    if (!screens[screenName]) {
      console.error(`[Router] Screen "${screenName}" is not registered.`);
      return;
    }

    const container = getContainer();
    if (!container) {
      console.error('[Router] #screen-container not found in DOM.');
      return;
    }

    isTransitioning = true;

    try {
      // ── Destroy current screen ───────────────────────────────
      if (currentScreen && screens[currentScreen] && typeof screens[currentScreen].destroy === 'function') {
        try {
          screens[currentScreen].destroy();
        } catch (err) {
          console.error(`[Router] Error destroying screen "${currentScreen}":`, err);
        }
      }

      // Animate out
      if (currentScreen) {
        await animateTransition(container, 'out');
      }

      // Push to history (unless told not to)
      if (!options.skipHistory && !options.replace && currentScreen) {
        historyStack.push({ screen: currentScreen, params: currentParams });
      }

      // Replace entry instead of push
      if (options.replace && historyStack.length > 0) {
        historyStack[historyStack.length - 1] = { screen: screenName, params };
      }

      // ── Render new screen ────────────────────────────────────
      currentScreen = screenName;
      currentParams = params;

      // Clear container
      container.innerHTML = '';
      container.scrollTop = 0;

      // Set data attribute for CSS scoping
      container.dataset.screen = screenName;
      document.body.dataset.screen = screenName;

      // Update sidebar
      updateSidebar(screenName);

      // Render
      try {
        await screens[screenName].render(container, params);
      } catch (err) {
        console.error(`[Router] Error rendering screen "${screenName}":`, err);
        container.innerHTML = `
          <div class="screen-error">
            <div class="screen-error__icon">⚠️</div>
            <h2>${window.I18n ? window.I18n.t('status.error') : 'Something went wrong'}</h2>
            <p>${window.Helpers ? window.Helpers.escapeHtml(err.message) : err.message}</p>
            <button class="btn btn--primary" onclick="window.Router.back()">
              ${window.I18n ? window.I18n.t('btn.back') : 'Back'}
            </button>
          </div>
        `;
      }

      // Animate in
      await animateTransition(container, 'in');

      // Dispatch custom event
      window.dispatchEvent(new CustomEvent('lumina:navigate', {
        detail: { screen: screenName, params },
      }));

    } finally {
      isTransitioning = false;
    }
  }

  /**
   * Go back to the previous screen.
   */
  function back() {
    if (historyStack.length === 0) {
      // Default to home if nothing in stack
      navigate('home', {}, { skipHistory: true });
      return;
    }
    const prev = historyStack.pop();
    navigate(prev.screen, prev.params, { skipHistory: true });
  }

  /**
   * Get the name of the currently active screen.
   * @returns {string|null}
   */
  function getCurrentScreen() {
    return currentScreen;
  }

  /**
   * Get the params of the currently active screen.
   * @returns {Object|null}
   */
  function getCurrentParams() {
    return currentParams;
  }

  /**
   * Get the history stack depth.
   * @returns {number}
   */
  function getHistoryDepth() {
    return historyStack.length;
  }

  /**
   * Check if a screen is registered.
   * @param {string} name
   * @returns {boolean}
   */
  function isRegistered(name) {
    return name in screens;
  }

  /**
   * Clear the history stack.
   */
  function clearHistory() {
    historyStack.length = 0;
  }

  /**
   * Replace the current screen without adding to history.
   * @param {string} screenName
   * @param {Object} params
   */
  function replace(screenName, params = {}) {
    return navigate(screenName, params, { replace: true });
  }

  /**
   * Initialise the router and navigate to the default screen.
   * @param {string} [defaultScreen='home']
   */
  function init(defaultScreen = 'home') {
    // Set up keyboard shortcut for back navigation
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && currentScreen !== 'home' && currentScreen !== 'player') {
        back();
      }
    });

    // Navigate to default
    if (screens[defaultScreen]) {
      navigate(defaultScreen, {}, { skipHistory: true });
    }
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.Router = Object.freeze({
    register,
    navigate,
    replace,
    back,
    getCurrentScreen,
    getCurrentParams,
    getHistoryDepth,
    canGoBack: () => getHistoryDepth() > 0,
    isRegistered,
    clearHistory,
    init,
    VALID_SCREENS,
  });
})();
