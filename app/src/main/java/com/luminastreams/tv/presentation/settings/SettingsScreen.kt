@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay

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
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val backFR = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    val sidebarFR = remember { FocusRequester() }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { sidebarFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BG_DARK)) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(ACCENT_BLUE.copy(alpha = 0.03f), Color.Transparent), radius = 1500f)
        ))

        Row(Modifier.fillMaxSize()) {

            // ── LEFT SIDEBAR ──
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .padding(vertical = 40.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 32.dp),
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

                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = tr("Settings", "הגדרות"),
                        color = TEXT_PRIMARY,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                val categories = SettingsCategory.values().toList()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier.focusGroup().focusRestorer()
                ) {
                    items(categories) { cat ->
                        val isSelected = state.selectedCategory == cat

                        // Map each category to a title and standard Material Icon
                        val (title, icon) = when (cat) {
                            SettingsCategory.ACCOUNT -> Pair(tr("Account & Network", "חשבון ורשת"), Icons.Default.Person)
                            SettingsCategory.PLAYBACK -> Pair(tr("Video & Audio", "וידאו וסאונד"), Icons.Default.PlayCircle)
                            SettingsCategory.SUBTITLES -> Pair(tr("Subtitles & UI", "כתוביות וממשק"), Icons.Default.Subtitles)
                            SettingsCategory.PERFORMANCE -> Pair(tr("Performance", "ביצועים ואחסון"), Icons.Default.Speed)
                            SettingsCategory.SYSTEM_INFO -> Pair(tr("System Info", "אודות המערכת"), Icons.Default.Info)
                        }

                        val surfaceMod = if (isSelected) Modifier.focusRequester(sidebarFR) else Modifier

                        Surface(
                            onClick = {
                                viewModel.setCategory(cat)
                                runCatching { contentFR.requestFocus() }
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = TEXT_PRIMARY.copy(alpha = 0.15f),
                                contentColor = if (isSelected) TEXT_PRIMARY else TEXT_MUTED,
                                focusedContentColor = TEXT_PRIMARY
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = surfaceMod
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) viewModel.setCategory(cat) }
                        ) {
                            // Row to hold the Icon and the Text
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSelected) TEXT_PRIMARY else TEXT_MUTED
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = title,
                                    fontSize = if (isSelected) 17.sp else 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color(0x11FFFFFF)))

            // ── RIGHT CONTENT AREA ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 32.dp, end = 64.dp, top = 40.dp, bottom = 40.dp)
                    .focusGroup()
                    .onPreviewKeyEvent { ev ->
                        if (ev.key == Key.Back && ev.type == KeyEventType.KeyDown) {
                            runCatching { sidebarFR.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
            ) {
                AnimatedContent(
                    targetState = state.selectedCategory,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                    label = "settings_crossfade"
                ) { category ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {

                        when (category) {
                            SettingsCategory.ACCOUNT -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(tr("REAL-DEBRID INTEGRATION", "חיבור לשרתי הפרימיום"))
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.focusRequester(contentFR)) {
                                        BuildRdBanner(state, viewModel, isLowTier)
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(Modifier.height(8.dp))
                                    SectionHeader(tr("SERVICE HEALTH", "סטטוס שרתים"))
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ServerHealthCard(state, viewModel, isLowTier)
                                }
                            }

                            SettingsCategory.PLAYBACK -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(tr("VIDEO DECODING", "פענוח וידאו"))
                                }
                                item {
                                    Box(Modifier.focusRequester(contentFR)) {
                                        ToggleTile(
                                            title = tr("HW Acceleration", "האצת חומרה"),
                                            desc  = tr("Use hardware decoders", "שימוש במפענחי המכשיר"),
                                            icon  = Icons.Default.Memory,
                                            isChecked = state.hwAcceleration,
                                            isLowTier = isLowTier
                                        ) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) }
                                    }
                                }
                                item {
                                    ToggleTile(
                                        title = tr("Auto Frame Rate", "התאמת קצב (AFR)"),
                                        desc  = tr("Match TV to content", "מונע ריצודים בוידאו"),
                                        icon  = Icons.Default.Monitor,
                                        isChecked = state.autoFrameRate,
                                        isLowTier = isLowTier
                                    ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(Modifier.height(8.dp))
                                    SectionHeader(tr("AUDIO SETTINGS", "הגדרות שמע"))
                                }
                                item {
                                    ToggleTile(
                                        title = tr("Audio Passthrough", "העברת שמע גולמי"),
                                        desc  = tr("Send raw audio to AV", "העברה ישירה לרסיבר"),
                                        icon  = Icons.Default.SurroundSound,
                                        isChecked = state.audioPassthrough,
                                        isLowTier = isLowTier
                                    ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
                                }
                                item {
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
                            }

                            SettingsCategory.SUBTITLES -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(tr("SUBTITLES PREFERENCES", "העדפות כתוביות"))
                                }
                                item {
                                    val subs = listOf("Hebrew", "English", "None")
                                    val currentIdx = subs.indexOf(state.defaultSubtitles).takeIf { it >= 0 } ?: 0
                                    Box(Modifier.focusRequester(contentFR)) {
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
                                }
                                item {
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
                                item {
                                    ToggleTile(
                                        title = tr("Yellow Subtitles", "כתוביות צהובות"),
                                        desc  = tr("Classic cinema style", "צבע צהוב קולנועי"),
                                        icon  = Icons.Default.FormatColorText,
                                        isChecked = state.yellowSubtitles,
                                        isLowTier = isLowTier
                                    ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
                                }
                                item {
                                    CycleTile(
                                        title = tr("App Language", "שפת האפליקציה"),
                                        currentValue = if (state.appLanguage == "he") "עברית" else "English",
                                        icon = Icons.Default.Language,
                                        isLowTier = isLowTier
                                    ) {
                                        viewModel.updateStringSetting("app_lang", if (state.appLanguage == "he") "en" else "he")
                                    }
                                }
                            }

                            SettingsCategory.PERFORMANCE -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(tr("PERFORMANCE & MEMORY", "ניהול זיכרון וביצועים"))
                                }
                                item {
                                    Box(Modifier.focusRequester(contentFR)) {
                                        ToggleTile(
                                            title = tr("Lite UI Mode", "ממשק קל (Lite)"),
                                            desc  = tr("Disable heavy animations", "מכבה אנימציות כבדות"),
                                            icon  = Icons.Default.Speed,
                                            isChecked = state.liteUiMode,
                                            isLowTier = isLowTier
                                        ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
                                    }
                                }
                                item {
                                    ToggleTile(
                                        title = tr("Pre-allocate Buffer", "שריון זיכרון לנגן"),
                                        desc  = tr("Fixes 4K buffering", "שומר RAM לניגון רציף"),
                                        icon  = Icons.Default.Storage,
                                        isChecked = state.preAllocateBuffer,
                                        isLowTier = isLowTier
                                    ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(Modifier.height(8.dp))
                                    SectionHeader(tr("STORAGE CLEARANCE", "ניקוי נתונים"))
                                }
                                item {
                                    ActionTile(
                                        title = tr("Clear Image Cache", "ניקוי מטמון תמונות"),
                                        desc  = tr("Used: ", "בשימוש: ") + state.cacheSizeStr,
                                        icon  = Icons.Default.DeleteSweep,
                                        isLowTier = isLowTier
                                    ) { viewModel.clearCache() }
                                }
                                item {
                                    ActionTile(
                                        title = tr("Clear Search History", "מחק היסטוריית חיפוש"),
                                        desc  = if (state.searchHistoryStatus == "Clear") tr("Remove saved searches", "מנקה חיפושים קודמים") else state.searchHistoryStatus,
                                        icon  = Icons.AutoMirrored.Filled.ManageSearch,
                                        isLowTier = isLowTier
                                    ) { if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory() }
                                }
                            }

                            SettingsCategory.SYSTEM_INFO -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(tr("ABOUT DEVICE", "אודות המערכת"))
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.focusRequester(contentFR)) {
                                        SystemInfoCard(state)
                                    }
                                }
                            }
                        }
                    }
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
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
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
        modifier = Modifier.aspectRatio(2.2f).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = if (focused || isChecked) TEXT_PRIMARY else TEXT_MUTED, modifier = Modifier.size(28.dp))
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
        modifier = Modifier.aspectRatio(2.2f).onFocusChanged { focused = it.isFocused }
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
        modifier = Modifier.aspectRatio(2.2f).onFocusChanged { focused = it.isFocused }
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

// ── REAL-DEBRID & SERVER STATUS BANNERS ──

@Composable
private fun BuildRdBanner(state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    if (state.rdToken.isNotEmpty()) {

        LaunchedEffect(Unit) {
            if (state.rdSpeedTestResult == null) {
                viewModel.checkRealDebridAccount()
            }
        }

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
                        Text(state.rdSpeedTestResult!!, color = TEXT_PRIMARY, fontSize = 14.sp, modifier = Modifier.padding(end = 24.dp))
                    } else {
                        Button(onClick = { viewModel.checkRealDebridAccount() }, colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f))) {
                            Text(if (state.rdSpeedTesting) tr("Contacting...", "בודק...") else tr("Check Status", "בדוק חיבור"), color = TEXT_PRIMARY)
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(tr("Click to Disconnect", "לחץ לניתוק"), color = if(focused) ACCENT_RED else TEXT_MUTED, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        when (val auth = state.authStatus) {

            is SettingsAuthStatus.WaitingForUser -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD_IDLE)) {
                    Row(Modifier.fillMaxWidth()) {

                        Box(Modifier.weight(1.1f).fillMaxHeight().background(CARD_FOCUSED).padding(32.dp)) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Box(Modifier.size(48.dp).background(Color.White.copy(0.1f), CircleShape), Alignment.Center) {
                                    Icon(Icons.Default.PhonelinkRing, null, tint = ACCENT_GOLD, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(tr("Device Linking", "קישור מכשיר"), color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(tr("Use your phone or computer to enter the code provided on the right. This will link your premium Real-Debrid account to this TV.", "השתמש בטלפון או במחשב והזן את הקוד המופיע במסך על מנת לחבר את חשבון הפרימיום שלך לטלוויזיה."), color = TEXT_MUTED, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }

                        Box(Modifier.weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(tr("1. VISIT WEBSITE", "1. כנס לכתובת"), color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(auth.url, color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(32.dp))
                                Text(tr("2. ENTER CODE", "2. הזן קוד"), color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(auth.userCode, color = ACCENT_GOLD, fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
                            }
                        }
                    }
                }
            }

            is SettingsAuthStatus.Loading -> {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(CARD_IDLE), Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = ACCENT_GOLD)
                }
            }

            else -> {
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

@Composable
private fun ServerHealthCard(state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (isLowTier) 1f else 1.02f
    val glow = if (isLowTier || !focused) Glow.None else Glow(Color.Black.copy(0.6f), 16.dp)

    Surface(
        onClick = { viewModel.checkServerStatuses() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        scale = ClickableSurfaceDefaults.scale(focusedScale = scale),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = glow),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(2.dp, TEXT_PRIMARY), shape = RoundedCornerShape(16.dp))
        ),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Real-Debrid API", "שרתי Real-Debrid"), color = TEXT_MUTED, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.rdServerStatus, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.width(2.dp).height(40.dp).background(Color(0x22FFFFFF)))

            Column(Modifier.weight(1f).padding(start = 32.dp)) {
                Text(tr("Torrentio Scraper", "שרתי Torrentio"), color = TEXT_MUTED, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.torrentioServerStatus, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.checkServerStatuses() },
                colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f)),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(if (state.isCheckingServers) tr("Checking...", "בודק...") else tr("Refresh", "רענן"), color = TEXT_PRIMARY)
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