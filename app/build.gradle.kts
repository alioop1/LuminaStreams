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

    signingConfigs {
        create("release") {
            storeFile     = file("keystore.jks")
            storePassword = "lumina123"
            keyAlias      = "lumina"
            keyPassword   = "lumina123"
        }
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
            isProfileable     = true
            signingConfig     = signingConfigs.getByName("release")
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

    // ✅ FIX 3: opt-in גלובלי ל-UnstableApi — מסיר את כל שגיאות הקומפייל
    kotlinOptions {
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi")
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
            isEnable       = false  // APK אחד שעובד על כל הארכיטקטורות
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

    implementation(libs.androidx.compose.material.icons.extended)

    // ── Media3 / ExoPlayer — full TV 4K suite ─────────────────────────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.decoder)

    // ── Coil 2 image loading ──────────────────────────────────────────────
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
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // ── Tests ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.google.zxing:core:3.5.3")
}
