# ════════════════════════════════════════════════════════════════════════
# Lumina Streams TV — ProGuard / R8 rules
# Optimised for: Android TV, ExoPlayer 4K, Retrofit, Coil, Compose
# ════════════════════════════════════════════════════════════════════════

# ── Debugging ─────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { **; }
-dontwarn kotlin.**

# ── Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── Compose ───────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class androidx.tv.** { *; }
-dontwarn androidx.compose.**

# ── Retrofit + Gson ───────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Keep all Gson DTOs (data classes serialised from TMDB/Torrentio/RD APIs)
-keep class com.luminastreams.tv.data.api.** { *; }
-keep class com.luminastreams.tv.domain.usecase.DeviceCodeResponse { *; }
-keep class com.luminastreams.tv.domain.usecase.CredentialsResponse { *; }
-keep class com.luminastreams.tv.domain.usecase.TokenResponse { *; }
-keep class com.luminastreams.tv.presentation.details.TorrentioResponse { *; }
-keep class com.luminastreams.tv.presentation.details.TorrentioStream { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── ExoPlayer / Media3 ────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn androidx.media3.**
# Keep decoder extensions
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.datasource.** { *; }

# ── Coil ──────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── OkHttp ────────────────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.internal.** { *; }

# ── Jsoup ─────────────────────────────────────────────────────────────
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ── AndroidX Security (EncryptedSharedPreferences) ────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── AndroidX AppCompat (needed for TrackSelectionDialogBuilder) ───────
-keep class androidx.appcompat.** { *; }
-keep class androidx.appcompat.view.ContextThemeWrapper { *; }

# ── Palette ───────────────────────────────────────────────────────────
-keep class androidx.palette.** { *; }

# ── JSON (org.json — used in ViewModels) ──────────────────────────────
-keep class org.json.** { *; }

# ── Navigation Compose ────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ── App domain models (must survive R8 for state restoration) ─────────
-keep class com.luminastreams.tv.domain.model.** { *; }
-keep class com.luminastreams.tv.presentation.**.** { *; }

# ── Leanback / TV Launcher ────────────────────────────────────────────
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**
