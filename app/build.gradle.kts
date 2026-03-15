plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.luminastreams.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luminastreams.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-Lumina"

        // ── TV: multi-core rendering hint ──────────────────────────────────
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
    }

    // ── Release: R8 full-mode + resource shrinking ─────────────────────────
    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    // ── Compose compiler — performance flags ───────────────────────────────
    // ✅ FIXED: stabilityConfigurationFiles (plural) — new API in Compose compiler plugin
    composeCompiler {
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("stability_config.conf")
        )
    }

    // ── Packaging: strip unused native libs to reduce APK / install time ──
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "**/*.kotlin_builtins"
            )
        }
        // Keep only armeabi-v7a + arm64-v8a — all Android TVs are ARM
        jniLibs {
            pickFirsts += setOf("**/*.so")
        }
    }

    // ── ABI splits: ship separate APKs per CPU arch for lean installs ──────
    splits {
        abi {
            isEnable = true
            reset()
            // All Android TV hardware is ARM; x86/x86_64 only needed for emulators
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true   // keep universal as fallback
        }
    }

    // ── Lint ───────────────────────────────────────────────────────────────
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // ── AndroidX core ──────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)          // required for TrackSelectionDialogBuilder
    implementation(libs.material)

    // ── Compose TV ────────────────────────────────────────────────────────
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Compose Standard ──────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    // ✅ FIXED: string literals instead of catalog aliases (cascade-safe)
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ── Material Icons Extended (Tv, LiveTv, VideoLibrary …) ──────────────
    implementation("androidx.compose.material:material-icons-extended")

    // ── Media3 / ExoPlayer — FULL suite for TV 4K playback ────────────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)          // HLS streams
    implementation("androidx.media3:media3-exoplayer-dash:1.5.0")    // DASH (Real-Debrid links)
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0") // OkHttp transport layer
    implementation("androidx.media3:media3-common:1.5.0")            // subtitle / track types
    implementation("androidx.media3:media3-decoder:1.5.0")           // software decoders fallback

    // ── Coil — memory-optimised image loading ──────────────────────────────
    // coil-okhttp does NOT exist as a separate artifact.
    // Coil detects OkHttp on the classpath automatically (via okhttp3 below).
    implementation(libs.coil.compose)

    // ── Palette — backdrop color theming ──────────────────────────────────
    implementation(libs.androidx.palette.ktx)

    // ── Security — EncryptedSharedPreferences for RD token ────────────────
    implementation(libs.androidx.security.crypto)

    // ── Networking ────────────────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.jsoup)

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Tests ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}