package com.luminastreams.tv.presentation.iptv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.luminastreams.tv.data.local.iptv.PlaylistEntity
import com.luminastreams.tv.presentation.iptv.CrispTextField
import kotlinx.coroutines.flow.MutableStateFlow

private val ColorAccentGreen = Color(0xFF32D74B)
private val ColorAccentRed = Color(0xFFFF453A)
private val ColorGlass = Color.White.copy(alpha = 0.08f)

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
    viewModel: IptvSettingsViewModel = viewModel(),
    activePlaylist: PlaylistEntity? = null,
    onUpdatePlaylist: ((name: String, url: String, epgUrl: String) -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    onAddPlaylist: (() -> Unit)? = null
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

    // Editable playlist fields
    var editingPlaylist by remember { mutableStateOf(false) }
    var editName by remember(activePlaylist) { mutableStateOf(activePlaylist?.name ?: "") }
    var editUrl by remember(activePlaylist) { mutableStateOf(activePlaylist?.url ?: "") }
    var editEpg by remember(activePlaylist) { mutableStateOf(activePlaylist?.epgUrl ?: "") }

    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(top = 40.dp, bottom = 40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 40.dp)) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = if (isHeb) "הגדרות לומינה" else "Lumina Settings", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            }

            LazyColumn(modifier = Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp)) {

                // ─── PLAYLIST MANAGEMENT ───
                item { SettingsSectionTitle(if (isHeb) "פלייליסט" else "PLAYLIST") }

                if (activePlaylist != null) {
                    if (!editingPlaylist) {
                        // Show current playlist info
                        item {
                            PlaylistInfoCard(
                                playlist = activePlaylist,
                                onEdit = { editingPlaylist = true },
                                onDelete = onDeletePlaylist,
                                modifier = Modifier
                                    .focusRequester(firstItemFocus)
                                    .onGloballyPositioned {
                                        if (!hasFocused) {
                                            try { firstItemFocus.requestFocus(); hasFocused = true } catch (_: Exception) {}
                                        }
                                    }
                            )
                        }
                    } else {
                        // Editing mode — show text fields
                        item {
                            PlaylistEditCard(
                                name = editName,
                                url = editUrl,
                                epg = editEpg,
                                onNameChange = { editName = it },
                                onUrlChange = { editUrl = it },
                                onEpgChange = { editEpg = it },
                                onSave = {
                                    onUpdatePlaylist?.invoke(editName, editUrl, editEpg)
                                    editingPlaylist = false
                                },
                                onCancel = {
                                    editName = activePlaylist.name
                                    editUrl = activePlaylist.url
                                    editEpg = activePlaylist.epgUrl
                                    editingPlaylist = false
                                },
                                modifier = Modifier
                                    .focusRequester(firstItemFocus)
                                    .onGloballyPositioned {
                                        if (!hasFocused) {
                                            try { firstItemFocus.requestFocus(); hasFocused = true } catch (_: Exception) {}
                                        }
                                    }
                            )
                        }
                    }
                } else {
                    // No playlist — show add button
                    item {
                        SettingsActionItem(
                            title = if (isHeb) "הוספת פלייליסט" else "Add Playlist",
                            description = if (isHeb) "הגדר פלייליסט M3U חדש עם EPG אופציונלי." else "Configure a new M3U playlist with optional EPG guide.",
                            actionLabel = if (isHeb) "התחל" else "SETUP",
                            actionColor = ColorAccentGreen,
                            onClick = { onAddPlaylist?.invoke() },
                            modifier = Modifier
                                .focusRequester(firstItemFocus)
                                .onGloballyPositioned {
                                    if (!hasFocused) {
                                        try { firstItemFocus.requestFocus(); hasFocused = true } catch (_: Exception) {}
                                    }
                                }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)); SettingsSectionTitle(if (isHeb) "כללי" else "GENERAL") }
                item {
                    SettingsToggleItem(
                        title = if (isHeb) "הפעלה אוטומטית" else "Auto-Start on Boot",
                        description = if (isHeb) "הפעל את לומינה אוטומטית ונגן את הערוץ האחרון." else "Automatically launch Lumina and play the last viewed channel.",
                        isChecked = autoStart,
                        onClick = { viewModel.toggleAutoStart() }
                    )
                }
                item {
                    SettingsCycleItem(title = if (isHeb) "קטגוריה ברירת מחדל" else "Default Launch Category", description = if (isHeb) "בחר איזו קטגוריה נפתחת ראשונה." else "Choose which category opens first.", currentValue = viewModel.defaultCategoryOptions[categoryIdx], onClick = { viewModel.cycleCategory() })
                }
                item { Spacer(modifier = Modifier.height(24.dp)); SettingsSectionTitle(if (isHeb) "ניגון וסטרימינג" else "PLAYBACK & STREAMING") }
                item {
                    SettingsCycleItem(title = if (isHeb) "גודל באפר רשת" else "Network Buffer Size", description = if (isHeb) "הגדל את הבאפר אם השידורים נתקעים." else "Increase buffer if streams are stuttering on Wi-Fi.", currentValue = viewModel.bufferOptions[bufferIdx], onClick = { viewModel.cycleBuffer() })
                }
                item {
                    SettingsCycleItem(title = if (isHeb) "מנוע פענוח וידאו" else "Video Decoder Engine", description = if (isHeb) "עבור לפענוח תוכנה אם אתה רואה מסך שחור." else "Switch to Software decoding if you experience black screens.", currentValue = viewModel.decoderOptions[decoderIdx], onClick = { viewModel.cycleDecoder() })
                }
                item {
                    SettingsToggleItem(title = if (isHeb) "הרצה אחורה / Catchup" else "Time-Shift / Catchup", description = if (isHeb) "אפשר חזרה אחורה בזמן לערוצים נתמכים." else "Enable scrubbing back in time for supported channels.", isChecked = catchupEnabled, onClick = { viewModel.toggleCatchup() })
                }
                item { Spacer(modifier = Modifier.height(24.dp)); SettingsSectionTitle(if (isHeb) "תוכן ונתונים" else "CONTENT & DATA") }
                item {
                    SettingsCycleItem(title = if (isHeb) "סנכרון EPG אוטומטי" else "EPG Auto-Sync Interval", description = if (isHeb) "כל כמה זמן להוריד את מדריך השידורים." else "How often should the app download the TV Guide.", currentValue = viewModel.epgSyncOptions[epgSyncIdx], onClick = { viewModel.cycleEpgSync() })
                }
                item {
                    SettingsToggleItem(title = if (isHeb) "בקרת הורים (PIN)" else "Parental Control (PIN)", description = if (isHeb) "נעל קטגוריות ספציפיות עם קוד PIN." else "Lock specific categories with a 4-digit PIN.", isChecked = parentalControl, onClick = { viewModel.toggleParentalControl() })
                }
            }
        }
    }
}

// ─── PLAYLIST COMPONENTS ───

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistInfoCard(
    playlist: PlaylistEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).background(ColorAccentGreen.copy(0.3f), CircleShape))
                Box(Modifier.size(8.dp).background(ColorAccentGreen, CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name.ifBlank { if (isHeb) "הפלייליסט שלי" else "My Playlist" },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    text = if (isHeb) "פעיל · מחובר" else "Active · Connected",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ColorAccentGreen
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        PlaylistDetailRow(Icons.Default.Link, if (isHeb) "קישור M3U" else "M3U SOURCE", playlist.url)
        Spacer(Modifier.height(10.dp))
        PlaylistDetailRow(Icons.Default.Tv, if (isHeb) "קישור EPG" else "EPG GUIDE", playlist.epgUrl.ifBlank { if (isHeb) "לא הוגדר" else "Not configured" })

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                onClick = onEdit,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.08f), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (isHeb) "עריכה" else "Edit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (onDelete != null) {
                Surface(
                    onClick = onDelete,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = ColorAccentRed.copy(0.1f), focusedContainerColor = ColorAccentRed, contentColor = ColorAccentRed, focusedContentColor = Color.White),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (isHeb) "מחק" else "Delete", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.04f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, color = Color.White.copy(0.85f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistEditCard(
    name: String,
    url: String,
    epg: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onEpgChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Save button pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val savePulse by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    Box(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0D0D0D))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(ColorAccentGreen.copy(0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ColorAccentGreen, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (isHeb) "עריכת פלייליסט" else "Edit Playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (isHeb) "עדכן את הקישורים שלך" else "Update your stream links", fontSize = 12.sp, color = Color.White.copy(0.4f))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Fields — clean, no borders, just labels + inputs
            Text(if (isHeb) "שם" else "NAME", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ColorAccentGreen.copy(0.7f), letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            CrispTextField(value = name, label = if (isHeb) "שם פלייליסט" else "Playlist Name", onValueChange = onNameChange)

            Spacer(Modifier.height(20.dp))

            Text(if (isHeb) "מקור" else "SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF00BCD4).copy(0.7f), letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            CrispTextField(value = url, label = if (isHeb) "קישור M3U" else "M3U Link", onValueChange = onUrlChange)

            Spacer(Modifier.height(20.dp))

            Text(if (isHeb) "לוח שידורים" else "GUIDE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C4DFF).copy(0.7f), letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            CrispTextField(value = epg, label = if (isHeb) "קישור EPG (אופציונלי)" else "EPG Link (Optional)", onValueChange = onEpgChange, isLast = true)

            Spacer(Modifier.height(32.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Save — pulsing green glow
                Surface(
                    onClick = onSave,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = ColorAccentGreen.copy(savePulse), focusedContainerColor = ColorAccentGreen, contentColor = ColorAccentGreen, focusedContentColor = Color.White),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (isHeb) "שמור" else "Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                // Cancel
                Surface(
                    onClick = onCancel,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.06f), focusedContainerColor = Color.White, contentColor = Color.White.copy(0.7f), focusedContentColor = Color.Black),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (isHeb) "ביטול" else "Cancel", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ─── GENERIC SETTINGS COMPONENTS ───

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
            Box(modifier = Modifier.width(52.dp).height(30.dp).background(color = if (isChecked) ColorAccentGreen else if (isFocused) Color.LightGray else Color.White.copy(alpha = 0.2f), shape = CircleShape).padding(4.dp), contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart) {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsActionItem(title: String, description: String, actionLabel: String, actionColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Box(
                Modifier.background(if (isFocused) actionColor else actionColor.copy(0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (isFocused) Color.White else actionColor, letterSpacing = 1.sp)
            }
        }
    }
}