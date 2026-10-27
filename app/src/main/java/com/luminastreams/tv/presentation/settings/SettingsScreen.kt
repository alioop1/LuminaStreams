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
    val backFR = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    val sidebarFR = remember { FocusRequester() }
    val isLowTier = DeviceProfile.tier == DeviceProfile.Tier.LOW

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { sidebarFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF222428))) {
        Row(Modifier.fillMaxSize()) {

            // ── LEFT SIDEBAR ──
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1C1E))
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
                                focusedContainerColor = Color.Transparent,
                                contentColor = if (isSelected) Color.White else Color(0xFFAAAEB4),
                                focusedContentColor = Color.White
                            ),
                            shape = ClickableSurfaceDefaults.shape(RectangleShape),
                            border = ClickableSurfaceDefaults.border(Border.None, Border.None),
                            modifier = surfaceMod
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) viewModel.setCategory(cat) }
                        ) {
                            // Row to hold the Icon and the Text
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(
                                                Color.White.copy(0.1f),
                                                CircleShape
                                            )
                                        )
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) Color.White else Color(0xFFAAAEB4)
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

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
                    val categoryTitle = when (category) {
                        SettingsCategory.ACCOUNT -> tr("Account & Network", "חשבון ורשת")
                        SettingsCategory.PLAYBACK -> tr("Video & Audio", "וידאו ושמע")
                        SettingsCategory.SUBTITLES -> tr("Subtitles & UI", "כתוביות וממשק")
                        SettingsCategory.PERFORMANCE -> tr("Performance", "ביצועים וזיכרון")
                        SettingsCategory.SYSTEM_INFO -> tr("System Info", "מידע מערכת")
                    }

                    Column(
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 650.dp)
                    ) {
                        Text(
                            text = categoryTitle,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 40.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (category) {
                                SettingsCategory.ACCOUNT -> {
                                    item {
                                        SectionHeader(tr("REAL-DEBRID INTEGRATION", "חיבור לשרתי הפרימיום"))
                                    }
                                    item {
                                        BuildRdBanner(modifier = Modifier.focusRequester(contentFR), state = state, viewModel = viewModel, isLowTier = isLowTier)
                                    }
                                    item {
                                        SectionHeader(tr("SERVICE HEALTH", "סטטוס שרתים"))
                                    }
                                    item {
                                        ServerHealthCard(state = state, viewModel = viewModel, isLowTier = isLowTier)
                                    }
                                    item {
                                        SectionHeader(tr("CLOUD SYNC", "סנכרון ענן"))
                                    }
                                    item {
                                        CloudSyncCard(state = state, viewModel = viewModel, isLowTier = isLowTier)
                                    }
                                }

                                SettingsCategory.PLAYBACK -> {
                                    item {
                                        SectionHeader(tr("VIDEO DECODING", "פענוח וידאו"))
                                    }
                                    item {
                                        SettingsGroup {
                                            ToggleRow(
                                                modifier = Modifier.focusRequester(contentFR),
                                                title = tr("HW Acceleration", "האצת חומרה"),
                                                icon  = Icons.Default.Memory,
                                                isChecked = state.hwAcceleration
                                            ) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) }
                                            ToggleRow(
                                                title = tr("Auto Frame Rate", "התאמת קצב (AFR)"),
                                                icon  = Icons.Default.Monitor,
                                                isChecked = state.autoFrameRate
                                            ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
                                        }
                                    }
                                    item {
                                        SectionHeader(tr("AUDIO SETTINGS", "הגדרות שמע"))
                                    }
                                    item {
                                        SettingsGroup {
                                            ToggleRow(
                                                title = tr("Audio Passthrough", "העברת שמע גולמי"),
                                                icon  = Icons.Default.SurroundSound,
                                                isChecked = state.audioPassthrough
                                            ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
                                            
                                            val langs = listOf("original", "he", "en")
                                            val currentIdx = langs.indexOf(state.preferredAudioLang).takeIf { it >= 0 } ?: 0
                                            CycleRow(
                                                title = tr("Preferred Audio", "שפת שמע מועדפת"),
                                                currentValue = when(state.preferredAudioLang) {
                                                    "original" -> tr("Original", "שפת מקור")
                                                    "he" -> "Hebrew"
                                                    "en" -> "English"
                                                    else -> state.preferredAudioLang
                                                },
                                                icon = Icons.Default.RecordVoiceOver
                                            ) {
                                                val next = langs[(currentIdx + 1) % langs.size]
                                                viewModel.updateStringSetting("preferred_audio_lang", next)
                                            }
                                        }
                                    }
                                    item {
                                        SectionHeader(tr("STREAMING FILTERS", "מסנני הזרמה"))
                                    }
                                    item {
                                        SettingsGroup {
                                            val resList = listOf("Unlimited", "4K", "1080p", "720p")
                                            val currentResIdx = resList.indexOf(state.maxResolution).takeIf { it >= 0 } ?: 0
                                            CycleRow(
                                                title = tr("Max Resolution", "רזולוציה מקסימלית"),
                                                currentValue = if (state.maxResolution == "Unlimited") tr("Unlimited", "ללא הגבלה") else state.maxResolution,
                                                icon = Icons.Default.HighQuality
                                            ) {
                                                val next = resList[(currentResIdx + 1) % resList.size]
                                                viewModel.updateStringSetting("max_resolution", next)
                                            }

                                            val sizeList = listOf("Unlimited", "20GB", "10GB", "5GB")
                                            val currentSizeIdx = sizeList.indexOf(state.maxFileSize).takeIf { it >= 0 } ?: 0
                                            CycleRow(
                                                title = tr("Max File Size", "גודל קובץ מקסימלי"),
                                                currentValue = if (state.maxFileSize == "Unlimited") tr("Unlimited", "ללא הגבלה") else state.maxFileSize,
                                                icon = Icons.Default.FolderZip
                                            ) {
                                                val next = sizeList[(currentSizeIdx + 1) % sizeList.size]
                                                viewModel.updateStringSetting("max_file_size", next)
                                            }
                                        }
                                    }
                                }

                                SettingsCategory.SUBTITLES -> {
                                    item {
                                        SectionHeader(tr("SUBTITLES PREFERENCES", "העדפות כתוביות"))
                                    }
                                    item {
                                        SettingsGroup {
                                            val subs = listOf("Hebrew", "English", "None")
                                            val currentIdx = subs.indexOf(state.defaultSubtitles).takeIf { it >= 0 } ?: 0
                                            CycleRow(
                                                modifier = Modifier.focusRequester(contentFR),
                                                title = tr("Default Subtitles", "שפת כתוביות"),
                                                currentValue = when(state.defaultSubtitles) {
                                                    "Hebrew" -> tr("Hebrew", "עברית")
                                                    "English" -> tr("English", "אנגלית")
                                                    else -> tr("Off", "ללא")
                                                },
                                                icon = Icons.Default.Subtitles
                                            ) {
                                                val next = subs[(currentIdx + 1) % subs.size]
                                                viewModel.updateStringSetting("def_subs", next)
                                            }

                                            val sizes = listOf("small", "medium", "large", "xlarge")
                                            val currentIdxSize = sizes.indexOf(state.subtitleFontScale).takeIf { it >= 0 } ?: 1
                                            CycleRow(
                                                title = tr("Subtitle Size", "גודל כתוביות"),
                                                currentValue = when(state.subtitleFontScale) {
                                                    "small" -> tr("Small", "קטן")
                                                    "medium" -> tr("Medium", "בינוני")
                                                    "large" -> tr("Large", "גדול")
                                                    "xlarge" -> tr("Extra Large", "ענק")
                                                    else -> "Medium"
                                                },
                                                icon = Icons.Default.FormatSize
                                            ) {
                                                val next = sizes[(currentIdxSize + 1) % sizes.size]
                                                viewModel.updateStringSetting("subtitle_font_scale", next)
                                            }

                                            ToggleRow(
                                                title = tr("Yellow Subtitles", "כתוביות צהובות"),
                                                icon  = Icons.Default.FormatColorText,
                                                isChecked = state.yellowSubtitles
                                            ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }

                                            CycleRow(
                                                title = tr("App Language", "שפת האפליקציה"),
                                                currentValue = if (state.appLanguage == "he") "עברית" else "English",
                                                icon = Icons.Default.Language
                                            ) {
                                                viewModel.updateStringSetting("app_lang", if (state.appLanguage == "he") "en" else "he")
                                            }
                                        }
                                    }
                                }

                                SettingsCategory.PERFORMANCE -> {
                                    item {
                                        SectionHeader(tr("PERFORMANCE & MEMORY", "ניהול זיכרון וביצועים"))
                                    }
                                    item {
                                        SettingsGroup {
                                            ToggleRow(
                                                modifier = Modifier.focusRequester(contentFR),
                                                title = tr("Lite UI Mode", "ממשק קל (Lite)"),
                                                icon  = Icons.Default.Speed,
                                                isChecked = state.liteUiMode
                                            ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
                                            ToggleRow(
                                                title = tr("Pre-allocate Buffer", "שריון זיכרון לנגן"),
                                                icon  = Icons.Default.Storage,
                                                isChecked = state.preAllocateBuffer
                                            ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
                                        }
                                    }
                                    item {
                                        SectionHeader(tr("STORAGE CLEARANCE", "ניקוי נתונים"))
                                    }
                                    item {
                                        SettingsGroup {
                                            ToggleRow(
                                                title = tr("Save Search History", "שמור היסטוריית חיפוש"),
                                                icon  = Icons.Default.History,
                                                isChecked = state.saveSearchHistory
                                            ) { viewModel.updateToggleSetting("save_history", !state.saveSearchHistory) }

                                            ActionRow(
                                                title = tr("Clear Image Cache", "ניקוי מטמון תמונות"),
                                                desc  = state.cacheSizeStr,
                                                icon  = Icons.Default.DeleteSweep
                                            ) { viewModel.clearCache() }

                                            ActionRow(
                                                title = tr("Clear Search History", "מחק היסטוריית חיפוש"),
                                                desc  = if (state.searchHistoryStatus == "Clear") tr("Clear", "נקה") else state.searchHistoryStatus,
                                                icon  = Icons.AutoMirrored.Filled.ManageSearch
                                            ) { if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory() }
                                        }
                                    }
                                }

                                SettingsCategory.SYSTEM_INFO -> {
                                    item {
                                        SectionHeader(tr("ABOUT DEVICE", "אודות המערכת"))
                                    }
                                    item {
                                        SystemInfoCard(modifier = Modifier.focusRequester(contentFR), state = state)
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
        color = Color(0xFFAAAEB4),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 24.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF282A2E))
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    modifier: Modifier = Modifier,
    title: String, icon: ImageVector,
    isChecked: Boolean, onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B3E43)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (focused) Color.White else Color(0xFFAAAEB4), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(20.dp))
                Text(title, color = if (focused) Color.White else Color(0xFFE3E3E3), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            
            // Modern Cyan Switch
            val switchOffset by animateDpAsState(if (isChecked) 20.dp else 4.dp, tween(300))
            val switchBg by animateColorAsState(if (isChecked) Color(0xFF00E5FF) else Color(0xFF5A5D62), tween(300))
            
            Box(
                modifier = Modifier.width(42.dp).height(22.dp).clip(RoundedCornerShape(50))
                    .background(switchBg),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.padding(start = switchOffset).size(14.dp).background(Color(0xFF1A1C1E), CircleShape))
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

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B3E43)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (focused) Color.White else Color(0xFFAAAEB4), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(20.dp))
                Text(title, color = if (focused) Color.White else Color(0xFFE3E3E3), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(currentValue, color = if (focused) Color.White else Color(0xFFAAAEB4), fontSize = 15.sp)
        }
    }
}

@Composable
private fun ActionRow(
    modifier: Modifier = Modifier,
    title: String, desc: String, icon: ImageVector, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B3E43)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (focused) Color.White else Color(0xFFAAAEB4), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(20.dp))
                Text(title, color = if (focused) Color.White else Color(0xFFE3E3E3), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(desc, color = if (focused) Color.White else Color(0xFFAAAEB4), fontSize = 15.sp)
        }
    }
}

// ── REAL-DEBRID & SERVER STATUS BANNERS ──

@Composable
private fun BuildRdBanner(modifier: Modifier = Modifier, state: SettingsState, viewModel: SettingsViewModel, isLowTier: Boolean) {
    if (state.rdToken.isNotEmpty()) {

        LaunchedEffect(Unit) {
            if (state.rdSpeedTestResult == null) {
                viewModel.checkRealDebridAccount()
            }
        }

        var focused by remember { mutableStateOf(false) }
        val scale = if (isLowTier) 1f else 1.02f
        val glow = if (isLowTier || !focused) Glow.None else Glow(Color(0xFFD97706).copy(alpha = 0.5f), 24.dp)
        
        val glowingBorder = remember { Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFF8B5CF6))) }
        val bannerGradient = remember { Brush.horizontalGradient(listOf(Color(0xFF451A03), Color(0xFF1E1B4B))) }

        Surface(
            onClick  = { viewModel.logoutRealDebrid() },
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
                        Text(tr("REAL-DEBRID ACTIVE", "חשבון פרימיום פעיל"), color = Color(0xFFF59E0B), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Token:", "טוקן:") + " ${state.rdToken.take(5)}•••••••••${state.rdToken.takeLast(4)}", color = Color.White.copy(0.6f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.rdSpeedTestResult != null) {
                        Text(state.rdSpeedTestResult, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 24.dp))
                    } else {
                        Button(onClick = { viewModel.checkRealDebridAccount() }, colors = ButtonDefaults.colors(containerColor = Color.White.copy(0.1f))) {
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
                    onClick  = { viewModel.startRealDebridAuth() },
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
                            Text(tr("Connect Real-Debrid", "חבר חשבון Real-Debrid"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                Text(tr("Real-Debrid API", "שרתי Real-Debrid"), color = Color.White.copy(0.5f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.rdServerStatus, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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