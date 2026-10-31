/** LuminaStreams TV — start-up. */
(() => {
  'use strict';
  const lang = TV.lang();
  document.documentElement.setAttribute('lang', lang);
  document.documentElement.setAttribute('dir', lang === 'he' ? 'rtl' : 'ltr');

  // Screens are drawn in dp; the player too (Android's 180 dpi: 1706.67 dp across).
  const fit = () => {
    TV.fit();
    document.documentElement.style.setProperty('--pz', String(window.innerWidth / TV.W));
  };
  fit();
  window.addEventListener('resize', fit);

  // Same account and sync as every other platform.
  if (window.FirebaseAuth) {
    window.FirebaseAuth.init().catch(() => {});
    window.FirebaseAuth.onAuthStateChanged(user => {
      if (!window.SyncManager) return;
      if (user) window.SyncManager.onSignedIn().catch(() => {});
      else window.SyncManager.onSignedOut();
    });
  }

  TV.open('home');

  // webOS: the app was brought back to the front (e.g. from the launcher).
  document.addEventListener('webOSRelaunch', () => {});
})();
