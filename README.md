# LuminaStreams 🌟

**Android TV Streaming Application** built with Kotlin, Jetpack Compose, and Firebase.

---

## 📋 Prerequisites

| Tool | Required Version | Status |
|------|-----------------|--------|
| **Android Studio** | Latest (Ladybug+) | ✅ Installed |
| **JDK** | 21+ (auto-provisioned by Gradle) | ✅ Auto-download |
| **Android SDK** | API 37 (compileSdk) | ✅ Installed |
| **Node.js** | 18+ | ✅ v24.11.1 |
| **Firebase CLI** | Latest | ✅ v15.18.0 |
| **Git** | Any | ✅ v2.45.1 |

---

## 🚀 Quick Start (Windows)

### Option 1: Automatic Setup (Recommended)
```powershell
# Double-click setup.bat or run:
.\setup.bat
```

### Option 2: PowerShell Script
```powershell
# Full setup + build
.\setup_windows.ps1

# Setup only, skip build
.\setup_windows.ps1 -SkipBuild

# Clean build
.\setup_windows.ps1 -CleanBuild

# Build & deploy to connected device
.\setup_windows.ps1 -DeployDebug
.\setup_windows.ps1 -DeployRelease
```

### Option 3: Manual Setup
```powershell
# 1. Set environment variables
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[System.Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "$env:LOCALAPPDATA\Android\Sdk", "User")

# 2. Build the project
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease

# 3. Install on device
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Launch the app
adb shell am start -n com.luminastreams.tv/.MainActivity
```

---

## 🏗️ Project Structure

```
LuminaStreams/
├── app/                          # Android TV Application
│   ├── src/main/
│   │   ├── java/com/luminastreams/tv/   # Kotlin source code
│   │   ├── res/                         # Resources (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml          # App manifest
│   ├── build.gradle.kts                 # App-level build config
│   ├── google-services.json             # Firebase config
│   ├── keystore.jks                     # Release signing key
│   ├── proguard-rules.pro               # R8/ProGuard rules
│   └── release/                         # Release APK output
├── web/                          # Firebase Hosting (Web)
│   ├── public/                          # Static web files
│   │   ├── index.html
│   │   ├── style.css
│   │   └── app.js
│   ├── firebase.json                    # Hosting config
│   ├── firestore.rules                  # Security rules
│   └── firestore.indexes.json           # Firestore indexes
├── gradle/
│   ├── libs.versions.toml               # Version catalog
│   ├── gradle-daemon-jvm.properties     # JDK 21 toolchain config
│   └── wrapper/                         # Gradle wrapper
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Project settings
├── gradle.properties             # Gradle JVM & project flags
├── local.properties              # SDK path (auto-generated)
├── setup_windows.ps1             # Windows setup script
└── setup.bat                     # Quick launcher
```

---

## 🔧 Tech Stack

### Android App
| Technology | Version | Purpose |
|-----------|---------|---------|
| **Kotlin** | 2.3.20 | Programming language |
| **Jetpack Compose** | 1.10.6 | Modern UI toolkit |
| **Material 3** | 1.4.0 | Design system |
| **AndroidX TV** | Foundation 1.0.0-rc01 | TV-specific components |
| **Media3 / ExoPlayer** | 1.10.0 | Video playback |
| **Room** | 2.8.4 | Local database |
| **Retrofit** | 3.0.0 | REST API client |
| **OkHttp** | 5.3.2 | HTTP client |
| **Coil** | 2.7.0 | Image loading |
| **Firebase Auth** | BOM 33.9.0 | Authentication |
| **Firebase Firestore** | BOM 33.9.0 | Cloud database |
| **ZXing** | 3.5.4 | QR code generation |

### Build System
| Tool | Version |
|------|---------|
| **Gradle** | 9.4.1 |
| **AGP** | 9.1.1 |
| **KSP** | 2.3.2 |
| **JDK Toolchain** | 21 |

---

## 📱 Building

### Debug Build
```powershell
.\gradlew.bat assembleDebug
# Output: app\build\outputs\apk\debug\app-debug.apk
```

### Release Build (Signed)
```powershell
.\gradlew.bat assembleRelease
# Output: app\build\outputs\apk\release\app-release.apk
# Also copied to: app\release\app-release.apk
```

### Clean Build
```powershell
.\gradlew.bat clean assembleDebug
```

---

## 📲 Device Deployment

### Connect to Android TV via ADB
```powershell
# Enable Developer Options on the TV:
# Settings → About → Build Number (click 7 times)
# Settings → Developer Options → USB Debugging → ON

# Connect via WiFi
adb connect <TV_IP_ADDRESS>:5555

# Verify connection
adb devices

# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch the app
adb shell am start -n com.luminastreams.tv/.MainActivity

# View logs
adb logcat -s "LuminaStreams"
```

---

## 🌐 Firebase / Web Deployment

### Login to Firebase
```powershell
cd web
firebase login
```

### Deploy Web App
```powershell
cd web
firebase deploy --only hosting
```

### Deploy Firestore Rules & Indexes
```powershell
cd web
firebase deploy --only firestore
```

### Deploy Everything
```powershell
cd web
firebase deploy
```

### Firebase Project
- **Project ID:** `luminastreams-1a474`
- **Hosting:** Firebase Hosting
- **Database:** Firestore (nam5 region)

---

## 🔐 Signing

The release build uses a keystore located at `app/keystore.jks`:

| Property | Value |
|----------|-------|
| **Store File** | `app/keystore.jks` |
| **Key Alias** | `lumina` |

> ⚠️ **Important:** Keep the keystore file secure. Loss of the keystore means you cannot update the app on the Play Store.

---

## 🛠️ Common Issues

### Build fails with "SDK not found"
```powershell
# Ensure local.properties has the correct SDK path:
# sdk.dir=C\:\\Users\\TechPC\\AppData\\Local\\Android\\Sdk
```

### JDK 21 toolchain error
Gradle automatically downloads JDK 21 via the Foojay Disco API. If this fails:
```powershell
# Check gradle/gradle-daemon-jvm.properties for toolchain URLs
# Or install JDK 21 manually from https://adoptium.net
```

### Firebase CLI not found
```powershell
npm install -g firebase-tools
```

### ADB connection issues
```powershell
adb kill-server
adb start-server
adb connect <IP>:5555
```

---

## 📊 Build Targets

| Build Type | minSdk | targetSdk | compileSdk | Minify | Shrink |
|-----------|--------|-----------|------------|--------|--------|
| **Debug** | 26 | 37 | 37 | ❌ | ❌ |
| **Release** | 26 | 37 | 37 | ✅ (R8) | ✅ |

---

## 📄 License

LuminaStreams © 2026 — All rights reserved.
