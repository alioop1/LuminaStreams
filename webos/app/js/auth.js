/**
 * LuminaStreams TV — Sign-in, 1:1 with the Android TV AuthScreen.kt (+ AuthViewModel.kt):
 * a 920×600 dp glass card — device linking with a QR code on the start side, the manual
 * login / register form on the end side — or the "Welcome Back" card when signed in.
 * Same Firebase account and the same device_links/{code} Firestore handshake as Android.
 * Fields are real <input>s: OK opens the LG on-screen keyboard; its Enter works like the
 * Android IME action (email → password → confirm → button).
 */
(() => {
  'use strict';
  const { tr, esc, el } = TV;

  const WEB = 'luminastreams-1a474.web.app';
  const QR = size => `https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=https://${WEB}`;
  const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no I/O/0/1
  const sleep = ms => new Promise(r => setTimeout(r, ms));

  /** Firebase loads its SDK from the network at start-up: wait until auth + Firestore exist. */
  async function firebaseReady(FA) {
    if (!FA) throw new Error(tr('Firebase not available', 'Firebase לא זמין'));
    if (FA.getFirestore()) return FA.getFirestore();
    try { Promise.resolve(FA.init()).catch(() => {}); } catch {}
    for (let i = 0; i < 75; i++) {
      await sleep(200);
      if (FA.getFirestore()) return FA.getFirestore();
    }
    throw new Error(tr('Firebase not initialized', 'Firebase לא אותחל'));
  }

  // One auth-state listener for the module (FirebaseAuth has no unsubscribe): it repaints the open screen.
  let live = null;
  let hooked = false;
  function hookAuth(FA) {
    if (hooked || !FA || !FA.onAuthStateChanged) return;
    hooked = true;
    FA.onAuthStateChanged(() => { if (live) live(); });
  }

  /** FirebaseAuth already translates to Hebrew; English UI gets English (Android shows Hebrew only). */
  function errorText(e) {
    if (TV.rtl()) return (e && e.message) || 'שגיאה לא ידועה';
    const en = {
      'auth/invalid-email': 'The email address is not valid.',
      'auth/user-disabled': 'This account has been disabled.',
      'auth/user-not-found': 'User not found. Check the email or register.',
      'auth/wrong-password': 'Wrong password.',
      'auth/invalid-credential': 'Wrong email or password.',
      'auth/email-already-in-use': 'This email is already registered. Try logging in instead.',
      'auth/weak-password': 'The password is too weak. Use at least 6 characters.',
      'auth/too-many-requests': 'Too many attempts. Try again later.',
      'auth/network-request-failed': 'Network error. Check the internet connection.',
      'auth/missing-password': 'A password is required.',
    }[(e && e.code) || ''];
    return en || (e && e.message) || 'Unknown error';
  }

  TV.register('auth', () => {
    const FA = window.FirebaseAuth;
    const user = () => (FA && FA.getCurrentUser ? FA.getCurrentUser() : null);

    const s = {
      mode: 'login',        // 'login' | 'register'
      loading: false,
      error: '', info: '',
      linking: false, code: null, linkStatus: null, dots: 1, linkRef: null,
      focus: 'email', lastForm: 'email',
      successEmail: null,
      dead: false,
    };
    let dotsTimer = 0;

    const root = el(`
      <div class="screen ag">
        <div class="ag-glow"></div>
        <div class="ag-card">
          <div class="ag-left">
            <div class="ag-def">
              <div class="ag-t">${esc(tr('Link Device', 'חבר מכשיר'))}</div>
              <div class="ag-s">${esc(tr('Scan to login from your phone', 'סרוק כדי להתחבר מהנייד'))}</div>
              <div class="ag-qr"><img src="${QR(250)}" alt=""></div>
              <div class="ag-url" dir="ltr">${WEB}</div>
              <div class="ag-link" data-n="link">${esc(tr('Generate Link Code', 'צור קוד חיבור'))}</div>
            </div>
            <div class="ag-lnk hidden">
              <div class="ag-t">${esc(tr('Link Device', 'חיבור מכשיר'))}</div>
              <div class="ag-s">${esc(tr('Enter this code on the website', 'הקלד קוד זה באתר'))}</div>
              <div class="ag-code"><span dir="ltr"></span></div>
              <div class="ag-wait"></div>
              <div class="ag-qr2"><img src="${QR(200)}" alt=""></div>
              <div class="ag-url2" dir="ltr">${WEB}</div>
            </div>
          </div>
          <div class="ag-vdiv"></div>
          <div class="ag-right">
            <div class="ag-ft"></div>
            <div class="ag-f" data-n="email"><input class="ag-in" type="email" autocomplete="off" spellcheck="false" placeholder="${esc(tr('Email', 'אימייל'))}"></div>
            <div class="ag-f" data-n="pass"><input class="ag-in" type="password" autocomplete="off" placeholder="${esc(tr('Password', 'סיסמה'))}"></div>
            <div class="ag-f" data-n="confirm"><input class="ag-in" type="password" autocomplete="off" placeholder="${esc(tr('Confirm Password', 'אימות סיסמה'))}"></div>
            <div class="ag-err"></div>
            <div class="ag-sub" data-n="submit"></div>
            <div class="ag-tog" data-n="toggle"></div>
            <div class="ag-tog" data-n="reset">${esc(tr('Forgot password?', 'שכחת סיסמה?'))}</div>
          </div>
        </div>
        <div class="ag-in-view hidden">
          <div class="ag-av"></div>
          <div class="ag-wb">${esc(tr('Welcome Back', 'ברוך שובך'))}</div>
          <div class="ag-em" dir="ltr"></div>
          <div class="ag-out" data-n="logout">${esc(tr('Logout', 'התנתק'))}</div>
        </div>
      </div>`);

    const $ = sel => TV.$(root, sel);
    const node = n => root.querySelector(`[data-n="${n}"]`);
    const inputs = { email: $('[data-n="email"] input'), pass: $('[data-n="pass"] input'), confirm: $('[data-n="confirm"] input') };
    const card = $('.ag-card');
    const inView = $('.ag-in-view');

    const signedInEmail = () => s.successEmail || (user() ? user().email || '' : null);
    /** Focus order of the visible column(s). */
    function formOrder() {
      return ['email', 'pass'].concat(s.mode === 'register' ? ['confirm'] : [], ['submit', 'toggle'], s.mode === 'login' ? ['reset'] : []);
    }
    const linkVisible = () => !s.linking;
    function validFocus() {
      if (signedInEmail() != null) { s.focus = 'logout'; return; }
      if (s.focus === 'link' && linkVisible()) return;
      if (formOrder().indexOf(s.focus) < 0) s.focus = formOrder().indexOf(s.lastForm) >= 0 ? s.lastForm : 'email';
    }

    function render() {
      const email = signedInEmail();
      const inMode = email != null;
      card.classList.toggle('hidden', inMode);
      inView.classList.toggle('hidden', !inMode);
      if (inMode) {
        $('.ag-av').textContent = (email.charAt(0) || 'U').toUpperCase();
        $('.ag-em').textContent = email;
      }
      // Left: QR + "Generate Link Code", or the code being linked.
      $('.ag-def').classList.toggle('hidden', s.linking && !!s.code);
      $('.ag-lnk').classList.toggle('hidden', !(s.linking && s.code));
      if (s.code) $('.ag-code span').textContent = s.code.split('').join(' ');
      $('.ag-wait').textContent = s.linkStatus || `${tr('Waiting for link', 'ממתין לחיבור')}${'.'.repeat(s.dots)}`;
      // Right: the form.
      $('.ag-ft').textContent = s.mode === 'login' ? tr('Manual Login', 'התחברות ידנית') : tr('Create Account', 'יצירת חשבון');
      node('confirm').classList.toggle('hidden', s.mode !== 'register');
      node('reset').classList.toggle('hidden', s.mode !== 'login');
      inputs.pass.setAttribute('enterkeyhint', s.mode === 'register' ? 'next' : 'done');
      const err = $('.ag-err');
      err.textContent = s.error || s.info;
      err.classList.toggle('info', !s.error && !!s.info);
      err.classList.toggle('hidden', !(s.error || s.info));
      node('submit').textContent = s.loading ? tr('Please Wait...', 'אנא המתן...') : s.mode === 'login' ? tr('Login', 'התחבר') : tr('Register', 'הרשם');
      node('toggle').textContent = s.mode === 'login' ? tr('Switch to Register', 'עבור להרשמה') : tr('Switch to Login', 'עבור להתחברות');
      validFocus();
      paint();
    }
    function paint() {
      const typing = typingIn();
      root.querySelectorAll('[data-n]').forEach(n => {
        // The field's Surface is the focused thing; while its text field types, the Surface is not.
        n.classList.toggle('f', n.dataset.n === s.focus && n.dataset.n !== typing);
      });
    }
    function typingIn() {
      const a = document.activeElement;
      for (const k of Object.keys(inputs)) if (inputs[k] === a) return k;
      return null;
    }
    function setFocus(n) {
      s.focus = n;
      if (n !== 'link' && n !== 'logout') s.lastForm = n;
      paint();
    }

    /* ── Actions (AuthViewModel) ── */
    function done(email) {
      // Android: isSuccess → the signed-in card shows, then after 1 s the screen closes.
      s.loading = false;
      s.successEmail = email || (user() && user().email) || '';
      render();
      setTimeout(() => {
        if (s.dead) return;
        s.successEmail = null;
        if (TV.current() && TV.current().el === root) TV.back();
        else render();
      }, 1000);
    }
    async function login() {
      if (s.loading) return;
      const email = inputs.email.value.trim();
      const pass = inputs.pass.value;
      if (!email || !pass) { s.error = tr('Please fill in all fields', 'אנא מלא את כל השדות'); s.info = ''; render(); return; }
      s.loading = true; s.error = ''; s.info = '';
      render();
      try {
        await firebaseReady(FA);
        const u = await FA.login(email, pass);
        if (s.dead) return;
        done(u && u.email);
      } catch (e) {
        if (s.dead) return;
        s.loading = false; s.error = errorText(e);
        render();
      }
    }
    async function register() {
      if (s.loading) return;
      const email = inputs.email.value.trim();
      const pass = inputs.pass.value;
      const confirm = inputs.confirm.value;
      s.info = '';
      if (!email || !pass || !confirm) { s.error = tr('Please fill in all fields', 'אנא מלא את כל השדות'); render(); return; }
      if (pass !== confirm) { s.error = tr('The passwords do not match', 'הסיסמאות אינן תואמות'); render(); return; }
      if (pass.length < 6) { s.error = tr('The password must have at least 6 characters', 'הסיסמה חייבת להכיל לפחות 6 תווים'); render(); return; }
      s.loading = true; s.error = '';
      render();
      try {
        await firebaseReady(FA);
        const u = await FA.register(email, pass);
        if (s.dead) return;
        done(u && u.email);
      } catch (e) {
        if (s.dead) return;
        s.loading = false; s.error = errorText(e);
        render();
      }
    }
    async function resetPassword() {
      if (s.loading) return;
      const email = inputs.email.value.trim();
      s.info = '';
      if (!email) { s.error = tr('Enter your email first', 'הזן קודם את כתובת האימייל'); render(); return; }
      s.loading = true; s.error = '';
      render();
      try {
        await firebaseReady(FA);
        await FA.resetPassword(email);
        if (s.dead) return;
        s.loading = false; s.info = tr('A password reset email was sent', 'נשלח מייל לאיפוס הסיסמה');
      } catch (e) {
        if (s.dead) return;
        s.loading = false; s.error = errorText(e);
      }
      render();
    }
    async function logout() {
      if (FA) await FA.logout();
      if (s.dead) return;
      s.successEmail = null;
      s.focus = 'email';
      render();
    }

    /* ── Device linking: device_links/{code} = {status: 'pending', created}, poll every 3 s for 5 min ── */
    function stopDots() { clearInterval(dotsTimer); dotsTimer = 0; }
    async function startLink() {
      if (s.linking) return;
      const code = Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
      s.linking = true; s.code = code; s.linkStatus = null; s.error = ''; s.info = ''; s.dots = 1;
      if (s.focus === 'link') s.focus = s.lastForm;
      stopDots();
      dotsTimer = setInterval(() => { s.dots = (s.dots % 3) + 1; if (!s.linkStatus) $('.ag-wait').textContent = `${tr('Waiting for link', 'ממתין לחיבור')}${'.'.repeat(s.dots)}`; }, 600);
      render();
      const finish = () => { s.linking = false; s.code = null; stopDots(); };
      let ref = null;
      try {
        const db = await firebaseReady(FA);
        if (s.dead) return;
        ref = db.collection('device_links').doc(code);
        s.linkRef = ref;
        await ref.set({ status: 'pending', created: Date.now() });
        for (let attempt = 0; attempt < 100; attempt++) {
          await sleep(3000);
          if (s.dead || s.linkRef !== ref) return;
          const doc = await ref.get();
          const d = doc.exists ? doc.data() : null;
          if (d && d.status === 'completed' && d.email && d.password) {
            s.linkStatus = tr('Connecting...', 'מתחבר...');
            render();
            try {
              const u = await FA.login(d.email, d.password);
              finish();
              s.linkStatus = null;
              if (!s.dead) done(u && u.email);
            } catch (e) {
              finish();
              s.linkStatus = null;
              s.error = errorText(e);
              if (!s.dead) render();
            }
            ref.delete().catch(() => {});
            s.linkRef = null;
            return;
          }
        }
        finish();
        s.linkStatus = null;
        s.linkRef = null;
        ref.delete().catch(() => {});
        if (!s.dead) { TV.toast(tr('The code has expired', 'פג תוקף הקוד')); render(); }
      } catch (e) {
        finish();
        s.linkRef = null;
        if (ref) ref.delete().catch(() => {});
        if (s.dead) return;
        s.error = `${tr('Connection error:', 'שגיאה בחיבור:')} ${(e && e.message) || ''}`;
        render();
      }
    }

    /* ── Input fields: OK opens the LG keyboard; its Enter = the Android IME action ── */
    function openField(n) {
      setFocus(n);
      const inp = inputs[n];
      inp.focus();
      // Put the caret at the end (the TV keyboard edits from there).
      try { const v = inp.value; inp.setSelectionRange(v.length, v.length); } catch {}
      paint();
    }
    function imeAction(n) {
      if (n === 'email') { openField('pass'); return; }
      if (n === 'pass' && s.mode === 'register') { openField('confirm'); return; }
      inputs[n].blur();
      setFocus('submit');
    }
    Object.keys(inputs).forEach(n => {
      inputs[n].addEventListener('focus', () => { s.focus = n; s.lastForm = n; paint(); });
      inputs[n].addEventListener('blur', () => paint());
      inputs[n].addEventListener('input', () => { if (s.error || s.info) { s.error = ''; s.info = ''; render(); } });
    });

    function activate(n) {
      switch (n) {
        case 'email': case 'pass': case 'confirm': openField(n); break;
        case 'submit': if (s.mode === 'login') login(); else register(); break;
        case 'toggle': s.mode = s.mode === 'login' ? 'register' : 'login'; render(); break;
        case 'reset': resetPassword(); break;
        case 'link': startLink(); break;
        case 'logout': logout(); break;
        default:
      }
    }
    root.querySelectorAll('[data-n]').forEach(n => TV.pointer(n, () => setFocus(n.dataset.n), () => { setFocus(n.dataset.n); activate(n.dataset.n); }));

    function move(dir) {
      const order = formOrder();
      const i = order.indexOf(s.focus);
      if (i < 0) return;
      const j = Math.max(0, Math.min(order.length - 1, i + dir));
      setFocus(order[j]);
    }

    function onKey(key) {
      const typing = typingIn();
      if (typing) {
        // The core leaves left/right and Backspace to the field; the rest arrives here first.
        if (key === 'ok') { imeAction(typing); return true; }
        inputs[typing].blur();
        if (key === 'up') move(-1);
        else if (key === 'down') move(1);
        else paint();
        return true;
      }
      if (s.focus === 'logout') {
        if (key === 'ok') { activate('logout'); return true; }
        return key !== 'back';
      }
      if (s.focus === 'link') {
        if (TV.fwd(key)) { setFocus(s.lastForm); return true; }
        if (key === 'ok') { activate('link'); return true; }
        return key !== 'back';
      }
      if (key === 'up') { move(-1); return true; }
      if (key === 'down') { move(1); return true; }
      // The QR / link column sits on the start side of the card.
      if (TV.bwd(key)) { if (linkVisible()) setFocus('link'); return true; }
      if (key === 'ok') { activate(s.focus); return true; }
      return key !== 'back';
    }

    const refresh = () => { if (!s.dead && !s.successEmail) render(); };
    live = refresh;
    hookAuth(FA);
    render();

    return {
      el: root,
      onKey,
      onShow(first) { if (!first) { live = refresh; render(); } },
      onHide() { const t = typingIn(); if (t) inputs[t].blur(); },
      destroy() {
        s.dead = true;
        if (live === refresh) live = null;
        stopDots();
        const t = typingIn();
        if (t) inputs[t].blur();
        if (s.linkRef) { s.linkRef.delete().catch(() => {}); s.linkRef = null; }
      },
    };
  });
})();
