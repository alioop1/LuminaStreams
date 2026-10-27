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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
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
    val backFR    = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    val menuFR    = remember { FocusRequester() }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { menuFR.requestFocus() }
    }

    // ── COLORS ────────────────────────────────────────────────────────────
    val BG        = Color(0xFF0F0F0F)   // Near-black Netflix canvas
    val SIDEBAR   = Color(0xFF171717)   // Slightly lifted sidebar panel
    val CARD_BG   = Color(0xFF1C1C1E)   // Apple "grouped" card surface
    val GOLD      = Color(0xFFE2B258)   // LuminaStreams accent
    val MUTED     = Color(0xFF6E6E73)   // Apple secondary label
    val SEPARATOR = Color(0xFF2C2C2E)   // Apple separator

    Box(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxSize()) {

            // ══════════════════════════════════════════════════════════════
            //  SIDEBAR – Netflix-style with Apple spacing
            // ══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(SIDEBAR)
                    .padding(top = 48.dp, bottom = 32.dp)
                    .focusGroup()
                    .focusRestorer()
            ) {
                // Back + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)
                ) {
                    IconButton(
                        onClick  = onNavigateBack,
                        modifier = Modifier.size(40.dp).focusRequester(backFR),
                        colors   = IconButtonDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor          = MUTED,
                            focusedContentColor   = Color.Black
                        )
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text       = tr("Settings", "הגדרות"),
                        color      = Color.White,
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Separator
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(SEPARATOR))
                Spacer(Modifier.height(16.dp))

                // Category list
                SettingsCategory.values().forEach { cat ->
                    val isSelected = state.selectedCategory == cat
                    val label = when (cat) {
                        SettingsCategory.ACCOUNT      -> tr("Account",  "חשבון")
                        SettingsCategory.PLAYBACK     -> tr("Playback", "ניגון")
                        SettingsCategory.SUBTITLES    -> tr("Subtitles","כתוביות")
                        SettingsCategory.PERFORMANCE  -> tr("System",   "מערכת")
                        SettingsCategory.SYSTEM_INFO  -> tr("About",    "אודות")
                    }

                    val focusedScale = if (isLowTier) 1f else 1.02f
                    Surface(
                        onClick = {
                            viewModel.setCategory(cat)
                            runCatching { contentFR.requestFocus() }
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor          = if (isSelected) Color.White else MUTED,
                            focusedContentColor   = Color.Black
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .then(if (isSelected) Modifier.focusRequester(menuFR) else Modifier)
                            .onPreviewKeyEvent { ev ->
                                if (ev.key == Key.DirectionLeft && ev.type == KeyEventType.KeyDown) {
                                    runCatching { contentFR.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                                false
                            }
                            .onFocusChanged { if (it.isFocused) viewModel.setCategory(cat) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            // Netflix-style active indicator
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(
                                        if (isSelected) GOLD else Color.Transparent,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text       = label,
                                fontSize   = 17.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            //  CONTENT AREA
            // ══════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusGroup()
                    .onPreviewKeyEvent { ev ->
                        if ((ev.key == Key.Back || ev.key == Key.DirectionRight) && ev.type == KeyEventType.KeyDown) {
                            runCatching { menuFR.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
            ) {
                AnimatedContent(
                    targetState = state.selectedCategory,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                    contentAlignment = Alignment.TopStart,
                    label = "settings_content"
                ) { category ->
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start  = 48.dp, end    = 48.dp,
                            top    = 16.dp, bottom = 64.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (category) {
                            SettingsCategory.ACCOUNT -> {
                                item { SectionHeader(tr("PREMIUM", "חשבון פרימיום")) }
                                item {
                                    BuildRdBanner(
                                        modifier  = Modifier.focusRequester(contentFR),
                                        state     = state,
                                        viewModel = viewModel,
                                        isLowTier = isLowTier
                                    )
                                }
                                item { SectionHeader(tr("SERVICE STATUS", "סטטוס שרתים")) }
                                item {
                                    ServerHealthCard(
                                        state     = state,
                                        viewModel = viewModel,
                                        isLowTier = isLowTier
                                    )
                                }
                                item { SectionHeader(tr("CLOUD SYNC", "סנכרון ענן")) }
                                item {
                                    CloudSyncCard(
                                        state     = state,
                                        viewModel = viewModel,
                                        isLowTier = isLowTier
                                    )
                                }
                            }

                            SettingsCategory.PLAYBACK -> {
                                item { SectionHeader(tr("AUDIO", "שמע")) }
                                item {
                                    SettingsCard {
                                        val langs = listOf("original", "he", "en")
                                        val lIdx  = langs.indexOf(state.preferredAudioLang).takeIf { it >= 0 } ?: 0
                                        ToggleRow(
                                            modifier  = Modifier.focusRequester(contentFR),
                                            title     = tr("Audio Passthrough", "העברת שמע גולמי"),
                                            icon      = Icons.Default.SurroundSound,
                                            isChecked = state.audioPassthrough
                                        ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
                                        CardDivider()
                                        CycleRow(
                                            title        = tr("Preferred Audio", "שפת שמע מועדפת"),
                                            currentValue = when (state.preferredAudioLang) {
                                                "original" -> tr("Original", "מקור")
                                                "he"       -> "עברית"
                                                else       -> "English"
                                            },
                                            icon = Icons.Default.RecordVoiceOver
                                        ) {
                                            val next = langs[(lIdx + 1) % langs.size]
                                            viewModel.updateStringSetting("preferred_audio_lang", next)
                                        }
                                    }
                                }
                                item { SectionHeader(tr("QUALITY", "איכות")) }
                                item {
                                    SettingsCard {
                                        val resList = listOf("Unlimited", "4K", "1080p", "720p")
                                        val rIdx    = resList.indexOf(state.maxResolution).takeIf { it >= 0 } ?: 0
                                        CycleRow(
                                            title        = tr("Max Resolution", "רזולוציה מקסימלית"),
                                            currentValue = if (state.maxResolution == "Unlimited") tr("No Limit", "ללא הגבלה") else state.maxResolution,
                                            icon = Icons.Default.HighQuality
                                        ) { viewModel.updateStringSetting("max_resolution", resList[(rIdx + 1) % resList.size]) }
                                        CardDivider()
                                        val sizeList = listOf("Unlimited", "20GB", "10GB", "5GB")
                                        val sIdx     = sizeList.indexOf(state.maxFileSize).takeIf { it >= 0 } ?: 0
                                        CycleRow(
                                            title        = tr("Max File Size", "גודל קובץ מקסימלי"),
                                            currentValue = if (state.maxFileSize == "Unlimited") tr("No Limit", "ללא הגבלה") else state.maxFileSize,
                                            icon = Icons.Default.FolderZip
                                        ) { viewModel.updateStringSetting("max_file_size", sizeList[(sIdx + 1) % sizeList.size]) }
                                    }
                                }
                            }

                            SettingsCategory.SUBTITLES -> {
                                item { SectionHeader(tr("LANGUAGE & DISPLAY", "שפה ותצוגה")) }
                                item {
                                    SettingsCard {
                                        val subs = listOf("Hebrew", "English", "None")
                                        val sIdx = subs.indexOf(state.defaultSubtitles).takeIf { it >= 0 } ?: 0
                                        CycleRow(
                                            modifier     = Modifier.focusRequester(contentFR),
                                            title        = tr("Default Subtitles", "כתוביות ברירת מחדל"),
                                            currentValue = when (state.defaultSubtitles) {
                                                "Hebrew"  -> tr("Hebrew",  "עברית")
                                                "English" -> tr("English", "אנגלית")
                                                else      -> tr("Off",     "כבוי")
                                            },
                                            icon = Icons.Default.Subtitles
                                        ) { viewModel.updateStringSetting("def_subs", subs[(sIdx + 1) % subs.size]) }
                                        CardDivider()
                                        val sizes = listOf("small", "medium", "large", "xlarge")
                                        val fIdx  = sizes.indexOf(state.subtitleFontScale).takeIf { it >= 0 } ?: 1
                                        CycleRow(
                                            title        = tr("Subtitle Size", "גודל כתוביות"),
                                            currentValue = when (state.subtitleFontScale) {
                                                "small"  -> tr("Small",       "קטן")
                                                "large"  -> tr("Large",       "גדול")
                                                "xlarge" -> tr("Extra Large",  "ענק")
                                                else     -> tr("Medium",      "בינוני")
                                            },
                                            icon = Icons.Default.FormatSize
                                        ) { viewModel.updateStringSetting("subtitle_font_scale", sizes[(fIdx + 1) % sizes.size]) }
                                        CardDivider()
                                        ToggleRow(
                                            title     = tr("Yellow Subtitles", "כתוביות צהובות"),
                                            icon      = Icons.Default.FormatColorText,
                                            isChecked = state.yellowSubtitles
                                        ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
                                        CardDivider()
                                        CycleRow(
                                            title        = tr("App Language", "שפת הממשק"),
                                            currentValue = if (state.appLanguage == "he") "עברית" else "English",
                                            icon = Icons.Default.Language
                                        ) { viewModel.updateStringSetting("app_lang", if (state.appLanguage == "he") "en" else "he") }
                                    }
                                }
                            }

                            SettingsCategory.PERFORMANCE -> {
                                item { SectionHeader(tr("PERFORMANCE", "ביצועים")) }
                                item {
                                    SettingsCard {
                                        ToggleRow(
                                            modifier  = Modifier.focusRequester(contentFR),
                                            title     = tr("Lite UI Mode", "מצב ממשק קל"),
                                            icon      = Icons.Default.Speed,
                                            isChecked = state.liteUiMode
                                        ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
                                        CardDivider()
                                        ToggleRow(
                                            title     = tr("Pre-allocate Buffer", "הקצאת זיכרון מראש"),
                                            icon      = Icons.Default.Storage,
                                            isChecked = state.preAllocateBuffer
                                        ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
                                    }
                                }
                                item { SectionHeader(tr("STORAGE", "אחסון")) }
                                item {
                                    SettingsCard {
                                        ToggleRow(
                                            title     = tr("Save Search History", "שמור היסטוריית חיפוש"),
                                            icon      = Icons.Default.History,
                                            isChecked = state.saveSearchHistory
                                        ) { viewModel.updateToggleSetting("save_history", !state.saveSearchHistory) }
                                        CardDivider()
                                        ActionRow(
                                            title = tr("Clear Image Cache", "נקה מטמון תמונות"),
                                            desc  = state.cacheSizeStr,
                                            icon  = Icons.Default.DeleteSweep
                                        ) { viewModel.clearCache() }
                                        CardDivider()
                                        ActionRow(
                                            title = tr("Clear Search History", "נקה היסטוריית חיפוש"),
                                            desc  = if (state.searchHistoryStatus == "Clear") tr("Clear", "נקה") else state.searchHistoryStatus,
                                            icon  = Icons.AutoMirrored.Filled.ManageSearch
                                        ) { if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory() }
                                    }
                                }
                            }

                            SettingsCategory.SYSTEM_INFO -> {
                                item { SectionHeader(tr("ABOUT", "אודות")) }
                                item {
                                    SystemInfoCard(
                                        modifier = Modifier.focusRequester(contentFR),
                                        state    = state
                                    )
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
        text          = title,
        color         = Color(0xFF6E6E73),
        fontSize      = 12.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C1E))
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)   // aligns with text after icon + spacer
            .height(0.5.dp)
            .background(Color(0xFF2C2C2E))
    )
}


@Composable
private fun ToggleRow(
    modifier: Modifier = Modifier,
    title: String, icon: ImageVector,
    isChecked: Boolean, onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    Surface(
        onClick     = onToggle,
        shape       = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors      = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.White
        ),
        scale       = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier    = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier          = Modifier.size(32.dp).background(
                        if (focused) Color.Black.copy(0.08f) else Color(0xFF2C2C2E),
                        RoundedCornerShape(8.dp)
                    ),
                    contentAlignment  = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (focused) Color.Black else Color(0xFF6E6E73), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text       = title,
                    color      = if (focused) Color.Black else Color.White,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            // Animated switch
            val switchOffset by animateDpAsState(if (isChecked) 18.dp else 3.dp, tween(250))
            val switchBg     by animateColorAsState(
                when { isChecked && focused -> Color.Black; isChecked -> Color(0xFFE2B258); else -> Color(0xFF3A3A3C) },
                tween(250)
            )
            val knobColor    by animateColorAsState(
                if (isChecked && focused) Color(0xFFE2B258) else Color.White,
                tween(250)
            )
            Box(
                modifier         = Modifier.width(40.dp).height(24.dp).clip(RoundedCornerShape(50)).background(switchBg),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.padding(start = switchOffset).size(18.dp).background(knobColor, CircleShape))
            }
        }
    }
}


@Composable
private fun CycleRow(
    modifier: Modifier = Modifier,
    title: String, currentValue: String, icon: ImageVector, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    Surface(
        onClick     = onClick,
        shape       = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors      = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.White
        ),
        scale       = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier    = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(32.dp).background(
                        if (focused) Color.Black.copy(0.08f) else Color(0xFF2C2C2E),
                        RoundedCornerShape(8.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (focused) Color.Black else Color(0xFF6E6E73), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(title, color = if (focused) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentValue, color = if (focused) Color(0xFF555555) else Color(0xFF6E6E73), fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint     = if (focused) Color(0xFF555555) else Color(0xFF3A3A3C),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
private fun ActionRow(
    modifier: Modifier = Modifier,
    title: String, desc: String, icon: ImageVector, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    Surface(
        onClick     = onClick,
        shape       = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors      = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.White
        ),
        scale       = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier    = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(32.dp).background(
                        if (focused) Color.Black.copy(0.08f) else Color(0xFF2C2C2E),
                        RoundedCornerShape(8.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (focused) Color.Black else Color(0xFF6E6E73), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(title, color = if (focused) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            }
            Text(desc, color = if (focused) Color(0xFF555555) else Color(0xFF6E6E73), fontSize = 15.sp)
        }
    }
}


// ── AllDebrid & SERVER STATUS BANNERS ──

@Composable
private fun BuildRdBanner(modifier: Modifier = Modifier, state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    if (state.debridToken.isNotEmpty()) {

        LaunchedEffect(Unit) {
            if (state.rdSpeedTestResult == null) {
                viewModel.checkAllDebridAccount()
            }
        }

        var focused by remember { mutableStateOf(false) }
        val scale = if (isLowTier) 1f else 1.02f
        val glow = if (isLowTier || !focused) Glow.None else Glow(Color(0xFFD97706).copy(alpha = 0.5f), 24.dp)
        
        val glowingBorder = remember { Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFF8B5CF6))) }
        val bannerGradient = remember { Brush.horizontalGradient(listOf(Color(0xFF451A03), Color(0xFF1E1B4B))) }

        Surface(
            onClick  = { viewModel.logoutAllDebrid() },
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(0.05f)),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = scale),
            glow     = ClickableSurfaceDefaults.glow(focusedGlow = glow),
            border   = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(0.3f)), shape = RoundedCornerShape(20.dp)),
                focusedBorder = Border(border = BorderStroke(2.dp, glowingBorder), shape = RoundedCornerShape(20.dp))
            ),
            modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        ) {
            Row(Modifier.fillMaxWidth().background(bannerGradient).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                        Text(tr("AllDebrid ACTIVE", "חשבון פרימיום פעיל"), color = Color(0xFFF59E0B), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Token:", "טוקן:") + " ${state.debridToken.take(5)}•••••••••${state.debridToken.takeLast(4)}", color = Color.White.copy(0.6f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.rdSpeedTestResult != null) {
                        Text(state.rdSpeedTestResult, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 24.dp))
                    } else {
                        Button(onClick = { viewModel.checkAllDebridAccount() }, colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f))) {
                            Text(if (state.rdSpeedTesting) tr("Contacting...", "בודק...") else tr("Check Status", "בדוק חיבור"), color = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(tr("Click to Disconnect", "לחץ לניתוק"), color = if(focused) Color(0xFFEF4444) else Color.White.copy(0.4f), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        when (val auth = state.authStatus) {

            is SettingsAuthStatus.WaitingForUser -> {
                val bannerGradient = remember { Brush.horizontalGradient(listOf(Color(0xFF161622), Color(0xFF1E1E2E))) }
                val codeGlow = remember { Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFF59E0B))) }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bannerGradient)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        // Left side - instructions
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF3B82F6).copy(alpha = 0.05f))
                                .padding(28.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier.size(52.dp).background(Color(0xFF3B82F6).copy(0.15f), CircleShape),
                                Alignment.Center
                            ) {
                                Icon(Icons.Default.PhonelinkRing, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                tr("Device Linking", "חיבור מכשיר"),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr(
                                    "Visit the website from your phone or computer and enter the code shown here.",
                                    "הכנס לאתר מהטלפון או מהמחשב והקש את הקוד שמופיע כאן."
                                ),
                                color = Color.White.copy(0.55f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }

                        // Divider
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.White.copy(0.08f))
                        )

                        // Right side - code
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                tr("1. VISIT WEBSITE", "1. כנס לאתר"),
                                color = Color.White.copy(0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                auth.url,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                tr("2. ENTER CODE", "2. הקש קוד"),
                                color = Color.White.copy(0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            // Glowing code box
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, codeGlow, RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0D0D15))
                                    .padding(horizontal = 28.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    auth.userCode,
                                    color = Color(0xFFF59E0B),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 8.sp
                                )
                            }
                        }
                    }
                }
            }

            is SettingsAuthStatus.Idle -> {
                var focused by remember { mutableStateOf(false) }
                val glow = if (isLowTier || !focused) Glow.None else Glow(Color(0xFF3B82F6).copy(0.5f), 24.dp)
                val glowingBorder = remember { Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))) }

                Surface(
                    onClick  = { viewModel.startAllDebridAuth() },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2E3135), focusedContainerColor = Color(0xFF383C41)),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    border   = ClickableSurfaceDefaults.border(Border.None, Border.None),
                    modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                ) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(Color(0xFFF59E0B).copy(0.15f), CircleShape), Alignment.Center) {
                            Icon(Icons.Default.VpnKey, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Connect AllDebrid", "חבר חשבון AllDebrid"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(tr("Unlock 4K streaming and zero-buffering playback.", "תמיכה ב-4K וסטרימינג ללא עצירות."), color = Color.White.copy(0.6f), fontSize = 14.sp)
                        }
                        Box(Modifier.background(if (focused) Color.White else Color(0xFF3B82F6).copy(0.15f), RoundedCornerShape(50)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Text(tr("Link Account", "קשר חשבון"), color = if(focused) Color(0xFF161622) else Color(0xFF3B82F6), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            is SettingsAuthStatus.Loading -> {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF161622)), Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color(0xFFF59E0B))
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun ServerHealthCard(modifier: Modifier = Modifier, state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (isLowTier) 1f else 1.02f
    val glow = if (isLowTier || !focused) Glow.None else Glow(Color(0xFF3B82F6).copy(0.5f), 24.dp)
    val glowingBorder = remember { Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))) }

    Surface(
        onClick = { viewModel.checkServerStatuses() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2E3135), focusedContainerColor = Color(0xFF383C41)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(Border.None, Border.None),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("AllDebrid API", "שרתי AllDebrid"), color = Color.White.copy(0.5f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.adServerStatus, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.width(2.dp).height(40.dp).background(Color.White.copy(0.1f)))

            Column(Modifier.weight(1f).padding(start = 32.dp)) {
                Text(tr("Torrentio Scraper", "שרתי Torrentio"), color = Color.White.copy(0.5f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.torrentioServerStatus, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.checkServerStatuses() },
                colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.15f)),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(if (state.isCheckingServers) tr("Checking...", "בודק...") else tr("Refresh", "רענן"), color = Color.White)
            }
        }
    }
}

// ── CLOUD SYNC ──

@Composable
private fun CloudSyncCard(modifier: Modifier = Modifier, state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    var focused by remember { mutableStateOf(false) }

    val syncStatusText = when (state.syncStatus) {
        "PUSHING" -> tr("Uploading data...", "מעלה נתונים...")
        "PULLING" -> tr("Downloading data...", "מוריד נתונים...")
        "SUCCESS" -> tr("Sync Complete ✓", "סנכרון הושלם ✓")
        "ERROR"   -> tr("Sync Failed ✗", "סנכרון נכשל ✗")
        "NO_USER" -> tr("Not logged in", "לא מחובר לחשבון")
        else      -> ""
    }

    val syncColor = when (state.syncStatus) {
        "SUCCESS" -> ACCENT_GREEN
        "ERROR", "NO_USER" -> Color(0xFFEF4444)
        else -> ACCENT_BLUE
    }

    val isLoggedIn = state.userEmail != null

    Surface(
        onClick = {
            if (!state.isSyncing && isLoggedIn) viewModel.triggerManualSync()
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2E3135), focusedContainerColor = Color(0xFF383C41)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isLowTier) 1f else 1.02f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = if (isLowTier || !focused) Glow.None else Glow(ACCENT_BLUE.copy(0.4f), 24.dp)
        ),
        border = ClickableSurfaceDefaults.border(Border.None, Border.None),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.size(56.dp).background(ACCENT_BLUE.copy(0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        null,
                        tint = if (state.isSyncing) ACCENT_BLUE else if (focused) Color.White else ACCENT_BLUE,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    if (isLoggedIn) {
                        Text(
                            tr("Sync Now", "סנכרן עכשיו"),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.userEmail ?: "",
                            color = Color.White.copy(0.5f),
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            tr("Not Logged In", "לא מחובר"),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tr("Login to sync between devices", "התחבר כדי לסנכרן בין מכשירים"),
                            color = Color.White.copy(0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (syncStatusText.isNotEmpty()) {
                Text(
                    syncStatusText,
                    color = syncColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else if (isLoggedIn) {
                Box(
                    Modifier.background(
                        if (focused) Color.White else ACCENT_BLUE.copy(0.15f),
                        RoundedCornerShape(50)
                    ).padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (state.isSyncing) tr("Syncing...", "מסנכרן...") else tr("Sync", "סנכרן"),
                        color = if (focused) Color(0xFF161622) else ACCENT_BLUE,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ── SYSTEM INFO ──

@Composable
private fun SystemInfoCard(modifier: Modifier = Modifier, state: SettingsState) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2E3135), focusedContainerColor = Color(0xFF383C41)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(Border.None, Border.None),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Box(Modifier.fillMaxWidth().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tr("App Version", "גרסת אפליקציה"), color = Color.White.copy(0.5f), fontSize = 12.sp)
                Text(state.appVersion, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Device Capability", "יכולת מכשיר"), color = Color.White.copy(0.5f), fontSize = 12.sp)
                Text(state.deviceTier.ifEmpty { "Detecting..." }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Engines", "מנועים"), color = Color.White.copy(0.5f), fontSize = 12.sp)
                Text("Torrentio / TMDB / Ktuvit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
}
