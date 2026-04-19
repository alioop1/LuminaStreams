package com.luminastreams.tv.presentation.iptv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import kotlinx.coroutines.flow.MutableStateFlow

class IptvSettingsViewModel : ViewModel() {
    val autoStart = MutableStateFlow(false)
    val parentalControl = MutableStateFlow(false)
    val catchupEnabled = MutableStateFlow(true)

    val bufferOptions = listOf("Fast Start", "Stable (Recommended)", "Deep Buffer (Slow Net)")
    val bufferIndex = MutableStateFlow(1)

    val epgSyncOptions = listOf("On Boot", "Every 12 Hours", "Every 24 Hours")
    val epgSyncIndex = MutableStateFlow(0)

    val defaultCategoryOptions = listOf("All Channels", "Favorites", "Last Viewed")
    val defaultCategoryIndex = MutableStateFlow(2)

    val decoderOptions = listOf("Hardware (Fast)", "Software (Safe)")
    val decoderIndex = MutableStateFlow(0)

    fun toggleAutoStart() { autoStart.value = !autoStart.value }
    fun toggleParentalControl() { parentalControl.value = !parentalControl.value }
    fun toggleCatchup() { catchupEnabled.value = !catchupEnabled.value }

    fun cycleBuffer() { bufferIndex.value = (bufferIndex.value + 1) % bufferOptions.size }
    fun cycleEpgSync() { epgSyncIndex.value = (epgSyncIndex.value + 1) % epgSyncOptions.size }
    fun cycleCategory() { defaultCategoryIndex.value = (defaultCategoryIndex.value + 1) % defaultCategoryOptions.size }
    fun cycleDecoder() { decoderIndex.value = (decoderIndex.value + 1) % decoderOptions.size }
}

@Composable
fun IptvSettingsScreen(
    viewModel: IptvSettingsViewModel = viewModel()
) {
    val autoStart by viewModel.autoStart.collectAsState()
    val parentalControl by viewModel.parentalControl.collectAsState()
    val catchupEnabled by viewModel.catchupEnabled.collectAsState()
    val bufferIdx by viewModel.bufferIndex.collectAsState()
    val epgSyncIdx by viewModel.epgSyncIndex.collectAsState()
    val categoryIdx by viewModel.defaultCategoryIndex.collectAsState()
    val decoderIdx by viewModel.decoderIndex.collectAsState()

    val firstItemFocus = remember { FocusRequester() }
    var hasFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(top = 40.dp, bottom = 40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 40.dp)) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Lumina Settings", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            }

            LazyColumn(modifier = Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp)) {
                item { SettingsSectionTitle("GENERAL") }
                item {
                    SettingsToggleItem(
                        title = "Auto-Start on Boot",
                        description = "Automatically launch Lumina and play the last viewed channel.",
                        isChecked = autoStart,
                        onClick = { viewModel.toggleAutoStart() },
                        modifier = Modifier
                            .focusRequester(firstItemFocus)
                            .onGloballyPositioned {
                                if (!hasFocused) {
                                    try { firstItemFocus.requestFocus(); hasFocused = true } catch (_: Exception) {}
                                }
                            }
                    )
                }
                item {
                    SettingsCycleItem(title = "Default Launch Category", description = "Choose which category opens first.", currentValue = viewModel.defaultCategoryOptions[categoryIdx], onClick = { viewModel.cycleCategory() })
                }
                item { Spacer(modifier = Modifier.height(24.dp)); SettingsSectionTitle("PLAYBACK & STREAMING") }
                item {
                    SettingsCycleItem(title = "Network Buffer Size", description = "Increase buffer if streams are stuttering on Wi-Fi.", currentValue = viewModel.bufferOptions[bufferIdx], onClick = { viewModel.cycleBuffer() })
                }
                item {
                    SettingsCycleItem(title = "Video Decoder Engine", description = "Switch to Software decoding if you experience black screens.", currentValue = viewModel.decoderOptions[decoderIdx], onClick = { viewModel.cycleDecoder() })
                }
                item {
                    SettingsToggleItem(title = "Time-Shift / Catchup", description = "Enable scrubbing back in time for supported channels.", isChecked = catchupEnabled, onClick = { viewModel.toggleCatchup() })
                }
                item { Spacer(modifier = Modifier.height(24.dp)); SettingsSectionTitle("CONTENT & DATA") }
                item {
                    SettingsCycleItem(title = "EPG Auto-Sync Interval", description = "How often should the app download the TV Guide.", currentValue = viewModel.epgSyncOptions[epgSyncIdx], onClick = { viewModel.cycleEpgSync() })
                }
                item {
                    SettingsToggleItem(title = "Parental Control (PIN)", description = "Lock specific categories with a 4-digit PIN.", isChecked = parentalControl, onClick = { viewModel.toggleParentalControl() })
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(text = title, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp, top = 8.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsToggleItem(title: String, description: String, isChecked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.3f), elevation = 16.dp))
    ) {
        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, fontSize = 14.sp, color = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.5f), lineHeight = 20.sp)
            }
            Box(modifier = Modifier.width(52.dp).height(30.dp).background(color = if (isChecked) Color(0xFF32D74B) else if (isFocused) Color.LightGray else Color.White.copy(alpha = 0.2f), shape = CircleShape).padding(4.dp), contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(modifier = Modifier.size(22.dp).background(Color.White, CircleShape))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsCycleItem(title: String, description: String, currentValue: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.3f), elevation = 16.dp))
    ) {
        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, fontSize = 14.sp, color = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.5f), lineHeight = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "◄", color = if (isFocused) Color.DarkGray else Color.Transparent, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = currentValue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "►", color = if (isFocused) Color.DarkGray else Color.Transparent, fontSize = 14.sp)
            }
        }
    }
}