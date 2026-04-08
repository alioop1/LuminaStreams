@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  PALETTE - MODERN DARK THEME
// ══════════════════════════════════════════════════════════════════
private val BG_DARK       = Color(0xFF070709)
private val CARD_IDLE     = Color(0xFF141419)
private val CARD_FOCUSED  = Color(0xFF282832)
private val TEXT_PRIMARY  = Color(0xFFFFFFFF)
private val TEXT_MUTED    = Color(0xFF8A8A93)
private val ACCENT_RED    = Color(0xFFE50914)
private val ACCENT_GOLD   = Color(0xFFE5C07B)
private val ACCENT_BLUE   = Color(0xFF4D90FE)
private val ACCENT_GREEN  = Color(0xFF43A047)

@Composable
fun tr(en: String, he: String): String {
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en
}

// ══════════════════════════════════════════════════════════════════
//  ROOT SCREEN: GRID DASHBOARD
// ══════════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
) {
    val backFR = remember { FocusRequester() }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { backFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BG_DARK)) {
        // Subtle background glow
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(ACCENT_BLUE.copy(alpha = 0.03f), Color.Transparent), radius = 1500f)
        ))

        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = onNavigateBack,
                    modifier = Modifier.size(48.dp).focusRequester(backFR),
                    colors   = IconButtonDefaults.colors(
                        containerColor        = CARD_IDLE,
                        focusedContainerColor = TEXT_PRIMARY,
                        contentColor          = TEXT_PRIMARY,
                        focusedContentColor   = BG_DARK
                    )
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(24.dp)) }

                Spacer(Modifier.width(20.dp))
                Text(
                    text = tr("Settings Dashboard", "לוח הגדרות מערכת"),
                    color = TEXT_PRIMARY,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            // The main Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), // 4 עמודות בכרטיסיות רגילות
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                // ── SECTION 1: ACCOUNT & REAL-DEBRID (FULL SPAN) ──
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                    SectionHeader(tr("REAL-DEBRID & NETWORK", "חיבור חשבונות ורשת"))
                }

                item(span = { GridItemSpan(maxLineSpan) }, contentType = "RdBanner") {
                    BuildRdBanner(state, viewModel, isLowTier)
                }

                // ── SECTION 2: VIDEO & AUDIO ──
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(tr("VIDEO & AUDIO", "וידאו וסאונד"))
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("HW Acceleration", "האצת חומרה"),
                        desc  = tr("Use hardware decoders", "שימוש במפענחי המכשיר"),
                        icon  = Icons.Default.Memory,
                        isChecked = state.hwAcceleration,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) }
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("Auto Frame Rate", "התאמת קצב (AFR)"),
                        desc  = tr("Match TV to content", "מונע ריצודים בוידאו"),
                        icon  = Icons.Default.Monitor,
                        isChecked = state.autoFrameRate,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("Audio Passthrough", "העברת שמע גולמי"),
                        desc  = tr("Send raw audio to AV", "העברה ישירה לרסיבר"),
                        icon  = Icons.Default.SurroundSound,
                        isChecked = state.audioPassthrough,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
                }

                item(contentType = "Cycle") {
                    val langs = listOf("original", "he", "en")
                    val currentIdx = langs.indexOf(state.preferredAudioLang).takeIf { it >= 0 } ?: 0
                    CycleTile(
                        title = tr("Preferred Audio", "שפת שמע מועדפת"),
                        currentValue = when(state.preferredAudioLang) {
                            "original" -> tr("🎬 Original", "🎬 שפת מקור")
                            "he" -> "🇮🇱 Hebrew"
                            "en" -> "🇺🇸 English"
                            else -> state.preferredAudioLang
                        },
                        icon = Icons.Default.RecordVoiceOver,
                        isLowTier = isLowTier
                    ) {
                        val next = langs[(currentIdx + 1) % langs.size]
                        viewModel.updateStringSetting("preferred_audio_lang", next)
                    }
                }

                // ── SECTION 3: SUBTITLES & UI ──
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(tr("SUBTITLES & INTERFACE", "כתוביות וממשק"))
                }

                item(contentType = "Cycle") {
                    val subs = listOf("Hebrew", "English", "None")
                    val currentIdx = subs.indexOf(state.defaultSubtitles).takeIf { it >= 0 } ?: 0
                    CycleTile(
                        title = tr("Default Subtitles", "שפת כתוביות"),
                        currentValue = when(state.defaultSubtitles) {
                            "Hebrew" -> tr("Hebrew", "עברית")
                            "English" -> tr("English", "אנגלית")
                            else -> tr("Off", "ללא")
                        },
                        icon = Icons.Default.Subtitles,
                        isLowTier = isLowTier
                    ) {
                        val next = subs[(currentIdx + 1) % subs.size]
                        viewModel.updateStringSetting("def_subs", next)
                    }
                }

                item(contentType = "Cycle") {
                    val sizes = listOf("small", "medium", "large", "xlarge")
                    val currentIdx = sizes.indexOf(state.subtitleFontScale).takeIf { it >= 0 } ?: 1
                    CycleTile(
                        title = tr("Subtitle Size", "גודל כתוביות"),
                        currentValue = when(state.subtitleFontScale) {
                            "small" -> tr("Small", "קטן")
                            "medium" -> tr("Medium", "בינוני")
                            "large" -> tr("Large", "גדול")
                            "xlarge" -> tr("Extra Large", "ענק")
                            else -> "Medium"
                        },
                        icon = Icons.Default.FormatSize,
                        isLowTier = isLowTier
                    ) {
                        val next = sizes[(currentIdx + 1) % sizes.size]
                        viewModel.updateStringSetting("subtitle_font_scale", next)
                    }
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("Yellow Subtitles", "כתוביות צהובות"),
                        desc  = tr("Classic cinema style", "צבע צהוב קולנועי"),
                        icon  = Icons.Default.FormatColorText,
                        isChecked = state.yellowSubtitles,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
                }

                item(contentType = "Cycle") {
                    CycleTile(
                        title = tr("App Language", "שפת האפליקציה"),
                        currentValue = if (state.appLanguage == "he") "עברית" else "English",
                        icon = Icons.Default.Language,
                        isLowTier = isLowTier
                    ) {
                        viewModel.updateStringSetting("app_lang", if (state.appLanguage == "he") "en" else "he")
                    }
                }

                // ── SECTION 4: PERFORMANCE & DATA ──
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(tr("PERFORMANCE & STORAGE", "ביצועים, אחסון ונתונים"))
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("Lite UI Mode", "ממשק קל (Lite)"),
                        desc  = tr("Disable heavy animations", "מכבה אנימציות כבדות"),
                        icon  = Icons.Default.Speed,
                        isChecked = state.liteUiMode,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
                }

                item(contentType = "Toggle") {
                    ToggleTile(
                        title = tr("Pre-allocate Buffer", "שריון זיכרון לנגן"),
                        desc  = tr("Fixes 4K buffering", "שומר RAM לניגון רציף"),
                        icon  = Icons.Default.Storage,
                        isChecked = state.preAllocateBuffer,
                        isLowTier = isLowTier
                    ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
                }

                item(contentType = "Action") {
                    ActionTile(
                        title = tr("Clear Image Cache", "ניקוי מטמון תמונות"),
                        desc  = tr("Used: ", "בשימוש: ") + state.cacheSizeStr,
                        icon  = Icons.Default.DeleteSweep,
                        isLowTier = isLowTier
                    ) { viewModel.clearCache() }
                }

                item(contentType = "Action") {
                    ActionTile(
                        title = tr("Clear Search History", "מחק היסטוריית חיפוש"),
                        desc  = if (state.searchHistoryStatus == "Clear") tr("Remove saved searches", "מנקה חיפושים קודמים") else state.searchHistoryStatus,
                        icon  = Icons.Default.ManageSearch,
                        isLowTier = isLowTier
                    ) { if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory() }
                }

                // ── SECTION 5: SYSTEM INFO (FULL SPAN) ──
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Info") {
                    SystemInfoCard(state)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  COMPONENTS
// ══════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TEXT_MUTED,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleTile(
    title: String, desc: String, icon: ImageVector,
    isChecked: Boolean, isLowTier: Boolean, onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val scale = if (isLowTier) 1f else 1.05f
    val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)
    val bgColor = if (focused) CARD_FOCUSED else if (isChecked) ACCENT_BLUE.copy(0.12f) else CARD_IDLE

    Surface(
        onClick  = onToggle,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = bgColor, focusedContainerColor = CARD_FOCUSED),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
        border   = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(2.dp, TEXT_PRIMARY), shape = RoundedCornerShape(16.dp))
        ),
        modifier = Modifier.aspectRatio(1.8f).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = if (focused || isChecked) TEXT_PRIMARY else TEXT_MUTED, modifier = Modifier.size(28.dp))
                // Toggle Switch UI
                Box(
                    modifier = Modifier.width(36.dp).height(18.dp).clip(RoundedCornerShape(50))
                        .background(if (isChecked) ACCENT_BLUE else Color.White.copy(0.1f))
                        .padding(2.dp),
                    contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(Modifier.size(14.dp).background(Color.White, CircleShape))
                }
            }
            Column {
                Text(title, color = TEXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(desc, color = TEXT_MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun CycleTile(
    title: String, currentValue: String, icon: ImageVector,
    isLowTier: Boolean, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (isLowTier) 1f else 1.05f
    val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
        border   = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(2.dp, TEXT_PRIMARY), shape = RoundedCornerShape(16.dp))
        ),
        modifier = Modifier.aspectRatio(1.8f).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (focused) TEXT_PRIMARY else TEXT_MUTED, modifier = Modifier.size(28.dp))
            Column {
                Text(title, color = TEXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(currentValue, color = ACCENT_GREEN, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Default.Sync, null, tint = ACCENT_GREEN, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    title: String, desc: String, icon: ImageVector,
    isLowTier: Boolean, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (isLowTier) 1f else 1.05f
    val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
        border   = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(2.dp, TEXT_PRIMARY), shape = RoundedCornerShape(16.dp))
        ),
        modifier = Modifier.aspectRatio(1.8f).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (focused) TEXT_PRIMARY else TEXT_MUTED, modifier = Modifier.size(28.dp))
            Column {
                Text(title, color = TEXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(desc, color = TEXT_MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

// ── REAL-DEBRID BANNERS ──

@Composable
private fun BuildRdBanner(state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    if (state.rdToken.isNotEmpty()) {
        // Connected
        var focused by remember { mutableStateOf(false) }
        val scale = if (isLowTier) 1f else 1.02f
        val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)

        Surface(
            onClick  = { viewModel.logoutRealDebrid() },
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
            glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
            border   = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, ACCENT_RED), shape = RoundedCornerShape(16.dp))
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        ) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(ACCENT_GOLD.copy(0.1f), Color.Transparent))).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = ACCENT_GOLD, modifier = Modifier.size(24.dp))
                        Text(tr("REAL-DEBRID ACTIVE", "מנוי פרימיום מחובר"), color = ACCENT_GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Token:", "אסימון:") + " ${state.rdToken.take(5)}••••••••${state.rdToken.takeLast(4)}", color = TEXT_MUTED, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.rdSpeedTestResult != null) {
                        Text(state.rdSpeedTestResult, color = TEXT_PRIMARY, fontSize = 14.sp, modifier = Modifier.padding(end = 24.dp))
                    } else {
                        Button(onClick = { viewModel.runSpeedTest() }, colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f))) {
                            Text(if (state.rdSpeedTesting) tr("Testing...", "בודק...") else tr("Speed Test", "בדיקת מהירות"), color = TEXT_PRIMARY)
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(tr("Click to Disconnect", "לחץ לניתוק"), color = if(focused) ACCENT_RED else TEXT_MUTED, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Not Connected
        when (val auth = state.authStatus) {
            is SettingsAuthStatus.WaitingForUser -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD_IDLE).padding(28.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Action Required", "נדרש אישור"), color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text(tr("Visit:", "היכנס לכתובת:"), color = TEXT_MUTED, fontSize = 16.sp)
                        Text(auth.url, color = TEXT_PRIMARY, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(16.dp))
                        Text(tr("Enter code:", "והזן את הקוד:"), color = TEXT_MUTED, fontSize = 16.sp)
                        Text(auth.userCode, color = ACCENT_GOLD, fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
                    }
                }
            }
            is SettingsAuthStatus.Loading -> {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(CARD_IDLE), Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = ACCENT_GOLD)
                }
            }
            else -> {
                // Connect Button
                var focused by remember { mutableStateOf(false) }
                val scale = if (isLowTier) 1f else 1.02f
                val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)

                Surface(
                    onClick  = { viewModel.startRealDebridAuth() },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
                    glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
                    border   = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(border = BorderStroke(2.dp, TEXT_PRIMARY), shape = RoundedCornerShape(16.dp))
                    ),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                ) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(Color.White.copy(0.1f), CircleShape), Alignment.Center) {
                            Icon(Icons.Default.VpnKey, null, tint = ACCENT_GOLD, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Connect Real-Debrid", "חבר חשבון Real-Debrid"), color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(tr("Unlock 4K streaming and zero-buffering playback.", "מאפשר צפייה ב-4K ללא טעינות מטורנטים."), color = TEXT_MUTED, fontSize = 14.sp)
                        }
                        Box(Modifier.background(if (focused) TEXT_PRIMARY else Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(tr("Link Account", "קשר חשבון"), color = if(focused) BG_DARK else TEXT_PRIMARY, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── SYSTEM INFO ──

@Composable
private fun SystemInfoCard(state: SettingsState) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD_IDLE).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tr("App Version", "גרסת אפליקציה"), color = TEXT_MUTED, fontSize = 12.sp)
                Text(state.appVersion, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Device Capability", "חומרת המכשיר"), color = TEXT_MUTED, fontSize = 12.sp)
                Text(state.deviceTier.ifEmpty { "Detecting..." }, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Engines", "מנועים"), color = TEXT_MUTED, fontSize = 12.sp)
                Text("Torrentio / TMDB / Ktuvit", color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}