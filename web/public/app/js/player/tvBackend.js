/**
 * LuminaStreams — playback backend for the LG webOS app.
 * Drives the TV's native <video> element: webOS hands it to the panel's own
 * media pipeline, which decodes HEVC/AV1/Dolby Vision/HDR10 and Dolby Atmos/
 * DTS itself and shows the TV's native "Dolby Vision" / "Dolby Atmos" popups.
 * Exposes the same command interface as the Windows (mpv) backend, so the
 * Android player UI (androidPlayer.js) runs unchanged on top of it.
 */
(() => {
  'use strict';

  const ERROR_TEXT = {
    1: 'Playback was aborted',
    2: 'Network error while loading the stream',
    3: 'The TV could not decode this stream',
    4: 'This stream format is not supported',
  };

  /** Parse SRT or WebVTT text into [{start, end, text}] (seconds). */
  function parseCues(text) {
    const toSec = t => {
      const m = /(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})/.exec(t);
      if (!m) return null;
      return (Number(m[1] || 0) * 3600) + Number(m[2]) * 60 + Number(m[3]) + Number(m[4].padEnd(3, '0')) / 1000;
    };
    const cues = [];
    for (const block of text.replace(/\r/g, '').split(/\n{2,}/)) {
      const lines = block.split('\n').filter(Boolean);
      const timeIdx = lines.findIndex(l => l.includes('-->'));
      if (timeIdx < 0) continue;
      const [a, b] = lines[timeIdx].split('-->');
      const start = toSec(a);
      const end = toSec(b);
      if (start == null || end == null) continue;
      const body = lines.slice(timeIdx + 1).join('\n').replace(/<[^>]+>/g, '').replace(/\{[^}]*\}/g, '').trim();
      if (body) cues.push({ start, end, text: body });
    }
    return cues.sort((x, y) => x.start - y.start);
  }

  /** Fetch a subtitle file as text (UTF-8, falling back to windows-1255 for Hebrew). */
  async function fetchSubtitleText(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Subtitle HTTP ${res.status}`);
    const bytes = await res.arrayBuffer();
    try {
      return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    } catch {
      return new TextDecoder('windows-1255').decode(bytes);
    }
  }

  /**
   * @param {HTMLVideoElement} video
   * @param {HTMLElement} subtitleEl   where subtitles are drawn
   * @param {{url: string, init: Object, startSeconds?: number, subtitleUrl?: string,
   *          subStyle?: {yellow?: boolean, size?: string},
   *          onProgress?: Function, onRestart?: Function, onNext?: Function, onClose?: Function}} opts
   */
  function create(video, subtitleEl, opts) {
    const listeners = { init: new Set(), state: new Set(), event: new Set() };
    const sub = (set, cb) => { set.add(cb); return () => set.delete(cb); };
    const emit = (set, payload) => set.forEach(cb => { try { cb(payload); } catch (e) { console.error(e); } });

    let externalCues = [];
    let externalActive = false;
    let embeddedTrack = null; // a TextTrack of the stream itself
    let subDelay = 0;
    let started = false;
    let buffering = true;
    let lastText = null;
    const cleanup = [];

    // Android subtitle look (yellow/white, 4 sizes).
    const SIZE_PX = { small: 34, medium: 42, large: 52, xlarge: 62 };
    const style = opts.subStyle || {};
    subtitleEl.style.color = style.yellow ? '#FFE600' : '#FFFFFF';
    // Sizes are screen pixels on a 1080p TV; the player is zoomed (Android dp), so divide.
    const zoom = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--tv-player-zoom')) || 1;
    subtitleEl.style.fontSize = `${(SIZE_PX[style.size] || SIZE_PX.medium) / zoom}px`;

    function tracks() {
      const list = [];
      const audio = video.audioTracks;
      if (audio && audio.length) {
        for (let i = 0; i < audio.length; i++) {
          const t = audio[i];
          list.push({ id: i, type: 'audio', lang: t.language || '', title: t.label || '', codec: '', channels: 0, selected: !!t.enabled, external: false });
        }
      }
      const text = video.textTracks;
      for (let i = 0; i < text.length; i++) {
        const t = text[i];
        if (t.kind !== 'subtitles' && t.kind !== 'captions') continue;
        list.push({ id: i, type: 'sub', lang: t.language || '', title: t.label || '', codec: '', channels: 0, selected: t === embeddedTrack, external: false });
      }
      if (externalCues.length) {
        list.push({ id: 'web', type: 'sub', lang: '', title: 'Web', codec: '', channels: 0, selected: externalActive, external: true });
      }
      return list;
    }

    function state() {
      return {
        position: video.currentTime || 0,
        duration: isFinite(video.duration) ? video.duration : 0,
        paused: video.paused,
        buffering,
        started,
        width: video.videoWidth || 0,
        height: video.videoHeight || 0,
        fps: 0,
        tracks: tracks(),
        subDelay,
        speed: video.playbackRate || 1,
        volume: 100,
        muted: false,
      };
    }

    let lastEmit = 0;
    function pushState(force) {
      const now = Date.now();
      if (!force && now - lastEmit < 250) return;
      lastEmit = now;
      const s = state();
      emit(listeners.state, s);
      if (opts.onProgress) opts.onProgress(s);
    }

    function renderSubtitle() {
      let text = '';
      if (externalActive && externalCues.length) {
        const t = video.currentTime - subDelay;
        const cue = externalCues.find(c => t >= c.start && t <= c.end);
        text = cue ? cue.text : '';
      } else if (embeddedTrack && embeddedTrack.activeCues) {
        text = [...embeddedTrack.activeCues].map(c => (c.text || '').replace(/<[^>]+>/g, '')).join('\n');
      }
      if (text !== lastText) {
        lastText = text;
        subtitleEl.textContent = text;
      }
    }

    const on = (type, fn) => { video.addEventListener(type, fn); cleanup.push(() => video.removeEventListener(type, fn)); };
    on('timeupdate', () => {
      if (video.currentTime > 0 && !video.paused) { started = true; buffering = false; }
      renderSubtitle();
      pushState(false);
    });
    on('playing', () => { started = true; buffering = false; pushState(true); });
    on('waiting', () => { buffering = true; pushState(true); });
    ['pause', 'play', 'durationchange', 'loadedmetadata', 'ratechange', 'seeked'].forEach(t => on(t, () => { renderSubtitle(); pushState(true); }));
    on('error', () => {
      const code = video.error ? video.error.code : 0;
      emit(listeners.event, { type: 'error', message: ERROR_TEXT[code] || 'Playback error' });
    });
    // Finished: the app decides between the next episode and closing.
    on('ended', () => { if (opts.onNext) opts.onNext(null); else if (opts.onClose) opts.onClose(); });
    // Track lists appear once the TV has parsed the stream.
    if (video.audioTracks && video.audioTracks.addEventListener) {
      const f = () => pushState(true);
      ['addtrack', 'change'].forEach(t => video.audioTracks.addEventListener(t, f));
      cleanup.push(() => ['addtrack', 'change'].forEach(t => video.audioTracks.removeEventListener(t, f)));
    }
    const textAdded = () => pushState(true);
    video.textTracks.addEventListener('addtrack', textAdded);
    cleanup.push(() => video.textTracks.removeEventListener('addtrack', textAdded));

    async function loadExternal(url) {
      externalCues = parseCues(await fetchSubtitleText(url));
      externalActive = externalCues.length > 0;
      if (embeddedTrack) { embeddedTrack.mode = 'disabled'; embeddedTrack = null; }
      renderSubtitle();
      pushState(true);
      return externalActive;
    }

    function setAspect(mode) {
      const root = video.closest('.ap-root');
      if (root) {
        root.classList.toggle('ap-aspect-zoom', mode === 'ZOOM');
        root.classList.toggle('ap-aspect-stretch', mode === 'STRETCH' || /:/.test(mode));
      }
      // Forced ratios: size the video box to that ratio and stretch the picture into it.
      const m = /^(\d+):(\d+)$/.exec(mode);
      if (m) {
        const ratio = Number(m[1]) / Number(m[2]);
        // The player's own box (it is zoomed on the TV, so not the window size).
        const W = video.parentElement.clientWidth;
        const H = video.parentElement.clientHeight;
        const w = Math.min(W, H * ratio);
        const h = w / ratio;
        Object.assign(video.style, { width: `${w}px`, height: `${h}px`, left: `${(W - w) / 2}px`, top: `${(H - h) / 2}px` });
      } else {
        Object.assign(video.style, { width: '', height: '', left: '', top: '' });
      }
    }

    const commands = {
      togglePause: () => (video.paused ? video.play() : video.pause()),
      seek: t => { video.currentTime = Math.max(0, Number(t) || 0); },
      setProperty: (name, value) => {
        switch (name) {
          case 'aid': {
            const audio = video.audioTracks;
            if (audio) for (let i = 0; i < audio.length; i++) audio[i].enabled = i === Number(value);
            break;
          }
          case 'sid': {
            if (embeddedTrack) embeddedTrack.mode = 'disabled';
            embeddedTrack = null;
            externalActive = false;
            if (value === 'web') externalActive = externalCues.length > 0;
            else if (value !== 'no') {
              embeddedTrack = video.textTracks[Number(value)] || null;
              if (embeddedTrack) embeddedTrack.mode = 'hidden'; // cues drawn by us, Android style
            }
            renderSubtitle();
            break;
          }
          case 'speed': video.playbackRate = Number(value) || 1; break;
          case 'sub-delay': subDelay = Number(value) || 0; renderSubtitle(); break;
          default: break; // volume/mute: the TV remote controls the TV's own volume
        }
        pushState(true);
        return true;
      },
      webSub: url => loadExternal(url),
      aspect: mode => { setAspect(mode); return true; },
      syncToHeard: () => {
        if (!externalActive) return false;
        const t = video.currentTime - subDelay;
        const cue = externalCues.find(c => t >= c.start && t <= c.end);
        if (!cue) return false;
        subDelay = video.currentTime - cue.start;
        renderSubtitle();
        pushState(true);
        return true;
      },
      retry: () => {
        const pos = video.currentTime;
        video.load();
        video.currentTime = pos;
        return video.play();
      },
      resume: choice => {
        if (choice === 'restart') {
          video.currentTime = 0;
          if (opts.onRestart) opts.onRestart();
        }
        return video.play();
      },
      playNext: () => { if (opts.onNext) opts.onNext(null); },
      fullscreen: () => true,
      close: () => { if (opts.onClose) opts.onClose(); },
    };

    // Start: native pipeline straight from the stream URL (no MSE), so the TV
    // decodes Dolby Vision/Atmos itself.
    video.src = opts.url;
    const resume = opts.init && opts.init.resumeSeconds > 30;
    const startAt = resume ? opts.init.resumeSeconds : (opts.startSeconds || 0);
    if (startAt > 0) {
      const seekOnce = () => { video.currentTime = startAt; video.removeEventListener('loadedmetadata', seekOnce); };
      video.addEventListener('loadedmetadata', seekOnce);
    }
    if (!resume) video.play().catch(() => {});
    if (opts.subtitleUrl) loadExternal(opts.subtitleUrl).catch(() => {});

    return {
      cmd: (name, ...args) => {
        const fn = commands[name];
        if (!fn) return Promise.reject(new Error(`Unknown player command ${name}`));
        try { return Promise.resolve(fn(...args)); } catch (err) { return Promise.reject(err); }
      },
      onInit: cb => sub(listeners.init, cb),
      onState: cb => sub(listeners.state, cb),
      onEvent: cb => sub(listeners.event, cb),
      ready: () => {
        emit(listeners.init, opts.init || {});
        pushState(true);
      },
      /** Web subtitle list for the menu (after the player opened). */
      setWebSubs: payload => emit(listeners.event, { type: 'subtitles', ...payload }),
      /** Push any UI event, e.g. {type: 'nextEpisode', value}. */
      emitEvent: payload => emit(listeners.event, payload),
      destroy: () => {
        cleanup.forEach(f => f());
        video.pause();
        video.removeAttribute('src');
        video.load();
      },
      state,
    };
  }

  window.TvBackend = Object.freeze({ create, parseCues });
})();
