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
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Material Icons Extended — ALL icons (Tv, LiveTv, VideoLibrary, etc.)
    implementation("androidx.compose.material:material-icons-extended")

    // Retrofit & Network (TMDB API)
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Compose TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose Standard (for Nav and UI)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Media3 (ExoPlayer) - Core, UI, and Decoders
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)

    // Coil for Memory-Optimized Images
    implementation(libs.coil.compose)

    // Palette for Color-Thief UI
    implementation(libs.androidx.palette.ktx)

    // Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // Network & Scraping
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.jsoup)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
