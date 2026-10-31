/**
 * LuminaStreams — the player screen on LG webOS.
 * Mounts the Android player UI over the TV's native <video> (tvBackend.js) and
 * keeps watch progress in sync like every other platform.
 */
(() => {
  'use strict';

  let session = null; // { root, backend, handle, params, key, position, duration }
  let lastSavedAt = 0;

  function saveProgress(force) {
    if (!session || !session.params.imdbId || !window.WatchProgress) return;
    if (!(session.duration > 15) || !(session.position > 0)) return;
    const now = Date.now();
    if (!force && now - lastSavedAt < 5000) return; // Android cadence
    lastSavedAt = now;
    const p = session.params;
    window.WatchProgress.save({
      mediaId: p.id, imdbId: p.imdbId, type: p.type,
      title: p.showTitle || p.title || '', poster_path: p.poster || '',
      season: p.season, episode: p.episode,
      position: session.position, duration: session.duration,
    });
  }

  function finish() {
    const wasActive = !!session;
    cleanup();
    if (wasActive && window.Router.getCurrentScreen() === 'player') window.Router.back();
  }

  /**
   * @param {HTMLElement} _container  the app's screen container (the player covers it)
   * @param {Object} params  player route params (url, title, imdbId, type, season, episode, ...)
   */
  function start(_container, params) {
    cleanup();
    const p = params;
    const WP = window.WatchProgress;
    const key = WP ? WP.keyFor(p.type, p.imdbId, p.season, p.episode) : null;
    const saved = key ? WP.get(key) : null;
    const resumeSeconds = saved && saved.position > 30 && (saved.percentage || 0) < 90 ? saved.position : 0;
    const lang = window.I18n && window.I18n.getLanguage ? window.I18n.getLanguage() : 'he';

    const root = document.createElement('div');
    root.className = 'ap-tv';
    root.innerHTML = '<video class="ap-video" preload="auto"></video><div class="ap-subtitle" aria-live="off"></div>';
    document.body.appendChild(root);
    document.documentElement.classList.add('tv-playing');

    const backend = window.TvBackend.create(root.querySelector('video'), root.querySelector('.ap-subtitle'), {
      url: p.url,
      init: {
        lang,
        title: p.title || '',
        showTitle: p.showTitle || p.title || '',
        backdrop: p.backdrop || '',
        logo: p.logo || '',
        season: p.season || null,
        episode: p.episode || null,
        resumeSeconds: p.live ? 0 : resumeSeconds,
      },
      subStyle: {
        yellow: !!(window.Storage && window.Storage.get('settings_yellow_subs', false)),
        size: (window.Storage && window.Storage.get('settings_sub_font_size', 'medium')) || 'medium',
      },
      onProgress: s => {
        if (!session) return;
        session.position = s.position;
        if (s.duration > 0) session.duration = s.duration;
        saveProgress(false);
      },
      onRestart: () => { if (key && WP) WP.remove(key); },
      onNext: given => {
        const next = given || (session && session.nextEpisode);
        if (next) {
          try { sessionStorage.setItem(window.NativePlayback.helpers.PENDING_NEXT_KEY, JSON.stringify({ id: p.id, ...next })); } catch {}
        }
        finish();
      },
      onClose: finish,
    });

    session = { root, backend, params: p, key, position: 0, duration: 0, handle: null };
    session.handle = window.AndroidPlayer.mount(root, backend, { tv: true });

    // Subtitles and the next episode arrive after playback has started.
    const helpers = window.NativePlayback && window.NativePlayback.helpers;
    if (helpers && !p.live) {
      const mine = session;
      helpers.webSubtitles(p).then(subs => {
        if (session !== mine) return;
        backend.setWebSubs({ list: subs.list, selected: subs.preferred });
        if (subs.preferred) backend.cmd('webSub', subs.preferred).catch(() => {});
      });
      helpers.nextEpisodeOf(p).then(next => {
        if (session !== mine || !next) return;
        mine.nextEpisode = next;
        backend.emitEvent({ type: 'nextEpisode', value: next });
      });
    }
  }

  function cleanup() {
    if (!session) return;
    saveProgress(true);
    if (window.SyncManager) window.SyncManager.flushProgress().catch(() => {});
    const s = session;
    session = null;
    try { s.handle && s.handle.destroy(); } catch {}
    try { s.backend.destroy(); } catch {}
    s.root.remove();
    document.documentElement.classList.remove('tv-playing');
  }

  /** Play a live channel (IPTV) in the player. */
  function playLive(url, title) {
    start(null, { url, title, live: true, type: 'live' });
    return true;
  }

  window.TvPlayback = Object.freeze({ start, cleanup, playLive });
})();
