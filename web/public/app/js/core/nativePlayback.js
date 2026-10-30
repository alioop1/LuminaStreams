/**
 * LuminaStreams — Native playback (Windows desktop app only)
 * In the desktop app, video plays in mpv/VLC instead of the browser <video>,
 * so MKV, HEVC, DTS/TrueHD and embedded subtitles all work (like Stremio/Kodi).
 * This screen shows what is playing and keeps watch progress in sync.
 * On the website `window.electronAPI` doesn't exist and none of this runs.
 */
(() => {
  'use strict';

  const { tr } = window.I18n;
  const { escapeHtml } = window.Helpers;

  let session = null;
  let unsubscribers = [];
  let lastSavedAt = 0;
  let current = null; // { params, position, duration }

  function api() {
    return window.electronAPI && window.electronAPI.nativePlayer;
  }

  /** True when this is the desktop app, a native player exists and the user hasn't opted out. */
  async function shouldUse(params) {
    if (!api() || (params && params.forceBuiltin)) return false;
    try {
      const info = await api().info();
      return !!info.available;
    } catch {
      return false;
    }
  }

  function fmt(seconds) {
    if (!isFinite(seconds) || seconds < 0) return '0:00';
    const s = Math.floor(seconds);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
  }

  function saveProgress(force) {
    if (!current || !current.params.imdbId || !window.WatchProgress) return;
    if (!(current.duration > 15) || !(current.position > 0)) return;
    const now = Date.now();
    if (!force && now - lastSavedAt < 5000) return; // Android cadence
    lastSavedAt = now;
    const p = current.params;
    window.WatchProgress.save({
      mediaId: p.id,
      imdbId: p.imdbId,
      type: p.type,
      title: p.showTitle || p.title || '',
      poster_path: p.poster || '',
      season: p.season,
      episode: p.episode,
      position: current.position,
      duration: current.duration,
    });
  }

  /** All web subtitles for the title, plus the preferred one to load at start. */
  async function webSubtitles(p) {
    if (!p.imdbId || !window.Subtitles) return { list: [], preferred: null };
    try {
      const subs = (await window.Subtitles.fetchAll(p.type === 'series' ? 'series' : 'movie', p.imdbId, p.season, p.episode))
        .filter(s => s.url);
      const pref = window.Storage ? window.Storage.getSubtitleLang() : 'heb';
      const preferred = pref === 'none' ? null : (subs.find(s => s.lang3 === pref) || null);
      return {
        list: subs.slice(0, 40).map(s => ({ url: s.url, lang: s.lang3 || s.lang || '', source: s.source || '' })),
        preferred: preferred && preferred.url,
      };
    } catch {
      return { list: [], preferred: null };
    }
  }

  /** Next episode of a series (same season, else first of the next season). */
  async function nextEpisodeOf(p) {
    if (p.type !== 'series' || !p.id || !p.season || !p.episode || !window.TMDB) return null;
    try {
      const season = await window.TMDB.getTvSeason(p.id, p.season);
      if ((season.episodes || []).some(e => e.episode_number === p.episode + 1)) {
        return { season: p.season, episode: p.episode + 1 };
      }
      const show = await window.TMDB.getTvDetails(p.id);
      if (show.number_of_seasons > p.season) return { season: p.season + 1, episode: 1 };
    } catch {}
    return null;
  }

  // Up-next/end of episode: details reopens sources for this episode (Android
  // stores auto_play_season/episode the same way).
  const PENDING_NEXT_KEY = 'lumina_autoplay_next';
  function takePendingNext(tmdbId) {
    try {
      const raw = sessionStorage.getItem(PENDING_NEXT_KEY);
      if (!raw) return null;
      const next = JSON.parse(raw);
      if (String(next.id) !== String(tmdbId)) return null;
      sessionStorage.removeItem(PENDING_NEXT_KEY);
      return next;
    } catch {
      return null;
    }
  }

  function setStatus(container, text) {
    const el = container.querySelector('#native-status');
    if (el) el.textContent = text;
  }

  /**
   * @param {HTMLElement} container
   * @param {Object} params  player route params (url, title, imdbId, type, season, episode, ...)
   */
  async function start(container, params) {
    cleanup();
    current = { params, position: 0, duration: 0 };
    const p = params;

    const key = window.WatchProgress ? window.WatchProgress.keyFor(p.type, p.imdbId, p.season, p.episode) : null;
    const saved = key ? window.WatchProgress.get(key) : null;
    const startSeconds = saved && saved.position > 30 && (saved.percentage || 0) < 90 ? saved.position : 0;

    container.innerHTML = `
      <div class="native-player" style="--bg:url('${escapeHtml(p.backdrop || '')}')">
        <div class="native-player-card">
          <div class="native-player-eyebrow">${tr('Now playing', 'מתנגן כעת')}</div>
          <h1 class="native-player-title">${escapeHtml(p.title || '')}</h1>
          <div class="native-player-status" id="native-status">${tr('Opening player…', 'פותח את הנגן…')}</div>
          <div class="native-player-bar"><div class="native-player-fill" id="native-fill"></div></div>
          <div class="native-player-time"><span id="native-pos">0:00</span><span id="native-dur"></span></div>
          ${startSeconds ? `<div class="native-player-note">${tr('Resuming from', 'ממשיך מ-')} ${fmt(startSeconds)}</div>` : ''}
          <div class="native-player-actions">
            <button class="ps5-btn-play" id="native-stop">■ ${tr('Stop', 'עצור')}</button>
            <button class="ps5-btn-glass" id="native-builtin">${tr('Use built-in player', 'נגן מובנה')}</button>
          </div>
        </div>
      </div>
    `;

    container.querySelector('#native-stop').addEventListener('click', () => api().stop());
    container.querySelector('#native-builtin').addEventListener('click', () => {
      cleanup();
      window.Router.replace('player', { ...p, forceBuiltin: true });
    });

    const [subs, nextEpisode] = await Promise.all([webSubtitles(p), nextEpisodeOf(p)]);
    if (!current || current.params !== p) return; // left the screen meanwhile

    const onProgress = data => {
      if (!current || data.session !== session) return;
      current.position = data.position || 0;
      if (data.duration > 0) current.duration = data.duration;
      const pct = current.duration ? Math.min(100, (current.position / current.duration) * 100) : 0;
      const fill = container.querySelector('#native-fill');
      if (fill) fill.style.width = `${pct}%`;
      const pos = container.querySelector('#native-pos');
      const dur = container.querySelector('#native-dur');
      if (pos) pos.textContent = fmt(current.position);
      if (dur) dur.textContent = current.duration ? fmt(current.duration) : '';
      let status = data.paused ? tr('Paused', 'מושהה') : tr('Playing', 'מתנגן');
      if (data.decoder) {
        const gpu = data.decoder !== 'no';
        status += ` • ${gpu ? `GPU (${data.decoder})` : 'CPU'}`;
      }
      if (data.codec) status += ` • ${String(data.codec).split(' ')[0]}`;
      setStatus(container, status);
      saveProgress(false);
    };
    const onEnded = data => {
      if (!current || data.session !== session) return;
      if (data.position) current.position = data.position;
      if (data.duration) current.duration = data.duration;
      saveProgress(true);
      if (window.SyncManager) window.SyncManager.flushProgress().catch(() => {});
      cleanup();
      if (window.Router.getCurrentScreen() === 'player') window.Router.back();
    };
    const onError = message => {
      setStatus(container, `${tr('Player error', 'שגיאת נגן')}: ${message}`);
    };
    const onRestart = () => {
      // "From start" in the resume dialog: forget the saved position (synced as a deletion).
      if (key && window.WatchProgress) window.WatchProgress.remove(key);
    };
    const onNext = next => {
      if (!next) return;
      try { sessionStorage.setItem(PENDING_NEXT_KEY, JSON.stringify({ id: p.id, ...next })); } catch {}
    };
    unsubscribers = [
      api().onProgress(onProgress),
      api().onEnded(onEnded),
      api().onError(onError),
      api().onRestart ? api().onRestart(onRestart) : () => {},
      api().onNext ? api().onNext(onNext) : () => {},
    ];

    try {
      const lang = window.I18n && window.I18n.getLanguage ? window.I18n.getLanguage() : 'he';
      const result = await api().play({
        url: p.url,
        title: p.title || '',
        startSeconds,
        subtitleUrls: subs.preferred ? [subs.preferred] : [],
        live: !!p.live,
        subStyle: {
          yellow: !!(window.Storage && window.Storage.get('settings_yellow_subs', false)),
          size: (window.Storage && window.Storage.get('settings_sub_font_size', 'medium')) || 'medium',
        },
        // Shown by the Android-style player controls (desktop/src/overlay).
        overlay: {
          lang,
          showTitle: p.showTitle || p.title || '',
          backdrop: p.backdrop || '',
          logo: p.logo || '',
          season: p.season || null,
          episode: p.episode || null,
          nextEpisode,
          resumeSeconds: startSeconds,
          dolbyVision: p.hdr === 'DV',
          atmos: /atmos/i.test(p.audio || ''),
        },
      });
      session = result.session;
      if (api().setWebSubs) api().setWebSubs({ list: subs.list, selected: subs.preferred });
      setStatus(container, `${tr('Playing in', 'מתנגן ב-')}${result.player === 'mpv' ? 'mpv' : 'VLC'}`);
    } catch (err) {
      setStatus(container, `${tr('Could not start the player', 'לא ניתן להפעיל את הנגן')}: ${err.message}`);
    }
  }

  /** Stop listening (and stop playback if still running). Safe to call repeatedly. */
  function cleanup() {
    const wasActive = !!current;
    unsubscribers.forEach(off => { try { off(); } catch {} });
    unsubscribers = [];
    if (wasActive) {
      saveProgress(true);
      if (api()) api().stop();
    }
    current = null;
    session = null;
  }

  /** Play a live channel (IPTV) without leaving the current screen. */
  async function playLive(url, title) {
    if (!api()) return false;
    try {
      await api().play({ url, title, live: true });
      return true;
    } catch {
      return false;
    }
  }

  window.NativePlayback = Object.freeze({ shouldUse, start, cleanup, playLive, takePendingNext });
})();
