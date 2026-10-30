/**
 * LuminaStreams — IPTV Screen
 * M3U playlist management, channel list with groups, inline player,
 * favorites, recently watched, and EPG info display.
 */
(() => {
  'use strict';

  const { tr, escapeHtml, debounce } = window.Helpers;

  /* ── State ────────────────────────────────────────────────────────── */
  let channels        = [];
  let groups          = {};
  let favorites       = [];
  let recentChannels  = [];
  let activeChannel   = null;
  let hlsInstance     = null;
  let channelFilter   = '';
  let collapsedGroups = new Set();
  let destroyed       = false;
  let containerEl     = null;

  /* ── Storage keys ────────────────────────────────────────────────── */
  const IPTV_PLAYLISTS_KEY = 'iptv_playlists';
  const IPTV_FAVORITES_KEY = 'iptv_favorites';
  const IPTV_RECENT_KEY    = 'iptv_recent';

  /* ── HLS.js loader ───────────────────────────────────────────────── */
  function loadHls() {
    return new Promise((resolve, reject) => {
      if (window.Hls) return resolve();
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.20/dist/hls.min.js';
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('Failed to load hls.js'));
      document.head.appendChild(s);
    });
  }

  /* ── M3U Parser ──────────────────────────────────────────────────── */
  function parseM3U(content) {
    const lines = content.split('\n').map(l => l.trim()).filter(Boolean);
    const parsed = [];
    let i = 0;

    // Skip #EXTM3U header
    if (lines[0]?.startsWith('#EXTM3U')) i = 1;

    while (i < lines.length) {
      const line = lines[i];

      if (line.startsWith('#EXTINF')) {
        // Parse EXTINF line
        const info = parseExtInf(line);
        // Next non-comment line should be the URL
        i++;
        while (i < lines.length && lines[i].startsWith('#')) i++;
        if (i < lines.length && !lines[i].startsWith('#')) {
          info.url = lines[i].trim();
          if (info.url && (info.url.startsWith('http') || info.url.startsWith('rtmp'))) {
            info.id = `ch_${parsed.length}`;
            parsed.push(info);
          }
        }
      }
      i++;
    }

    return parsed;
  }

  function parseExtInf(line) {
    const result = {
      name: '',
      logo: '',
      group: tr('Ungrouped', 'ללא קבוצה'),
      tvgId: '',
      tvgName: '',
      url: '',
    };

    // Extract attributes
    const logoMatch = line.match(/tvg-logo="([^"]*)"/i);
    if (logoMatch) result.logo = logoMatch[1];

    const groupMatch = line.match(/group-title="([^"]*)"/i);
    if (groupMatch && groupMatch[1]) result.group = groupMatch[1];

    const tvgIdMatch = line.match(/tvg-id="([^"]*)"/i);
    if (tvgIdMatch) result.tvgId = tvgIdMatch[1];

    const tvgNameMatch = line.match(/tvg-name="([^"]*)"/i);
    if (tvgNameMatch) result.tvgName = tvgNameMatch[1];

    // Channel name is after the last comma in the EXTINF line
    const commaIdx = line.lastIndexOf(',');
    if (commaIdx !== -1) {
      result.name = line.substring(commaIdx + 1).trim();
    }

    // Fallback name
    if (!result.name) result.name = result.tvgName || result.tvgId || 'Unknown Channel';

    return result;
  }

  /* ── Group channels ──────────────────────────────────────────────── */
  function groupChannels(channelList) {
    const grouped = {};

    // Favorites first
    const favIds = new Set(favorites);
    const favChannels = channelList.filter(ch => favIds.has(ch.id));
    if (favChannels.length > 0) {
      grouped[`⭐ ${tr('Favorites', 'מועדפים')}`] = favChannels;
    }

    // Recently watched
    if (recentChannels.length > 0) {
      const recentSet = new Set(recentChannels);
      const recent = channelList.filter(ch => recentSet.has(ch.id)).slice(0, 10);
      if (recent.length > 0) {
        grouped[`🕐 ${tr('Recently Watched', 'נצפו לאחרונה')}`] = recent;
      }
    }

    // Regular groups
    channelList.forEach(ch => {
      if (!grouped[ch.group]) grouped[ch.group] = [];
      grouped[ch.group].push(ch);
    });

    return grouped;
  }

  /* ── Filter channels ─────────────────────────────────────────────── */
  function getFilteredChannels() {
    if (!channelFilter) return channels;
    const q = channelFilter.toLowerCase();
    return channels.filter(ch =>
      ch.name.toLowerCase().includes(q) ||
      ch.group.toLowerCase().includes(q) ||
      ch.tvgName?.toLowerCase().includes(q)
    );
  }

  /* ── Favorites management ────────────────────────────────────────── */
  function toggleFavorite(channelId) {
    const idx = favorites.indexOf(channelId);
    if (idx === -1) {
      favorites.push(channelId);
    } else {
      favorites.splice(idx, 1);
    }
    window.Storage.set(IPTV_FAVORITES_KEY, favorites);
    renderChannelList();
  }

  function addToRecent(channelId) {
    recentChannels = recentChannels.filter(id => id !== channelId);
    recentChannels.unshift(channelId);
    if (recentChannels.length > 20) recentChannels = recentChannels.slice(0, 20);
    window.Storage.set(IPTV_RECENT_KEY, recentChannels);
  }

  /* ── Play channel ────────────────────────────────────────────────── */
  async function playChannel(channel) {
    if (!channel?.url || destroyed) return;

    activeChannel = channel;
    addToRecent(channel.id);
    renderChannelList(); // Update active state

    const videoEl = document.getElementById('iptv-video');
    const placeholder = document.getElementById('iptv-placeholder');
    const infoBar = document.getElementById('iptv-channel-info');

    if (placeholder) placeholder.style.display = 'none';
    if (videoEl) videoEl.style.display = 'block';

    // Update channel info bar
    if (infoBar) {
      infoBar.innerHTML = `
        <div style="display:flex;align-items:center;gap:12px;padding:12px 16px">
          ${channel.logo
            ? `<img src="${escapeHtml(channel.logo)}" style="width:32px;height:32px;border-radius:6px;object-fit:contain;background:rgba(238,242,255,0.04)" onerror="this.style.display='none'" />`
            : ''
          }
          <div style="flex:1;min-width:0">
            <div style="font-size:14px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
              ${escapeHtml(channel.name)}
            </div>
            <div style="font-size:11px;color:var(--text-muted)">${escapeHtml(channel.group)}</div>
          </div>
          <div class="channel-live-dot"></div>
          <span style="font-size:11px;color:var(--red);font-weight:600">LIVE</span>
        </div>
      `;
    }

    // Cleanup previous HLS
    if (hlsInstance) {
      hlsInstance.destroy();
      hlsInstance = null;
    }

    const url = channel.url;
    const isHls = url.includes('.m3u8') || url.includes('m3u8');

    try {
      if (isHls) {
        try { await loadHls(); } catch (e) { console.warn('[IPTV] hls.js unavailable:', e); }
      }
      if (isHls && window.Hls?.isSupported()) {
        hlsInstance = new window.Hls({
          maxBufferLength: 30,
          liveSyncDuration: 3,
          liveMaxLatencyDuration: 10,
        });
        hlsInstance.loadSource(url);
        hlsInstance.attachMedia(videoEl);
        hlsInstance.on(window.Hls.Events.MANIFEST_PARSED, () => {
          videoEl.play().catch(() => {});
        });
        hlsInstance.on(window.Hls.Events.ERROR, (_, data) => {
          if (data.fatal) {
            if (data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
              hlsInstance.startLoad();
            } else if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
              hlsInstance.recoverMediaError();
            }
          }
        });
      } else {
        // Direct source (MP4, native HLS, etc.)
        videoEl.src = url;
        videoEl.play().catch(() => {});
      }
    } catch (err) {
      console.error('[IPTV] Play error:', err);
    }
  }

  /* ── Load playlist ───────────────────────────────────────────────── */
  async function loadPlaylist(url) {
    if (!url) return;

    try {
      const loadingEl = document.getElementById('iptv-loading');
      if (loadingEl) loadingEl.style.display = 'flex';

      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const content = await res.text();

      channels = parseM3U(content);
      groups = groupChannels(channels);

      // Save URL
      window.Storage.set(IPTV_PLAYLISTS_KEY, [url]);

      if (loadingEl) loadingEl.style.display = 'none';

      renderChannelList();

      console.log(`[IPTV] Loaded ${channels.length} channels in ${Object.keys(groups).length} groups`);
    } catch (err) {
      console.error('[IPTV] Playlist load error:', err);
      const loadingEl = document.getElementById('iptv-loading');
      if (loadingEl) {
        loadingEl.innerHTML = `
          <div style="color:var(--red);font-size:14px;text-align:center">
            <div style="margin-bottom:8px">❌ ${tr('Failed to load playlist', 'טעינת הפלייליסט נכשלה')}</div>
            <div style="font-size:12px;color:var(--text-muted)">${escapeHtml(err.message)}</div>
          </div>
        `;
      }
    }
  }

  /* ── Render channel list ─────────────────────────────────────────── */
  function renderChannelList() {
    if (destroyed) return;
    const listEl = document.getElementById('iptv-channel-list');
    if (!listEl) return;

    const filtered = getFilteredChannels();
    const grouped = groupChannels(filtered);

    listEl.innerHTML = '';

    if (filtered.length === 0) {
      listEl.innerHTML = `
        <div style="padding:40px 20px;text-align:center">
          <div style="font-size:32px;margin-bottom:12px;opacity:0.2">📺</div>
          <div style="font-size:13px;color:var(--text-dim)">
            ${channels.length === 0
              ? tr('No channels loaded', 'לא נטענו ערוצים')
              : tr('No matching channels', 'לא נמצאו ערוצים')
            }
          </div>
        </div>
      `;
      return;
    }

    for (const [groupName, groupChannels] of Object.entries(grouped)) {
      const isCollapsed = collapsedGroups.has(groupName);

      // Group header
      const header = document.createElement('div');
      header.className = 'channel-category';
      header.style.cssText = `
        cursor:pointer;display:flex;align-items:center;justify-content:space-between;
        padding:10px 16px 6px;user-select:none;transition:color 0.15s ease;
      `;
      header.innerHTML = `
        <span>${escapeHtml(groupName)} <span style="font-size:10px;opacity:0.5">(${groupChannels.length})</span></span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          style="transform:rotate(${isCollapsed ? '-90deg' : '0'});transition:transform 0.2s ease;opacity:0.4">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      `;
      header.addEventListener('click', () => {
        if (collapsedGroups.has(groupName)) {
          collapsedGroups.delete(groupName);
        } else {
          collapsedGroups.add(groupName);
        }
        renderChannelList();
      });
      listEl.appendChild(header);

      // Channel items
      if (!isCollapsed) {
        groupChannels.forEach(ch => {
          const isActive = activeChannel?.id === ch.id;
          const isFav = favorites.includes(ch.id);

          const item = document.createElement('div');
          item.className = `channel-item ${isActive ? 'active' : ''}`;
          item.innerHTML = `
            ${ch.logo
              ? `<img class="channel-logo" src="${escapeHtml(ch.logo)}"
                  onerror="this.style.background='var(--navy)';this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 36 36%22><rect fill=%22%230D1525%22 width=%2236%22 height=%2236%22 rx=%226%22/><text x=%2250%25%22 y=%2250%25%22 fill=%22%23EEF2FF30%22 font-size=%2214%22 text-anchor=%22middle%22 dominant-baseline=%22middle%22>📺</text></svg>'" />`
              : `<div class="channel-logo" style="display:flex;align-items:center;justify-content:center;background:var(--navy)">
                  <span style="font-size:16px;opacity:0.3">📺</span>
                </div>`
            }
            <div style="flex:1;min-width:0">
              <div class="channel-name">${escapeHtml(ch.name)}</div>
            </div>
            ${isActive ? '<div class="channel-live-dot"></div>' : ''}
            <button class="iptv-fav-btn" data-id="${ch.id}" style="
              background:none;border:none;cursor:pointer;font-size:16px;
              opacity:${isFav ? '1' : '0.3'};transition:opacity 0.15s ease;
              padding:4px;flex-shrink:0;
            " title="${tr('Favorite', 'מועדף')}">
              ${isFav ? '⭐' : '☆'}
            </button>
          `;

          item.addEventListener('click', (e) => {
            if (e.target.closest('.iptv-fav-btn')) return;
            playChannel(ch);
          });

          const favBtn = item.querySelector('.iptv-fav-btn');
          favBtn?.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleFavorite(ch.id);
          });

          listEl.appendChild(item);
        });
      }
    }
  }

  /* ── Debounced channel filter ────────────────────────────────────── */
  const debouncedFilter = debounce((value) => {
    channelFilter = value;
    renderChannelList();
  }, 200);

  /* ── Build page HTML ─────────────────────────────────────────────── */
  function buildHTML() {
    const savedUrls = window.Storage.get(IPTV_PLAYLISTS_KEY, []);
    const lastUrl = savedUrls[0] || '';

    return `
      <div class="iptv-page" style="animation:fadeIn 0.3s ease">
        <!-- Channels sidebar -->
        <div class="iptv-channels">
          <!-- Playlist URL input -->
          <div style="padding:12px 12px 8px">
            <div style="display:flex;gap:8px;margin-bottom:10px">
              <input type="text" class="form-input" id="iptv-url-input"
                placeholder="${tr('M3U Playlist URL...', 'כתובת פלייליסט M3U...')}"
                value="${escapeHtml(lastUrl)}"
                style="padding:10px 14px;font-size:13px;flex:1;border-radius:var(--radius-md)" />
              <button class="btn btn-primary btn-sm" id="iptv-load-btn" style="flex-shrink:0;padding:10px 16px">
                ${tr('Load', 'טען')}
              </button>
            </div>

            <!-- Channel search -->
            <div style="position:relative">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                style="position:absolute;right:12px;top:50%;transform:translateY(-50%);opacity:0.3;pointer-events:none">
                <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
              </svg>
              <input type="text" class="form-input" id="iptv-channel-search"
                placeholder="${tr('Filter channels...', 'סנן ערוצים...')}"
                style="padding:8px 14px;font-size:12px;width:100%;border-radius:var(--radius-sm)" />
            </div>
          </div>

          <!-- Channel count -->
          <div id="iptv-channel-count" style="padding:4px 16px;font-size:11px;color:var(--text-muted)">
            ${channels.length > 0 ? `${channels.length} ${tr('channels', 'ערוצים')}` : ''}
          </div>

          <!-- Loading indicator -->
          <div id="iptv-loading" style="display:none;flex-direction:column;align-items:center;justify-content:center;padding:40px 20px;gap:12px">
            <div class="spinner"></div>
            <div style="font-size:13px;color:var(--text-dim)">${tr('Loading channels...', 'טוען ערוצים...')}</div>
          </div>

          <!-- Channel list -->
          <div id="iptv-channel-list"></div>
        </div>

        <!-- Player area -->
        <div style="display:flex;flex-direction:column;overflow:hidden">
          <div class="iptv-player" style="flex:1;position:relative">
            <!-- Placeholder -->
            <div id="iptv-placeholder" style="
              display:flex;flex-direction:column;align-items:center;justify-content:center;
              width:100%;height:100%;gap:16px;
            ">
              <div style="
                width:80px;height:80px;border-radius:50%;
                background:linear-gradient(135deg,var(--cyan),var(--purple));
                display:flex;align-items:center;justify-content:center;
                font-size:32px;opacity:0.8;
              ">📺</div>
              <div style="font-size:18px;font-weight:600;color:var(--text-dim)">
                ${tr('Select a channel to watch', 'בחר ערוץ לצפייה')}
              </div>
              <div style="font-size:13px;color:var(--text-muted);max-width:300px;text-align:center">
                ${tr('Load an M3U playlist and select a channel from the sidebar to start watching.',
                     'טען פלייליסט M3U ובחר ערוץ מהסרגל הצדדי כדי להתחיל לצפות.')}
              </div>
            </div>

            <!-- Video element -->
            <video id="iptv-video" style="
              display:none;width:100%;height:100%;object-fit:contain;background:#000;
            " autoplay></video>
          </div>

          <!-- Channel info bar -->
          <div id="iptv-channel-info" style="
            border-top:1px solid var(--glass-border);
            background:rgba(7,11,20,0.8);
            min-height:52px;
          "></div>

          <!-- EPG section (placeholder) -->
          <div class="iptv-epg" id="iptv-epg" style="display:none">
            <div class="epg-timeline" id="iptv-epg-timeline"></div>
          </div>
        </div>
      </div>
    `;
  }

  /* ── Bind events ─────────────────────────────────────────────────── */
  function bindEvents() {
    // Load playlist button
    const loadBtn = document.getElementById('iptv-load-btn');
    const urlInput = document.getElementById('iptv-url-input');

    loadBtn?.addEventListener('click', () => {
      const url = urlInput?.value?.trim();
      if (url) loadPlaylist(url);
    });

    urlInput?.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        const url = urlInput.value?.trim();
        if (url) loadPlaylist(url);
      }
    });

    // Channel search
    const searchInput = document.getElementById('iptv-channel-search');
    searchInput?.addEventListener('input', (e) => {
      debouncedFilter(e.target.value);
    });

    // Video error handling
    const videoEl = document.getElementById('iptv-video');
    videoEl?.addEventListener('error', () => {
      console.error('[IPTV] Video playback error');
    });
  }

  /* ── Render ──────────────────────────────────────────────────────── */
  async function render(container, p) {
    destroyed = false;
    containerEl = container;

    // Load saved data
    favorites = window.Storage.get(IPTV_FAVORITES_KEY, []);
    recentChannels = window.Storage.get(IPTV_RECENT_KEY, []);

    container.innerHTML = buildHTML();
    bindEvents();

    // Auto-load saved playlist
    const savedUrls = window.Storage.get(IPTV_PLAYLISTS_KEY, []);
    if (savedUrls.length > 0 && channels.length === 0) {
      loadPlaylist(savedUrls[0]);
    } else {
      renderChannelList();
    }
  }

  /* ── Destroy ─────────────────────────────────────────────────────── */
  function destroy() {
    destroyed = true;

    // Cleanup HLS
    if (hlsInstance) {
      hlsInstance.destroy();
      hlsInstance = null;
    }

    // Stop video
    const videoEl = document.getElementById('iptv-video');
    if (videoEl) {
      videoEl.pause();
      videoEl.removeAttribute('src');
      videoEl.load();
    }

    activeChannel = null;
    channelFilter = '';
    containerEl = null;
  }

  /* ── Register ────────────────────────────────────────────────────── */
  window.IptvScreen = { render, destroy };
  window.Router.register('iptv', window.IptvScreen);
})();
