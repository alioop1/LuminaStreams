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
    updateDoc,
    setDoc
} from "https://www.gstatic.com/firebasejs/10.8.1/firebase-firestore.js";

// Firebase Configuration (from google-services.json)
const firebaseConfig = {
    apiKey: "AIzaSyAgm55fEQ44QlmOOfNmEWfYs3TrjT4snpo",
    authDomain: "luminastreams-1a474.firebaseapp.com",
    projectId: "luminastreams-1a474",
    storageBucket: "luminastreams-1a474.firebasestorage.app",
    messagingSenderId: "181931286608",
    appId: "1:181931286608:web:1234567890abcdef"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
auth.languageCode = 'he';

// UI Elements
const tabLogin = document.getElementById('tabLogin');
const tabRegister = document.getElementById('tabRegister');
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

// State Elements
const authCard = document.getElementById('authCard');
const successCard = document.getElementById('successCard');
const userEmailDisplay = document.getElementById('userEmailDisplay');
const logoutBtn = document.getElementById('logoutBtn');

// Device Link Elements
const linkSection = document.getElementById('linkSection');
const linkCodeInput = document.getElementById('linkCodeInput');
const linkPasswordInput = document.getElementById('linkPasswordInput');
const linkBtn = document.getElementById('linkBtn');
const linkStatus = document.getElementById('linkStatus');

// State
let isLoginMode = true;

// Utility: Show Message
function showMessage(msg, type = 'error') {
    messageBanner.textContent = msg;
    messageBanner.className = `message-banner ${type}`;
    messageBanner.style.display = 'block';
}

function hideMessage() {
    messageBanner.style.display = 'none';
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

// Tab Switching
function setMode(loginMode) {
    isLoginMode = loginMode;
    hideMessage();
    if (loginMode) {
        tabLogin.classList.add('active');
        tabRegister.classList.remove('active');
        confirmPassGroup.style.display = 'none';
        confirmPassInput.required = false;
        btnText.textContent = 'התחבר עכשיו';
        forgotPassword.style.display = 'inline';
    } else {
        tabRegister.classList.add('active');
        tabLogin.classList.remove('active');
        confirmPassGroup.style.display = 'block';
        confirmPassInput.required = true;
        btnText.textContent = 'צור חשבון';
        forgotPassword.style.display = 'none';
    }
}

tabLogin.addEventListener('click', () => setMode(true));
tabRegister.addEventListener('click', () => setMode(false));

// Translate Firebase Errors to Hebrew
function translateError(error) {
    const msg = error.code || error.message;
    if (msg.includes('auth/invalid-credential') || msg.includes('auth/user-not-found') || msg.includes('auth/wrong-password')) {
        return "שם משתמש או סיסמה שגויים";
    }
    if (msg.includes('auth/email-already-in-use')) {
        return "כתובת הדוא״ל הזו כבר רשומה במערכת. נסה להתחבר";
    }
    if (msg.includes('auth/weak-password')) {
        return "הסיסמה חלשה מדי. אנא בחר סיסמה עם 6 תווים לפחות";
    }
    if (msg.includes('auth/invalid-email')) {
        return "כתובת הדוא״ל אינה תקינה";
    }
    if (msg.includes('auth/too-many-requests')) {
        return "יותר מדי ניסיונות התחברות. נסה שוב מאוחר יותר";
    }
    return "אירעה שגיאה. אנא נסה שנית";
}

// Form Submission
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
            showMessage("הסיסמאות אינן תואמות");
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

// Forgot Password
forgotPassword.addEventListener('click', async (e) => {
    e.preventDefault();
    const email = emailInput.value.trim();
    if (!email) {
        showMessage("אנא הכנס את כתובת הדוא״ל שלך למעלה כדי לאפס סיסמה");
        return;
    }

    try {
        await sendPasswordResetEmail(auth, email);
        showMessage("מייל לאיפוס סיסמה נשלח בהצלחה!", "success");
    } catch (error) {
        showMessage(translateError(error));
    }
});

// Logout
logoutBtn.addEventListener('click', () => {
    signOut(auth);
});

// Device Linking
linkBtn.addEventListener('click', async () => {
    const code = linkCodeInput.value.trim().toUpperCase();
    if (!code || code.length !== 6) {
        linkStatus.textContent = "אנא הכנס קוד בן 6 תווים";
        linkStatus.className = "link-status error";
        return;
    }

    const linkPassword = linkPasswordInput.value;
    if (!linkPassword) {
        linkStatus.textContent = "אנא הכנס את הסיסמה שלך";
        linkStatus.className = "link-status error";
        return;
    }

    const user = auth.currentUser;
    if (!user) {
        linkStatus.textContent = "אנא התחבר תחילה";
        linkStatus.className = "link-status error";
        return;
    }

    linkBtn.disabled = true;
    linkStatus.textContent = "מקשר מכשיר...";
    linkStatus.className = "link-status loading";

    try {
        const linkRef = doc(db, "device_links", code);
        const linkDoc = await getDoc(linkRef);

        if (!linkDoc.exists()) {
            linkStatus.textContent = "קוד לא נמצא. ודא שהקוד מוצג בטלוויזיה";
            linkStatus.className = "link-status error";
            linkBtn.disabled = false;
            return;
        }

        if (linkDoc.data().status !== "pending") {
            linkStatus.textContent = "קוד זה כבר נוצל. נסה ליצור קוד חדש בטלוויזיה";
            linkStatus.className = "link-status error";
            linkBtn.disabled = false;
            return;
        }

        // Write credentials to complete the link
        await updateDoc(linkRef, {
            status: "completed",
            email: user.email,
            password: linkPassword,
            linked_at: Date.now()
        });

        linkStatus.textContent = "✓ המכשיר חובר בהצלחה! הטלוויזיה תתחבר אוטומטית";
        linkStatus.className = "link-status success";
        linkCodeInput.value = '';
        linkPasswordInput.value = '';

        // Clear status after 5 seconds
        setTimeout(() => {
            linkStatus.textContent = "";
            linkBtn.disabled = false;
        }, 5000);

    } catch (error) {
        console.error("Link error:", error);
        linkStatus.textContent = "שגיאה בקישור: " + error.message;
        linkStatus.className = "link-status error";
        linkBtn.disabled = false;
    }
});

// Auth State Observer
onAuthStateChanged(auth, (user) => {
    if (user) {
        authCard.style.display = 'none';
        successCard.style.display = 'block';
        linkSection.style.display = 'block';
        userEmailDisplay.textContent = user.email;
    } else {
        authCard.style.display = 'block';
        successCard.style.display = 'none';
        linkSection.style.display = 'none';
        passwordInput.value = '';
        confirmPassInput.value = '';
    }
});
