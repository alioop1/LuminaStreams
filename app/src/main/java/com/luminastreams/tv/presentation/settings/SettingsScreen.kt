@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_FUTURE_ERROR")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.luminastreams.tv.ui.theme.NetflixRed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun SettingsScreen(state: SettingsState, viewModel: SettingsViewModel, isRtl: Boolean, onToggleLanguage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // --- LEFT PANE: Minimalist Navigation Rail ---
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .focusGroup()
                .focusRestorer()
                .background(Color(0x0AFFFFFF))
                .padding(vertical = 56.dp, horizontal = 24.dp)
        ) {
            Text(
                text = if (isRtl) "הגדרות" else "Settings",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
            )

            SettingsCategory.entries.forEach { category ->
                val isSelected = state.selectedCategory == category

                Surface(
                    onClick = { viewModel.setCategory(category) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) Color(0x1AFFFFFF) else Color.Transparent,
                        focusedContainerColor = Color.White,
                        contentColor = if (isSelected) Color.White else Color(0x80FFFFFF),
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(60.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRtl) category.titleHe else category.titleEn,
                            fontSize = 20.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- RIGHT PANE: Clean Content Area ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusGroup()
                .padding(start = 48.dp, end = 64.dp, top = 56.dp)
        ) {
            Crossfade(
                targetState = state.selectedCategory,
                animationSpec = tween(300),
                label = "settings_fade"
            ) { category ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = if (isRtl) category.titleHe else category.titleEn,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        )
                    }

                    when (category) {
                        SettingsCategory.ACCOUNT -> accountSettings(state, viewModel, isRtl)
                        SettingsCategory.PLAYBACK -> playbackSettings(state, viewModel, isRtl)
                        SettingsCategory.PRIVACY -> privacySettings(state, viewModel, isRtl)
                        SettingsCategory.SYSTEM -> systemSettings(state, viewModel, isRtl, onToggleLanguage)
                    }
                }
            }
        }
    }
}

// --- CATEGORY EXTENSIONS ---

private fun androidx.compose.foundation.lazy.LazyListScope.accountSettings(state: SettingsState, viewModel: SettingsViewModel, isRtl: Boolean) {
    item {
        if (state.rdToken.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1A4CAF50)).padding(32.dp)) {
                Column {
                    Text("Real-Debrid Active", color = Color(0xFF81C784), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Token: ${state.rdToken.take(8)}********", color = Color(0xB3FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
                    Button(
                        onClick = { viewModel.logoutRealDebrid() },
                        colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                    ) { Text(if (isRtl) "התנתק" else "Logout", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
        } else {
            when (val authState = state.authStatus) {
                is SettingsAuthStatus.WaitingForUser -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x0DFFFFFF))
                            // FIX 1: Reduced horizontal padding from 48.dp to 16.dp to give the boxes more room
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if(isRtl) "הכנס למכשיר נייד או למחשב בכתובת:" else "Go to this URL on your phone/PC:", color = Color.LightGray, fontSize = 20.sp)

                            Text(
                                text = authState.url,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(top = 12.dp, bottom = 40.dp)
                            )

                            Text(if(isRtl) "והזן את הקוד המאובטח:" else "And enter this secure code:", color = Color.LightGray, fontSize = 20.sp)

                            // FIX 2: Force Left-to-Right layout so the English code is always ordered correctly
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth() // Ensures it takes available space and centers content
                                        .padding(top = 24.dp),
                                    // FIX 3: Reduced gap between boxes to 8.dp and ensure centering
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Real Debrid uses 8 chars, so default to 8 spaces
                                    val codeToDisplay = if (authState.userCode.isEmpty()) "        " else authState.userCode

                                    codeToDisplay.forEach { char ->
                                        Box(
                                            modifier = Modifier
                                                // FIX 4: Shrunk box width slightly to guarantee 8 fit on screen
                                                .size(28.dp, 40.dp)
                                                .shadow(
                                                    elevation = 6.dp,
                                                    shape = RoundedCornerShape(6.dp),
                                                    ambientColor = Color.Black,
                                                    spotColor = Color.Black
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF111111))
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = Color(0x33FFFFFF),
                                                    shape = RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = char.toString(),
                                                style = TextStyle(
                                                    // FIX 5: Scaled font down slightly to match the new box size
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50),
                                                    shadow = Shadow(
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        offset = Offset(2f, 2f),
                                                        blurRadius = 4f
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Text(if(isRtl) "ממתין לאישור מרוחק..." else "Awaiting remote approval...", color = Color(0x80FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(top = 40.dp))
                        }
                    }
                }
                else -> {
                    PremiumSettingItem(
                        title = if (isRtl) "התחברות ל-Real Debrid" else "Login to Real-Debrid",
                        value = if (isRtl) "התחל תהליך" else "Start Auth",
                        onClick = { viewModel.startRealDebridAuth() }
                    )
                    if (authState is SettingsAuthStatus.Error) {
                        Text(authState.message, color = NetflixRed, modifier = Modifier.padding(top = 16.dp, start = 16.dp))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playbackSettings(state: SettingsState, viewModel: SettingsViewModel, isRtl: Boolean) {
    val resolutions = listOf("4K", "1080p", "720p")
    val subs = listOf("Hebrew", "English", "None")

    item { PremiumSettingItem(if (isRtl) "איכות וידאו מקסימלית" else "Max Video Quality", state.maxResolution) { viewModel.updateStringSetting("max_res", resolutions[(resolutions.indexOf(state.maxResolution) + 1) % resolutions.size]) } }
    item { PremiumSettingItem(if (isRtl) "ניגון אוטומטי לפרק הבא" else "Auto-Play Next Episode", if (state.autoPlayNext) "פעיל" else "כבוי") { viewModel.updateToggleSetting("auto_play", !state.autoPlayNext) } }
    item { PremiumSettingItem(if (isRtl) "שפת כתוביות ברירת מחדל" else "Default Subtitles", state.defaultSubtitles) { viewModel.updateStringSetting("def_subs", subs[(subs.indexOf(state.defaultSubtitles) + 1) % subs.size]) } }
    item { PremiumSettingItem(if (isRtl) "האצת חומרה (מומלץ)" else "Hardware Acceleration", if (state.hwAcceleration) "פעיל" else "כבוי") { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.privacySettings(state: SettingsState, viewModel: SettingsViewModel, isRtl: Boolean) {
    item { PremiumSettingItem(if (isRtl) "סינון תוכן לילדים" else "Safe Search", if (state.safeSearch) "פעיל" else "כבוי") { viewModel.updateToggleSetting("safe_search", !state.safeSearch) } }
    item { PremiumSettingItem(if (isRtl) "שמור היסטוריית חיפושים" else "Save Search History", if (state.saveSearchHistory) "פעיל" else "כבוי") { viewModel.updateToggleSetting("save_history", !state.saveSearchHistory) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.systemSettings(state: SettingsState, viewModel: SettingsViewModel, isRtl: Boolean, onToggleLanguage: () -> Unit) {
    val themes = listOf("Netflix Red", "Ocean Blue", "Dark Mode")
    item { PremiumSettingItem(if (isRtl) "שפת ממשק" else "App Language", if (isRtl) "עברית (RTL)" else "English (LTR)") { viewModel.updateToggleSetting("is_hebrew", !state.isHebrew); onToggleLanguage() } }
    item { PremiumSettingItem(if (isRtl) "עיצוב (Theme)" else "Theme Color", state.themeColor) { viewModel.updateStringSetting("theme_color", themes[(themes.indexOf(state.themeColor) + 1) % themes.size]) } }
    item { PremiumSettingItem(if (isRtl) "ניקוי זיכרון מטמון" else "Clear Cache", state.cacheSizeStr) { viewModel.clearCache() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PremiumSettingItem(title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0AFFFFFF),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(value, color = LocalContentColor.current.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}