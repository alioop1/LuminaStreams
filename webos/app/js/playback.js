/**
 * LuminaStreams TV — playback: the shared Android player (androidPlayer.js, the
 * same one the Windows app uses) over the TV's native <video> (tvBackend.js), so
 * webOS decodes Dolby Vision / Atmos itself and shows its own pop-ups.
 * Laid out in Android dp like the rest of the app (1706.67 dp across the screen).
 */
(() => {
  'use strict';
  const { tr } = TV;
  let session = null;

  function save(force) {
    const S = session;
    if (!S || !S.p.imdbId || !window.WatchProgress) return;
    if (!(S.duration > 15) || !(S.position > 0)) return;
    const now = Date.now();
    if (!force && now - S.savedAt < 5000) return; // Android cadence
    S.savedAt = now;
    const p = S.p;
    window.WatchProgress.save({
      mediaId: p.id, imdbId: p.imdbId, type: p.type,
      title: p.showTitle || p.title || '', poster_path: p.poster || '',
      season: p.season, episode: p.episode,
      position: S.position, duration: S.duration,
    });
  }

  function stop() {
    if (!session) return null;
    save(true);
    if (window.SyncManager) window.SyncManager.flushProgress().catch(() => {});
    const S = session;
    session = null;
    try { S.handle.destroy(); } catch {}
    try { S.backend.destroy(); } catch {}
    S.root.remove();
    document.getElementById('tv').style.visibility = '';
    return S;
  }

  /**
   * @param {Object} p  { url, title, showTitle, type: 'movie'|'series', id, imdbId, backdrop, poster, logo,
   *                      season, episode, onNext(next), onEnd() }
   */
  TV.play = p => {
    stop();
    const WP = window.WatchProgress;
    const key = WP && p.imdbId ? WP.keyFor(p.type, p.imdbId, p.season, p.episode) : null;
    const saved = key ? WP.get(key) : null;
    const resumeSeconds = saved && saved.position > 30 && (saved.percentage || 0) < 90 ? saved.position : 0;

    const root = document.createElement('div');
    root.className = 'ap-tv';
    root.innerHTML = '<video class="ap-video" preload="auto"></video><div class="ap-subtitle" aria-live="off"></div>';
    document.body.appendChild(root);
    // Nothing else needs drawing while a film plays.
    document.getElementById('tv').style.visibility = 'hidden';

    const finish = (next) => {
      const S = stop();
      if (!S) return;
      if (next && p.onNext) p.onNext(next);
      else if (p.onEnd) p.onEnd();
    };
    let nextEpisode = null;
    const backend = window.TvBackend.create(root.querySelector('video'), root.querySelector('.ap-subtitle'), {
      url: p.url,
      init: {
        lang: TV.lang(),
        title: p.title || '', showTitle: p.showTitle || p.title || '',
        backdrop: p.backdrop || '', logo: p.logo || '',
        season: p.season || null, episode: p.episode || null,
        resumeSeconds,
      },
      subStyle: {
        yellow: !!(window.Storage && window.Storage.get('settings_yellow_subs', false)),
        size: (window.Storage && window.Storage.get('settings_sub_font_size', 'medium')) || 'medium',
      },
      onProgress: st => {
        if (!session) return;
        session.position = st.position;
        if (st.duration > 0) session.duration = st.duration;
        save(false);
      },
      onRestart: () => { if (key && WP) WP.remove(key); },
      onNext: given => finish(given || nextEpisode),
      onClose: () => finish(null),
    });
    session = { root, backend, p, position: 0, duration: 0, savedAt: 0, handle: null };
    session.handle = window.AndroidPlayer.mount(root, backend, { tv: true });

    // Web subtitles and the next episode, after playback has started.
    const helpers = window.NativePlayback && window.NativePlayback.helpers;
    if (helpers) {
      const mine = session;
      helpers.webSubtitles(p).then(subs => {
        if (session !== mine) return;
        backend.setWebSubs({ list: subs.list, selected: subs.preferred });
        if (subs.preferred) backend.cmd('webSub', subs.preferred).catch(() => {});
      }).catch(() => {});
      if (p.type === 'series') {
        helpers.nextEpisodeOf(p).then(next => {
          if (session !== mine || !next) return;
          nextEpisode = next;
          backend.emitEvent({ type: 'nextEpisode', value: next });
        }).catch(() => {});
      }
    }
  };

  TV.playing = () => !!session;
  window.addEventListener('beforeunload', () => save(true));
  document.addEventListener('visibilitychange', () => { if (document.hidden) save(true); });
  void tr;
})();
