/**
 * LuminaStreams — PS5 Cinematic Details Screen
 * Matches 1:1 the Android TV Compose Details UI (DetailsScreen.kt, DetailsComponents.kt, wsa_details.png).
 * Features:
 *  - Full-bleed cinematic backdrop with dark gradient vignettes
 *  - Floating back button circle (top right in RTL)
 *  - Right column: Transparent movie logo (or prestige title), metadata row (★ rating, year, runtime, 16+, genres),
 *    Primary "צפה עכשיו" white pill button, "טריילר" glass pill, "+ הוסף" glass pill, and Hebrew synopsis
 *  - If TV series: Season selector pills and episodes modal
 *  - Left column: Floating 2:3 poster with 20px rounded corners and glowing shadow
 *  - Bottom section: "שחקנים" (Cast) with circular actor portraits
 *  - Sources Modal: Torrentio & RealDebrid/AllDebrid scrapers with resolution badges, seeds, size,
 *    and 1-tap playback + Outplayer/VLC launch options on iOS Safari.
 */
(() => {
  'use strict';

  const {
    posterUrl, backdropUrl, profileUrl, originalUrl, MOVIE_GENRES, TV_GENRES
  } = window.CONST;
  const {
    escapeHtml, truncate, formatRuntime, formatYear, formatFileSize, seLabel
  } = window.Helpers;
  const { t, tr } = window.I18n;

  /* ── State ────────────────────────────────────────────────────────── */
  let container = null;
  let mediaData = null;
  let currentType = 'movie';
  let currentId = null;
  let activeSeason = 1;

  /* ── Format Hebrew Runtime ────────────────────────────────────────── */
  function formatHebrewRuntime(minutes) {
    if (!minutes || minutes <= 0) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0 && m > 0) return `${h}שע ${m}ד`;
    if (h > 0) return `${h}שע`;
    return `${m}ד`;
  }

  function _imdbIdOf(data) {
    return (data && (data.external_ids?.imdb_id || data.imdb_id)) || null;
  }

  /**
   * What the main play button should start (Android DetailsViewModel):
   * movies resume themselves; series resume the latest episode, or move on
   * to the next one once it is finished.
   */
  function _resumeTarget(data) {
    const imdbId = _imdbIdOf(data);
    const WP = window.WatchProgress;
    if (!imdbId || !WP) return { season: 1, episode: 1, inProgress: false };
    if (currentType !== 'tv') {
      const p = WP.get(WP.keyFor('movie', imdbId));
      return { inProgress: !!p && p.percentage >= 2 && p.percentage < 90 };
    }
    const last = WP.getLatestEpisode(imdbId);
    if (!last) return { season: 1, episode: 1, inProgress: false };
    if (last.percentage < 90) {
      return { season: last.season, episode: last.episode, inProgress: last.percentage >= 2 };
    }
    return { season: last.season, episode: last.episode + 1, inProgress: false };
  }

  /* ── Build Shell HTML ─────────────────────────────────────────────── */
  function buildDetailsShell(data) {
    const isTv = currentType === 'tv';
    const title = escapeHtml(data.title || data.name || '');
    const year = formatYear(data.release_date || data.first_air_date);
    const runtimeMinutes = data.runtime || (data.episode_run_time && data.episode_run_time[0]) || 0;
    const runtimeStr = formatHebrewRuntime(runtimeMinutes);
    const rating = data.vote_average ? data.vote_average.toFixed(1) : '';
    const bgPath = data.backdrop_path || data.poster_path;
    const bgHighRes = bgPath ? window.CONST.heroUrl(bgPath) : '';
    const posterPath = data.poster_path || data.backdrop_path;
    const posterHighRes = posterPath ? `https://image.tmdb.org/t/p/w780${posterPath}` : 'assets/img/poster-placeholder.svg';

    // Genres
    const genreNames = (data.genres || []).map(g => g.name).slice(0, 3);
    const genresStr = genreNames.join(' • ');

    // Watch progress (shared keys with Android: movie_{imdb} / ep_{imdb}_s{S}e{E})
    const resume = _resumeTarget(data);
    let playBtnText;
    if (isTv) {
      playBtnText = resume.inProgress
        ? tr('CONTINUE', 'המשך צפייה')
        : tr(`WATCH S${resume.season} E${resume.episode}`, `צפה ע${resume.season} פ${resume.episode}`);
    } else {
      playBtnText = resume.inProgress ? tr('CONTINUE', 'המשך צפייה') : tr('WATCH NOW', 'צפה עכשיו');
    }

    // Watchlist state
    const inWatchlist = window.Storage.isInWatchlist(data.id, currentType);

    // Find transparent logo if available
    let logoHtml = `<h1 class="ps5-details-title">${title}</h1>`;
    if (data.images && data.images.logos && data.images.logos.length > 0) {
      const logos = data.images.logos;
      const logoObj = logos.find(l => l.iso_639_1 === 'he')
        || logos.find(l => l.iso_639_1 === 'en')
        || logos[0];
      if (logoObj && logoObj.file_path) {
        logoHtml = `<img class="ps5-details-logo" src="https://image.tmdb.org/t/p/w500${logoObj.file_path}" alt="${title}" />`;
      }
    }

    // Season pills
    let seasonPillsHtml = '';
    if (isTv && data.number_of_seasons > 0) {
      seasonPillsHtml = `
        <div class="ps5-seasons-row">
          <span class="ps5-seasons-label">עונות:</span>
          <div class="ps5-seasons-pills">
            ${Array.from({ length: data.number_of_seasons }, (_, i) => i + 1).map(sn => `
              <button class="ps5-season-pill${sn === activeSeason ? ' active' : ''}" data-season="${sn}">
                עונה ${sn}
              </button>
            `).join('')}
          </div>
        </div>
      `;
    }

    // Cast members
    const cast = (data.credits && data.credits.cast) ? data.credits.cast.slice(0, 14) : [];
    const castHtml = cast.length > 0 ? `
      <div class="ps5-details-cast-section">
        <h2 class="ps5-cast-heading">${tr('Cast', 'שחקנים')}</h2>
        <div class="ps5-details-cast-row">
          ${cast.map(actor => {
            const photo = actor.profile_path
              ? `https://image.tmdb.org/t/p/w185${actor.profile_path}`
              : 'assets/img/avatar-placeholder.svg';
            return `
              <div class="ps5-details-actor" title="${escapeHtml(actor.name)}">
                <img class="ps5-details-actor-img" src="${photo}" alt="${escapeHtml(actor.name)}" loading="lazy" />
                <span class="ps5-details-actor-name">${escapeHtml(actor.name)}</span>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    ` : '';

    return `
      <div class="ps5-details-layout">
        <!-- 1. Full-bleed Background Backdrop Layer -->
        <div class="ps5-details-backdrop" style="background-image: url('${bgHighRes}');">
          <div class="ps5-vignette-horiz"></div>
          <div class="ps5-vignette-vert"></div>
          <div class="ps5-vignette-top"></div>
        </div>

        <!-- 2. Back Button (Top Right in RTL) -->
        <button id="ps5-back-btn" class="ps5-back-btn" aria-label="חזרה" title="חזרה">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"></line>
            <polyline points="12 5 19 12 12 19"></polyline>
          </svg>
        </button>

        <!-- 3. Two-Column Hero Hub (matches wsa_details.png) -->
        <div class="ps5-details-hub">
          <!-- Right Column (RTL): Info & Actions -->
          <div class="ps5-details-info-col">
            <!-- Logo / Title -->
            <div class="ps5-details-logo-box">
              ${logoHtml}
            </div>

            <!-- Metadata Row: ★ 7.4  •  2026  •  2שע 9ד  •  [16+]  •  קומדיה • דרמה -->
            <div class="ps5-details-meta-row">
              ${rating ? `<span class="ps5-details-rating"><span class="star-gold">★</span> ${rating}</span>` : ''}
              ${year && year !== '—' ? `<span class="meta-dot">•</span><span class="ps5-details-year">${year}</span>` : ''}
              ${runtimeStr ? `<span class="meta-dot">•</span><span class="ps5-details-runtime">${runtimeStr}</span>` : ''}
              <span class="meta-dot">•</span>
              <span class="ps5-details-age-badge">16+</span>
              ${genresStr ? `<span class="meta-dot">•</span><span class="ps5-details-genres">${escapeHtml(genresStr)}</span>` : ''}
            </div>

            <!-- Action Buttons: צפה עכשיו, טריילר, הוסף -->
            <div class="ps5-details-actions">
              <button class="ps5-btn-play" id="detail-play-btn">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="6,3 20,12 6,21"/>
                </svg>
                <span>${playBtnText}</span>
              </button>

              <button class="ps5-btn-glass" id="detail-trailer-btn">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="6,3 20,12 6,21"/>
                </svg>
                <span>${tr('TRAILER', 'טריילר')}</span>
              </button>

              <button class="ps5-btn-glass${inWatchlist ? ' active' : ''}" id="detail-watchlist-btn">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  ${inWatchlist 
                    ? '<path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/>' 
                    : '<path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>'}
                </svg>
                <span>${inWatchlist ? tr('MY LIST', 'הרשימה שלי') : tr('ADD', 'הוסף')}</span>
              </button>
            </div>

            <!-- Hebrew Synopsis Text -->
            <div class="ps5-details-synopsis-box">
              <p class="ps5-details-synopsis">
                ${escapeHtml(data.overview || tr('No synopsis available.', 'אין תקציר זמין בעברית.'))}
              </p>
            </div>

            <!-- Season Selector (If TV) -->
            ${seasonPillsHtml}
          </div>

          <!-- Left Column (RTL): 2:3 High-Res Poster -->
          <div class="ps5-details-poster-col">
            <div class="ps5-details-poster-wrap">
              <img class="ps5-details-poster-img" src="${posterHighRes}" alt="${title}" />
            </div>
          </div>
        </div>

        <!-- 4. Cast Section (Bottom) -->
        ${castHtml}
      </div>
    `;
  }

  /* ── Open Sources Dialog (Matches Android showSources) ───────────── */
  async function openSourcesModal(season = null, episode = null) {
    const isTv = currentType === 'tv';
    const imdbId = _imdbIdOf(mediaData);
    const title = mediaData.title || mediaData.name || '';
    const backdrop = mediaData.backdrop_path ? window.CONST.heroUrl(mediaData.backdrop_path) : '';
    const poster = mediaData.poster_path ? `https://image.tmdb.org/t/p/w500${mediaData.poster_path}` : '';

    const label = isTv && season != null && episode != null
      ? `${title} — עונה ${season} פרק ${episode}`
      : title;

    const modalHtml = `
      <div class="ps5-sources-modal">
        <div class="modal-sheet-handle"></div>
        <div class="ps5-sources-header">
          <div class="ps5-sources-title-group">
            <h2 class="ps5-sources-title">מקורות צפייה</h2>
            <span class="ps5-sources-subtitle">${escapeHtml(label)}</span>
          </div>
          <button class="ps5-modal-close" id="sources-close-btn" aria-label="סגור">✕</button>
        </div>

        <div class="ps5-sources-filters" id="ps5-sources-filters" role="toolbar" aria-label="${tr('Filter', 'סינון')}"></div>

        <div class="ps5-sources-list" id="ps5-sources-list">
          <div class="ps5-sources-loading">
            <div class="ps5-spinner"></div>
            <span>סורק מקורות צפייה איכותיים (Torrentio & Debrid)...</span>
          </div>
        </div>
      </div>
    `;

    window.showModal(modalHtml, { bare: true });

    const listEl = document.getElementById('ps5-sources-list');
    const closeBtn = document.getElementById('sources-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        const overlay = document.getElementById('modal-overlay');
        if (overlay) overlay.classList.add('hidden');
      });
    }

    if (!imdbId) {
      if (listEl) {
        listEl.innerHTML = `<div class="ps5-sources-empty">לא נמצא מזהה IMDb עבור תוכן זה.</div>`;
      }
      return;
    }

    try {
      let streams = [];
      if (isTv && season != null && episode != null) {
        streams = await window.Torrentio.getEpisodeStreams(imdbId, season, episode);
      } else {
        streams = await window.Torrentio.getStreams(isTv ? 'series' : 'movie', imdbId);
      }

      if (!streams || streams.length === 0) {
        if (listEl) {
          listEl.innerHTML = `<div class="ps5-sources-empty">לא נמצאו מקורות זמינים כרגע.</div>`;
        }
        return;
      }

      // Filters (Android/iOS parity): cached, quality, Dolby Vision, audio language
      const filters = { cached: false, quality: null, dv: false, lang: null };
      const filtersEl = document.getElementById('ps5-sources-filters');
      const FILTER_DEFS = [
        { key: 'cached',  label: '⚡ AD+',       test: s => s.isCached },
        { key: 'quality', value: '4K',    label: '4K',           test: s => s.quality === '4K' },
        { key: 'quality', value: '1080p', label: '1080p',        test: s => s.quality === '1080p' },
        { key: 'dv',      label: 'Dolby Vision', test: s => s.hdr === 'DV' },
        { key: 'lang',    value: 'heb',   label: '🇮🇱 עברית',    test: s => (s.languages || []).includes('heb') },
        { key: 'lang',    value: 'eng',   label: '🇬🇧 English',  test: s => (s.languages || []).includes('eng') },
        { key: 'lang',    value: 'rus',   label: '🇷🇺 Русский',  test: s => (s.languages || []).includes('rus') },
      ];
      const isActive = f => (f.value === undefined ? filters[f.key] === true : filters[f.key] === f.value);
      const visibleStreams = () => streams.filter(st =>
        FILTER_DEFS.every(f => !isActive(f) || f.test(st)));

      const renderFilters = () => {
        if (!filtersEl) return;
        const anyActive = FILTER_DEFS.some(isActive);
        filtersEl.innerHTML = `
          <span class="ps5-filter-label">${tr('Filter', 'סינון')}</span>
          ${FILTER_DEFS.map((f, i) => `
            <button class="ps5-filter-pill${isActive(f) ? ' active' : ''}" data-filter="${i}" aria-pressed="${isActive(f)}">${escapeHtml(f.label)}</button>
          `).join('')}
          ${anyActive ? `<button class="ps5-filter-pill reset" data-filter="reset">${tr('Reset', 'נקה')}</button>` : ''}
        `;
        filtersEl.querySelectorAll('.ps5-filter-pill').forEach(btn => {
          btn.addEventListener('click', () => {
            if (btn.dataset.filter === 'reset') {
              Object.assign(filters, { cached: false, quality: null, dv: false, lang: null });
            } else {
              const f = FILTER_DEFS[Number(btn.dataset.filter)];
              if (f.value === undefined) filters[f.key] = !filters[f.key];
              else filters[f.key] = isActive(f) ? null : f.value;
            }
            renderFilters();
            renderStreams();
          });
        });
      };

      // Render streams list
      const renderStreams = () => {
      const shown = visibleStreams();
      const subtitleEl = document.querySelector('.ps5-sources-subtitle');
      if (subtitleEl) subtitleEl.textContent = `${label} • ${shown.length} ${tr('sources', 'מקורות')}`;
      if (shown.length === 0) {
        listEl.innerHTML = `<div class="ps5-sources-empty">${tr('No sources match these filters.', 'אין מקורות שמתאימים לסינון.')}</div>`;
        return;
      }
      listEl.innerHTML = shown.map(s => {
        const idx = streams.indexOf(s);
        const q = (s.quality || '1080p').toUpperCase();
        const qClass = q.includes('4K') || q.includes('2160') ? 'badge-4k' : (q.includes('1080') ? 'badge-1080' : 'badge-720');
        const seeds = s.seeders || 0;
        const sizeStr = s.size || (s.sizeBytes ? formatFileSize(s.sizeBytes) : '');
        const titleText = s.title || s.name || `Stream #${idx + 1}`;

        return `
          <div class="ps5-source-card" data-stream-idx="${idx}">
            <div class="ps5-source-badge ${qClass}">${q}</div>
            <div class="ps5-source-details">
              <div class="ps5-source-name">${escapeHtml(titleText)}</div>
              <div class="ps5-source-meta">
                ${seeds > 0 ? `<span class="source-seeds">▲ ${seeds}</span>` : ''}
                ${sizeStr ? `<span class="source-size">${sizeStr}</span>` : ''}
                ${s.isCached ? `<span class="source-debrid">⚡ AD+</span>` : ''}
              </div>
            </div>
            <div class="ps5-source-actions">
              <button class="ps5-btn-source-play">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>
                <span>צפה</span>
              </button>
            </div>
          </div>
        `;
      }).join('');
      attachStreamHandlers();
      };

      // Add click handlers on streams
      const attachStreamHandlers = () => {
      listEl.querySelectorAll('.ps5-source-card').forEach(card => {
        card.addEventListener('click', async () => {
          const idx = parseInt(card.dataset.streamIdx, 10);
          const stream = streams[idx];
          if (!stream) return;

          card.classList.add('loading');
          card.querySelector('.ps5-btn-source-play span').textContent = 'טוען...';

          try {
            // Torrentio's /resolve/alldebrid/... URL redirects to the AllDebrid
            // file itself (Android plays it directly too). Only a bare
            // infoHash needs the upload → poll → unlock flow.
            let playUrl = stream.url;
            if (!playUrl && stream.infoHash && window.AllDebrid) {
              const status = card.querySelector('.ps5-btn-source-play span');
              playUrl = await window.AllDebrid.resolveStream(
                stream.infoHash,
                msg => { if (status) status.textContent = msg; },
                season,
                episode
              );
            }
            if (!playUrl) throw new Error('No playable URL for this source');

            // Close modal
            const overlay = document.getElementById('modal-overlay');
            if (overlay) overlay.classList.add('hidden');

            // Launch Player
            window.Router.navigate('player', {
              url: playUrl,
              title: label,
              showTitle: title,
              type: isTv ? 'series' : 'movie',
              id: currentId,
              imdbId,
              backdrop,
              poster,
              season,
              episode,
              audio: stream.audio || null,
              isWebAudio: !!stream.isWebAudio,
              allStreams: streams,
            });
          } catch (err) {
            console.error('[Details] Stream launch failed:', err);
            window.showToast(tr('Failed to launch stream', 'שגיאה בהפעלת המקור'), 'error');
            card.classList.remove('loading');
            card.querySelector('.ps5-btn-source-play span').textContent = 'צפה';
          }
        });
      });
      };

      renderFilters();
      renderStreams();

    } catch (err) {
      console.error('[Details] Scraping failed:', err);
      if (listEl) {
        listEl.innerHTML = `<div class="ps5-sources-empty">שגיאה בסריקת המקורות: ${escapeHtml(err.message)}</div>`;
      }
    }
  }

  /* ── Open Episodes Modal (for TV Series) ─────────────────────────── */
  async function openEpisodesModal(seasonNum) {
    activeSeason = seasonNum;
    const tvTitle = mediaData.name || mediaData.title || '';

    const modalHtml = `
      <div class="ps5-sources-modal ps5-episodes-modal">
        <div class="modal-sheet-handle"></div>
        <div class="ps5-sources-header">
          <div class="ps5-sources-title-group">
            <h2 class="ps5-sources-title">עונה ${seasonNum} — פרקים</h2>
            <span class="ps5-sources-subtitle">${escapeHtml(tvTitle)}</span>
          </div>
          <button class="ps5-modal-close" id="episodes-close-btn" aria-label="סגור">✕</button>
        </div>

        <div class="ps5-episodes-list" id="ps5-episodes-list">
          <div class="ps5-sources-loading">
            <div class="ps5-spinner"></div>
            <span>טוען רשימת פרקים...</span>
          </div>
        </div>
      </div>
    `;

    window.showModal(modalHtml, { bare: true });

    const listEl = document.getElementById('ps5-episodes-list');
    const closeBtn = document.getElementById('episodes-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        const overlay = document.getElementById('modal-overlay');
        if (overlay) overlay.classList.add('hidden');
      });
    }

    try {
      const seasonData = await window.TMDB.getTvSeason(currentId, seasonNum);
      const episodes = seasonData.episodes || [];

      if (episodes.length === 0) {
        if (listEl) listEl.innerHTML = `<div class="ps5-sources-empty">לא נמצאו פרקים עבור עונה זו.</div>`;
        return;
      }

      listEl.innerHTML = episodes.map(ep => {
        const epThumb = ep.still_path
          ? `https://image.tmdb.org/t/p/w300${ep.still_path}`
          : 'assets/img/backdrop-placeholder.svg';
        const epTitle = ep.name || `פרק ${ep.episode_number}`;
        const epOverview = ep.overview || '';
        const epRuntime = ep.runtime ? `${ep.runtime} דקות` : '';

        return `
          <div class="ps5-episode-card" data-ep-num="${ep.episode_number}">
            <div class="ps5-episode-thumb-wrap">
              <img class="ps5-episode-thumb" src="${epThumb}" alt="${escapeHtml(epTitle)}" loading="lazy" />
              <div class="ps5-episode-num-badge">${ep.episode_number}</div>
            </div>
            <div class="ps5-episode-info">
              <div class="ps5-episode-title">${escapeHtml(epTitle)}</div>
              <div class="ps5-episode-overview">${escapeHtml(truncate(epOverview, 130))}</div>
              ${epRuntime ? `<div class="ps5-episode-runtime">${epRuntime}</div>` : ''}
            </div>
            <button class="ps5-btn-source-play">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>
              <span>צפה</span>
            </button>
          </div>
        `;
      }).join('');

      listEl.querySelectorAll('.ps5-episode-card').forEach(card => {
        card.addEventListener('click', () => {
          const epNum = parseInt(card.dataset.epNum, 10);
          openSourcesModal(seasonNum, epNum);
        });
      });

    } catch (err) {
      console.error('[Details] Episodes load error:', err);
      if (listEl) {
        listEl.innerHTML = `<div class="ps5-sources-empty">שגיאה בטעינת הפרקים: ${escapeHtml(err.message)}</div>`;
      }
    }
  }

  /* ── Play Trailer (in native player or YouTube modal) ─────────────── */
  function playTrailer(videos) {
    const trailers = (videos?.results || []).filter(v => v.site === 'YouTube');
    const trailer = trailers.find(v => v.type === 'Trailer') || trailers[0];

    if (!trailer || !trailer.key) {
      window.showToast(tr('No trailer available', 'לא נמצא טריילר זמין'), 'info');
      return;
    }

    const modalHtml = `
      <div class="ps5-trailer-modal">
        <div class="ps5-trailer-header">
          <span>${escapeHtml(trailer.name || 'טריילר')}</span>
          <button class="ps5-modal-close" onclick="document.getElementById('modal-overlay').classList.add('hidden')">✕</button>
        </div>
        <div class="ps5-trailer-video-wrap">
          <iframe src="https://www.youtube.com/embed/${trailer.key}?autoplay=1" 
                  frameborder="0" 
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                  allowfullscreen>
          </iframe>
        </div>
      </div>
    `;
    window.showModal(modalHtml, { bare: true });
  }

  /* ── Setup Event Listeners ────────────────────────────────────────── */
  function _setupListeners(data) {
    // Back button
    const backBtn = document.getElementById('ps5-back-btn');
    if (backBtn) {
      backBtn.addEventListener('click', () => {
        if (window.Router.canGoBack()) {
          window.Router.back();
        } else {
          window.Router.navigate('home');
        }
      });
    }

    // Play button
    const playBtn = document.getElementById('detail-play-btn');
    if (playBtn) {
      playBtn.addEventListener('click', () => {
        if (currentType === 'tv') {
          const { season, episode } = _resumeTarget(data);
          openSourcesModal(season, episode);
        } else {
          openSourcesModal();
        }
      });
    }

    // Trailer button
    const trailerBtn = document.getElementById('detail-trailer-btn');
    if (trailerBtn) {
      trailerBtn.addEventListener('click', () => {
        playTrailer(data.videos);
      });
    }

    // Watchlist button
    const watchlistBtn = document.getElementById('detail-watchlist-btn');
    if (watchlistBtn) {
      watchlistBtn.addEventListener('click', () => {
        const inList = window.Storage.isInWatchlist(data.id, currentType);
        if (inList) {
          window.Storage.removeFromWatchlist(data.id, currentType);
          watchlistBtn.classList.remove('active');
          watchlistBtn.querySelector('span').textContent = tr('ADD', 'הוסף');
          window.showToast(tr('Removed from watchlist', 'הוסר מהרשימה שלי'), 'info');
        } else {
          window.Storage.addToWatchlist({
            id: data.id,
            type: currentType,
            title: data.title || data.name,
            poster_path: data.poster_path,
            backdrop_path: data.backdrop_path,
            overview: data.overview,
            vote_average: data.vote_average,
            release_date: data.release_date || data.first_air_date,
            genre: (data.genres || []).map(g => g.name).join(', '),
          });
          watchlistBtn.classList.add('active');
          watchlistBtn.querySelector('span').textContent = tr('MY LIST', 'הרשימה שלי');
          window.showToast(tr('Added to watchlist', 'נוסף לרשימה שלי'), 'success');
        }
      });
    }

    // Season pills
    document.querySelectorAll('.ps5-season-pill').forEach(pill => {
      pill.addEventListener('click', () => {
        const sn = parseInt(pill.dataset.season, 10);
        openEpisodesModal(sn);
      });
    });
  }

  /* ── Screen Lifecycle ─────────────────────────────────────────────── */
  const DetailsScreen = {
    async render(containerEl, params = {}) {
      container = containerEl;
      currentId = params.id;
      currentType = params.type || 'movie';
      activeSeason = 1;

      // Loading state
      container.innerHTML = `
        <div class="ps5-details-layout loading">
          <div class="ps5-spinner" style="width: 48px; height: 48px; margin: auto;"></div>
        </div>
      `;

      try {
        const data = currentType === 'tv'
          ? await window.TMDB.getTvDetails(currentId)
          : await window.TMDB.getMovieDetails(currentId);

        mediaData = data;
        container.innerHTML = buildDetailsShell(data);
        _setupListeners(data);

        // Scroll to top
        window.scrollTo(0, 0);
      } catch (err) {
        console.error('[DetailsScreen] Render error:', err);
        container.innerHTML = `
          <div class="ps5-details-layout error">
            <h2>שגיאה בטעינת פרטי הכותר</h2>
            <p>${escapeHtml(err.message)}</p>
            <button class="ps5-btn-play" onclick="window.Router.navigate('home')">חזרה לדף הבית</button>
          </div>
        `;
      }
    },

    destroy() {
      container = null;
      mediaData = null;
    }
  };

  window.DetailsScreen = DetailsScreen;
})();
