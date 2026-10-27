package com.luminastreams.tv.presentation.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.presentation.iptv.IptvViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val EpgAccentLive = Color(0xFF00E5FF)
private val EpgAccentCatchup = Color(0xFFFF9500)
private val EpgGlass = Color.White.copy(alpha = 0.08f)
private val EpgDark = Color(0xFF0A0A0C)
private val epgTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgGuideOverlay(
    channel: ChannelEntity,
    viewModel: IptvViewModel,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onPlayCatchup: (String) -> Unit,
    onPlayLive: () -> Unit
) {
    val now = System.currentTimeMillis()

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Date tabs: Today + 6 days back
    val dateTabs = remember(isRtl) {
        (0..6).map { daysBack ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000
            val label = when (daysBack) {
                0 -> if (isRtl) "היום" else "Today"
                1 -> if (isRtl) "אתמול" else "Yesterday"
                else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(cal.timeInMillis))
            }
            Triple(label, dayStart, dayEnd)
        }
    }

    var selectedDateIndex by remember { mutableIntStateOf(0) }
    var programs by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val firstProgramFR = remember { FocusRequester() }

    // Load programs when date changes
    LaunchedEffect(selectedDateIndex, channel) {
        isLoading = true
        val (_, dayStart, dayEnd) = dateTabs[selectedDateIndex]
        programs = viewModel.getFullDayProgramsForChannel(channel, dayStart, dayEnd)
        isLoading = false
    }

    // Route focus to the first program after load
    LaunchedEffect(programs) {
        if (programs.isNotEmpty()) {
            delay(200)
            runCatching { firstProgramFR.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.85f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                    onClose(); true
                } else false
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, EpgDark.copy(0.95f), EpgDark, EpgDark)
                    )
                )
                .padding(top = 40.dp)
        ) {
            // ── HEADER: Channel info ──
            Row(
                modifier = Modifier.padding(horizontal = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel logo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(channel.name.take(1), color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "מדריך שידורים" else "TV Guide",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = channel.name,
                        color = Color.White.copy(0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                // Catch-up badge
                Box(
                    modifier = Modifier
                        .background(EpgAccentCatchup.copy(0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, EpgAccentCatchup, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↺ הקלטה 7 ימים" else "↺ 7-DAY CATCH-UP", color = EpgAccentCatchup, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── DATE TABS ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 64.dp),
                modifier = Modifier.focusGroup()
            ) {
                itemsIndexed(dateTabs) { idx, (label, _, _) ->
                    val isSelected = idx == selectedDateIndex
                    Surface(
                        onClick = { selectedDateIndex = idx },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) Color.White else EpgGlass,
                            focusedContainerColor = Color.White,
                            contentColor = if (isSelected) Color.Black else Color.White.copy(0.7f),
                            focusedContentColor = Color.Black
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = if (idx == 0) Modifier.focusRequester(focusRequester) else Modifier
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (idx == 0) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(EpgAccentLive, RoundedCornerShape(50))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── PROGRAMS TIMELINE ──
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "טוען לוח שידורים..." else "Loading schedule...", color = Color.White.copy(0.5f), fontSize = 16.sp)
                }
            } else if (programs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "אין לוח שידורים זמין" else "No schedule available", color = Color.White.copy(0.5f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "נתוני EPG לא מכסים תאריך זה" else "EPG data may not cover this date", color = Color.White.copy(0.3f), fontSize = 14.sp)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 48.dp),
                    modifier = Modifier.weight(1f).focusGroup()
                ) {
                    itemsIndexed(programs, key = { i, p -> "${p.startTime}_$i" }) { idx, program ->
                        val isLive = now in program.startTime..program.endTime
                        val isPast = program.endTime < now


                        EpgProgramCard(
                            program = program,
                            isLive = isLive,
                            isPast = isPast,

                            currentTime = now,
                            modifier = if (idx == 0) Modifier.focusRequester(firstProgramFR) else Modifier,
                            onClick = {
                                when {
                                    isLive -> onPlayLive()
                                    isPast -> {
                                        val catchupUrl = viewModel.buildCatchupUrl(channel.streamUrl, program.startTime)
                                        onPlayCatchup(catchupUrl)
                                    }
                                    // Future programs — just play live
                                    else -> onPlayLive()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgProgramCard(
    program: EpgProgramEntity,
    isLive: Boolean,
    isPast: Boolean,

    currentTime: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val durationMinutes = ((program.endTime - program.startTime) / 60000).coerceAtLeast(1)
    val widthDp = (durationMinutes * 5).toInt().coerceIn(200, 500).dp
    val timeString = "${epgTimeFormat.format(Date(program.startTime))} - ${epgTimeFormat.format(Date(program.endTime))}"

    Surface(
        onClick = onClick,
        modifier = modifier.width(widthDp).height(120.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isLive -> EpgAccentLive.copy(alpha = 0.15f)
                isPast -> Color(0xFF1A1A1E)
                else -> EpgGlass
            },
            focusedContainerColor = Color(0xFF1F1F23)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, if (isLive) EpgAccentLive else if (isPast) EpgAccentCatchup else Color.White
                )
            ),
            border = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = (if (isLive) EpgAccentLive else if (isPast) EpgAccentCatchup else Color.White).copy(0.2f),
                elevation = 16.dp
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Time + status badge row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeString,
                        color = when {
                            isLive -> EpgAccentLive
                            isPast -> Color.White.copy(0.4f)
                            else -> Color.White.copy(0.6f)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    when {
                        isLive -> {
                            Box(
                                Modifier
                                    .background(EpgAccentLive, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "● שידור חי" else "● LIVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        isPast -> {
                            Box(
                                Modifier
                                    .background(EpgAccentCatchup.copy(0.2f), RoundedCornerShape(4.dp))
                                    .border(1.dp, EpgAccentCatchup, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↺ הקלטה" else "↺ CATCHUP", color = EpgAccentCatchup, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        else -> {
                            Box(
                                Modifier
                                    .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "בקרוב" else "UPCOMING", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Title
                Text(
                    text = program.title.ifBlank { if (LocalLayoutDirection.current == LayoutDirection.Rtl) "תוכניה לא ידועה" else "Unknown Program" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description (if available)
                if (program.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = program.description,
                        color = Color.White.copy(0.4f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duration
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${durationMinutes}min",
                    color = Color.White.copy(0.3f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live progress bar
            if (isLive && program.startTime > 0) {
                val progress = ((currentTime - program.startTime).toFloat() / (program.endTime - program.startTime).toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(EpgAccentLive)
                    )
                }
            } else if (isPast) {
                // Full orange bar for past programs (catch-up available)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(EpgAccentCatchup.copy(0.4f))
                )
            }
        }
    }
}
