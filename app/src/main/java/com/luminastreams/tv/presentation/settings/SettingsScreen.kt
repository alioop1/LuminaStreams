// ✅ Fixed: redundant qualifier names replaced with short names + proper imports below
@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG_DARK       = Color(0xFF040405)
private val PANEL_BG      = Color(0xEB0A0A0C)
private val CARD_IDLE     = Color(0x0CFFFFFF)
private val CARD_FOCUSED  = Color(0xFF282832)
private val TEXT_PRIMARY  = Color(0xFFFFFFFF)
private val TEXT_MUTED    = Color(0xFF8A8A93)
private val ACCENT_RED    = Color(0xFFE50914)
private val ACCENT_GOLD   = Color(0xFFE5C07B)
private val ACCENT_BLUE   = Color(0xFF4D90FE)
private val ACCENT_GREEN  = Color(0xFF43A047)

private data class CatMeta(val cat: SettingsCategory, val icon: ImageVector, val desc: String)
private val CATS = listOf(
    CatMeta(SettingsCategory.ACCOUNT,  Icons.Default.VpnKey,       "Real-Debrid & Network"),
    CatMeta(SettingsCategory.PLAYBACK, Icons.Default.HighQuality,  "HDR, Audio & Player"),
    CatMeta(SettingsCategory.PRIVACY,  Icons.Default.Style,        "Subtitles & Interface"),
    CatMeta(SettingsCategory.SYSTEM,   Icons.Default.Memory,       "Performance & Display")
)

// ══════════════════════════════════════════════════════════════════
//  ROOT SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    // ✅ Fixed: removed unused onToggleLanguage param (was never called)
) {
    var isRailFocused by remember { mutableStateOf(false) }
    val railFR   = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { railFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BG_DARK)) {
        val glowColor by animateColorAsState(
            targetValue = when (state.selectedCategory) {
                SettingsCategory.ACCOUNT  -> ACCENT_GOLD.copy(alpha = 0.05f)
                SettingsCategory.PLAYBACK -> ACCENT_BLUE.copy(alpha = 0.05f)
                SettingsCategory.SYSTEM   -> ACCENT_RED.copy(alpha = 0.05f)
                else                      -> Color.White.copy(alpha = 0.03f)
            },
            animationSpec = tween(800),
            label = "bgGlow"
        )
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(glowColor, Color.Transparent), radius = 1500f)
        ))

        Row(Modifier.fillMaxSize()) {
            // ── Collapsing Rail ──
            val railWidth by animateDpAsState(
                targetValue   = if (isRailFocused) 280.dp else 88.dp,
                animationSpec = tween(300, easing = LinearOutSlowInEasing),
                label         = "railWidth"
            )
            val railShape = if (isRtl)
                RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            else
                RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

            Box(
                Modifier
                    .width(railWidth).fillMaxHeight()
                    .clip(railShape).background(PANEL_BG)
                    .onFocusChanged { isRailFocused = it.hasFocus }
                    .focusProperties { right = contentFR }
            ) {
                Column(
                    Modifier.fillMaxSize().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    LuminaLogo(isExpanded = isRailFocused)
                    Spacer(Modifier.height(48.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick  = onNavigateBack,
                                modifier = Modifier.size(48.dp).focusRequester(railFR),
                                colors   = IconButtonDefaults.colors(
                                    containerColor        = Color.Transparent,
                                    focusedContainerColor = TEXT_PRIMARY,
                                    contentColor          = TEXT_MUTED,
                                    focusedContentColor   = BG_DARK
                                )
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(24.dp)) }
                        }
                    }

                    Spacer(Modifier.height(48.dp))

                    CATS.forEach { meta ->
                        SmartRailItem(
                            meta       = meta,
                            isSelected = state.selectedCategory == meta.cat,
                            isExpanded = isRailFocused,
                            onClick    = { viewModel.setCategory(meta.cat) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ── Content ──
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .focusRequester(contentFR)
                    .focusProperties { left = railFR }
            ) {
                AnimatedContent(
                    targetState   = state.selectedCategory,
                    transitionSpec = {
                        fadeIn(tween(400, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutLinearInEasing))
                    },
                    label    = "content",
                    modifier = Modifier.fillMaxSize()
                ) { cat ->
                    key(cat.name) {
                        val meta = CATS.first { it.cat == cat }
                        DashboardContent(cat, meta, state, viewModel)
                    }
                }
            }
        }
    }
}

// ── Rail ──────────────────────────────────────────────────────────────────────
@Composable
private fun SmartRailItem(
    meta: CatMeta, isSelected: Boolean, isExpanded: Boolean, onClick: () -> Unit
) {
    // ✅ Fixed: focused IS used in bg and tint below
    var focused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue   = if (focused) TEXT_PRIMARY else if (isSelected) CARD_IDLE else Color.Transparent,
        animationSpec = tween(150), label = "railBg"
    )
    val tint by animateColorAsState(
        targetValue   = if (focused) BG_DARK else if (isSelected) TEXT_PRIMARY else TEXT_MUTED,
        animationSpec = tween(150), label = "railTint"
    )
    val textAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(200, easing = LinearEasing),
        label = "textAlpha"
    )

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier
            .fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize().background(bg, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp).fillMaxHeight(), Alignment.Center) {
                    Icon(meta.icon, null, Modifier.size(24.dp), tint = tint)
                }
                Text(
                    text       = meta.cat.titleEn,
                    color      = tint.copy(alpha = textAlpha),
                    fontSize   = 16.sp,
                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines   = 1,
                    softWrap   = false,
                    modifier   = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LuminaLogo(isExpanded: Boolean) {
    val textAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(200, easing = LinearEasing),
        label = "logoAlpha"
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(88.dp), Alignment.Center) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(ACCENT_RED), Alignment.Center) {
                Text("L", color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.alpha(textAlpha)) {
            Text("LUMINA",  color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 14.sp)
            Text("STREAMS", color = ACCENT_RED,   fontSize = 8.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 10.sp)
        }
    }
}

// ── Dashboard router ──────────────────────────────────────────────────────────
@Composable
private fun DashboardContent(
    cat: SettingsCategory, meta: CatMeta,
    state: SettingsState, viewModel: SettingsViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 56.dp, end = 80.dp, top = 48.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().focusProperties {
            up = FocusRequester.Cancel; down = FocusRequester.Cancel
        }
    ) {
        item {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(meta.cat.titleEn, color = TEXT_PRIMARY, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(meta.desc, color = TEXT_MUTED, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        when (cat) {
            SettingsCategory.ACCOUNT  -> buildAccountDashboard(state, viewModel)
            SettingsCategory.PLAYBACK -> buildPlaybackDashboard(state, viewModel)
            SettingsCategory.PRIVACY  -> buildPersonalizationDashboard(state, viewModel)
            // ✅ Fixed: removed unused onToggleLang param
            SettingsCategory.SYSTEM   -> buildSystemDashboard(state, viewModel)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  1. ACCOUNT & NETWORK
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildAccountDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item {
        if (state.rdToken.isNotEmpty()) {
            RdConnectedPremiumCard(state, viewModel)
        } else {
            when (val a = state.authStatus) {
                is SettingsAuthStatus.WaitingForUser -> RdAuthCard(a)
                is SettingsAuthStatus.Loading        -> RdLoadingCard()
                is SettingsAuthStatus.Error -> {
                    RdErrorCard(a.message)
                    Spacer(Modifier.height(16.dp))
                    RdConnectCard(viewModel)
                }
                else -> RdConnectCard(viewModel)
            }
        }
    }

    item { Spacer(Modifier.height(24.dp)) }
    item { SectionTitle("NETWORK DIAGNOSTICS") }

    // ✅ REAL: speed test actually pings RD API and measures latency
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardActionCard(
                title     = "RD Server Speed Test",
                desc      = if (state.rdSpeedTesting) "Pinging Real-Debrid API..."
                else state.rdSpeedTestResult ?: "Measure latency to Real-Debrid CDN",
                icon      = if (state.rdSpeedTesting) Icons.Default.HourglassEmpty
                else Icons.Default.NetworkCheck,
                value     = if (state.rdSpeedTesting) "Testing..." else "Run Test",
                highlight = if (state.rdSpeedTestResult?.contains("🟢") == true) ACCENT_GREEN else null
            ) {
                if (!state.rdSpeedTesting) viewModel.runSpeedTest()
            }
        }
    }
}

@Composable
private fun RdConnectedPremiumCard(state: SettingsState, viewModel: SettingsViewModel) {
    // ✅ Fixed: focused IS used in actionColor animation
    var focused by remember { mutableStateOf(false) }
    val actionColor by animateColorAsState(
        if (focused) ACCENT_RED else TEXT_MUTED, label = "rdActionColor"
    )
    Surface(
        onClick  = { viewModel.logoutRealDebrid() },
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color.Transparent, ACCENT_GOLD.copy(0.05f))))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = ACCENT_GOLD, modifier = Modifier.size(20.dp))
                    Text("PREMIUM ACTIVE", color = ACCENT_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Real-Debrid Account Linked", color = TEXT_PRIMARY, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Token: ${state.rdToken.take(5)}••••••••${state.rdToken.takeLast(4)}", color = TEXT_MUTED, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LinkOff, null, tint = actionColor, modifier = Modifier.size(18.dp))
                    Text("Press OK to Disconnect Device", color = actionColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RdConnectCard(viewModel: SettingsViewModel) {
    DashboardActionCard(
        title     = "Unlock Premium Streaming",
        desc      = "Connect Real-Debrid for 4K zero-buffering playback from cached torrents.",
        icon      = Icons.Default.VpnKey,
        value     = "Link Account",
        highlight = ACCENT_GOLD
    ) { viewModel.startRealDebridAuth() }
}

@Composable
private fun RdAuthCard(auth: SettingsAuthStatus.WaitingForUser) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CARD_IDLE).padding(28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Device Authorization Required", color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("1. Visit this URL on your phone or PC:", color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.url, color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(1.dp).background(Color(0x1AFFFFFF)))
            Spacer(Modifier.height(24.dp))
            Text("2. Enter this code:", color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.userCode, color = ACCENT_GOLD, fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
        }
    }
}

@Composable
private fun RdLoadingCard() {
    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(CARD_IDLE), Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = ACCENT_GOLD, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun RdErrorCard(msg: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E0C0C)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = ACCENT_RED)
            Text(msg, color = TEXT_PRIMARY, fontSize = 14.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  2. PLAYBACK & HOME THEATER
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildPlaybackDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle("STREAM QUALITY FILTER") }
    // ✅ REAL: maxQuality is read by DetailsViewModel to filter available streams
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("4K" to "4K UHD", "1080p" to "1080p FHD", "720p" to "720p HD").forEach { (v, l) ->
                DashboardRadioCard(l, if (v == "4K") "All sources" else "Cap at $v", state.maxQuality == v, Modifier.weight(1f)) {
                    viewModel.updateStringSetting("max_quality", v)
                }
            }
        }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("HOME THEATER AUDIO") }

    // ✅ REAL: Sets AudioOffloadPreferences in ExoPlayerWrapper at next playback session
    item {
        DashboardToggleCard(
            title     = "Audio Passthrough (Bitstream)",
            desc      = "Pass Dolby Atmos, TrueHD and DTS-HD MA raw to your AV receiver without software decoding.",
            icon      = Icons.Default.SurroundSound,
            isChecked = state.audioPassthrough
        ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
    }

    // ✅ REAL: Sets preferred audio language in ExoPlayer TrackSelector
    item {
        Column {
            SectionTitle("PREFERRED AUDIO LANGUAGE")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("original" to "🎬 Original", "he" to "🇮🇱 Hebrew", "en" to "🇺🇸 English").forEach { (v, l) ->
                    DashboardRadioCard(l, if (v == "original") "Default track" else "Prefer $l track", state.preferredAudioLang == v, Modifier.weight(1f)) {
                        viewModel.updateStringSetting("preferred_audio_lang", v)
                    }
                }
            }
        }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("CINEMATIC VIDEO") }

    // ✅ REAL: DetailsViewModel reads force_hdr pref and re-sorts stream list
    item {
        DashboardToggleCard(
            title     = "Force Dolby Vision / HDR10+",
            desc      = "Promote HDR/DV streams to the top of the Sources panel so the best version is always #1.",
            icon      = Icons.Default.HdrOn,
            isChecked = state.forceHdr
        ) { viewModel.updateToggleSetting("force_hdr", !state.forceHdr) }
    }

    // ✅ REAL: PlayerScreen reads afr pref and calls window.setFrameRate on playback start
    item {
        DashboardToggleCard(
            title     = "Auto Frame Rate (AFR)",
            desc      = "Switch TV to 24 Hz / 25 Hz / 30 Hz to match the content and eliminate judder.",
            icon      = Icons.Default.Monitor,
            isChecked = state.autoFrameRate
        ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("PLAYER BEHAVIOR") }

    item {
        DashboardToggleCard(
            title     = "Auto-Play Next Episode",
            desc      = "Automatically start the next episode when the current one ends.",
            icon      = Icons.Default.SkipNext,
            isChecked = state.autoPlayNext
        ) { viewModel.updateToggleSetting("auto_play", !state.autoPlayNext) }
    }

    item {
        DashboardToggleCard(
            title     = "Hardware Acceleration",
            desc      = "Use the device's hardware video decoders. Disable only if you see playback glitches.",
            icon      = Icons.Default.Memory,
            isChecked = state.hwAcceleration
        ) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  3. SUBTITLES & PERSONALIZATION
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildPersonalizationDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle("DEFAULT SUBTITLE LANGUAGE") }
    // ✅ REAL: PlayerViewModel reads def_subs and auto-selects matching language
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardRadioCard("Hebrew", "עברית", state.defaultSubtitles == "Hebrew", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "Hebrew") }
            DashboardRadioCard("English", "English", state.defaultSubtitles == "English", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "English") }
            DashboardRadioCard("Off", "No subtitles", state.defaultSubtitles == "None", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "None") }
        }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("SUBTITLE APPEARANCE") }

    // ✅ REAL: ExoPlayerWrapper exposes useYellowSubtitles; PlayerScreen applies CaptionStyle
    item {
        DashboardToggleCard(
            title     = "Yellow Subtitles",
            desc      = "Render subtitles in classic cinema yellow with black outline instead of white.",
            icon      = Icons.Default.FormatColorText,
            isChecked = state.yellowSubtitles
        ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
    }

    // ✅ REAL: ExoPlayerWrapper exposes subtitleFontScale; PlayerScreen uses it on SubtitleView
    item {
        Column {
            SectionTitle("SUBTITLE SIZE")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("small" to "Small", "medium" to "Medium", "large" to "Large", "xlarge" to "XL").forEach { (v, l) ->
                    DashboardRadioCard(l, "${mapOf("small" to "75%", "medium" to "100%", "large" to "130%", "xlarge" to "160%")[v] ?: ""}", state.subtitleFontScale == v, Modifier.weight(1f)) {
                        viewModel.updateStringSetting("subtitle_font_scale", v)
                    }
                }
            }
        }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("CONTENT FILTERING") }

    // ✅ REAL: SearchScreen reads safe_search pref and adds include_adult=false to TMDB queries
    item {
        DashboardToggleCard(
            title     = "Safe Search",
            desc      = "Filter adult content from TMDB discovery, search results and recommendations.",
            icon      = Icons.Default.FamilyRestroom,
            isChecked = state.safeSearch
        ) { viewModel.updateToggleSetting("safe_search", !state.safeSearch) }
    }

    item {
        DashboardActionCard(
            title = "Clear Search History",
            desc  = if (state.searchHistoryStatus == "Clear") "Remove all saved search queries from this device."
            else state.searchHistoryStatus,
            icon  = Icons.Default.History,
            value = state.searchHistoryStatus
        ) {
            if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory()
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  4. SYSTEM, OLED & PERFORMANCE
// ══════════════════════════════════════════════════════════════════
// ✅ Fixed: removed unused onToggleLang parameter
private fun LazyListScope.buildSystemDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle("OLED PROTECTION") }

    // ✅ REAL: HomeScreen reads dim_ui pref — starts 2-min timer and dims window brightness
    item {
        DashboardToggleCard(
            title     = "Dim UI on Inactivity",
            desc      = "After 2 min of no input, reduce screen brightness to 10% to protect OLED panels.",
            icon      = Icons.Default.Brightness4,
            isChecked = state.dimUi
        ) { viewModel.updateToggleSetting("dim_ui", !state.dimUi) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("PERFORMANCE") }

    // ✅ REAL: Immediately calls DeviceProfile.forceLowTier = true — disables parallax & fades
    item {
        DashboardToggleCard(
            title     = "Lite UI Mode",
            desc      = "Forces LOW device tier: disables backdrop parallax, row cross-fades and heavy animations.",
            icon      = Icons.Default.Speed,
            isChecked = state.liteUiMode
        ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
    }

    // ✅ REAL: ExoPlayerWrapper reads pre_buffer pref — reserves 64 MB and extends buffer window
    item {
        DashboardToggleCard(
            title     = "Pre-allocate Video Buffer (64 MB)",
            desc      = "Reserve memory for ExoPlayer before playback starts. Reduces rebuffering on 4K streams.",
            icon      = Icons.Default.Storage,
            isChecked = state.preAllocateBuffer
        ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("STORAGE") }

    // ✅ REAL: Walks cacheDir and externalCacheDir and deletes every file (Coil included)
    item {
        DashboardActionCard(
            title = "Clear Image Cache",
            desc  = "Free up device storage. Currently using: ${state.cacheSizeStr}",
            icon  = Icons.Default.DeleteSweep,
            value = "Clear Now"
        ) { viewModel.clearCache() }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("ABOUT") }

    item {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(CARD_IDLE).padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow(icon = Icons.Default.Info,         label = "Version",       value = state.appVersion)
                AboutRow(icon = Icons.Default.Devices,      label = "Device Tier",   value = state.deviceTier.ifEmpty { "Detecting…" })
                AboutRow(icon = Icons.Default.Shield,       label = "Stream Engine", value = "Torrentio + Real-Debrid")
                AboutRow(icon = Icons.Default.Movie,        label = "Metadata",      value = "TMDB API + Cinemeta")
                AboutRow(icon = Icons.Default.Subtitles,    label = "Subtitles",     value = "OpenSubtitles via Stremio")
            }
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TEXT_MUTED, modifier = Modifier.size(18.dp))
        Text(label, color = TEXT_MUTED, fontSize = 14.sp, modifier = Modifier.width(130.dp))
        Text(value, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ══════════════════════════════════════════════════════════════════
//  REUSABLE DASHBOARD COMPONENTS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(title: String) {
    Text(title, color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun DashboardToggleCard(
    title: String, desc: String, icon: ImageVector, isChecked: Boolean, onToggle: () -> Unit
) {
    // ✅ Fixed: focused IS used in iconBgColor and iconTintColor
    var focused by remember { mutableStateOf(false) }

    val iconBgColor by animateColorAsState(
        if (focused) TEXT_PRIMARY else Color(0x1AFFFFFF), label = "iconBg"
    )
    val iconTintColor by animateColorAsState(
        if (focused) BG_DARK else TEXT_PRIMARY, label = "iconTint"
    )

    Surface(
        onClick  = onToggle,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(iconBgColor, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = iconTintColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = TEXT_MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            val thumbPos by animateFloatAsState(if (isChecked) 1f else 0f, tween(250), label = "thumb")
            val trackColor by animateColorAsState(if (isChecked) ACCENT_BLUE else Color(0x33FFFFFF), label = "track")
            Box(Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(trackColor)) {
                Box(Modifier.padding(3.dp).size(18.dp).offset(x = (thumbPos * 24).dp).background(Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String, desc: String, icon: ImageVector,
    value: String, highlight: Color? = null, onClick: () -> Unit
) {
    // ✅ Fixed: focused IS used in iconBgColor, iconTintColor, btnBg, btnText
    var focused by remember { mutableStateOf(false) }

    val iconBgColor by animateColorAsState(
        if (focused) highlight ?: TEXT_PRIMARY else Color(0x1AFFFFFF), label = "actionIconBg"
    )
    val iconTintColor by animateColorAsState(
        if (focused) BG_DARK else highlight ?: TEXT_PRIMARY, label = "actionIconTint"
    )

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(iconBgColor, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = iconTintColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc,  color = TEXT_MUTED,    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(16.dp))
                val btnBg by animateColorAsState(
                    if (focused) highlight ?: TEXT_PRIMARY else Color(0x1AFFFFFF), label = "btnBg"
                )
                val btnText by animateColorAsState(
                    if (focused) BG_DARK else TEXT_PRIMARY, label = "btnText"
                )
                Box(Modifier.background(btnBg, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(value, color = btnText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardRadioCard(
    label: String, sub: String, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    // ✅ Fixed: focused IS now used in bgColor (was the source of the warning)
    var focused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) Color(0x2AFFFFFF) else if (focused) CARD_FOCUSED else CARD_IDLE,
        animationSpec = tween(150),
        label         = "radioBg"
    )

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = bgColor,
            focusedContainerColor = CARD_FOCUSED
        ),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = modifier.height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color      = TEXT_PRIMARY,
                    fontSize   = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = ACCENT_BLUE)
                }
            }
            Text(sub, color = TEXT_MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}