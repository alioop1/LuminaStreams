@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_FUTURE_ERROR")
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  ULTRA PREMIUM PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG_DARK       = Color(0xFF040405)
private val PANEL_BG      = Color(0xFF0A0A0C)
private val CARD_IDLE     = Color(0xFF121215)
private val CARD_FOCUSED  = Color(0xFF1E1E24)
private val BORDER_IDLE   = Color(0xFF202025)
private val BORDER_FOCUS  = Color(0xFFFFFFFF)
private val TEXT_PRIMARY  = Color(0xFFFFFFFF)
private val TEXT_MUTED    = Color(0xFF8A8A93)
private val ACCENT_RED    = Color(0xFFE50914)
private val ACCENT_GOLD   = Color(0xFFE5C07B) // Premium Real-Debrid Gold
private val ACCENT_BLUE   = Color(0xFF4D90FE)

private data class CatMeta(val cat: SettingsCategory, val icon: ImageVector, val desc: String)
private val CATS = listOf(
    CatMeta(SettingsCategory.ACCOUNT,  Icons.Default.VpnKey, "Real-Debrid & Network"),
    CatMeta(SettingsCategory.PLAYBACK, Icons.Default.HighQuality, "HDR, Audio & Player"),
    CatMeta(SettingsCategory.PRIVACY,  Icons.Default.Style, "Subtitles & Interface"),
    CatMeta(SettingsCategory.SYSTEM,   Icons.Default.Memory, "Performance & Display")
)

// ══════════════════════════════════════════════════════════════════
//  ROOT DASHBOARD SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    var isRailFocused by remember { mutableStateOf(false) }
    val railFR = remember { FocusRequester() }
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
            animationSpec = tween(800)
        )
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glowColor, Color.Transparent), radius = 1500f)))

        Row(Modifier.fillMaxSize()) {
            // ── SMART COLLAPSING RAIL ──
            val railWidth by animateDpAsState(if (isRailFocused) 280.dp else 80.dp, tween(300, easing = FastOutSlowInEasing), label = "railWidth")

            Box(
                Modifier.width(railWidth).fillMaxHeight().background(PANEL_BG)
                    .border(1.dp, BORDER_IDLE, RoundedCornerShape(0.dp))
                    .onFocusChanged { isRailFocused = it.hasFocus }
                    .focusProperties { right = contentFR }
            ) {
                Column(Modifier.fillMaxSize().padding(vertical = 32.dp), horizontalAlignment = if (isRailFocused) Alignment.Start else Alignment.CenterHorizontally) {

                    LuminaLogo(isExpanded = isRailFocused)
                    Spacer(Modifier.height(48.dp))

                    Box(Modifier.padding(horizontal = if (isRailFocused) 24.dp else 0.dp)) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(48.dp).focusRequester(railFR),
                            colors = IconButtonDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = TEXT_PRIMARY, contentColor = TEXT_MUTED, focusedContentColor = BG_DARK)
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(24.dp)) }
                    }

                    Spacer(Modifier.height(48.dp))

                    CATS.forEach { meta ->
                        SmartRailItem(
                            meta = meta,
                            isSelected = state.selectedCategory == meta.cat,
                            isExpanded = isRailFocused,
                            onClick = { viewModel.setCategory(meta.cat) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ── CONTENT DASHBOARD ──
            Box(Modifier.weight(1f).fillMaxHeight().focusRequester(contentFR).focusProperties { left = railFR }) {
                AnimatedContent(
                    targetState = state.selectedCategory,
                    transitionSpec = {
                        fadeIn(tween(400, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutLinearInEasing))
                    },
                    label = "content",
                    modifier = Modifier.fillMaxSize()
                ) { cat ->
                    key(cat.name) {
                        val meta = CATS.first { it.cat == cat }
                        DashboardContent(cat, meta, state, viewModel, onToggleLanguage)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartRailItem(meta: CatMeta, isSelected: Boolean, isExpanded: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) TEXT_PRIMARY else if (isSelected) CARD_IDLE else Color.Transparent
    val tint = if (focused) BG_DARK else if (isSelected) TEXT_PRIMARY else TEXT_MUTED

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f), // ביטול קפיצה
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (isExpanded) 16.dp else 12.dp)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.fillMaxSize().background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.spacedBy(16.dp) else Arrangement.Center
        ) {
            Icon(meta.icon, null, Modifier.size(24.dp), tint = tint)
            if (isExpanded) {
                Text(meta.cat.titleEn, color = tint, fontSize = 16.sp, fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DashboardContent(
    cat: SettingsCategory, meta: CatMeta,
    state: SettingsState, viewModel: SettingsViewModel, onToggleLang: () -> Unit
) {
    LazyColumn(
        // הוגדל הרווח בתחתית ל-120dp כדי שכרטיס ה-RD לא ייחתך
        contentPadding = PaddingValues(start = 56.dp, end = 80.dp, top = 48.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), // צמצום קל של הרווחים בין הכרטיסיות
        modifier = Modifier.fillMaxSize().focusProperties {
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
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
            SettingsCategory.SYSTEM   -> buildSystemDashboard(state, viewModel, onToggleLang)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  1. ACCOUNT & NETWORK DASHBOARD
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.buildAccountDashboard(state: SettingsState, viewModel: SettingsViewModel) {
    item {
        if (state.rdToken.isNotEmpty()) {
            RdConnectedPremiumCard(state, viewModel)
        } else {
            when (val a = state.authStatus) {
                is SettingsAuthStatus.WaitingForUser -> RdAuthCard(a)
                is SettingsAuthStatus.Loading        -> RdLoadingCard()
                is SettingsAuthStatus.Error          -> { RdErrorCard(a.message); Spacer(Modifier.height(16.dp)); RdConnectCard(viewModel) }
                else -> RdConnectCard(viewModel)
            }
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
    item { SectionTitle("DIAGNOSTICS") }
    item {
        DashboardActionCard(
            title = "RD Server Speed Test",
            desc = "Ping Real-Debrid servers to diagnose buffering issues",
            icon = Icons.Default.NetworkCheck,
            value = "Run Test"
        ) { /* הפעלת בדיקת מהירות */ }
    }
}

@Composable
private fun RdConnectedPremiumCard(state: SettingsState, viewModel: SettingsViewModel) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { viewModel.logoutRealDebrid() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border = ClickableSurfaceDefaults.border(Border(BorderStroke(1.dp, BORDER_IDLE)), Border(BorderStroke(2.dp, BORDER_FOCUS))),
        scale = ClickableSurfaceDefaults.scale(1f), // ביטול קפיצה
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CARD_IDLE, Color(0xFF1F1A0F)))).padding(24.dp)) {
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
                    Icon(Icons.Default.LinkOff, null, tint = if (focused) ACCENT_RED else TEXT_MUTED, modifier = Modifier.size(18.dp))
                    Text("Press OK to Disconnect Device", color = if (focused) ACCENT_RED else TEXT_MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RdConnectCard(viewModel: SettingsViewModel) {
    DashboardActionCard(
        title = "Unlock Premium Streaming",
        desc = "Connect Real-Debrid for 4K zero-buffering playback from cached torrents.",
        icon = Icons.Default.VpnKey,
        value = "Link Account",
        highlight = ACCENT_GOLD
    ) { viewModel.startRealDebridAuth() }
}

@Composable
private fun RdAuthCard(auth: SettingsAuthStatus.WaitingForUser) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CARD_IDLE).border(1.dp, ACCENT_GOLD, RoundedCornerShape(20.dp)).padding(28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Device Authorization Required", color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Text("1. Go to this URL on your phone or PC", color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.url, color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Black)

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(1.dp).background(BORDER_IDLE))
            Spacer(Modifier.height(24.dp))

            Text("2. Enter this Code", color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.userCode, color = ACCENT_GOLD, fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
        }
    }
}

@Composable
private fun RdLoadingCard() {
    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(CARD_IDLE).border(1.dp, BORDER_IDLE, RoundedCornerShape(20.dp)), Alignment.Center) {
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
//  2. PLAYBACK & HOME THEATER DASHBOARD
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.buildPlaybackDashboard(state: SettingsState, viewModel: SettingsViewModel) {
    item { SectionTitle("HOME THEATER AUDIO") }
    item {
        DashboardToggleCard(
            title = "Audio Passthrough (Bitstream)",
            desc = "Send raw audio (Dolby Atmos, TrueHD, DTS-HD MA) directly to your AV Receiver or Soundbar.",
            icon = Icons.Default.SurroundSound,
            isChecked = state.audioPassthrough
        ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("CINEMATIC VIDEO") }
    item {
        DashboardToggleCard(
            title = "Force Dolby Vision / HDR10+",
            desc = "Prioritize high dynamic range formats when scraping Torrentio sources.",
            icon = Icons.Default.HdrOn,
            isChecked = state.forceHdr
        ) { viewModel.updateToggleSetting("force_hdr", !state.forceHdr) }
    }
    item {
        DashboardToggleCard(
            title = "Auto Frame Rate (AFR)",
            desc = "Automatically switch TV refresh rate (e.g., to 24Hz) to match movie frame rate and eliminate judder.",
            icon = Icons.Default.Monitor,
            isChecked = state.autoFrameRate
        ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("PLAYER BEHAVIOR") }
    item {
        DashboardToggleCard(
            title = "Auto-Play Next Episode",
            desc = "Seamlessly start the next episode during TV show binges.",
            icon = Icons.Default.SkipNext,
            isChecked = state.autoPlayNext
        ) { viewModel.updateToggleSetting("auto_play", !state.autoPlayNext) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  3. PERSONALIZATION & SUBTITLES DASHBOARD
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.buildPersonalizationDashboard(state: SettingsState, viewModel: SettingsViewModel) {
    item { SectionTitle("SUBTITLE PREFERENCES") }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardRadioCard("Hebrew", "עברית", state.defaultSubtitles == "Hebrew", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "Hebrew") }
            DashboardRadioCard("English", "English", state.defaultSubtitles == "English", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "English") }
            DashboardRadioCard("None", "Off", state.defaultSubtitles == "None", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "None") }
        }
    }
    item {
        DashboardToggleCard(
            title = "Yellow Subtitles",
            desc = "Use classic cinematic yellow text instead of white for better readability on bright scenes.",
            icon = Icons.Default.FormatColorText,
            isChecked = state.yellowSubtitles
        ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("CONTENT FILTERING") }
    item {
        DashboardToggleCard(
            title = "Safe Search",
            desc = "Filter out adult and explicit titles from TMDB discovery and search results.",
            icon = Icons.Default.FamilyRestroom,
            isChecked = state.safeSearch
        ) { viewModel.updateToggleSetting("safe_search", !state.safeSearch) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  4. SYSTEM, OLED & PERFORMANCE DASHBOARD
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.buildSystemDashboard(state: SettingsState, viewModel: SettingsViewModel, onToggleLang: () -> Unit) {
    item { SectionTitle("OLED PROTECTION") }
    item {
        DashboardToggleCard(
            title = "Dim UI on Inactivity",
            desc = "Reduce screen brightness by 80% after 2 minutes of inactivity to prevent OLED burn-in.",
            icon = Icons.Default.Brightness4,
            isChecked = state.dimUi
        ) { viewModel.updateToggleSetting("dim_ui", !state.dimUi) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("PERFORMANCE (2GB RAM OPTIMIZED)") }
    item {
        DashboardToggleCard(
            title = "Lite UI Mode",
            desc = "Disable heavy background blurs and crossfades to ensure 60FPS UI navigation.",
            icon = Icons.Default.Speed,
            isChecked = state.liteUiMode
        ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
    }
    item {
        DashboardToggleCard(
            title = "Pre-allocate Video Buffer",
            desc = "Reserve RAM specifically for ExoPlayer before playback to prevent 'Out of Memory' crashes.",
            icon = Icons.Default.Memory,
            isChecked = state.preAllocateBuffer
        ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
    }

    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle("STORAGE") }
    item {
        DashboardActionCard(
            title = "Clear Image Cache",
            desc = "Free up device storage. Currently using: ${state.cacheSizeStr}",
            icon = Icons.Default.Storage,
            value = "Clear Now"
        ) { viewModel.clearCache() }
    }
}

// ══════════════════════════════════════════════════════════════════
//  REUSABLE DASHBOARD COMPONENTS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(title: String) {
    Text(title, color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp, bottom = 0.dp))
}

@Composable
private fun DashboardToggleCard(title: String, desc: String, icon: ImageVector, isChecked: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border = ClickableSurfaceDefaults.border(Border(BorderStroke(1.dp, BORDER_IDLE)), Border(BorderStroke(2.dp, BORDER_FOCUS))),
        scale = ClickableSurfaceDefaults.scale(1f), // ביטול קפיצה
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused } // הקטנת גובה
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(if (focused) TEXT_PRIMARY else BORDER_IDLE, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = if (focused) BG_DARK else TEXT_PRIMARY, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = TEXT_MUTED, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            // Custom Switch
            val thumbPos by animateFloatAsState(if (isChecked) 1f else 0f, tween(250))
            val trackColor by animateColorAsState(if (isChecked) ACCENT_BLUE else BORDER_IDLE)
            Box(Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(trackColor)) {
                Box(Modifier.padding(3.dp).size(18.dp).offset(x = (thumbPos * 24).dp).background(if (isChecked) BG_DARK else TEXT_MUTED, CircleShape))
            }
        }
    }
}

@Composable
private fun DashboardActionCard(title: String, desc: String, icon: ImageVector, value: String, highlight: Color? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border = ClickableSurfaceDefaults.border(Border(BorderStroke(1.dp, BORDER_IDLE)), Border(BorderStroke(2.dp, highlight ?: BORDER_FOCUS))),
        scale = ClickableSurfaceDefaults.scale(1f), // ביטול קפיצה
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused } // הקטנת גובה
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(if (focused) highlight ?: TEXT_PRIMARY else BORDER_IDLE, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = if (focused) BG_DARK else highlight ?: TEXT_PRIMARY, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = TEXT_MUTED, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(16.dp))
                Box(Modifier.background(if (focused) highlight ?: TEXT_PRIMARY else BORDER_IDLE, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(value, color = if (focused) BG_DARK else TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardRadioCard(label: String, sub: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) BORDER_IDLE else CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border = ClickableSurfaceDefaults.border(Border(BorderStroke(1.dp, BORDER_IDLE)), Border(BorderStroke(2.dp, BORDER_FOCUS))),
        scale = ClickableSurfaceDefaults.scale(1f), // ביטול קפיצה
        modifier = modifier.height(76.dp).onFocusChanged { focused = it.isFocused } // הקטנת גובה
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium)
                if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = ACCENT_BLUE)
            }
            Text(sub, color = TEXT_MUTED, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LuminaLogo(isExpanded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = if (isExpanded) 24.dp else 0.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(ACCENT_RED), Alignment.Center) {
            Text("L", color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        if (isExpanded) {
            Column {
                Text("LUMINA",  color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 14.sp)
                Text("STREAMS", color = ACCENT_RED,   fontSize = 8.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 10.sp)
            }
        }
    }
}