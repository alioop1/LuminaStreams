plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace  = "com.luminastreams.tv"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.luminastreams.tv"
        minSdk          = 26
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0-Lumina"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            isDebuggable      = true
            isMinifyEnabled   = false
            isShrinkResources = false
        }
        getByName("release") {
            isDebuggable      = false
            isMinifyEnabled   = true
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

    // ✅ stabilityConfigurationFiles (plural) — correct API for Compose compiler plugin
    composeCompiler {
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("stability_config.conf")
        )
    }

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
    }

    splits {
        abi {
            isEnable       = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError       = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // ── AndroidX core ──────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)   // required by TrackSelectionDialogBuilder
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
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // ✅ FIXED: catalog alias instead of bare string (ensures version = 1.7.6)
    // was: implementation("androidx.compose.material:material-icons-extended")
    //      ↑ no version = Unresolved reference / version conflict
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Media3 / ExoPlayer — full TV 4K suite ─────────────────────────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)          // HLS streams
    implementation(libs.androidx.media3.exoplayer.dash)         // DASH (Real-Debrid links)
    implementation(libs.androidx.media3.datasource.okhttp)      // OkHttp transport layer
    implementation(libs.androidx.media3.common)                 // subtitle / track types
    implementation(libs.androidx.media3.decoder)                // software decoder fallback

    // ── Coil 2 image loading ──────────────────────────────────────────────
    // ✅ DO NOT upgrade to Coil 3 — entire codebase uses coil.* packages:
    //    LuminaApp.kt  → coil.ImageLoader, coil.memory.MemoryCache, coil.disk.DiskCache
    //    LocalStorage.kt → coil.imageLoader extension
    // Coil 3 moved all of this to coil3.* = compile errors throughout
    implementation(libs.coil.compose)

    // ── Palette / Security ─────────────────────────────────────────────────
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.security.crypto)

    // ── Networking ────────────────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.jsoup)
    implementation(libs.gson)

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Tests ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}