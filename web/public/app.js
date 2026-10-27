import { initializeApp } from "https://www.gstatic.com/firebasejs/10.8.1/firebase-app.js";
import { 
    getAuth, 
    signInWithEmailAndPassword, 
    createUserWithEmailAndPassword,
    sendPasswordResetEmail,
    onAuthStateChanged,
    signOut
} from "https://www.gstatic.com/firebasejs/10.8.1/firebase-auth.js";
import {
    getFirestore,
    doc,
    getDoc,
    updateDoc
} from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";

// Official Firebase Web Configuration for LuminaStreams
const firebaseConfig = {
    projectId: "luminastreams-1a474",
    appId: "1:181931286608:web:3addfe0fe7232c619bf155",
    storageBucket: "luminastreams-1a474.firebasestorage.app",
    apiKey: "AIzaSyBknWKO2Nc7x65ItMnjYJohDciRESqw6-M",
    authDomain: "luminastreams-1a474.firebaseapp.com",
    messagingSenderId: "181931286608",
    measurementId: "G-H6N4X60YQW"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
auth.languageCode = 'he';

// UI Elements: Hub Tabs
const hubTabs = document.querySelectorAll('.hub-tab');
const paneAuth = document.getElementById('paneAuth');
const paneLink = document.getElementById('paneLink');
const paneApp = document.getElementById('paneApp');

// UI Elements: Sub-tabs (Auth)
const subTabLogin = document.getElementById('subTabLogin');
const subTabRegister = document.getElementById('subTabRegister');
const authForm = document.getElementById('authForm');
const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const confirmPassInput = document.getElementById('confirmPassword');
const confirmPassGroup = document.getElementById('confirmPassGroup');
const btnText = document.getElementById('btnText');
const btnLoader = document.getElementById('btnLoader');
const submitBtn = document.getElementById('submitBtn');
const messageBanner = document.getElementById('messageBanner');
const forgotPassword = document.getElementById('forgotPassword');

// UI Elements: Cards
const authCard = document.getElementById('authCard');
const successCard = document.getElementById('successCard');
const userEmailDisplay = document.getElementById('userEmailDisplay');
const logoutBtn = document.getElementById('logoutBtn');

// UI Elements: Device Linking (Tab mode)
const linkCodeInput = document.getElementById('linkCodeInput');
const linkPasswordInput = document.getElementById('linkPasswordInput');
const linkBtn = document.getElementById('linkBtn');
const linkStatus = document.getElementById('linkStatus');

// UI Elements: Device Linking (Logged-in inline mode)
const loggedLinkCode = document.getElementById('loggedLinkCode');
const loggedLinkPass = document.getElementById('loggedLinkPass');
const loggedLinkBtn = document.getElementById('loggedLinkBtn');
const loggedLinkStatus = document.getElementById('loggedLinkStatus');

// State
let isLoginMode = true;

// Tab Switching (Hub: Auth / Link / Web App)
hubTabs.forEach(tab => {
    tab.addEventListener('click', () => {
        hubTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const mode = tab.dataset.mode;

        paneAuth.classList.remove('active');
        paneLink.classList.remove('active');
        paneApp.classList.remove('active');

        if (mode === 'auth') paneAuth.classList.add('active');
        else if (mode === 'link') paneLink.classList.add('active');
        else if (mode === 'app') paneApp.classList.add('active');

        hideMessage();
    });
});

// Sub-Tab Switching (Login vs Register)
function setAuthMode(loginMode) {
    isLoginMode = loginMode;
    hideMessage();
    if (loginMode) {
        subTabLogin.classList.add('active');
        subTabRegister.classList.remove('active');
        confirmPassGroup.style.display = 'none';
        confirmPassInput.required = false;
        btnText.textContent = 'התחבר עכשיו';
        forgotPassword.style.display = 'inline';
    } else {
        subTabRegister.classList.add('active');
        subTabLogin.classList.remove('active');
        confirmPassGroup.style.display = 'block';
        confirmPassInput.required = true;
        btnText.textContent = 'צור חשבון חדש';
        forgotPassword.style.display = 'none';
    }
}

if (subTabLogin) subTabLogin.addEventListener('click', () => setAuthMode(true));
if (subTabRegister) subTabRegister.addEventListener('click', () => setAuthMode(false));

// Message Banner Utility
function showMessage(msg, type = 'error') {
    if (!messageBanner) return;
    messageBanner.textContent = msg;
    messageBanner.className = `message-banner ${type}`;
    messageBanner.style.display = 'block';
}

function hideMessage() {
    if (messageBanner) messageBanner.style.display = 'none';
}

function setLoading(isLoading) {
    if (isLoading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline-block';
        submitBtn.disabled = true;
    } else {
        btnText.style.display = 'inline';
        btnLoader.style.display = 'none';
        submitBtn.disabled = false;
    }
}

// Hebrew Error Translation
function translateError(error) {
    const msg = error?.code || error?.message || '';
    if (msg.includes('auth/invalid-credential') || msg.includes('auth/user-not-found') || msg.includes('auth/wrong-password')) {
        return "כתובת דוא״ל או סיסמה שגויים. אנא בדוק את הפרטים ונסה שנית";
    }
    if (msg.includes('auth/email-already-in-use')) {
        return "כתובת דוא״ל זו כבר רשומה במערכת. באפשרותך להתחבר";
    }
    if (msg.includes('auth/weak-password')) {
        return "הסיסמה חלשה מדי. בחר סיסמה עם 6 תווים לפחות";
    }
    if (msg.includes('auth/invalid-email')) {
        return "כתובת הדוא״ל אינה תקינה";
    }
    if (msg.includes('auth/too-many-requests')) {
        return "יותר מדי ניסיונות התחברות. נסה שוב בעוד מספר דקות";
    }
    return error?.message || "אירעה שגיאה בעיבוד הבקשה. אנא נסה שוב";
}

// Form Submission (Login / Register)
if (authForm) {
    authForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideMessage();
        
        const email = emailInput.value.trim();
        const password = passwordInput.value;

        if (isLoginMode) {
            setLoading(true);
            try {
                await signInWithEmailAndPassword(auth, email, password);
            } catch (error) {
                showMessage(translateError(error));
            } finally {
                setLoading(false);
            }
        } else {
            const confirmPass = confirmPassInput.value;
            if (password !== confirmPass) {
                showMessage("הסיסמאות שהוזנו אינן תואמות");
                return;
            }

            setLoading(true);
            try {
                await createUserWithEmailAndPassword(auth, email, password);
            } catch (error) {
                showMessage(translateError(error));
            } finally {
                setLoading(false);
            }
        }
    });
}

// Forgot Password
if (forgotPassword) {
    forgotPassword.addEventListener('click', async (e) => {
        e.preventDefault();
        const email = emailInput.value.trim();
        if (!email) {
            showMessage("אנא הזן את כתובת הדוא״ל שלך בשדה למעלה כדי לשלוח קישור לאיפוס");
            return;
        }

        try {
            await sendPasswordResetEmail(auth, email);
            showMessage("קישור לאיפוס סיסמה נשלח בהצלחה לתיבת הדוא״ל שלך!", "success");
        } catch (error) {
            showMessage(translateError(error));
        }
    });
}

// Logout
if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
        try {
            await signOut(auth);
        } catch (err) {
            console.error('Logout error:', err);
        }
    });
}

// Device Linking Logic Helper
async function performDeviceLink(code, password, email, btnEl, statusEl) {
    const cleanCode = (code || '').trim().toUpperCase();
    if (!cleanCode || cleanCode.length !== 6) {
        statusEl.textContent = "אנא הזן קוד בן 6 תווים";
        statusEl.className = "link-status error";
        return;
    }

    if (!password) {
        statusEl.textContent = "אנא הזן את סיסמת החשבון לאימות";
        statusEl.className = "link-status error";
        return;
    }

    btnEl.disabled = true;
    statusEl.textContent = "מקשר מכשיר לטלוויזיה...";
    statusEl.className = "link-status loading";

    try {
        const linkRef = doc(db, "device_links", cleanCode);
        const linkDoc = await getDoc(linkRef);

        if (!linkDoc.exists()) {
            statusEl.textContent = "הקוד לא נמצא. ודא שהקוד מוצג כעת במסך הטלוויזיה";
            statusEl.className = "link-status error";
            btnEl.disabled = false;
            return;
        }

        const data = linkDoc.data();
        if (data.status !== "pending") {
            statusEl.textContent = "קוד זה כבר נוצל בעבר. צור קוד חדש בטלוויזיה";
            statusEl.className = "link-status error";
            btnEl.disabled = false;
            return;
        }

        // Write credentials to complete the link
        await updateDoc(linkRef, {
            status: "completed",
            email: email,
            password: password,
            linked_at: Date.now()
        });

        statusEl.textContent = "✓ המכשיר חובר בהצלחה! הטלוויזיה מתחברת כעת באופן אוטומטי";
        statusEl.className = "link-status success";

        setTimeout(() => {
            statusEl.textContent = "";
            btnEl.disabled = false;
        }, 6000);

    } catch (error) {
        console.error("Link error:", error);
        statusEl.textContent = "שגיאה בביצוע הקישור: " + (error.message || error);
        statusEl.className = "link-status error";
        btnEl.disabled = false;
    }
}

// Event: Link in Tab mode
if (linkBtn) {
    linkBtn.addEventListener('click', async () => {
        const user = auth.currentUser;
        const email = user ? user.email : emailInput.value.trim();
        const pass = linkPasswordInput.value || passwordInput.value;
        if (!email) {
            linkStatus.textContent = "אנא התחבר לחשבון או הזן דוא״ל וסיסמה תחילה";
            linkStatus.className = "link-status error";
            return;
        }
        await performDeviceLink(linkCodeInput.value, pass, email, linkBtn, linkStatus);
    });
}

// Event: Link in Logged-in mode
if (loggedLinkBtn) {
    loggedLinkBtn.addEventListener('click', async () => {
        const user = auth.currentUser;
        if (!user) return;
        await performDeviceLink(
            loggedLinkCode.value,
            loggedLinkPass.value,
            user.email,
            loggedLinkBtn,
            loggedLinkStatus
        );
    });
}

// Auth State Observer
onAuthStateChanged(auth, (user) => {
    if (user) {
        if (authCard) authCard.style.display = 'none';
        if (successCard) successCard.style.display = 'block';
        if (userEmailDisplay) userEmailDisplay.textContent = user.email;
        
        // Auto-fill logged link password if available in session
        if (passwordInput && passwordInput.value && loggedLinkPass) {
            loggedLinkPass.value = passwordInput.value;
        }
    } else {
        if (authCard) authCard.style.display = 'block';
        if (successCard) successCard.style.display = 'none';
        if (passwordInput) passwordInput.value = '';
        if (confirmPassInput) confirmPassInput.value = '';
        if (loggedLinkCode) loggedLinkCode.value = '';
        if (loggedLinkPass) loggedLinkPass.value = '';
    }
});
