/**
 * LuminaStreams — Settings Screen
 * 5 sections: Account, Playback, Subtitles, Interface, System.
 * All settings persist to Storage immediately on change.
 */
(() => {
  'use strict';

  const { t, tr } = window.I18n;

  /* ── Settings Keys ─────────────────────────────────────────────── */
  const SETTINGS_KEYS = {
    AUDIO_LANG:      'settings_audio_lang',
    AUTOPLAY_NEXT:   'settings_autoplay_next',
    SUBTITLE_LANG:   'settings_subtitle_lang',
    YELLOW_SUBS:     'settings_yellow_subs',
    SUB_FONT_SIZE:   'settings_sub_font_size',
    AUTO_HEB_SUBS:   'settings_auto_heb_subs',
    REDUCE_MOTION:   'settings_reduce_motion',
  };

  /* ── State ─────────────────────────────────────────────────────── */
  let rdUserInfo = null;

  /* ── Render ────────────────────────────────────────────────────── */

  async function render(container) {
    // Load settings from storage
    const s = _loadSettings();

    // Try to fetch AllDebrid user info
    rdUserInfo = null;
    if (window.AllDebrid && window.AllDebrid.isAuthenticated()) {
      try { rdUserInfo = await window.AllDebrid.getUser(); } catch {}
    }

    const firebaseUser = window.FirebaseAuth ? window.FirebaseAuth.getCurrentUser() : null;

    container.innerHTML = `
      <div class="settings-page page">
        <h1 class="settings-page-title">${t('settings.title')}</h1>

        <!-- ═══════════ ACCOUNT ═══════════ -->
        <div class="settings-section animate-fadeIn">
          <h2 class="settings-section-title">
            ${tr('Account', 'חשבון')}
          </h2>

          <!-- AllDebrid -->
          <div class="account-card hover-glow">
            <div class="account-avatar-large" style="background: ${rdUserInfo ? 'var(--grad-cyan-purple)' : 'rgba(238,242,255,0.08)'};">
              ${rdUserInfo ? '⚡' : '🔗'}
            </div>
            <div class="account-info">
              <h3>AllDebrid</h3>
              <p>${rdUserInfo
                ? `${rdUserInfo.username || 'User'} · ${tr('Premium expires:', 'פרימיום עד:')} ${rdUserInfo.expiration ? new Date(rdUserInfo.expiration).toLocaleDateString() : '—'}`
                : tr('Disconnected', 'מנותק')
              }</p>
            </div>
            <span class="account-status ${rdUserInfo ? 'premium' : 'free'}">
              ${rdUserInfo ? tr('Connected', 'מחובר') : tr('Disconnected', 'מנותק')}
            </span>
          </div>
          <div style="margin-bottom: 16px;">
            ${rdUserInfo
              ? `<button class="btn btn-danger btn-sm" id="btn-rd-disconnect">${t('btn.disconnect')}</button>`
              : `<button class="btn btn-primary btn-sm" id="btn-rd-connect">${tr('Connect AllDebrid', 'חבר AllDebrid')}</button>`
            }
          </div>

          <!-- Firebase Auth -->
          <div class="account-card hover-glow">
            <div class="account-avatar-large" style="background: ${firebaseUser ? 'var(--grad-cyan-purple)' : 'rgba(238,242,255,0.08)'};">
              ${firebaseUser ? firebaseUser.email.charAt(0).toUpperCase() : '👤'}
            </div>
            <div class="account-info">
              <h3>${tr('Firebase Account', 'חשבון Firebase')}</h3>
              <p>${firebaseUser
                ? firebaseUser.email
                : tr('Not logged in', 'לא מחובר')
              }</p>
            </div>
            <span class="account-status ${firebaseUser ? 'premium' : 'free'}">
              ${firebaseUser ? tr('Connected', 'מחובר') : tr('Disconnected', 'מנותק')}
            </span>
          </div>
          <div style="margin-bottom: 8px;">
            ${firebaseUser
              ? `<button class="btn btn-danger btn-sm" id="btn-firebase-logout">${t('btn.logout')}</button>`
              : `<button class="btn btn-primary btn-sm" id="btn-firebase-login">${t('btn.login')}</button>`
            }
          </div>
        </div>

        <!-- ═══════════ PLAYBACK ═══════════ -->
        <div class="settings-section animate-fadeIn" style="animation-delay: 0.05s;">
          <h2 class="settings-section-title">
            ${tr('Playback', 'נגן')}
          </h2>

          <!-- Audio Language -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Preferred Audio Language', 'שפת שמע מועדפת')}</div>
              <div class="settings-desc">${tr('Default audio track selection', 'בחירת רצועת שמע ברירת מחדל')}</div>
            </div>
            <select class="settings-select" data-setting="audio_lang">
              <option value="he" ${s.audioLang === 'he' ? 'selected' : ''}>${tr('Hebrew', 'עברית')}</option>
              <option value="en" ${s.audioLang === 'en' ? 'selected' : ''}>${tr('English', 'אנגלית')}</option>
              <option value="original" ${s.audioLang === 'original' ? 'selected' : ''}>${tr('Original', 'מקורי')}</option>
            </select>
          </div>

          <!-- Autoplay Next Episode -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${t('settings.autoplay')}</div>
              <div class="settings-desc">${tr('Automatically play next episode when current one ends', 'הפעל אוטומטית את הפרק הבא בסיום')}</div>
            </div>
            <div class="toggle ${s.autoplayNext ? 'active' : ''}" data-setting="autoplay_next"></div>
          </div>

          <!-- Default Subtitle Language -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Default Subtitle Language', 'שפת כתוביות ברירת מחדל')}</div>
            </div>
            <select class="settings-select" data-setting="subtitle_lang">
              <option value="heb" ${s.subtitleLang === 'heb' ? 'selected' : ''}>${tr('Hebrew', 'עברית')}</option>
              <option value="eng" ${s.subtitleLang === 'eng' ? 'selected' : ''}>${tr('English', 'אנגלית')}</option>
              <option value="ara" ${s.subtitleLang === 'ara' ? 'selected' : ''}>${tr('Arabic', 'ערבית')}</option>
              <option value="rus" ${s.subtitleLang === 'rus' ? 'selected' : ''}>${tr('Russian', 'רוסית')}</option>
              <option value="none" ${s.subtitleLang === 'none' ? 'selected' : ''}>${tr('None', 'ללא')}</option>
            </select>
          </div>
        </div>

        <!-- ═══════════ SUBTITLES ═══════════ -->
        <div class="settings-section animate-fadeIn" style="animation-delay: 0.1s;">
          <h2 class="settings-section-title">
            ${t('settings.subtitles')}
          </h2>

          <!-- Yellow Subtitles -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Yellow Subtitles', 'כתוביות צהובות')}</div>
              <div class="settings-desc">${tr('Display subtitles in yellow for better visibility', 'הצג כתוביות בצהוב לנראות טובה יותר')}</div>
            </div>
            <div class="toggle ${s.yellowSubs ? 'active' : ''}" data-setting="yellow_subs"></div>
          </div>

          <!-- Subtitle Font Size -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Subtitle Font Size', 'גודל גופן כתוביות')}</div>
            </div>
            <select class="settings-select" data-setting="sub_font_size">
              <option value="small" ${s.subFontSize === 'small' ? 'selected' : ''}>${tr('Small', 'קטן')}</option>
              <option value="medium" ${s.subFontSize === 'medium' ? 'selected' : ''}>${tr('Medium', 'בינוני')}</option>
              <option value="large" ${s.subFontSize === 'large' ? 'selected' : ''}>${tr('Large', 'גדול')}</option>
              <option value="xlarge" ${s.subFontSize === 'xlarge' ? 'selected' : ''}>${tr('Extra Large', 'גדול מאוד')}</option>
            </select>
          </div>

          <!-- Auto-load Hebrew Subtitles -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Auto-load Hebrew Subtitles', 'טעינת כתוביות עברית אוטומטית')}</div>
              <div class="settings-desc">${tr('Automatically search and load Hebrew subtitles', 'חפש וטען כתוביות בעברית באופן אוטומטי')}</div>
            </div>
            <div class="toggle ${s.autoHebSubs ? 'active' : ''}" data-setting="auto_heb_subs"></div>
          </div>
        </div>

        <!-- ═══════════ INTERFACE ═══════════ -->
        <div class="settings-section animate-fadeIn" style="animation-delay: 0.15s;">
          <h2 class="settings-section-title">
            ${tr('Interface', 'ממשק')}
          </h2>

          <!-- Language -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${t('settings.language')}</div>
              <div class="settings-desc">${tr('Changes the app language and direction', 'משנה את שפת האפליקציה והכיוון')}</div>
            </div>
            <select class="settings-select" data-setting="language">
              <option value="he" ${window.I18n.getLanguage() === 'he' ? 'selected' : ''}>${t('settings.lang_he')}</option>
              <option value="en" ${window.I18n.getLanguage() === 'en' ? 'selected' : ''}>${t('settings.lang_en')}</option>
            </select>
          </div>

          <!-- Reduce Motion -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Reduce Motion', 'צמצם אנימציות')}</div>
              <div class="settings-desc">${tr('Minimizes animations throughout the app', 'מצמצם אנימציות ברחבי האפליקציה')}</div>
            </div>
            <div class="toggle ${s.reduceMotion ? 'active' : ''}" data-setting="reduce_motion"></div>
          </div>
        </div>

        <!-- ═══════════ SYSTEM ═══════════ -->
        <div class="settings-section animate-fadeIn" style="animation-delay: 0.2s;">
          <h2 class="settings-section-title">
            ${tr('System', 'מערכת')}
          </h2>

          <!-- App Version -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${t('settings.version')}</div>
            </div>
            <span style="font-size: 13px; color: var(--text-dim); font-variant-numeric: tabular-nums;">1.0.0</span>
          </div>

          <!-- Cache Size -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Cache Size', 'גודל מטמון')}</div>
              <div class="settings-desc" id="cache-size-display">${_estimateCacheSize()}</div>
            </div>
            <button class="btn btn-secondary btn-sm" id="btn-clear-cache">
              ${t('settings.clear_cache')}
            </button>
          </div>

          <!-- Clear Search History -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Search History', 'היסטוריית חיפוש')}</div>
              <div class="settings-desc">${tr(`${window.Storage.getSearchHistory().length} searches stored`, `${window.Storage.getSearchHistory().length} חיפושים שמורים`)}</div>
            </div>
            <button class="btn btn-secondary btn-sm" id="btn-clear-search">
              ${t('search.clear_history')}
            </button>
          </div>

          <!-- Clear Watch Progress -->
          <div class="settings-row">
            <div>
              <div class="settings-label">${tr('Watch Progress', 'התקדמות צפייה')}</div>
              <div class="settings-desc">${tr('Clear all watch progress data', 'מחק את כל נתוני התקדמות הצפייה')}</div>
            </div>
            <button class="btn btn-danger btn-sm" id="btn-clear-progress">
              ${t('btn.clear')}
            </button>
          </div>
        </div>
      </div>
    `;

    _attachEventListeners(container);
  }

  /* ── Settings Load/Save ────────────────────────────────────────── */

  function _loadSettings() {
    return {
      audioLang:    window.Storage.get(SETTINGS_KEYS.AUDIO_LANG, 'he'),
      autoplayNext: window.Storage.get(SETTINGS_KEYS.AUTOPLAY_NEXT, true),
      subtitleLang: window.Storage.get(SETTINGS_KEYS.SUBTITLE_LANG, 'heb'),
      yellowSubs:   window.Storage.get(SETTINGS_KEYS.YELLOW_SUBS, false),
      subFontSize:  window.Storage.get(SETTINGS_KEYS.SUB_FONT_SIZE, 'medium'),
      autoHebSubs:  window.Storage.get(SETTINGS_KEYS.AUTO_HEB_SUBS, true),
      reduceMotion: window.Storage.get(SETTINGS_KEYS.REDUCE_MOTION, false),
    };
  }

  function _saveSetting(key, value) {
    window.Storage.set(key, value);
  }

  /* ── Cache Helpers ─────────────────────────────────────────────── */

  function _estimateCacheSize() {
    let total = 0;
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        const val = localStorage.getItem(key);
        total += (key.length + (val ? val.length : 0)) * 2; // UTF-16
      }
    } catch {}
    return window.Helpers.formatFileSize(total);
  }

  /* ── Event Handlers ────────────────────────────────────────────── */

  function _attachEventListeners(container) {

    // ── Toggles ─────────────────────────────────────────────
    container.querySelectorAll('.toggle[data-setting]').forEach(toggle => {
      toggle.addEventListener('click', () => {
        const isActive = toggle.classList.toggle('active');
        const setting  = toggle.dataset.setting;

        switch (setting) {
          case 'autoplay_next':
            _saveSetting(SETTINGS_KEYS.AUTOPLAY_NEXT, isActive);
            break;
          case 'yellow_subs':
            _saveSetting(SETTINGS_KEYS.YELLOW_SUBS, isActive);
            break;
          case 'auto_heb_subs':
            _saveSetting(SETTINGS_KEYS.AUTO_HEB_SUBS, isActive);
            break;
          case 'reduce_motion':
            _saveSetting(SETTINGS_KEYS.REDUCE_MOTION, isActive);
            document.body.classList.toggle('reduce-motion', isActive);
            break;
        }
      });
    });

    // ── Dropdowns ───────────────────────────────────────────
    container.querySelectorAll('.settings-select[data-setting]').forEach(select => {
      select.addEventListener('change', () => {
        const setting = select.dataset.setting;
        const value   = select.value;

        switch (setting) {
          case 'audio_lang':
            _saveSetting(SETTINGS_KEYS.AUDIO_LANG, value);
            break;
          case 'subtitle_lang':
            _saveSetting(SETTINGS_KEYS.SUBTITLE_LANG, value);
            if (window.Storage.setSubtitleLang) window.Storage.setSubtitleLang(value);
            break;
          case 'sub_font_size':
            _saveSetting(SETTINGS_KEYS.SUB_FONT_SIZE, value);
            break;
          case 'language':
            window.I18n.setLanguage(value);
            // Re-render to reflect the new language
            render(container);
            break;
        }
      });
    });

    // ── AllDebrid Connect ─────────────────────────────────
    const rdConnectBtn = container.querySelector('#btn-rd-connect');
    if (rdConnectBtn) {
      rdConnectBtn.addEventListener('click', async () => {
        if (!window.AllDebrid) return;

        try {
          rdConnectBtn.disabled = true;
          rdConnectBtn.textContent = tr('Starting…', 'מתחיל…');

          const authData = await window.AllDebrid.startAuth();

          // Show the code in a modal
          if (window.showModal) {
            window.showModal(`
              <div style="text-align: center; padding: 20px;">
                <h3 style="margin-bottom: 16px;">${tr('Authorize AllDebrid', 'חיבור AllDebrid')}</h3>
                <a href="${authData.verificationUrl}" target="_blank"
                   style="color: var(--cyan); font-size: 14px; word-break: break-all;">
                  ${authData.verificationUrl}
                </a>
                <div style="
                  font-size: 36px; font-weight: 800; letter-spacing: 4px;
                  margin: 24px 0; padding: 16px;
                  background: rgba(0,212,255,0.08); border-radius: var(--radius-md);
                  border: 2px dashed rgba(0,212,255,0.3); color: var(--cyan);
                  font-family: monospace;">
                  ${authData.userCode}
                </div>
                <p style="color: var(--text-dim); font-size: 13px;">
                  ${tr('Waiting for approval…', 'ממתין לאישור…')}
                </p>
                <div class="spinner" style="margin: 16px auto;"></div>
              </div>
            `);
          }

          // Poll for authorization
          await window.AllDebrid.pollAuth(authData.deviceCode, authData.userCode);

          if (window.showToast) window.showToast(tr('AllDebrid connected successfully!', 'AllDebrid חובר בהצלחה!'), 'success');
          render(container);
        } catch (err) {
          if (window.showToast) window.showToast(err.message || tr('Auth failed', 'אימות נכשל'), 'error');
          rdConnectBtn.disabled = false;
          rdConnectBtn.textContent = tr('Connect AllDebrid', 'חבר AllDebrid');
        }
      });
    }

    // ── AllDebrid Disconnect ────────────────────────────────
    const rdDisconnectBtn = container.querySelector('#btn-rd-disconnect');
    if (rdDisconnectBtn) {
      rdDisconnectBtn.addEventListener('click', () => {
        if (window.AllDebrid) window.AllDebrid.logout();
        if (window.showToast) window.showToast(tr('AllDebrid disconnected', 'AllDebrid נותק'), 'info');
        render(container);
      });
    }

    // ── Firebase Login ──────────────────────────────────────
    const fbLoginBtn = container.querySelector('#btn-firebase-login');
    if (fbLoginBtn) {
      fbLoginBtn.addEventListener('click', () => {
        window.Router.navigate('auth');
      });
    }

    // ── Firebase Logout ─────────────────────────────────────
    const fbLogoutBtn = container.querySelector('#btn-firebase-logout');
    if (fbLogoutBtn) {
      fbLogoutBtn.addEventListener('click', async () => {
        if (window.FirebaseAuth) {
          await window.FirebaseAuth.logout();
          if (window.SyncManager) window.SyncManager.stopRealtimeSync();
        }
        if (window.showToast) window.showToast(tr('Signed out', 'יצאת מהחשבון'), 'info');
        render(container);
      });
    }

    // ── Clear Cache ─────────────────────────────────────────
    const clearCacheBtn = container.querySelector('#btn-clear-cache');
    if (clearCacheBtn) {
      clearCacheBtn.addEventListener('click', () => {
        // Clear non-essential storage items
        const keysToKeep = ['lumina_language', 'lumina_rd_token', 'lumina_rd_refresh_token',
          'lumina_rd_client_id', 'lumina_rd_client_secret', 'lumina_rd_token_expiry',
          'lumina_watchlist', 'lumina_watch_progress'];

        const keys = [];
        for (let i = 0; i < localStorage.length; i++) {
          const key = localStorage.key(i);
          if (key.startsWith('lumina_') && !keysToKeep.includes(key)) {
            keys.push(key);
          }
        }
        keys.forEach(k => localStorage.removeItem(k));

        const display = container.querySelector('#cache-size-display');
        if (display) display.textContent = _estimateCacheSize();
        if (window.showToast) window.showToast(tr('Cache cleared', 'המטמון נוקה'), 'success');
      });
    }

    // ── Clear Search History ────────────────────────────────
    const clearSearchBtn = container.querySelector('#btn-clear-search');
    if (clearSearchBtn) {
      clearSearchBtn.addEventListener('click', () => {
        window.Storage.clearSearchHistory();
        if (window.showToast) window.showToast(tr('Search history cleared', 'היסטוריית חיפוש נוקתה'), 'success');
        render(container);
      });
    }

    // ── Clear Watch Progress ────────────────────────────────
    const clearProgressBtn = container.querySelector('#btn-clear-progress');
    if (clearProgressBtn) {
      clearProgressBtn.addEventListener('click', () => {
        if (window.WatchProgress) window.WatchProgress.clear();
        if (window.showToast) window.showToast(tr('Watch progress cleared', 'התקדמות הצפייה נוקתה'), 'success');
      });
    }
  }

  /* ── Destroy ───────────────────────────────────────────────────── */

  function destroy() {
    rdUserInfo = null;
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.SettingsScreen = Object.freeze({ render, destroy });
})();
