/**
 * LuminaStreams — Auth Screen
 * Login / Register tabs with Firebase Auth integration.
 * Glassmorphism card centered on screen, Hebrew error messages.
 */
(() => {
  'use strict';

  const { t, tr } = window.I18n;

  /* ── State ─────────────────────────────────────────────────────── */
  let activeTab = 'login'; // 'login' | 'register'

  /* ── Render ────────────────────────────────────────────────────── */

  function render(container) {
    // If already logged in, show account info instead
    const user = window.FirebaseAuth ? window.FirebaseAuth.getCurrentUser() : null;

    if (user) {
      _renderLoggedIn(container, user);
      return;
    }

    container.innerHTML = `
      <div class="auth-page page">
        <div class="auth-card">
          <!-- Logo -->
          <div class="auth-logo">
            <h1>LuminaStreams</h1>
            <p>${tr('Sign in to sync your data across devices', 'התחבר כדי לסנכרן את הנתונים שלך בין מכשירים')}</p>
          </div>

          <!-- Tabs -->
          <div style="display: flex; gap: 4px; margin-bottom: 24px;">
            <button class="watchlist-tab auth-tab ${activeTab === 'login' ? 'active' : ''}"
                    data-auth-tab="login" style="flex: 1;">
              ${t('btn.login')}
            </button>
            <button class="watchlist-tab auth-tab ${activeTab === 'register' ? 'active' : ''}"
                    data-auth-tab="register" style="flex: 1;">
              ${tr('Register', 'הרשמה')}
            </button>
          </div>

          <!-- Error Display -->
          <div id="auth-error" class="form-error" style="display: none; margin-bottom: 12px; padding: 10px 14px; background: rgba(255,59,92,0.08); border-radius: var(--radius-sm); border: 1px solid rgba(255,59,92,0.2);">
          </div>

          <!-- Login Form -->
          <form id="form-login" class="auth-form" style="${activeTab === 'login' ? '' : 'display: none;'}">
            <div class="form-group">
              <label class="form-label">${tr('Email', 'אימייל')}</label>
              <input type="email" class="form-input" id="login-email"
                     placeholder="${tr('your@email.com', 'האימייל שלך')}"
                     autocomplete="email" required>
            </div>

            <div class="form-group">
              <label class="form-label">${tr('Password', 'סיסמה')}</label>
              <input type="password" class="form-input" id="login-password"
                     placeholder="${tr('Enter password', 'הזן סיסמה')}"
                     autocomplete="current-password" required>
            </div>

            <button type="submit" class="btn btn-primary w-full btn-lg" id="btn-login">
              ${t('btn.login')}
            </button>

            <button type="button" class="btn btn-ghost btn-sm" id="btn-forgot"
                    style="margin-top: 4px; font-size: 12px;">
              ${tr('Forgot Password?', 'שכחת סיסמה?')}
            </button>
          </form>

          <!-- Register Form -->
          <form id="form-register" class="auth-form" style="${activeTab === 'register' ? '' : 'display: none;'}">
            <div class="form-group">
              <label class="form-label">${tr('Email', 'אימייל')}</label>
              <input type="email" class="form-input" id="register-email"
                     placeholder="${tr('your@email.com', 'האימייל שלך')}"
                     autocomplete="email" required>
            </div>

            <div class="form-group">
              <label class="form-label">${tr('Password', 'סיסמה')}</label>
              <input type="password" class="form-input" id="register-password"
                     placeholder="${tr('At least 6 characters', 'לפחות 6 תווים')}"
                     autocomplete="new-password" required minlength="6">
            </div>

            <div class="form-group">
              <label class="form-label">${tr('Confirm Password', 'אימות סיסמה')}</label>
              <input type="password" class="form-input" id="register-confirm"
                     placeholder="${tr('Re-enter password', 'הזן שוב את הסיסמה')}"
                     autocomplete="new-password" required minlength="6">
            </div>

            <button type="submit" class="btn btn-primary w-full btn-lg" id="btn-register">
              ${tr('Create Account', 'צור חשבון')}
            </button>
          </form>

          <!-- Divider & Switch -->
          <div class="auth-divider" style="margin-top: 20px;">
            <span>${tr('or', 'או')}</span>
          </div>

          <div class="auth-link">
            ${activeTab === 'login'
              ? `${tr("Don't have an account?", 'אין לך חשבון?')} <a href="#" id="switch-to-register">${tr('Register', 'הרשמה')}</a>`
              : `${tr('Already have an account?', 'כבר יש לך חשבון?')} <a href="#" id="switch-to-login">${t('btn.login')}</a>`
            }
          </div>
        </div>
      </div>
    `;

    _attachEventListeners(container);
  }

  /**
   * Render the logged-in state with user info.
   */
  function _renderLoggedIn(container, user) {
    container.innerHTML = `
      <div class="auth-page page">
        <div class="auth-card" style="text-align: center;">
          <div class="auth-logo">
            <h1>LuminaStreams</h1>
          </div>

          <div class="account-avatar-large" style="margin: 0 auto 16px; width: 72px; height: 72px; font-size: 28px;">
            ${user.email.charAt(0).toUpperCase()}
          </div>

          <h2 style="font-size: 20px; font-weight: 700; margin-bottom: 8px;">
            ${tr('Welcome!', 'ברוכים הבאים!')}
          </h2>

          <p style="color: var(--text-dim); margin-bottom: 24px;">
            ${user.email}
          </p>

          <div style="display: flex; flex-direction: column; gap: 10px;">
            <button class="btn btn-primary" id="btn-go-home">
              ${tr('Go to Home', 'עבור לדף הבית')}
            </button>
            <button class="btn btn-secondary" id="btn-sync-now">
              ${tr('Sync Data Now', 'סנכרן נתונים עכשיו')}
            </button>
            <button class="btn btn-danger btn-sm" id="btn-auth-logout" style="margin-top: 8px;">
              ${t('btn.logout')}
            </button>
          </div>
        </div>
      </div>
    `;

    const goHomeBtn = container.querySelector('#btn-go-home');
    if (goHomeBtn) {
      goHomeBtn.addEventListener('click', () => window.Router.navigate('home'));
    }

    const syncBtn = container.querySelector('#btn-sync-now');
    if (syncBtn) {
      syncBtn.addEventListener('click', async () => {
        syncBtn.disabled = true;
        syncBtn.textContent = tr('Syncing…', 'מסנכרן…');
        try {
          if (window.SyncManager) await window.SyncManager.fullSync();
          if (window.showToast) window.showToast(tr('Sync complete', 'הסנכרון הושלם'), 'success');
        } catch {
          if (window.showToast) window.showToast(tr('Sync failed', 'הסנכרון נכשל'), 'error');
        }
        syncBtn.disabled = false;
        syncBtn.textContent = tr('Sync Data Now', 'סנכרן נתונים עכשיו');
      });
    }

    const logoutBtn = container.querySelector('#btn-auth-logout');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', async () => {
        if (window.SyncManager) await window.SyncManager.flushProgress().catch(() => {});
        if (window.FirebaseAuth) await window.FirebaseAuth.logout();
        _updateProfileNav(null);
        render(container);
      });
    }
  }

  /* ── Event Handlers ────────────────────────────────────────────── */

  function _attachEventListeners(container) {
    const errorDiv  = container.querySelector('#auth-error');
    const loginForm = container.querySelector('#form-login');
    const regForm   = container.querySelector('#form-register');

    // ── Tab Switching ───────────────────────────────────────
    container.querySelectorAll('.auth-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        activeTab = tab.dataset.authTab;
        _hideError(errorDiv);

        container.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        if (activeTab === 'login') {
          loginForm.style.display = '';
          regForm.style.display   = 'none';
        } else {
          loginForm.style.display = 'none';
          regForm.style.display   = '';
        }
      });
    });

    // Switch links
    const switchToReg = container.querySelector('#switch-to-register');
    if (switchToReg) {
      switchToReg.addEventListener('click', (e) => {
        e.preventDefault();
        activeTab = 'register';
        render(container);
      });
    }
    const switchToLogin = container.querySelector('#switch-to-login');
    if (switchToLogin) {
      switchToLogin.addEventListener('click', (e) => {
        e.preventDefault();
        activeTab = 'login';
        render(container);
      });
    }

    // ── Login Submit ────────────────────────────────────────
    if (loginForm) {
      loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        _hideError(errorDiv);

        const email    = container.querySelector('#login-email').value.trim();
        const password = container.querySelector('#login-password').value;
        const btn      = container.querySelector('#btn-login');

        if (!email || !password) {
          _showError(errorDiv, tr('Please fill in all fields', 'נא למלא את כל השדות'));
          return;
        }

        if (!window.FirebaseAuth) {
          _showError(errorDiv, tr('Firebase is not available', 'Firebase אינו זמין'));
          return;
        }

        btn.disabled = true;
        btn.textContent = tr('Logging in…', 'מתחבר…');

        try {
          const user = await window.FirebaseAuth.login(email, password);
          _updateProfileNav(user);
          // Cloud sync starts from the auth-state listener in app.js.

          if (window.showToast) window.showToast(tr('Welcome back!', 'ברוך שובך!'), 'success');
          window.Router.navigate('home');
        } catch (err) {
          _showError(errorDiv, err.message);
          btn.disabled = false;
          btn.textContent = t('btn.login');
        }
      });
    }

    // ── Register Submit ─────────────────────────────────────
    if (regForm) {
      regForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        _hideError(errorDiv);

        const email    = container.querySelector('#register-email').value.trim();
        const password = container.querySelector('#register-password').value;
        const confirm  = container.querySelector('#register-confirm').value;
        const btn      = container.querySelector('#btn-register');

        if (!email || !password || !confirm) {
          _showError(errorDiv, tr('Please fill in all fields', 'נא למלא את כל השדות'));
          return;
        }

        if (password !== confirm) {
          _showError(errorDiv, tr('Passwords do not match', 'הסיסמאות אינן תואמות'));
          return;
        }

        if (password.length < 6) {
          _showError(errorDiv, tr('Password must be at least 6 characters', 'הסיסמה חייבת להכיל לפחות 6 תווים'));
          return;
        }

        if (!window.FirebaseAuth) {
          _showError(errorDiv, tr('Firebase is not available', 'Firebase אינו זמין'));
          return;
        }

        btn.disabled = true;
        btn.textContent = tr('Creating account…', 'יוצר חשבון…');

        try {
          const user = await window.FirebaseAuth.register(email, password);
          _updateProfileNav(user);
          // Cloud sync starts from the auth-state listener in app.js.

          if (window.showToast) window.showToast(tr('Account created!', 'החשבון נוצר בהצלחה!'), 'success');
          window.Router.navigate('home');
        } catch (err) {
          _showError(errorDiv, err.message);
          btn.disabled = false;
          btn.textContent = tr('Create Account', 'צור חשבון');
        }
      });
    }

    // ── Forgot Password ─────────────────────────────────────
    const forgotBtn = container.querySelector('#btn-forgot');
    if (forgotBtn) {
      forgotBtn.addEventListener('click', async () => {
        const email = container.querySelector('#login-email').value.trim();

        if (!email) {
          _showError(errorDiv, tr('Enter your email address first', 'הזן את כתובת האימייל שלך קודם'));
          return;
        }

        if (!window.FirebaseAuth) {
          _showError(errorDiv, tr('Firebase is not available', 'Firebase אינו זמין'));
          return;
        }

        try {
          await window.FirebaseAuth.resetPassword(email);
          if (window.showToast) {
            window.showToast(
              tr('Password reset email sent', 'נשלח אימייל לאיפוס סיסמה'),
              'success'
            );
          }
        } catch (err) {
          _showError(errorDiv, err.message);
        }
      });
    }
  }

  /* ── Helpers ───────────────────────────────────────────────────── */

  function _showError(el, message) {
    if (!el) return;
    el.textContent  = message;
    el.style.display = 'block';
    el.style.animation = 'slideDown 0.2s ease';
  }

  function _hideError(el) {
    if (!el) return;
    el.style.display = 'none';
    el.textContent   = '';
  }

  function _updateProfileNav(user) {
    const profileBtn  = document.querySelector('#nav-profile');
    const profileSpan = profileBtn ? profileBtn.querySelector('span') : null;
    if (profileSpan) {
      profileSpan.textContent = user
        ? (user.email ? user.email.split('@')[0] : tr('Account', 'חשבון'))
        : tr('Account', 'חשבון');
    }
  }

  /* ── Destroy ───────────────────────────────────────────────────── */

  function destroy() {
    // No persistent listeners
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.AuthScreen = Object.freeze({ render, destroy });
})();
