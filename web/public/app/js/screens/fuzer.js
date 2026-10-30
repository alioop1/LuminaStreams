/**
 * LuminaStreams — Fuzer Screen
 * Hebrew torrent tracker interface with category browsing,
 * authentication, and AllDebrid integration.
 */
(() => {
  'use strict';

  const { tr, escapeHtml, formatFileSize, debounce } = window.Helpers;

  /* ── State ────────────────────────────────────────────────────────── */
  let activeCategory   = 'movies';
  let torrents         = [];
  let currentPage      = 1;
  let totalPages       = 1;
  let isLoading        = false;
  let isLoggedIn       = false;
  let searchQuery      = '';
  let destroyed        = false;
  let containerEl      = null;

  /* ── Storage keys ────────────────────────────────────────────────── */
  const FUZER_CREDS_KEY  = 'fuzer_credentials';
  const FUZER_COOKIE_KEY = 'fuzer_cookie';

  /* ── Categories ──────────────────────────────────────────────────── */
  const CATEGORIES = [
    { id: 'movies',         label: tr('Movies', 'סרטים'),                   catId: 7,   icon: '🎬' },
    { id: 'movies-hd',      label: tr('Movies HD', 'סרטים HD'),             catId: 9,   icon: '🎥' },
    { id: 'movies-4k',      label: tr('Movies 4K', 'סרטים 4K'),             catId: 58,  icon: '📽️' },
    { id: 'movies-dubbed',  label: tr('Dubbed Movies', 'סרטים מדובבים'),     catId: 59,  icon: '🎙️' },
    { id: 'series',         label: tr('Series', 'סדרות'),                   catId: 10,  icon: '📺' },
    { id: 'series-hd',      label: tr('Series HD', 'סדרות HD'),             catId: 11,  icon: '📡' },
    { id: 'series-4k',      label: tr('Series 4K', 'סדרות 4K'),             catId: 60,  icon: '🖥️' },
    { id: 'series-dubbed',  label: tr('Dubbed Series', 'סדרות מדובבות'),     catId: 61,  icon: '🎭' },
  ];

  /* ── Check login status ──────────────────────────────────────────── */
  function checkLoginStatus() {
    const creds = window.Storage.get(FUZER_CREDS_KEY, null);
    const cookie = window.Storage.get(FUZER_COOKIE_KEY, null);
    isLoggedIn = !!(creds && cookie);
    return isLoggedIn;
  }

  /* ── Login ───────────────────────────────────────────────────────── */
  async function performLogin(username, password) {
    // TODO: Implement actual Fuzer login HTTP call
    // POST to Fuzer login endpoint with username/password
    // Store the session cookie on success
    // Example:
    // const res = await fetch('https://www.fuzer.me/login.php', {
    //   method: 'POST',
    //   headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    //   body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`,
    // });
    // if (res.ok) {
    //   const cookie = res.headers.get('set-cookie');
    //   window.Storage.set(FUZER_COOKIE_KEY, cookie);
    // }

    // For now, store credentials and simulate login
    window.Storage.set(FUZER_CREDS_KEY, { username, password });
    window.Storage.set(FUZER_COOKIE_KEY, 'simulated_session');
    isLoggedIn = true;

    return true;
  }

  /* ── Logout ──────────────────────────────────────────────────────── */
  function performLogout() {
    window.Storage.remove(FUZER_CREDS_KEY);
    window.Storage.remove(FUZER_COOKIE_KEY);
    isLoggedIn = false;
  }

  /* ── Fetch torrents ──────────────────────────────────────────────── */
  async function fetchTorrents(categoryId, page = 1, query = '') {
    if (window.Fuzer) {
      try {
        let items = [];
        if (query && query.trim()) {
          items = await window.Fuzer.search(query.trim());
        } else {
          const cat = CATEGORIES.find(c => c.id === categoryId);
          const catId = cat ? cat.catId : 7;
          items = await window.Fuzer.getCategory(catId, page - 1);
        }
        return {
          torrents: items || [],
          totalPages: 10,
          currentPage: page,
        };
      } catch (e) {
        console.error('[FuzerScreen] fetch error:', e);
      }
    }

    return {
      torrents: [],
      totalPages: 1,
      currentPage: page,
    };
  }

  /* ── Extract magnet/hash from torrent ────────────────────────────── */
  async function getTorrentMagnet(torrentId) {
    // TODO: Implement actual Fuzer torrent detail page fetch
    // to extract the magnet link or info hash
    // Example:
    // const cookie = window.Storage.get(FUZER_COOKIE_KEY, '');
    // const res = await fetch(`https://www.fuzer.me/details.php?id=${torrentId}`, {
    //   headers: { 'Cookie': cookie }
    // });
    // const html = await res.text();
    // const magnetMatch = html.match(/magnet:\?xt=urn:btih:([a-fA-F0-9]+)/);
    // return magnetMatch ? magnetMatch[0] : null;

    return null;
  }

  /* ── Play torrent via AllDebrid ─────────────────────────────────── */
  async function playTorrent(torrent) {
    const debridClient = window.AllDebrid;
    if (!debridClient || !debridClient.isAuthenticated()) {
      showToast(tr('Please connect AllDebrid first', 'נא לחבר קודם AllDebrid'), 'error');
      return;
    }

    try {
      showToast(tr('Resolving stream...', 'מפענח מקור...'), 'info');

      // TODO: Get actual magnet link from Fuzer
      const magnet = await getTorrentMagnet(torrent.id);
      if (!magnet) {
        showToast(tr('Could not extract magnet link', 'לא ניתן לחלץ קישור מגנט'), 'error');
        return;
      }

      // Resolve via AllDebrid
      const streamUrl = await debridClient.resolveStream(magnet, (status) => {
        showToast(status, 'info');
      });

      if (streamUrl) {
        // Try to match with TMDB
        // TODO: Search TMDB for the torrent title to get metadata
        // const tmdbResult = await window.TMDB.searchMulti(torrent.title);

        window.Router.navigate('player', {
          url: streamUrl,
          title: torrent.title || torrent.name,
          type: activeCategory.startsWith('series') ? 'series' : 'movie',
        });
      }
    } catch (err) {
      console.error('[Fuzer] Play error:', err);
      showToast(tr('Failed to resolve stream', 'שגיאה בפענוח המקור'), 'error');
    }
  }

  /* ── Toast helper ────────────────────────────────────────────────── */
  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.style.cssText = `
      padding:12px 20px;border-radius:var(--radius-md);
      background:${type === 'error' ? 'rgba(255,59,92,0.9)' : type === 'success' ? 'rgba(0,230,118,0.9)' : 'rgba(0,212,255,0.9)'};
      color:${type === 'error' || type === 'info' ? '#fff' : 'var(--bg)'};
      font-size:13px;font-weight:500;
      animation:slideUp 0.3s ease;
      box-shadow:var(--shadow-card);
      max-width:400px;
    `;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.animation = 'fadeIn 0.2s ease reverse';
      setTimeout(() => toast.remove(), 200);
    }, 3000);
  }

  /* ── Quality badge color ─────────────────────────────────────────── */
  function getQualityColor(quality) {
    if (!quality) return 'var(--text-muted)';
    const q = quality.toLowerCase();
    if (q.includes('4k') || q.includes('2160')) return '#FFD700';
    if (q.includes('1080')) return 'var(--cyan)';
    if (q.includes('720')) return 'var(--purple)';
    return 'var(--text-dim)';
  }

  function detectQuality(title) {
    if (!title) return '';
    const t = title.toUpperCase();
    if (t.includes('2160P') || t.includes('4K') || t.includes('UHD')) return '4K';
    if (t.includes('1080P') || t.includes('FULL HD') || t.includes('FHD')) return '1080p';
    if (t.includes('720P') || t.includes('HD')) return '720p';
    if (t.includes('480P') || t.includes('SD')) return '480p';
    return '';
  }

  /* ── Load category ───────────────────────────────────────────────── */
  async function loadCategory(categoryId) {
    if (isLoading || destroyed) return;
    isLoading = true;

    const cat = CATEGORIES.find(c => c.id === categoryId);
    if (!cat) return;

    activeCategory = categoryId;
    currentPage = 1;
    torrents = [];

    renderCategoryTabs();
    renderLoadingGrid();

    try {
      const data = await fetchTorrents(cat.catId, currentPage, searchQuery);
      torrents = data.torrents || [];
      totalPages = data.totalPages || 1;
    } catch (err) {
      console.error('[Fuzer] Load error:', err);
    } finally {
      isLoading = false;
      renderTorrentGrid();
    }
  }

  /* ── Search handler ──────────────────────────────────────────────── */
  const debouncedSearch = debounce((query) => {
    if (destroyed) return;
    searchQuery = query;
    loadCategory(activeCategory);
  }, 400);

  /* ── Build login form ────────────────────────────────────────────── */
  function buildLoginHTML() {
    return `
      <div style="
        display:flex;align-items:center;justify-content:center;
        min-height:calc(100vh - 80px);padding:40px 20px;
      ">
        <div class="auth-card" style="max-width:440px;animation:fadeIn 0.4s ease">
          <div class="auth-logo">
            <div style="
              width:64px;height:64px;border-radius:50%;margin:0 auto 16px;
              background:linear-gradient(135deg,var(--cyan),var(--purple));
              display:flex;align-items:center;justify-content:center;
              font-size:28px;
            ">⚡</div>
            <h1 style="font-size:24px">Fuzer</h1>
            <p style="margin-top:6px">${tr('Connect your Fuzer account to browse torrents', 'חבר את חשבון Fuzer שלך כדי לגלוש בטורנטים')}</p>
          </div>

          <div class="auth-form" id="fuzer-login-form">
            <div class="form-group">
              <label class="form-label">${tr('Username', 'שם משתמש')}</label>
              <input type="text" class="form-input" id="fuzer-username"
                placeholder="${tr('Your Fuzer username', 'שם המשתמש שלך ב-Fuzer')}" />
            </div>
            <div class="form-group">
              <label class="form-label">${tr('Password', 'סיסמה')}</label>
              <input type="password" class="form-input" id="fuzer-password"
                placeholder="${tr('Your Fuzer password', 'הסיסמה שלך ב-Fuzer')}" />
            </div>
            <div id="fuzer-login-error" class="form-error" style="display:none"></div>
            <button class="btn btn-primary btn-lg" id="fuzer-login-btn" style="width:100%;margin-top:8px">
              ${tr('Login', 'התחבר')}
            </button>
          </div>

          <div style="text-align:center;margin-top:16px">
            <div style="font-size:12px;color:var(--text-muted)">
              ${tr('Your credentials are stored locally and never shared.',
                   'הפרטים שלך נשמרים מקומית ולעולם לא משותפים.')}
            </div>
          </div>

          <!-- RD Status -->
          <div style="
            margin-top:20px;padding:14px 18px;border-radius:var(--radius-md);
            background:rgba(238,242,255,0.03);border:1px solid var(--glass-border);
          ">
            <div style="display:flex;align-items:center;gap:10px">
              <div style="
                width:8px;height:8px;border-radius:50%;flex-shrink:0;
                background:${window.AllDebrid && window.AllDebrid.isAuthenticated() ? 'var(--green)' : 'var(--red)'};
                box-shadow:${window.AllDebrid && window.AllDebrid.isAuthenticated() ? 'var(--glow-green)' : 'var(--glow-red)'};
              "></div>
              <div>
                <div style="font-size:13px;font-weight:500">AllDebrid</div>
                <div style="font-size:11px;color:var(--text-muted)">
                  ${window.AllDebrid && window.AllDebrid.isAuthenticated()
                    ? tr('Connected', 'מחובר')
                    : tr('Not connected — required for streaming', 'לא מחובר — נדרש להזרמה')
                  }
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  /* ── Build main content ──────────────────────────────────────────── */
  function buildContentHTML() {
    const creds = window.Storage.get(FUZER_CREDS_KEY, {});

    return `
      <div style="padding:32px 48px;animation:fadeIn 0.3s ease">
        <!-- Header -->
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:28px;flex-wrap:wrap;gap:16px">
          <div>
            <h1 style="font-size:28px;font-weight:800;margin-bottom:4px">
              <span style="
                background:linear-gradient(135deg,var(--cyan),var(--purple));
                -webkit-background-clip:text;-webkit-text-fill-color:transparent;
                background-clip:text;
              ">Fuzer</span>
            </h1>
            <div style="font-size:13px;color:var(--text-dim)">
              ${tr('Logged in as', 'מחובר כ-')} <span style="color:var(--cyan);font-weight:600">${escapeHtml(creds.username || '')}</span>
            </div>
          </div>
          <div style="display:flex;align-items:center;gap:12px">
            <!-- Search -->
            <div style="position:relative">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                style="position:absolute;right:12px;top:50%;transform:translateY(-50%);opacity:0.3;pointer-events:none">
                <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
              </svg>
              <input type="text" class="form-input" id="fuzer-search-input"
                placeholder="${tr('Search torrents...', 'חפש טורנטים...')}"
                style="padding:10px 40px 10px 16px;font-size:13px;width:280px;border-radius:var(--radius-md);
                       background:var(--navy);border:1px solid var(--glass-border)" />
            </div>
            <button class="btn btn-ghost btn-sm" id="fuzer-logout-btn" style="color:var(--red)">
              ${tr('Logout', 'התנתק')}
            </button>
          </div>
        </div>

        <!-- Category tabs -->
        <div id="fuzer-category-tabs" style="
          display:flex;gap:4px;margin-bottom:24px;overflow-x:auto;
          padding-bottom:4px;
        "></div>

        <!-- Torrent grid/table -->
        <div id="fuzer-content-area"></div>

        <!-- Load more -->
        <div id="fuzer-load-more" style="text-align:center;padding:24px 0"></div>
      </div>
    `;
  }

  /* ── Render category tabs ────────────────────────────────────────── */
  function renderCategoryTabs() {
    const tabsEl = document.getElementById('fuzer-category-tabs');
    if (!tabsEl) return;

    tabsEl.innerHTML = CATEGORIES.map(cat => `
      <button class="season-tab ${activeCategory === cat.id ? 'active' : ''}"
        data-cat="${cat.id}" style="display:flex;align-items:center;gap:6px">
        <span>${cat.icon}</span>
        <span>${cat.label}</span>
      </button>
    `).join('');

    // Bind tab clicks
    tabsEl.querySelectorAll('.season-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        const catId = tab.dataset.cat;
        if (catId && catId !== activeCategory) {
          loadCategory(catId);
        }
      });
    });
  }

  /* ── Render loading grid ─────────────────────────────────────────── */
  function renderLoadingGrid() {
    const area = document.getElementById('fuzer-content-area');
    if (!area) return;

    area.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:8px">
        ${Array(8).fill(0).map((_, i) => `
          <div class="skeleton" style="
            height:56px;border-radius:var(--radius-md);
            animation:fadeIn 0.15s ease ${i * 0.04}s both;
          "></div>
        `).join('')}
      </div>
    `;
  }

  /* ── Render torrent grid ─────────────────────────────────────────── */
  function renderTorrentGrid() {
    if (destroyed) return;
    const area = document.getElementById('fuzer-content-area');
    const loadMoreEl = document.getElementById('fuzer-load-more');
    if (!area) return;

    if (torrents.length === 0) {
      area.innerHTML = `
        <div style="text-align:center;padding:60px 20px">
          <div style="font-size:48px;margin-bottom:16px;opacity:0.2">⚡</div>
          <div style="font-size:16px;color:var(--text-dim);margin-bottom:8px">
            ${tr('No torrents found', 'לא נמצאו טורנטים')}
          </div>
          <div style="font-size:13px;color:var(--text-muted)">
            ${tr('Torrent data will appear here when the Fuzer API is connected.',
                 'נתוני טורנטים יופיעו כאן כאשר ה-API של Fuzer יחובר.')}
          </div>
        </div>
      `;
      if (loadMoreEl) loadMoreEl.innerHTML = '';
      return;
    }

    // Table header
    let html = `
      <div style="
        display:grid;grid-template-columns:1fr 100px 90px 100px 140px;
        padding:8px 16px;font-size:11px;font-weight:600;color:var(--text-muted);
        text-transform:uppercase;letter-spacing:1px;
        border-bottom:1px solid var(--glass-border);margin-bottom:4px;
      ">
        <span>${tr('Title', 'שם')}</span>
        <span style="text-align:center">${tr('Quality', 'איכות')}</span>
        <span style="text-align:center">${tr('Size', 'גודל')}</span>
        <span style="text-align:center">${tr('Date', 'תאריך')}</span>
        <span style="text-align:center">${tr('Peers (Seeds / Leech)', 'מעלים / מורידים')}</span>
      </div>
    `;

    // Torrent rows
    torrents.forEach((torrent, idx) => {
      const quality = torrent.quality || detectQuality(torrent.title || torrent.name);
      const qualityColor = getQualityColor(quality);
      const sizeStr = torrent.size ? (typeof torrent.size === 'string' && torrent.size.toUpperCase().includes('B') ? torrent.size : formatFileSize(torrent.size)) : '—';

      html += `
        <div class="stream-card" data-idx="${idx}" style="
          display:grid;grid-template-columns:1fr 100px 90px 100px 140px;
          align-items:center;padding:12px 16px;margin-bottom:4px;cursor:pointer;
          animation:fadeIn 0.15s ease ${idx * 0.03}s both;
        ">
          <div style="min-width:0">
            <div style="font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
              ${escapeHtml(torrent.title || torrent.name || 'Unknown')}
            </div>
            <div style="font-size:11px;color:var(--text-muted);margin-top:2px">
              ${escapeHtml(torrent.uploader || '')}
            </div>
          </div>
          <div style="text-align:center">
            ${quality ? `
              <span style="
                padding:2px 8px;font-size:10px;font-weight:700;border-radius:4px;
                background:${qualityColor}20;color:${qualityColor};
                border:1px solid ${qualityColor}30;
              ">${quality}</span>
            ` : '—'}
          </div>
          <div style="text-align:center;font-size:12px;color:var(--text-dim)">
            ${sizeStr}
          </div>
          <div style="text-align:center;font-size:12px;color:var(--text-muted)">
            ${torrent.date || '—'}
          </div>
          <div style="text-align:center">
            <span style="font-size:12px;font-weight:700;color:var(--green)">
              ▲ ${torrent.seeders != null ? torrent.seeders : 0}
            </span>
            <span style="color:var(--text-muted);margin:0 4px">|</span>
            <span style="font-size:12px;font-weight:700;color:var(--red)">
              ▼ ${torrent.leechers != null ? torrent.leechers : 0}
            </span>
          </div>
        </div>
      `;
    });

    area.innerHTML = html;

    // Bind row clicks
    area.querySelectorAll('.stream-card[data-idx]').forEach(row => {
      row.addEventListener('click', () => {
        const idx = parseInt(row.dataset.idx);
        if (torrents[idx]) playTorrent(torrents[idx]);
      });
    });

    // Load more
    if (loadMoreEl) {
      if (currentPage < totalPages) {
        loadMoreEl.innerHTML = `
          <button class="btn btn-secondary" id="fuzer-load-more-btn">
            ${tr('Load More', 'טען עוד')}
          </button>
        `;
        document.getElementById('fuzer-load-more-btn')?.addEventListener('click', async () => {
          currentPage++;
          const cat = CATEGORIES.find(c => c.id === activeCategory);
          if (cat) {
            try {
              isLoading = true;
              const data = await fetchTorrents(cat.catId, currentPage, searchQuery);
              torrents = [...torrents, ...(data.torrents || [])];
              totalPages = data.totalPages || 1;
            } catch (err) {
              console.error('[Fuzer] Load more error:', err);
            } finally {
              isLoading = false;
              renderTorrentGrid();
            }
          }
        });
      } else {
        loadMoreEl.innerHTML = '';
      }
    }
  }

  /* ── Bind events (content mode) ──────────────────────────────────── */
  function bindContentEvents() {
    // Search
    const searchInput = document.getElementById('fuzer-search-input');
    searchInput?.addEventListener('input', (e) => {
      debouncedSearch(e.target.value);
    });

    // Logout
    const logoutBtn = document.getElementById('fuzer-logout-btn');
    logoutBtn?.addEventListener('click', () => {
      performLogout();
      renderPage();
    });
  }

  /* ── Bind events (login mode) ────────────────────────────────────── */
  function bindLoginEvents() {
    const loginBtn = document.getElementById('fuzer-login-btn');
    const usernameInput = document.getElementById('fuzer-username');
    const passwordInput = document.getElementById('fuzer-password');
    const errorEl = document.getElementById('fuzer-login-error');

    const doLogin = async () => {
      const username = usernameInput?.value?.trim();
      const password = passwordInput?.value?.trim();

      if (!username || !password) {
        if (errorEl) {
          errorEl.style.display = 'flex';
          errorEl.textContent = tr('Please fill in all fields', 'נא למלא את כל השדות');
        }
        return;
      }

      if (loginBtn) {
        loginBtn.disabled = true;
        loginBtn.innerHTML = `<div class="spinner sm"></div> ${tr('Logging in...', 'מתחבר...')}`;
      }

      try {
        await performLogin(username, password);
        renderPage();
      } catch (err) {
        if (errorEl) {
          errorEl.style.display = 'flex';
          errorEl.textContent = tr('Login failed. Check your credentials.', 'ההתחברות נכשלה. בדוק את הפרטים.');
        }
        if (loginBtn) {
          loginBtn.disabled = false;
          loginBtn.textContent = tr('Login', 'התחבר');
        }
      }
    };

    loginBtn?.addEventListener('click', doLogin);
    passwordInput?.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') doLogin();
    });
  }

  /* ── Render page (login or content) ──────────────────────────────── */
  function renderPage() {
    if (destroyed || !containerEl) return;

    checkLoginStatus();

    if (isLoggedIn) {
      containerEl.innerHTML = buildContentHTML();
      renderCategoryTabs();
      bindContentEvents();
      loadCategory(activeCategory);
    } else {
      containerEl.innerHTML = buildLoginHTML();
      bindLoginEvents();
    }
  }

  /* ── Render ──────────────────────────────────────────────────────── */
  async function render(container, p) {
    destroyed = false;
    containerEl = container;
    activeCategory = 'movies';
    currentPage = 1;
    torrents = [];
    searchQuery = '';

    // A browser cannot reach Fuzer directly: it sends no CORS headers, needs its
    // own cookies, and sign-in is now behind reCAPTCHA. Until the server-side
    // proxy exists (docs/PARITY.md step 6), say so instead of a fake login.
    container.innerHTML = `
      <div class="page" style="max-width:560px;margin:12vh auto 0;padding:0 20px;text-align:center;">
        <div style="font-size:44px;margin-bottom:12px;">✨</div>
        <h1 style="font-size:22px;font-weight:900;margin-bottom:10px;">Fuzer</h1>
        <p style="color:rgba(255,255,255,0.75);line-height:1.7;font-size:15px;">
          ${tr('Fuzer works in the iPhone app. In the browser it needs a server proxy, which is not set up yet.',
               'Fuzer זמין באפליקציית האייפון. בדפדפן הוא דורש שרת מתווך שעדיין לא הוגדר.')}
        </p>
      </div>
    `;
  }

  /* ── Destroy ─────────────────────────────────────────────────────── */
  function destroy() {
    destroyed = true;
    debouncedSearch.cancel?.();
    containerEl = null;
    torrents = [];
    searchQuery = '';
  }

  /* ── Register ────────────────────────────────────────────────────── */
  window.FuzerScreen = { render, destroy };
  window.Router.register('fuzer', window.FuzerScreen);
})();
