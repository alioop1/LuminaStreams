/**
 * LuminaStreams — Video Player Screen
 * Full-featured player with HLS.js support, subtitles, audio track switching,
 * volume booster, RTL controls, progress saving, and auto-hiding controls.
 */
(() => {
  'use strict';

  const { tr } = window.Helpers;

  /* ── State ────────────────────────────────────────────────────────── */
  let video            = null;
  let hlsInstance      = null;
  let params           = {};
  let subtitlesList    = [];
  let activeSubIndex   = -1;
  let isFetchingSubs   = false;
  let isFetchingStreams = false;
  let activeSelectorType = null; // 'subtitles' | 'audio' | null
  let controlsTimer    = null;
  let progressInterval = null;
  let isFullscreen     = false;
  let isDragging       = false;
  let destroyed        = false;

  // Web Audio API volume booster
  let audioCtx         = null;
  let audioSourceNode  = null;
  let audioGainNode    = null;
  let isAudioBoosted   = false;

  /* ── DOM refs ────────────────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);

  /* ── HLS.js loader ───────────────────────────────────────────────── */
  let hlsReady = false;

  function loadHls() {
    return new Promise((resolve, reject) => {
      if (window.Hls) { hlsReady = true; return resolve(); }
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.20/dist/hls.min.js';
      s.onload = () => { hlsReady = true; resolve(); };
      s.onerror = () => reject(new Error('Failed to load hls.js'));
      document.head.appendChild(s);
    });
  }

  /* ── Time formatting ─────────────────────────────────────────────── */
  function fmtTime(seconds) {
    if (!seconds || !isFinite(seconds)) return '00:00';
    const s = Math.floor(seconds);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const mm = String(m).padStart(2, '0');
    const ss = String(sec).padStart(2, '0');
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  }

  /* ── Controls auto-hide ──────────────────────────────────────────── */
  function showControls() {
    const overlay = $('player-overlay');
    const controls = $('player-controls');
    if (!overlay || !controls) return;
    overlay.style.cursor = 'default';
    controls.style.opacity = '1';
    controls.style.transform = 'translateY(0)';
    // Top bar
    const topBar = overlay.querySelector('.player-top-bar');
    if (topBar) {
      topBar.style.opacity = '1';
      topBar.style.transform = 'translateY(0)';
    }
    resetControlsTimer();
  }

  function hideControls() {
    if (video && video.paused) return;
    const selector = $('track-selector');
    if (selector && !selector.classList.contains('hidden')) return; // Keep visible if popup open
    const overlay = $('player-overlay');
    const controls = $('player-controls');
    if (!overlay || !controls) return;
    overlay.style.cursor = 'none';
    controls.style.opacity = '0';
    controls.style.transform = 'translateY(20px)';
    const topBar = overlay.querySelector('.player-top-bar');
    if (topBar) {
      topBar.style.opacity = '0';
      topBar.style.transform = 'translateY(-20px)';
    }
  }

  function resetControlsTimer() {
    clearTimeout(controlsTimer);
    controlsTimer = setTimeout(hideControls, 3500);
  }

  /* ── SRT → VTT conversion ────────────────────────────────────────── */
  function srtToVtt(srtText) {
    let vtt = 'WEBVTT\n\n';
    const content = srtText.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    const blocks = content.split(/\n\n+/);

    for (const block of blocks) {
      const lines = block.trim().split('\n');
      if (lines.length < 2) continue;

      let tsIdx = -1;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes('-->')) { tsIdx = i; break; }
      }
      if (tsIdx === -1) continue;

      const timestamp = lines[tsIdx].replace(/,/g, '.');
      const text = lines.slice(tsIdx + 1).join('\n');
      vtt += `${timestamp}\n${text}\n\n`;
    }
    return vtt;
  }

  /* ── Load subtitle track ─────────────────────────────────────────── */
  async function loadSubtitleTrack(sub) {
    if (!video) return;

    // Remove existing tracks
    const existing = video.querySelectorAll('track');
    existing.forEach(t => {
      if (t._blobUrl) URL.revokeObjectURL(t._blobUrl);
      t.remove();
    });

    if (!sub) {
      activeSubIndex = -1;
      return;
    }

    try {
      let trackUrl = sub.url;

      // Always fetch and wrap in blob to bypass cross-origin restrictions on <track>
      try {
        const res = await fetch(sub.url);
        if (res.ok) {
          // Hebrew sources (e.g. Wizdom) often serve windows-1255 labelled as UTF-8.
          const bytes = await res.arrayBuffer();
          let rawText;
          try {
            rawText = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
          } catch {
            rawText = new TextDecoder('windows-1255').decode(bytes);
          }
          let vttText;
          if (rawText.trim().startsWith('WEBVTT')) {
            vttText = rawText;
          } else {
            vttText = srtToVtt(rawText);
          }
          const blob = new Blob([vttText], { type: 'text/vtt;charset=utf-8' });
          trackUrl = URL.createObjectURL(blob);
        }
      } catch (fetchErr) {
        console.warn('[Player] Subtitle direct fetch failed, trying direct URL:', fetchErr);
      }

      const track = document.createElement('track');
      track.kind = 'subtitles';
      track.label = sub.language || 'Unknown';
      track.srclang = sub.lang3 || 'und';
      track.src = trackUrl;
      track.default = true;
      if (trackUrl.startsWith('blob:')) track._blobUrl = trackUrl;

      video.appendChild(track);

      // Activate after appending
      requestAnimationFrame(() => {
        if (video.textTracks && video.textTracks.length > 0) {
          for (let i = 0; i < video.textTracks.length; i++) {
            video.textTracks[i].mode = 'disabled';
          }
          video.textTracks[video.textTracks.length - 1].mode = 'showing';
        }
      });

      applySubtitleStyle();
    } catch (err) {
      console.error('[Player] Subtitle load error:', err);
    }
  }

  function applySubtitleStyle() {
    let styleEl = document.getElementById('subtitle-style');
    if (!styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = 'subtitle-style';
      document.head.appendChild(styleEl);
    }
    styleEl.textContent = `
      ::cue {
        direction: rtl;
        font-family: 'Inter', system-ui, -apple-system, sans-serif;
        color: #FFE600;
        background: rgba(0, 0, 0, 0.75);
        font-size: 1.35rem;
        line-height: 1.5;
        padding: 4px 10px;
        border-radius: 4px;
        text-shadow: 0 2px 5px rgba(0,0,0,0.9);
      }
    `;
  }

  /* ── Subtitle Selector Popup ─────────────────────────────────────── */
  function showSubtitleSelector() {
    activeSelectorType = 'subtitles';
    const selector = $('track-selector');
    const title = $('track-selector-title');
    const list = $('track-selector-list');
    if (!selector || !list) return;

    title.textContent = tr('Subtitles', 'כתוביות');
    list.innerHTML = '';

    // "Off" option
    const offItem = document.createElement('div');
    offItem.className = `subtitle-option ${activeSubIndex === -1 ? 'active' : ''}`;
    offItem.innerHTML = `
      <span style="width:20px;text-align:center;font-weight:bold;color:var(--cyan)">${activeSubIndex === -1 ? '✓' : ''}</span>
      <span>${tr('Off', 'כבוי')}</span>
    `;
    offItem.onclick = () => {
      activeSubIndex = -1;
      loadSubtitleTrack(null);
      showSubtitleSelector();
    };
    list.appendChild(offItem);

    if (isFetchingSubs && subtitlesList.length === 0) {
      const loading = document.createElement('div');
      loading.style.cssText = 'padding:24px 16px;text-align:center;color:rgba(238,242,255,0.6);font-size:13px;display:flex;flex-direction:column;align-items:center;gap:10px;';
      loading.innerHTML = '<div class="spinner" style="width:24px;height:24px;border-width:2px;"></div><span>מחפש כתוביות בעברית ובאנגלית…</span>';
      list.appendChild(loading);
      selector.classList.remove('hidden');
      return;
    }

    if (subtitlesList.length === 0) {
      const empty = document.createElement('div');
      empty.style.cssText = 'padding:18px;text-align:center;color:rgba(238,242,255,0.5);font-size:13px;';
      empty.textContent = 'לא נמצאו כתוביות זמינות לסרט/פרק זה';
      list.appendChild(empty);
      selector.classList.remove('hidden');
      return;
    }

    // Group by language
    const byLang = {};
    subtitlesList.forEach((sub, idx) => {
      const key = sub.lang3 || 'unknown';
      if (!byLang[key]) byLang[key] = [];
      byLang[key].push({ ...sub, _idx: idx });
    });

    for (const [lang, subs] of Object.entries(byLang)) {
      const header = document.createElement('div');
      header.style.cssText = 'padding:10px 14px 4px;font-size:11px;font-weight:700;color:var(--cyan);text-transform:uppercase;letter-spacing:1px;';
      header.textContent = subs[0].language || lang;
      list.appendChild(header);

      subs.forEach(sub => {
        const isAct = activeSubIndex === sub._idx;
        const item = document.createElement('div');
        item.className = `subtitle-option ${isAct ? 'active' : ''}`;
        item.innerHTML = `
          <span style="width:20px;text-align:center;color:var(--cyan);font-weight:bold">${isAct ? '✓' : ''}</span>
          <span style="flex:1;font-weight:${isAct ? '600' : '400'}">${sub.source}</span>
          <span style="font-size:11px;color:rgba(238,242,255,0.45)">${sub.filename ? window.Helpers.truncate(sub.filename, 28) : ''}</span>
        `;
        item.onclick = () => {
          activeSubIndex = sub._idx;
          loadSubtitleTrack(subtitlesList[sub._idx]);
          if (window.Storage) window.Storage.setSubtitleLang(sub.lang3);
          showSubtitleSelector();
        };
        list.appendChild(item);
      });
    }

    selector.classList.remove('hidden');
    selector.style.animation = 'fadeIn 0.2s ease';
  }

  /* ── Audio Tracks & Booster Selector Popup ────────────────────────── */
  let currentAudioLang = 'eng'; // Default is ALWAYS English!

  function showAudioSelector() {
    activeSelectorType = 'audio';
    const selector = $('track-selector');
    const title = $('track-selector-title');
    const list = $('track-selector-list');
    if (!selector || !list) return;

    title.textContent = tr('Audio & Sound', 'שמע וערוצי קול');
    list.innerHTML = '';

    // 1. Volume Booster (Web Audio API)
    const boostItem = document.createElement('div');
    boostItem.className = `subtitle-option ${isAudioBoosted ? 'active' : ''}`;
    boostItem.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid rgba(255,255,255,0.08);cursor:pointer;';
    boostItem.innerHTML = `
      <div style="display:flex;align-items:center;gap:10px;">
        <span style="font-size:18px;">🔊</span>
        <div>
          <div style="font-weight:700;font-size:13px;">הגברת עוצמת שמע (Boost 200%)</div>
          <div style="font-size:11px;color:rgba(238,242,255,0.5);">פתרון לסרטים עם קול שקט</div>
        </div>
      </div>
      <span style="padding:4px 10px;border-radius:12px;font-size:11px;font-weight:700;background:${isAudioBoosted ? 'var(--cyan)' : 'rgba(255,255,255,0.1)'};color:${isAudioBoosted ? '#000' : '#fff'};">${isAudioBoosted ? 'פעיל' : 'כבוי'}</span>
    `;
    boostItem.onclick = () => {
      toggleAudioBoost();
      showAudioSelector();
    };
    list.appendChild(boostItem);

    // 2. Language selection: STRICTLY English (Default), Russian (if available), Hebrew (if available)
    const streams = params.allStreams || [];
    const hasEng = streams.some(s => s.languages?.includes('eng'));
    const hasRus = streams.some(s => s.languages?.includes('rus'));
    const hasHeb = streams.some(s => s.languages?.includes('heb'));

    if (isFetchingStreams && streams.length === 0) {
      const loadingEl = document.createElement('div');
      loadingEl.style.cssText = 'padding:14px;display:flex;align-items:center;gap:10px;font-size:12px;color:rgba(238,242,255,0.7);';
      loadingEl.innerHTML = '<div class="spinner" style="width:16px;height:16px;border-width:2px;"></div><span>טוען מקורות שמע ושפות נוספות...</span>';
      list.appendChild(loadingEl);
    }

    if (hasRus || hasHeb || hasEng || streams.length > 0) {
      const langHeader = document.createElement('div');
      langHeader.style.cssText = 'padding:12px 14px 6px;font-size:11px;font-weight:700;color:var(--cyan);text-transform:uppercase;';
      langHeader.textContent = 'שפת שמע (Audio Language):';
      list.appendChild(langHeader);

      const langTabsContainer = document.createElement('div');
      langTabsContainer.style.cssText = 'display:flex;gap:8px;padding:0 14px 10px;border-bottom:1px solid rgba(255,255,255,0.08);overflow-x:auto;';

      const langs = [
        { code: 'eng', flag: '🇬🇧', label: 'אנגלית (ברירת מחדל)', show: true },
        { code: 'rus', flag: '🇷🇺', label: 'רוסית', show: hasRus },
        { code: 'heb', flag: '🇮🇱', label: 'עברית', show: hasHeb },
      ];

      langs.filter(l => l.show).forEach(l => {
        const isSelected = currentAudioLang === l.code;
        const btn = document.createElement('button');
        btn.style.cssText = `
          display:flex;align-items:center;gap:6px;border-radius:18px;padding:6px 12px;font-size:12px;font-weight:700;cursor:pointer;transition:all 0.2s;
          background:${isSelected ? 'var(--cyan)' : 'rgba(255,255,255,0.08)'};
          color:${isSelected ? '#000' : '#fff'};
          border:1px solid ${isSelected ? 'var(--cyan)' : 'rgba(255,255,255,0.15)'};
        `;
        btn.innerHTML = `<span>${l.flag}</span> <span>${l.label}</span>`;
        btn.onclick = (e) => {
          e.stopPropagation();
          currentAudioLang = l.code;
          showAudioSelector();
        };
        langTabsContainer.appendChild(btn);
      });

      list.appendChild(langTabsContainer);
    }

    // 3. Embedded HLS audio tracks (filtered ONLY to English, Russian, Hebrew)
    const ALLOWED_LANGS = ['eng', 'en', 'rus', 'ru', 'heb', 'he', 'iw'];
    if (hlsInstance && hlsInstance.audioTracks && hlsInstance.audioTracks.length > 1) {
      const matchingHls = hlsInstance.audioTracks.filter(t => {
        const langCode = (t.lang || t.name || '').toLowerCase();
        return ALLOWED_LANGS.some(al => langCode.includes(al));
      });

      if (matchingHls.length > 0) {
        const hlsHeader = document.createElement('div');
        hlsHeader.style.cssText = 'padding:10px 14px 4px;font-size:11px;font-weight:700;color:var(--cyan);text-transform:uppercase;';
        hlsHeader.textContent = 'ערוצי שמע מובנים (HLS):';
        list.appendChild(hlsHeader);

        matchingHls.forEach((track) => {
          const originalIdx = hlsInstance.audioTracks.indexOf(track);
          const isActive = hlsInstance.audioTrack === originalIdx;
          const item = document.createElement('div');
          item.className = `subtitle-option ${isActive ? 'active' : ''}`;
          item.innerHTML = `
            <span style="width:20px;text-align:center;color:var(--cyan);font-weight:bold">${isActive ? '✓' : ''}</span>
            <span style="flex:1">${track.name || track.lang || `רצועה ${originalIdx + 1}`}</span>
          `;
          item.onclick = () => {
            hlsInstance.audioTrack = originalIdx;
            showAudioSelector();
          };
          list.appendChild(item);
        });
      }
    }

    // 4. Stream sources for the selected language
    const streamsForLang = streams.filter(s => s.languages?.includes(currentAudioLang));
    const toRender = streamsForLang.length > 0 ? streamsForLang : (currentAudioLang === 'eng' ? streams : []);

    if (toRender.length > 0) {
      const streamHeader = document.createElement('div');
      streamHeader.style.cssText = 'padding:12px 14px 4px;font-size:11px;font-weight:700;color:rgba(238,242,255,0.6);text-transform:uppercase;';
      const langName = currentAudioLang === 'rus' ? 'רוסית' : (currentAudioLang === 'heb' ? 'עברית' : 'אנגלית');
      streamHeader.textContent = `מקורות שמע ב${langName} (תואמי דפדפן ראשונים):`;
      list.appendChild(streamHeader);

      toRender.slice(0, 16).forEach((st) => {
        const isCurrent = (st.url && st.url === params.url) || (!st.url && st.infoHash && params.url && params.url.includes(st.infoHash));
        const isWebAudio = st.isWebAudio || st.audio === 'AAC';
        const isWarning = ['DTS', 'DTS-HD', 'TrueHD', 'Atmos', 'DD5.1', 'AC3', 'EAC3'].includes(st.audio);

        const item = document.createElement('div');
        item.className = `subtitle-option ${isCurrent ? 'active' : ''}`;
        item.style.cssText = 'display:flex;align-items:center;gap:10px;padding:9px 14px;cursor:pointer;';
        item.innerHTML = `
          <span style="width:16px;text-align:center;color:var(--cyan);font-weight:bold">${isCurrent ? '✓' : ''}</span>
          <div style="flex:1;min-width:0;">
            <div style="display:flex;align-items:center;gap:6px;margin-bottom:2px;">
              <span style="font-weight:700;font-size:12px;">${st.quality || '1080p'}</span>
              ${isWebAudio ? `<span style="background:rgba(16,185,129,0.25);color:#34d399;border:1px solid rgba(16,185,129,0.4);border-radius:4px;padding:1px 6px;font-size:10px;font-weight:700;">🔊 שמע תואם כרום (AAC)</span>` : ''}
              ${isWarning ? `<span style="background:rgba(239,68,68,0.2);color:#fca5a5;border:1px solid rgba(239,68,68,0.3);border-radius:4px;padding:1px 6px;font-size:10px;">⚠️ ${st.audio} (ללא שמע בכרום)</span>` : (!isWebAudio && st.audio ? `<span style="background:rgba(255,255,255,0.1);color:#fff;border-radius:4px;padding:1px 6px;font-size:10px;">${st.audio}</span>` : '')}
            </div>
            <div style="font-size:11px;color:rgba(238,242,255,0.45);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${window.Helpers.truncate(st.filename || st.name, 40)}</div>
          </div>
        `;
        item.onclick = () => {
          if (!isCurrent) {
            switchStream(st);
          }
        };
        list.appendChild(item);
      });
    } else if (streams.length > 0) {
      const emptyEl = document.createElement('div');
      emptyEl.style.cssText = 'padding:16px 14px;color:rgba(238,242,255,0.5);font-size:12px;text-align:center;';
      emptyEl.textContent = 'לא נמצאו מקורות נוספים בשפה זו';
      list.appendChild(emptyEl);
    }

    selector.classList.remove('hidden');
    selector.style.animation = 'fadeIn 0.2s ease';
  }

  function hideTrackSelector() {
    activeSelectorType = null;
    const selector = $('track-selector');
    if (selector) selector.classList.add('hidden');
  }

  /* ── Switch Stream Source ────────────────────────────────────────── */
  async function switchStream(stream) {
    if (!stream) return;
    const currentPos = video ? video.currentTime : (params.startPosition || 0);
    saveProgress();
    hideTrackSelector();

    const overlay = $('player-overlay');
    const notice = document.createElement('div');
    notice.className = 'stream-switch-notice';
    notice.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(10,16,28,0.92);border:1px solid rgba(0,212,255,0.4);color:#fff;padding:18px 28px;border-radius:14px;z-index:70;display:flex;align-items:center;gap:14px;font-size:15px;box-shadow:0 12px 40px rgba(0,0,0,0.8);';
    notice.innerHTML = '<div class="spinner" style="width:24px;height:24px;border-width:2px;"></div><span>מחליף מקור שמע וממשיך צפייה...</span>';
    overlay?.appendChild(notice);

    try {
      let playUrl = stream.url;
      if (!playUrl && stream.infoHash) {
        const debridClient = window.AllDebrid;
        if (debridClient) {
          playUrl = await debridClient.resolveStream(stream.infoHash, null, params.season, params.episode);
        }
      }
      if (!playUrl) throw new Error('לא ניתן היה לפתוח את מקור השמע הנבחר');

      params.url = playUrl;
      params.audio = stream.audio || null;
      params.isWebAudio = !!stream.isWebAudio;
      params.startPosition = currentPos;
      await initPlayback(playUrl);
      if (video) video.currentTime = currentPos;
      checkAudioCompatibilityAndWarn();
    } catch (err) {
      console.error('[Player] Switch stream failed:', err);
      showError(err.message || 'שגיאה במעבר למקור שמע זה');
    } finally {
      notice.remove();
    }
  }

  /* ── Web Audio API Volume & Dialogue Booster ─────────────────────── */
  let audioCompressorNode = null;

  function toggleAudioBoost() {
    try {
      // In Web browser mode, MediaElementAudioSourceNode on non-CORS CDNs forces silence
      if (!window.electronAPI) {
        if (video) {
          video.muted = false;
          video.volume = 1.0;
          updateVolumeUI();
        }
        return false;
      }

      if (!audioCtx) {
        const AudioCtxClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtxClass) return false;
        audioCtx = new AudioCtxClass();
        audioSourceNode = audioCtx.createMediaElementSource(video);

        // Dynamic range compressor for clear speech & loudness normalization
        audioCompressorNode = audioCtx.createDynamicsCompressor();
        audioCompressorNode.threshold.value = -24;
        audioCompressorNode.knee.value = 30;
        audioCompressorNode.ratio.value = 12;
        audioCompressorNode.attack.value = 0.003;
        audioCompressorNode.release.value = 0.25;

        audioGainNode = audioCtx.createGain();
        audioSourceNode.connect(audioCompressorNode);
        audioCompressorNode.connect(audioGainNode);
        audioGainNode.connect(audioCtx.destination);
      }
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      isAudioBoosted = !isAudioBoosted;
      if (audioGainNode) {
        audioGainNode.gain.value = isAudioBoosted ? 2.5 : 1.0;
      }
      return isAudioBoosted;
    } catch (e) {
      console.warn('[Player] Audio boost toggle error:', e);
      return false;
    }
  }

  /* ── Speed selector ──────────────────────────────────────────────── */
  const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
  let currentSpeedIdx = 2; // 1x

  function cycleSpeed() {
    currentSpeedIdx = (currentSpeedIdx + 1) % SPEEDS.length;
    const speed = SPEEDS[currentSpeedIdx];
    if (video) video.playbackRate = speed;
    const btn = $('player-speed');
    if (btn) btn.textContent = `${speed}x`;
  }

  /* ── Progress bar ────────────────────────────────────────────────── */
  function updateProgress() {
    if (!video || isDragging) return;
    const current = video.currentTime;
    const duration = video.duration;
    if (!isFinite(duration) || duration === 0) return;

    const pct = (current / duration) * 100;

    const played = $('player-progress-played');
    const thumb = $('player-progress-thumb');
    const timeCurrent = $('player-time-current');
    const timeDuration = $('player-time-duration');

    if (played) played.style.width = `${pct}%`;
    if (thumb) thumb.style.left = `${pct}%`;
    if (timeCurrent) timeCurrent.textContent = fmtTime(current);
    if (timeDuration) timeDuration.textContent = fmtTime(duration);
  }

  function updateBuffered() {
    if (!video) return;
    const buffered = video.buffered;
    const duration = video.duration;
    if (!buffered.length || !isFinite(duration)) return;

    const end = buffered.end(buffered.length - 1);
    const pct = (end / duration) * 100;

    const buffEl = $('player-progress-buffered');
    if (buffEl) buffEl.style.width = `${pct}%`;
  }

  function seekToPosition(e) {
    if (!video || !isFinite(video.duration)) return;
    const bar = $('player-progress-bar');
    if (!bar) return;

    const rect = bar.getBoundingClientRect();
    // Timeline is always LTR (left = 0, right = end)
    let ratio = (e.clientX - rect.left) / rect.width;
    ratio = Math.max(0, Math.min(1, ratio));
    video.currentTime = ratio * video.duration;
    updateProgress();
  }

  /* ── Volume ──────────────────────────────────────────────────────── */
  function updateVolumeUI() {
    if (!video) return;
    const slider = $('player-volume-slider');
    const iconOn = $('icon-volume-on');
    const iconOff = $('icon-volume-off');
    if (slider) slider.value = video.muted ? 0 : Math.round(video.volume * 100);
    if (iconOn) iconOn.classList.toggle('hidden', video.muted || video.volume === 0);
    if (iconOff) iconOff.classList.toggle('hidden', !video.muted && video.volume > 0);
  }

  function setVolume(val) {
    if (!video) return;
    video.volume = Math.max(0, Math.min(1, val));
    video.muted = false;
    if (window.Storage) window.Storage.setVolume(video.volume);
    updateVolumeUI();
  }

  function toggleMute() {
    if (!video) return;
    video.muted = !video.muted;
    updateVolumeUI();
  }

  /* ── Play / Pause ────────────────────────────────────────────────── */
  function togglePlayPause() {
    if (!video) return;
    if (video.paused || video.ended) {
      video.play().catch(() => {});
    } else {
      video.pause();
    }
  }

  function updatePlayPauseIcon() {
    const iconPlay = $('icon-play');
    const iconPause = $('icon-pause');
    if (!video || !iconPlay || !iconPause) return;
    iconPlay.classList.toggle('hidden', !video.paused);
    iconPause.classList.toggle('hidden', video.paused);
  }

  /* ── Fullscreen ──────────────────────────────────────────────────── */
  function toggleFullscreen() {
    if (window.electronAPI && window.electronAPI.toggleFullscreen) {
      window.electronAPI.toggleFullscreen();
      isFullscreen = !isFullscreen;
    } else {
      const overlay = $('player-overlay');
      if (!document.fullscreenElement) {
        overlay?.requestFullscreen?.();
        isFullscreen = true;
      } else {
        document.exitFullscreen?.();
        isFullscreen = false;
      }
    }
    const enter = $('icon-fullscreen-enter');
    const exit = $('icon-fullscreen-exit');
    if (enter) enter.classList.toggle('hidden', isFullscreen);
    if (exit) exit.classList.toggle('hidden', !isFullscreen);
  }

  /* ── Watch progress ──────────────────────────────────────────────── */
  function saveProgress() {
    if (!video || !isFinite(video.currentTime) || !isFinite(video.duration)) return;
    if (video.duration < 15) return;
    // Progress is keyed by IMDb id (shared with Android/iOS); without it there is nothing to save.
    if (!window.WatchProgress || !params.imdbId) return;

    window.WatchProgress.save({
      mediaId: params.id,
      imdbId: params.imdbId,
      type: params.type,
      title: params.showTitle || params.title || '',
      poster_path: params.poster_path || params.poster || '',
      season: params.season,
      episode: params.episode,
      position: video.currentTime,
      duration: video.duration,
    });
  }

  function showResumeToast(pos) {
    const overlay = $('player-overlay');
    if (!overlay) return;
    const oldToast = overlay.querySelector('.player-resume-toast');
    if (oldToast) oldToast.remove();

    const toast = document.createElement('div');
    toast.className = 'player-resume-toast';
    toast.style.cssText = `
      position:absolute;top:70px;right:36px;background:rgba(10,16,28,0.92);
      border:1px solid rgba(0,212,255,0.4);color:#fff;padding:10px 18px;
      border-radius:12px;z-index:50;display:flex;align-items:center;gap:12px;
      font-size:13px;box-shadow:0 8px 30px rgba(0,0,0,0.7);animation:fadeIn 0.3s ease;
    `;
    toast.innerHTML = `
      <span>ממשיך מ-<strong>${fmtTime(pos)}</strong> ↻</span>
      <button style="background:rgba(255,255,255,0.15);border:none;color:#fff;padding:4px 10px;border-radius:6px;cursor:pointer;font-size:12px;">התחל מהתחלה</button>
    `;
    const startOverBtn = toast.querySelector('button');
    startOverBtn.onclick = (e) => {
      e.stopPropagation();
      if (video) video.currentTime = 0;
      toast.remove();
    };
    overlay.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.5s ease';
      setTimeout(() => toast.remove(), 500);
    }, 6000);
  }

  function ensureUnmuted() {
    if (video && video.muted) {
      video.muted = false;
      video.volume = Math.max(video.volume || 1.0, 0.8);
      updateVolumeUI();
      const notice = $('player-overlay')?.querySelector('.player-unmute-notice');
      if (notice) notice.remove();
    }
  }

  function showUnmuteNotice() {
    const overlay = $('player-overlay');
    if (!overlay) return;
    const old = overlay.querySelector('.player-unmute-notice');
    if (old) old.remove();

    const notice = document.createElement('div');
    notice.className = 'player-unmute-notice';
    notice.style.cssText = `
      position:absolute;top:24px;left:50%;transform:translateX(-50%);
      background:linear-gradient(135deg, #00d4ff, #0088ff);color:#0a101c;padding:12px 28px;
      border-radius:30px;font-weight:800;font-size:14px;z-index:65;
      cursor:pointer;box-shadow:0 6px 30px rgba(0,212,255,0.7);
      animation:pulse 1.8s infinite;display:flex;align-items:center;gap:10px;
    `;
    notice.innerHTML = '<span style="font-size:20px">🔊</span> <span>כרום השתיק את השמע — לחץ כאן להפעלת שמע</span>';
    notice.onclick = (e) => {
      e.stopPropagation();
      if (video) {
        video.muted = false;
        video.volume = 1.0;
        updateVolumeUI();
      }
      notice.remove();
    };
    overlay.appendChild(notice);
  }

  function checkAudioCompatibilityAndWarn() {
    const overlay = $('player-overlay');
    if (!overlay) return;
    const oldWarn = overlay.querySelector('.player-audio-warning-banner');
    if (oldWarn) oldWarn.remove();

    const isUnsupported = params.audio && ['AC3', 'DD5.1', 'EAC3', 'DTS', 'DTS-HD', 'TrueHD', 'Atmos'].includes(params.audio);
    if (!isUnsupported && (params.isWebAudio || params.audio === 'AAC')) {
      return;
    }

    const aacAlt = (params.allStreams || []).find(s => s.isWebAudio || s.audio === 'AAC');

    const banner = document.createElement('div');
    banner.className = 'player-audio-warning-banner';
    banner.style.cssText = `
      position:absolute;top:24px;left:50%;transform:translateX(-50%);
      background:rgba(20,24,36,0.96);border:1px solid rgba(239,68,68,0.7);
      box-shadow:0 8px 30px rgba(0,0,0,0.85);color:#fff;padding:10px 20px;
      border-radius:30px;z-index:66;display:flex;align-items:center;gap:12px;
      font-size:13px;animation:fadeIn 0.3s ease;
    `;
    banner.innerHTML = `
      <span style="color:#fca5a5;font-weight:600;">⚠️ שמע בפורמט ${params.audio || 'AC3/DTS'} אינו נתמך בכרום.</span>
      ${aacAlt ? `<button id="auto-switch-aac-btn" style="background:#10b981;border:none;color:#000;font-weight:800;padding:6px 16px;border-radius:20px;cursor:pointer;font-size:12px;display:flex;align-items:center;gap:6px;box-shadow:0 2px 10px rgba(16,185,129,0.4);">🔊 החלף עכשיו לשמע תואם כרום (AAC)</button>` : `<button id="open-audio-sel-btn" style="background:var(--cyan);border:none;color:#000;font-weight:700;padding:6px 16px;border-radius:20px;cursor:pointer;font-size:12px;">🔊 בחר מקור שמע אחר</button>`}
      <span id="close-audio-banner" style="cursor:pointer;opacity:0.6;font-size:16px;margin-right:4px;">✕</span>
    `;

    banner.querySelector('#auto-switch-aac-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      banner.remove();
      if (aacAlt) switchStream(aacAlt);
    });

    banner.querySelector('#open-audio-sel-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      banner.remove();
      showAudioSelector();
    });

    banner.querySelector('#close-audio-banner')?.addEventListener('click', (e) => {
      e.stopPropagation();
      banner.remove();
    });

    overlay.appendChild(banner);
  }

  /* ── Error overlay ───────────────────────────────────────────────── */
  function showError(message) {
    const overlay = $('player-overlay');
    if (!overlay) return;

    let errEl = overlay.querySelector('.player-error-overlay');
    if (!errEl) {
      errEl = document.createElement('div');
      errEl.className = 'player-error-overlay';
      errEl.style.cssText = `
        position:absolute;inset:0;display:flex;flex-direction:column;
        align-items:center;justify-content:center;background:rgba(0,0,0,0.88);
        z-index:50;gap:16px;animation:fadeIn 0.3s ease;
      `;
      overlay.appendChild(errEl);
    }

    errEl.innerHTML = `
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#FF3B5C" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/>
      </svg>
      <div style="font-size:17px;font-weight:700;color:#EEF2FF">${tr('Playback Error', 'שגיאת ניגון')}</div>
      <div style="font-size:13px;color:rgba(238,242,255,0.65);max-width:440px;text-align:center;line-height:1.5;">
        ${window.Helpers.escapeHtml(message)}
      </div>
      <div style="display:flex;gap:12px;margin-top:8px;">
        <button class="btn btn-primary" id="player-retry-btn">
          ${tr('Retry', 'נסה שוב ↻')}
        </button>
        <button class="btn btn-secondary" id="player-switch-audio-btn">
          ${tr('Choose Another Stream', 'בחר מקור שמע אחר 🔊')}
        </button>
      </div>
    `;

    errEl.querySelector('#player-retry-btn')?.addEventListener('click', () => {
      errEl.remove();
      initPlayback(params.url);
    });

    errEl.querySelector('#player-switch-audio-btn')?.addEventListener('click', () => {
      errEl.remove();
      showAudioSelector();
    });
  }

  function clearError() {
    const overlay = $('player-overlay');
    const err = overlay?.querySelector('.player-error-overlay');
    if (err) err.remove();
  }

  /* ── Keyboard handler ────────────────────────────────────────────── */
  function handleKeydown(e) {
    if (destroyed) return;
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    switch (e.key) {
      case ' ':
      case 'k':
        e.preventDefault();
        togglePlayPause();
        showControls();
        break;
      case 'ArrowLeft':
        e.preventDefault();
        if (video) video.currentTime = Math.max(0, video.currentTime - 10);
        showControls();
        break;
      case 'ArrowRight':
        e.preventDefault();
        if (video) video.currentTime = Math.min(video.duration, video.currentTime + 10);
        showControls();
        break;
      case 'ArrowUp':
        e.preventDefault();
        setVolume((video?.volume || 0) + 0.1);
        showControls();
        break;
      case 'ArrowDown':
        e.preventDefault();
        setVolume((video?.volume || 0) - 0.1);
        showControls();
        break;
      case 'f':
      case 'F':
        e.preventDefault();
        toggleFullscreen();
        break;
      case 'm':
      case 'M':
        e.preventDefault();
        toggleMute();
        showControls();
        break;
      case 'Escape':
        e.preventDefault();
        exitPlayer();
        break;
    }
  }

  /* ── Playback init ───────────────────────────────────────────────── */
  async function initPlayback(url) {
    if (!video || !url) return;
    clearError();

    const isHls = url.includes('.m3u8') || url.includes('m3u8');

    try {
      if (isHls) {
        await loadHls();
        if (window.Hls && window.Hls.isSupported()) {
          if (hlsInstance) { hlsInstance.destroy(); hlsInstance = null; }
          hlsInstance = new window.Hls({
            maxBufferLength: 60,
            maxMaxBufferLength: 120,
            startLevel: -1,
          });
          hlsInstance.loadSource(url);
          hlsInstance.attachMedia(video);

          hlsInstance.on(window.Hls.Events.MANIFEST_PARSED, async () => {
            if (params.startPosition && params.startPosition > 0) {
              video.currentTime = params.startPosition;
            }
            try {
              await video.play();
              ensureUnmuted();
            } catch (err) {
              if (err.name === 'NotAllowedError') {
                video.muted = true;
                await video.play();
                showUnmuteNotice();
              }
            }
            checkAudioCompatibilityAndWarn();
          });

          hlsInstance.on(window.Hls.Events.ERROR, (event, data) => {
            if (data.fatal) {
              switch (data.type) {
                case window.Hls.ErrorTypes.NETWORK_ERROR:
                  hlsInstance.startLoad();
                  break;
                case window.Hls.ErrorTypes.MEDIA_ERROR:
                  hlsInstance.recoverMediaError();
                  break;
                default:
                  showError(data.details || 'שגיאת ניגון HLS');
                  break;
              }
            }
          });
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
          video.src = url;
          checkAudioCompatibilityAndWarn();
        } else {
          showError('ניגון HLS אינו נתמך בדפדפן זה');
        }
      } else {
        // Direct stream (MP4 / MKV)
        video.src = url;
        video.addEventListener('loadedmetadata', async function onMeta() {
          video.removeEventListener('loadedmetadata', onMeta);
          if (params.startPosition && params.startPosition > 0) {
            video.currentTime = params.startPosition;
          }
          try {
            await video.play();
            ensureUnmuted();
          } catch (err) {
            if (err.name === 'NotAllowedError') {
              video.muted = true;
              try {
                await video.play();
                showUnmuteNotice();
              } catch (e2) {}
            }
          }
          checkAudioCompatibilityAndWarn();
        }, { once: true });
      }
    } catch (err) {
      console.error('[Player] Init error:', err);
      showError(err.message);
    }
  }

  /* ── Subtitle Fetcher ────────────────────────────────────────────── */
  async function fetchSubtitles() {
    // Resolve IMDb ID if missing
    if (!params.imdbId && params.id && window.TMDB) {
      try {
        const details = params.type === 'series'
          ? await window.TMDB.getTvDetails(params.id)
          : await window.TMDB.getMovieDetails(params.id);
        if (details?.external_ids?.imdb_id) {
          params.imdbId = details.external_ids.imdb_id;
        }
      } catch (e) {
        console.warn('[Player] TMDB external_id lookup failed:', e);
      }
    }

    if (!params.imdbId || !window.Subtitles) return;

    isFetchingSubs = true;
    try {
      const type = params.type === 'series' ? 'series' : 'movie';
      const subs = await window.Subtitles.fetchAll(type, params.imdbId, params.season, params.episode);
      if (destroyed) return;
      subtitlesList = subs || [];
      console.log(`[Player] Loaded ${subtitlesList.length} subtitles for ${params.imdbId}`);

      // Auto-load the preferred language only (Android: Hebrew / English / None).
      const prefLang = window.Storage ? window.Storage.getSubtitleLang() : 'heb';
      if (prefLang !== 'none' && subtitlesList.length > 0 && activeSubIndex === -1) {
        const autoIdx = subtitlesList.findIndex(s => s.lang3 === prefLang);
        if (autoIdx !== -1) {
          activeSubIndex = autoIdx;
          loadSubtitleTrack(subtitlesList[autoIdx]);
        }
      }
    } catch (err) {
      console.warn('[Player] Subtitle fetch error:', err);
    } finally {
      isFetchingSubs = false;
      if (activeSelectorType === 'subtitles') {
        showSubtitleSelector();
      }
    }
  }

  /* ── Background Stream Fetcher (for Audio Languages) ──────────────── */
  async function fetchStreamsIfNeeded() {
    if (params.allStreams && params.allStreams.length > 0) return;
    if (!window.Torrentio) return;

    // Resolve IMDb ID if missing
    if (!params.imdbId && params.id && window.TMDB) {
      try {
        const details = params.type === 'series'
          ? await window.TMDB.getTvDetails(params.id)
          : await window.TMDB.getMovieDetails(params.id);
        if (details?.external_ids?.imdb_id) {
          params.imdbId = details.external_ids.imdb_id;
        }
      } catch (e) {
        console.warn('[Player] TMDB lookup for streams failed:', e);
      }
    }

    if (!params.imdbId) return;

    isFetchingStreams = true;
    try {
      const type = params.type === 'series' ? 'series' : 'movie';
      let streams = [];
      if (type === 'series' && params.season != null && params.episode != null) {
        streams = await window.Torrentio.getEpisodeStreams(params.imdbId, params.season, params.episode);
      } else {
        streams = await window.Torrentio.getStreams(type, params.imdbId);
      }
      if (destroyed) return;
      if (streams && streams.length > 0) {
        params.allStreams = streams;
        console.log(`[Player] Loaded ${streams.length} alternate streams for audio tracks`);
        if (activeSelectorType === 'audio') {
          showAudioSelector();
        }
      }
    } catch (err) {
      console.warn('[Player] Streams fetch error:', err);
    } finally {
      isFetchingStreams = false;
      if (activeSelectorType === 'audio') {
        showAudioSelector();
      }
    }
  }

  /* ── Exit player ─────────────────────────────────────────────────── */
  function exitPlayer() {
    saveProgress();

    if (window.SyncManager && typeof window.SyncManager.flushProgress === 'function') {
      window.SyncManager.flushProgress().catch(() => {});
    }

    // Exit fullscreen if active
    if (isFullscreen) {
      if (window.electronAPI && window.electronAPI.toggleFullscreen) {
        window.electronAPI.toggleFullscreen();
      } else if (document.fullscreenElement) {
        document.exitFullscreen?.();
      }
      isFullscreen = false;
    }

    window.Router.back();
  }

  /* ── Bind all events ─────────────────────────────────────────────── */
  const boundHandlers = [];

  function on(el, evt, handler, options) {
    if (!el) return;
    el.addEventListener(evt, handler, options);
    boundHandlers.push({ el, evt, handler, options });
  }

  function bindEvents() {
    // Video events
    on(video, 'timeupdate', updateProgress);
    on(video, 'progress', updateBuffered);
    on(video, 'play', () => {
      ensureUnmuted();
      updatePlayPauseIcon();
      resetControlsTimer();
    });
    on(video, 'pause', () => { updatePlayPauseIcon(); showControls(); });
    on(video, 'ended', () => {
      updatePlayPauseIcon();
      showControls();
      saveProgress();
    });
    on(video, 'error', () => {
      const msg = video.error ? video.error.message : 'שגיאת ניגון לא מזוהה';
      showError(msg);
    });

    // Mouse move on overlay for controls show/hide
    const overlay = $('player-overlay');
    on(overlay, 'mousemove', showControls);
    on(overlay, 'click', (e) => {
      ensureUnmuted();
      if (e.target === video || e.target === overlay) {
        togglePlayPause();
      }
    });

    // Play/Pause button
    on($('player-play-pause'), 'click', (e) => {
      e.stopPropagation();
      ensureUnmuted();
      togglePlayPause();
    });

    // Skip buttons
    on($('player-skip-back'), 'click', (e) => {
      e.stopPropagation();
      if (video) video.currentTime = Math.max(0, video.currentTime - 10);
    });
    on($('player-skip-forward'), 'click', (e) => {
      e.stopPropagation();
      if (video) video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
    });

    // Volume
    on($('player-mute'), 'click', (e) => { e.stopPropagation(); toggleMute(); });
    on($('player-volume-slider'), 'input', (e) => {
      e.stopPropagation();
      setVolume(parseInt(e.target.value, 10) / 100);
    });

    // Progress bar — click to seek
    const progressBar = $('player-progress-bar');
    on(progressBar, 'mousedown', (e) => {
      e.stopPropagation();
      isDragging = true;
      seekToPosition(e);
    });
    on(document, 'mousemove', (e) => {
      if (isDragging) seekToPosition(e);
    });
    on(document, 'mouseup', () => {
      isDragging = false;
    });

    // Subtitles button
    on($('player-subtitles'), 'click', (e) => {
      e.stopPropagation();
      const selector = $('track-selector');
      if (activeSelectorType === 'subtitles' && selector && !selector.classList.contains('hidden')) {
        hideTrackSelector();
      } else {
        showSubtitleSelector();
      }
    });

    // Audio tracks button
    on($('player-audio'), 'click', (e) => {
      e.stopPropagation();
      const selector = $('track-selector');
      if (activeSelectorType === 'audio' && selector && !selector.classList.contains('hidden')) {
        hideTrackSelector();
      } else {
        showAudioSelector();
      }
    });

    // Track selector close
    on($('track-selector-close'), 'click', (e) => {
      e.stopPropagation();
      hideTrackSelector();
    });

    // Speed
    on($('player-speed'), 'click', (e) => {
      e.stopPropagation();
      cycleSpeed();
    });

    // Fullscreen
    on($('player-fullscreen'), 'click', (e) => {
      e.stopPropagation();
      toggleFullscreen();
    });

    // PiP
    on($('player-pip'), 'click', async (e) => {
      e.stopPropagation();
      try {
        if (document.pictureInPictureElement) {
          await document.exitPictureInPicture();
        } else if (video) {
          await video.requestPictureInPicture();
        }
      } catch (err) {
        console.warn('[Player] PiP error:', err);
      }
    });

    // External Player (VLC / Outplayer / Copy URL)
    on($('player-external'), 'click', (e) => {
      e.stopPropagation();
      const streamUrl = params.streamUrl || (video ? video.src : '');
      if (!streamUrl) {
        if (window.showToast) window.showToast('אין קישור זמין לניגון', 'warning');
        return;
      }

      const vlcUrl = `vlc://${streamUrl}`;
      const outplayerUrl = `outplayer://${streamUrl}`;

      const modalHtml = `
        <div style="text-align: right; padding: 12px; direction: rtl;">
          <h3 style="font-size: 18px; margin-bottom: 12px; color: #00D4FF;">🎬 פתיחה בנגן חיצוני</h3>
          <p style="font-size: 14px; color: #94a3b8; margin-bottom: 20px; line-height: 1.5;">
            אם הווידאו הוא בפורמט MKV או שהסאונד הוא DTS שאינו נתמך ישירות בדפדפן iOS, תוכל להפעיל אותו בלחיצה אחת בנגן מקצועי:
          </p>
          <div style="display: flex; flex-direction: column; gap: 12px;">
            <a href="${vlcUrl}" class="btn btn-primary" style="display: flex; align-items: center; justify-content: center; gap: 8px; text-decoration: none; padding: 12px; border-radius: 10px; font-weight: 600; background: linear-gradient(135deg, #FF8C00, #E65100); color: #fff;">
              <span>🟠 פתח ב-VLC for iOS</span>
            </a>
            <a href="${outplayerUrl}" class="btn" style="display: flex; align-items: center; justify-content: center; gap: 8px; text-decoration: none; padding: 12px; border-radius: 10px; font-weight: 600; background: rgba(255,255,255,0.08); color: #fff; border: 1px solid rgba(255,255,255,0.15);">
              <span>⚪ פתח ב-Outplayer</span>
            </a>
            <button id="btn-copy-stream-url" class="btn" style="display: flex; align-items: center; justify-content: center; gap: 8px; padding: 12px; border-radius: 10px; background: rgba(0,212,255,0.1); color: #00D4FF; border: 1px solid rgba(0,212,255,0.3); cursor: pointer;">
              <span>📋 העתק קישור סטרימינג ישיר</span>
            </button>
          </div>
        </div>
      `;

      if (window.showModal) {
        window.showModal(modalHtml);
        setTimeout(() => {
          const btnCopy = document.getElementById('btn-copy-stream-url');
          if (btnCopy) {
            btnCopy.addEventListener('click', () => {
              if (navigator.clipboard) {
                navigator.clipboard.writeText(streamUrl).then(() => {
                  if (window.showToast) window.showToast('קישור הסטרימינג הועתק ללוח!', 'success');
                });
              } else {
                prompt('העתק את הקישור:', streamUrl);
              }
            });
          }
        }, 100);
      } else {
        window.location.href = vlcUrl;
      }
    });

    // Back button
    on($('player-back'), 'click', (e) => {
      e.stopPropagation();
      exitPlayer();
    });

    // Keyboard
    on(document, 'keydown', handleKeydown);

    // Save progress periodically
    progressInterval = setInterval(saveProgress, 5000);
  }

  /* ── Render ──────────────────────────────────────────────────────── */
  let usingNative = false;

  async function render(container, p) {
    destroyed = false;
    params = { ...(p || {}) };
    if (params.type === 'tv') params.type = 'series';

    // Desktop app: hand the stream to mpv/VLC (plays every format) instead of <video>.
    if (window.NativePlayback && await window.NativePlayback.shouldUse(params)) {
      usingNative = true;
      window.NativePlayback.start(container, params);
      return;
    }
    usingNative = false;
    subtitlesList = [];
    activeSubIndex = -1;
    activeSelectorType = null;
    currentSpeedIdx = 2;
    isAudioBoosted = false;
    currentAudioLang = 'eng';

    // Show player overlay, hide sidebar & main content
    const overlay = $('player-overlay');
    const sidebar = $('sidebar');
    if (overlay) {
      overlay.classList.remove('hidden');
      overlay.setAttribute('dir', 'rtl');
    }
    if (sidebar) sidebar.style.display = 'none';
    container.style.display = 'none';

    // Set video ref
    video = $('video-player');
    if (!video) {
      console.error('[Player] #video-player not found');
      return;
    }

    // Unmute video explicitly
    video.muted = false;

    // Set title
    const titleEl = $('player-title');
    if (titleEl) {
      let displayTitle = params.title || '';
      if (params.type === 'series' && params.season && params.episode) {
        displayTitle += ` ${window.Helpers.seLabel(params.season, params.episode)}`;
      }
      titleEl.textContent = displayTitle;
    }

    // Check saved progress
    const progKey = window.WatchProgress
      ? window.WatchProgress.keyFor(params.type, params.imdbId, params.season, params.episode)
      : null;
    const savedProg = progKey ? window.WatchProgress.get(progKey) : null;

    if ((!params.startPosition || params.startPosition <= 0) && savedProg && savedProg.position > 10 && (savedProg.percentage || 0) < 95) {
      params.startPosition = savedProg.position;
      showResumeToast(savedProg.position);
    }

    // Restore volume
    const savedVol = window.Storage ? window.Storage.getVolume() : 1.0;
    video.volume = typeof savedVol === 'number' ? savedVol : 1.0;
    updateVolumeUI();

    // Reset speed display
    const speedBtn = $('player-speed');
    if (speedBtn) speedBtn.textContent = '1x';
    video.playbackRate = 1;

    // Bind events
    bindEvents();

    // Start playback
    if (params.url) {
      await initPlayback(params.url);
    }

    // Fetch subtitles asynchronously
    fetchSubtitles();

    // Fetch alternate streams in background for Audio track switching
    fetchStreamsIfNeeded();

    // Show controls initially
    showControls();
  }

  /* ── Destroy ─────────────────────────────────────────────────────── */
  function destroy() {
    destroyed = true;
    if (usingNative) {
      usingNative = false;
      window.NativePlayback.cleanup();
      params = {};
      return;
    }

    saveProgress();

    clearTimeout(controlsTimer);
    clearInterval(progressInterval);

    for (const { el, evt, handler, options } of boundHandlers) {
      el.removeEventListener(evt, handler, options);
    }
    boundHandlers.length = 0;

    if (hlsInstance) {
      hlsInstance.destroy();
      hlsInstance = null;
    }

    if (video) {
      video.pause();
      const tracks = video.querySelectorAll('track');
      tracks.forEach(t => {
        if (t._blobUrl) URL.revokeObjectURL(t._blobUrl);
        t.remove();
      });
      video.removeAttribute('src');
      video.load();
    }

    const overlay = $('player-overlay');
    const sidebar = $('sidebar');
    if (overlay) overlay.classList.add('hidden');
    if (sidebar) sidebar.style.display = '';

    hideTrackSelector();

    const enter = $('icon-fullscreen-enter');
    const exit = $('icon-fullscreen-exit');
    if (enter) enter.classList.remove('hidden');
    if (exit) exit.classList.add('hidden');

    const styleEl = document.getElementById('subtitle-style');
    if (styleEl) styleEl.remove();

    video = null;
    params = {};
    isDragging = false;
  }

  /* ── Register ────────────────────────────────────────────────────── */
  window.PlayerScreen = { render, destroy };
  window.Router.register('player', window.PlayerScreen);
})();
