/**
 * LuminaStreams — Firebase Auth Integration
 * Loads Firebase SDKs from CDN, handles auth state, login/register/logout,
 * and exposes Firestore instance for cloud sync.
 */
(() => {
  'use strict';

  /* ── Firebase Config ───────────────────────────────────────────── */
  const FIREBASE_CONFIG = {
    apiKey:            'AIzaSyBknWKO2Nc7x65ItMnjYJohDciRESqw6-M',
    authDomain:        'luminastreams-1a474.firebaseapp.com',
    projectId:         'luminastreams-1a474',
    storageBucket:     'luminastreams-1a474.firebasestorage.app',
    messagingSenderId: '181931286608',
    appId:             '1:181931286608:web:3addfe0fe7232c619bf155',
  };

  const SDK_URLS = [
    'https://www.gstatic.com/firebasejs/10.8.1/firebase-app-compat.js',
    'https://www.gstatic.com/firebasejs/10.8.1/firebase-auth-compat.js',
    'https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore-compat.js',
  ];

  /* ── State ─────────────────────────────────────────────────────── */
  let firebaseApp  = null;
  let auth         = null;
  let db           = null;
  let initialized  = false;
  let currentUser  = null;
  const authCallbacks = [];

  /* ── SDK Loader ────────────────────────────────────────────────── */

  /**
   * Load a script from a URL and return a promise.
   * @param {string} url
   * @returns {Promise<void>}
   */
  function loadScript(url) {
    return new Promise((resolve, reject) => {
      // Skip if already loaded
      const existing = document.querySelector(`script[src="${url}"]`);
      if (existing) { resolve(); return; }

      const script = document.createElement('script');
      script.src   = url;
      script.async = false; // load in order
      script.onload  = resolve;
      script.onerror = () => reject(new Error(`Failed to load: ${url}`));
      document.head.appendChild(script);
    });
  }

  /**
   * Load all Firebase SDK scripts sequentially.
   * @returns {Promise<void>}
   */
  async function loadSDKs() {
    for (const url of SDK_URLS) {
      await loadScript(url);
    }
  }

  /* ── Initialization ────────────────────────────────────────────── */

  /**
   * Initialize Firebase: load SDKs, create app, set up auth listener.
   * Safe to call multiple times — only initializes once.
   * @returns {Promise<void>}
   */
  async function init() {
    if (initialized) return;

    try {
      await loadSDKs();

      // Ensure firebase global is available
      if (typeof firebase === 'undefined') {
        console.error('[FirebaseAuth] Firebase SDK not available after loading.');
        return;
      }

      // Initialize app (avoid duplicate)
      if (!firebase.apps.length) {
        firebaseApp = firebase.initializeApp(FIREBASE_CONFIG);
      } else {
        firebaseApp = firebase.app();
      }

      auth = firebase.auth();
      db   = firebase.firestore();

      // Auth state listener
      auth.onAuthStateChanged(user => {
        currentUser = user
          ? { uid: user.uid, email: user.email, displayName: user.displayName }
          : null;

        // Notify all registered callbacks
        authCallbacks.forEach(cb => {
          try { cb(currentUser); } catch (err) {
            console.error('[FirebaseAuth] Auth callback error:', err);
          }
        });
      });

      initialized = true;
      console.log('[FirebaseAuth] Initialized successfully.');
    } catch (err) {
      console.error('[FirebaseAuth] Initialization failed:', err);
    }
  }

  /* ── Auth Methods ──────────────────────────────────────────────── */

  /**
   * Sign in with email and password.
   * @param {string} email
   * @param {string} password
   * @returns {Promise<{ uid: string, email: string }>}
   */
  async function login(email, password) {
    if (!auth) throw new Error('Firebase not initialized');

    try {
      const cred = await auth.signInWithEmailAndPassword(email, password);
      return { uid: cred.user.uid, email: cred.user.email };
    } catch (err) {
      throw _translateError(err);
    }
  }

  /**
   * Register a new account with email and password.
   * @param {string} email
   * @param {string} password
   * @returns {Promise<{ uid: string, email: string }>}
   */
  async function register(email, password) {
    if (!auth) throw new Error('Firebase not initialized');

    try {
      const cred = await auth.createUserWithEmailAndPassword(email, password);
      return { uid: cred.user.uid, email: cred.user.email };
    } catch (err) {
      throw _translateError(err);
    }
  }

  /**
   * Sign out the current user.
   * @returns {Promise<void>}
   */
  async function logout() {
    if (!auth) return;
    try {
      await auth.signOut();
    } catch (err) {
      console.error('[FirebaseAuth] Logout error:', err);
    }
  }

  /**
   * Send a password reset email.
   * @param {string} email
   * @returns {Promise<void>}
   */
  async function resetPassword(email) {
    if (!auth) throw new Error('Firebase not initialized');

    try {
      await auth.sendPasswordResetEmail(email);
    } catch (err) {
      throw _translateError(err);
    }
  }

  /**
   * Get current user info.
   * @returns {{ uid: string, email: string }|null}
   */
  function getCurrentUser() {
    return currentUser;
  }

  /**
   * Check if a user is currently logged in.
   * @returns {boolean}
   */
  function isLoggedIn() {
    return currentUser !== null;
  }

  /**
   * Register a callback for auth state changes.
   * @param {Function} callback  Receives user object or null
   */
  function onAuthStateChanged(callback) {
    if (typeof callback !== 'function') return;
    authCallbacks.push(callback);

    // Fire immediately with current state
    if (initialized) {
      try { callback(currentUser); } catch {}
    }
  }

  /**
   * Get the Firestore instance for cloud sync.
   * @returns {Object|null} Firestore instance
   */
  function getFirestore() {
    return db;
  }

  /* ── Error Translation ─────────────────────────────────────────── */

  /**
   * Translate Firebase error codes to Hebrew-friendly messages.
   * @param {Error} err
   * @returns {Error}
   */
  function _translateError(err) {
    const code = err.code || '';
    const messages = {
      'auth/invalid-email':            'כתובת האימייל אינה תקינה',
      'auth/user-disabled':            'חשבון זה הושעה',
      'auth/user-not-found':           'לא נמצא חשבון עם כתובת אימייל זו',
      'auth/wrong-password':           'הסיסמה שהוזנה שגויה',
      'auth/email-already-in-use':     'כתובת האימייל כבר בשימוש',
      'auth/operation-not-allowed':    'פעולה זו אינה מותרת',
      'auth/weak-password':            'הסיסמה חלשה מדי — נדרשים לפחות 6 תווים',
      'auth/too-many-requests':        'יותר מדי ניסיונות. נסה שוב מאוחר יותר',
      'auth/network-request-failed':   'שגיאת רשת — בדוק את החיבור לאינטרנט',
      'auth/invalid-credential':       'פרטי ההתחברות שגויים',
      'auth/requires-recent-login':    'נדרשת התחברות מחדש',
      'auth/missing-password':         'נדרשת סיסמה',
    };

    const message = messages[code] || err.message || 'שגיאה לא ידועה';
    const translated = new Error(message);
    translated.code = code;
    return translated;
  }

  /* ── Export ────────────────────────────────────────────────────── */
  window.FirebaseAuth = Object.freeze({
    init,
    login,
    register,
    logout,
    resetPassword,
    getCurrentUser,
    isLoggedIn,
    onAuthStateChanged,
    getFirestore,
  });
})();
