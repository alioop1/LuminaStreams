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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
//  PALETTE — Ultra Premium TV Design
// ══════════════════════════════════════════════════════════════════
private val BG          = Color(0xFF000000)
private val RAIL_BG     = Color(0xFF0B0B0C)
private val CARD_IDLE   = Color(0xFF141414)
private val WHITE       = Color(0xFFFFFFFF)
private val BLACK       = Color(0xFF000000)
private val DIM         = Color(0xB3FFFFFF)
private val DIM2        = Color(0x80FFFFFF)
private val BORDER      = Color(0xFF262626)
private val RED         = Color(0xFFE50914)
private val PREMIUM     = Color(0xFFD4AF37) // Subtle Gold for Real-Debrid

private data class CatMeta(val cat: SettingsCategory, val icon: ImageVector)
private val CATS = listOf(
    CatMeta(SettingsCategory.ACCOUNT,  Icons.Default.AccountCircle),
    CatMeta(SettingsCategory.PLAYBACK, Icons.Default.PlayCircle),
    CatMeta(SettingsCategory.PRIVACY,  Icons.Default.Shield),
    CatMeta(SettingsCategory.SYSTEM,   Icons.Default.Tune),
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
    onToggleLanguage: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { backFocusRequester.requestFocus() }
    }

    Row(Modifier.fillMaxSize().background(BG)) {
        // ── LEFT RAIL (Menu) ─────────────────────────────────────────────
        Box(
            Modifier.width(280.dp).fillMaxHeight()
                .background(RAIL_BG)
                .border(1.dp, BORDER, RoundedCornerShape(0.dp))
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 32.dp)) {

                // Back Button
                Box(Modifier.padding(horizontal = 24.dp)) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = WHITE,
                            contentColor = DIM,
                            focusedContentColor = BLACK
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.height(48.dp).fillMaxWidth().focusRequester(backFocusRequester)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp))
                            Text("Back to Home", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                CATS.forEach { meta ->
                    RailItem(
                        meta       = meta,
                        isSelected = state.selectedCategory == meta.cat,
                        onClick    = { viewModel.setCategory(meta.cat) }
                    )
                }

                Spacer(Modifier.weight(1f))

                // Branding
                Row(
                    Modifier.padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(24.dp).background(RED, RoundedCornerShape(6.dp)), Alignment.Center) {
                        Text("L", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("LUMINA STREAMS", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("Version 1.0.0", color = DIM2, fontSize = 9.sp)
                    }
                }
            }
        }

        // ── RIGHT CONTENT PANE ──────────────────────────────────────────
        Crossfade(
            targetState = state.selectedCategory,
            animationSpec = tween(250),
            label = "content",
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) { cat ->
            val meta = CATS.first { it.cat == cat }
            ContentPane(cat, meta, state, viewModel, onToggleLanguage)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  RAIL ITEM
// ══════════════════════════════════════════════════════════════════
@Composable
private fun RailItem(meta: CatMeta, isSelected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    val bgColor = when {
        focused -> WHITE
        isSelected -> Color(0xFF1A1A1A)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> BLACK
        isSelected -> WHITE
        else -> DIM2
    }

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = WHITE
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.fillMaxSize().background(bgColor).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(meta.icon, null, Modifier.size(20.dp), tint = contentColor)
            Text(meta.cat.titleEn, color = contentColor, fontSize = 15.sp, fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium)

            Spacer(Modifier.weight(1f))
            if (isSelected && !focused) {
                Box(Modifier.width(3.dp).height(16.dp).background(WHITE, CircleShape))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CONTENT PANE (LazyColumn for perfect D-Pad scrolling)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ContentPane(
    cat: SettingsCategory, meta: CatMeta,
    state: SettingsState, viewModel: SettingsViewModel, onToggleLang: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(meta.icon, null, Modifier.size(32.dp), tint = WHITE)
                Text(meta.cat.titleEn, color = WHITE, fontSize = 32.sp, fontWeight = FontWeight.Black)
            }
        }

        when (cat) {
            SettingsCategory.ACCOUNT  -> accountItems(state, viewModel)
            SettingsCategory.PLAYBACK -> playbackItems(state, viewModel)
            SettingsCategory.PRIVACY  -> privacyItems(state, viewModel)
            SettingsCategory.SYSTEM   -> systemItems(state, viewModel, onToggleLang)
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  ACCOUNT ITEMS
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.accountItems(state: SettingsState, viewModel: SettingsViewModel) {
    item {
        if (state.rdToken.isNotEmpty()) {
            RdConnectedCard(state, viewModel)
        } else {
            when (val a = state.authStatus) {
                is SettingsAuthStatus.WaitingForUser -> RdAuthCard(a)
                is SettingsAuthStatus.Loading        -> RdLoadingCard()
                is SettingsAuthStatus.Error          -> {
                    RdErrorCard(a.message)
                    Spacer(Modifier.height(16.dp))
                    RdConnectCard(viewModel)
                }
                else -> RdConnectCard(viewModel)
            }
        }
    }
}

@Composable
private fun RdConnectedCard(state: SettingsState, viewModel: SettingsViewModel) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CARD_IDLE).border(1.dp, BORDER, RoundedCornerShape(16.dp)).padding(32.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp), tint = PREMIUM)
                Text("REAL-DEBRID PREMIUM", color = PREMIUM, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("High-Speed Streaming Active", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Token: ${state.rdToken.take(6)}••••••••${state.rdToken.takeLast(4)}", color = DIM2, fontSize = 14.sp, fontFamily = FontFamily.Monospace)

            Spacer(Modifier.height(32.dp))
            ActionRow("Disconnect Account", "Remove this device from your Real-Debrid account", Icons.Default.LinkOff, "", danger = true) { viewModel.logoutRealDebrid() }
        }
    }
}

@Composable
private fun RdConnectCard(viewModel: SettingsViewModel) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CARD_IDLE).border(1.dp, BORDER, RoundedCornerShape(16.dp)).padding(32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(
                Modifier.size(80.dp).background(Color(0xFF1A1A1A), CircleShape).border(1.dp, BORDER, CircleShape),
                Alignment.Center
            ) { Icon(Icons.Default.Speed, null, Modifier.size(36.dp), tint = WHITE) }

            Column(Modifier.weight(1f)) {
                Text("Unlock Premium Speeds", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Connect your Real-Debrid account for instant 4K buffering-free playback from cached torrents.", color = DIM2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))

                ActionRow("Link Real-Debrid Account", "Generates a code to link your device", Icons.Default.VpnKey, "", danger = false) { viewModel.startRealDebridAuth() }
            }
        }
    }
}

@Composable
private fun RdAuthCard(auth: SettingsAuthStatus.WaitingForUser) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CARD_IDLE).border(1.dp, PREMIUM.copy(0.5f), RoundedCornerShape(16.dp)).padding(36.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Link Your Device", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("1. Visit this link on your phone or PC", color = DIM, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(auth.url, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Box(Modifier.width(1.dp).height(60.dp).background(BORDER))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("2. Enter this code", color = DIM, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(auth.userCode, color = PREMIUM, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                }
            }
        }
    }
}

@Composable
private fun RdLoadingCard() {
    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(CARD_IDLE).border(1.dp, BORDER, RoundedCornerShape(16.dp)), Alignment.Center) {
        Text("Communicating with Real-Debrid...", color = DIM, fontSize = 16.sp)
    }
}

@Composable
private fun RdErrorCard(message: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF260D0D)).border(1.dp, RED, RoundedCornerShape(16.dp)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = WHITE)
            Text(message, color = WHITE, fontSize = 15.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PLAYBACK ITEMS
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.playbackItems(state: SettingsState, viewModel: SettingsViewModel) {
    item { SectionLabel("VIDEO PREFERENCES") }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("4K" to "Ultra HD HDR", "1080p" to "Full HD", "720p" to "HD Ready").forEach { (q, sub) ->
                QualityCard(q, sub, state.maxResolution == q, Modifier.weight(1f)) { viewModel.updateStringSetting("max_res", q) }
            }
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
    item { SectionLabel("PLAYER SETTINGS") }
    item { ToggleRow("Auto-Play Next Episode", "Start the next episode automatically", Icons.Default.SkipNext, state.autoPlayNext) { viewModel.updateToggleSetting("auto_play", !state.autoPlayNext) } }
    item { ToggleRow("Hardware Acceleration", "Use device decoder for smooth playback", Icons.Default.Speed, state.hwAcceleration) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) } }
    item { Spacer(Modifier.height(16.dp)) }
    item { SectionLabel("SUBTITLES") }
    item { RadioRow("Hebrew", "עברית", Icons.Default.ClosedCaption, state.defaultSubtitles == "Hebrew") { viewModel.updateStringSetting("def_subs", "Hebrew") } }
    item { RadioRow("English", "English", Icons.Default.ClosedCaption, state.defaultSubtitles == "English") { viewModel.updateStringSetting("def_subs", "English") } }
    item { RadioRow("None", "Disabled", Icons.Default.Block, state.defaultSubtitles == "None") { viewModel.updateStringSetting("def_subs", "None") } }
}

@Composable
private fun QualityCard(label: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    val borderColor = when {
        focused -> WHITE
        selected -> WHITE.copy(0.5f)
        else -> BORDER
    }

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF262626) else CARD_IDLE,
            focusedContainerColor = Color(0xFF333333)
        ),
        border   = ClickableSurfaceDefaults.border(Border(border = BorderStroke(if (focused) 3.dp else 1.dp, borderColor), shape = RoundedCornerShape(12.dp))),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.height(100.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = WHITE)
            }
            Text(sub, color = DIM2, fontSize = 12.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PRIVACY ITEMS
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.privacyItems(state: SettingsState, viewModel: SettingsViewModel) {
    item { SectionLabel("CONTENT") }
    item { ToggleRow("Safe Search", "Hide explicit titles from search results", Icons.Default.FamilyRestroom, state.safeSearch) { viewModel.updateToggleSetting("safe_search", !state.safeSearch) } }
    item { Spacer(Modifier.height(16.dp)) }
    item { SectionLabel("HISTORY MANAGEMENT") }
    item { ToggleRow("Save Search History", "Keep recent queries for quick access", Icons.AutoMirrored.Filled.ManageSearch, state.saveSearchHistory) { viewModel.updateToggleSetting("save_history", !state.saveSearchHistory) } }
    item { ActionRow("Clear Search History", "Delete all previous searches", Icons.Default.DeleteSweep, state.searchHistoryStatus, danger = true) { viewModel.clearSearchHistory() } }
    item { ActionRow("Clear Watch History", "Remove titles from 'Continue Watching'", Icons.Default.History, state.watchHistoryStatus, danger = true) { viewModel.clearWatchHistory() } }
}

// ══════════════════════════════════════════════════════════════════
//  SYSTEM ITEMS
// ══════════════════════════════════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.systemItems(state: SettingsState, viewModel: SettingsViewModel, onToggleLang: () -> Unit) {
    item { SectionLabel("PREFERENCES") }
    item {
        ActionRow("App Language", "Change interface language", Icons.Default.Translate, if (state.isHebrew) "עברית" else "English", danger = false) {
            viewModel.updateToggleSetting("is_hebrew", !state.isHebrew); onToggleLang()
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
    item { SectionLabel("DEVICE STORAGE") }
    item { ActionRow("Clear Image Cache", "Free up space used by posters (${state.cacheSizeStr})", Icons.Default.Storage, "Clear", danger = false) { viewModel.clearCache() } }
    item { Spacer(Modifier.height(16.dp)) }
    item { SectionLabel("ABOUT") }
    item {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CARD_IDLE).border(1.dp, BORDER, RoundedCornerShape(12.dp)).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow("Version", "1.0.0-Lumina (Build 2026)")
                InfoRow("Media Engine", "ExoPlayer / Media3 1.5.0")
                InfoRow("Data Provider", "TMDB API v3")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DIM2, fontSize = 14.sp)
        Text(value, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ══════════════════════════════════════════════════════════════════
//  SHARED UI COMPONENTS (TV Optimized)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ToggleRow(label: String, sub: String, icon: ImageVector, enabled: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick  = onToggle,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = Color(0xFF262626)),
        border   = ClickableSurfaceDefaults.border(Border(border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) WHITE else BORDER), shape = RoundedCornerShape(12.dp))),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth().height(80.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (focused) WHITE else DIM)
            Column(Modifier.weight(1f)) {
                Text(label, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = DIM2, fontSize = 13.sp)
            }

            // Switch
            val thumbPos by animateFloatAsState(if (enabled) 1f else 0f, tween(200), label = "tp")
            val trackBg  by animateColorAsState(if (enabled) WHITE else Color(0xFF333333), tween(200), label = "tb")
            Box(Modifier.width(48.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(trackBg)) {
                Box(Modifier.padding(3.dp).size(20.dp).offset(x = (thumbPos * 22).dp).background(if (enabled) BLACK else DIM, CircleShape))
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, sub: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = if (selected) Color(0xFF1A1A1A) else CARD_IDLE, focusedContainerColor = Color(0xFF262626)),
        border   = ClickableSurfaceDefaults.border(Border(border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) WHITE else BORDER), shape = RoundedCornerShape(12.dp))),        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth().height(80.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (focused) WHITE else DIM)
            Column(Modifier.weight(1f)) {
                Text(label, color = WHITE, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Text(sub, color = DIM2, fontSize = 13.sp)
            }
            Box(Modifier.size(24.dp).border(2.dp, if (selected || focused) WHITE else DIM, CircleShape), Alignment.Center) {
                if (selected) Box(Modifier.size(12.dp).background(WHITE, CircleShape))
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, sub: String, icon: ImageVector, value: String, danger: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val textColor = if (danger) RED else WHITE

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor = if (danger && focused) Color(0xFF260D0D) else CARD_IDLE,
            focusedContainerColor = if (danger) Color(0xFF3D1515) else Color(0xFF262626)
        ),
        border   = ClickableSurfaceDefaults.border(Border(border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) (if(danger) RED else WHITE) else BORDER), shape = RoundedCornerShape(12.dp))),        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth().height(80.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (focused) textColor else DIM)
            Column(Modifier.weight(1f)) {
                Text(label, color = if (focused) textColor else WHITE, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (sub.isNotBlank()) Text(sub, color = DIM2, fontSize = 13.sp)
            }
            if (value.isNotBlank()) {
                Text(value, color = if (value.contains("ing") || value.contains("ed!")) PREMIUM else textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = DIM2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
}

@Composable
fun PremiumSettingItem(title: String, value: String, onClick: () -> Unit) {
    ActionRow(title, "", Icons.Default.Tune, value, danger = false, onClick = onClick)
}