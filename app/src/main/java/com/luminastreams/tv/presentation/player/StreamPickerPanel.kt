package com.luminastreams.tv.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape // הייבוא שתוקן
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check // הוחלף מ-CloudDone
import androidx.compose.material.icons.filled.Person // הוחלף מ-Group
import androidx.compose.material.icons.filled.Menu // הוחלף מ-Storage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.luminastreams.tv.domain.model.StreamSource
import com.luminastreams.tv.ui.components.LoadingIndicator
import com.luminastreams.tv.ui.theme.NetflixRed
import com.luminastreams.tv.ui.theme.TextPrimary
import com.luminastreams.tv.ui.theme.TextSecondary

@Composable
fun StreamPickerPanel(
    isVisible: Boolean,
    state: StreamPickerState,
    onClose: () -> Unit,
    onFilterSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onSourceSelect: (StreamSource) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(animationSpec = tween(400), initialOffsetX = { if (isRtl) -it else it }),
        exit = slideOutHorizontally(animationSpec = tween(400), targetOffsetX = { if (isRtl) -it else it })
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xB3000000))) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)
                    .background(Color(0xFF0F0F0F))
                    .padding(24.dp)
            ) {
                Text(if (isRtl) "בחר מקור ניגון" else "Select Source", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val filters = listOf("All", "4K", "1080p")
                        items(filters) { filter ->
                            Button(
                                onClick = { onFilterSelect(filter) },
                                colors = ButtonDefaults.colors(containerColor = if (state.currentFilter == filter) NetflixRed else Color(0x33FFFFFF), focusedContainerColor = Color.White),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(filter, fontWeight = FontWeight.Bold, color = if (state.currentFilter == filter) Color.White else Color.LightGray)
                            }
                        }
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    TvLazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(items = state.filteredSources, key = { it.id }) { source ->
                            StreamItemCard(source = source, isResolving = state.resolvingLinkId == source.id, isRtl = isRtl, onClick = { onSourceSelect(source) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamItemCard(source: StreamSource, isResolving: Boolean, isRtl: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF2A2A2A)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(border = BorderStroke(2.dp, NetflixRed), shape = RoundedCornerShape(8.dp))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(source.groupName, color = NetflixRed, fontWeight = FontWeight.Black, fontSize = 16.sp)

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(Icons.Default.Menu, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${source.sizeGb} GB", color = TextSecondary, fontSize = 14.sp)
                }

                if (source.isCached) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x334CAF50)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RD+ Cached", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("⬆ ${source.seeders} Seeders", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isResolving) {
                Text(if (isRtl) "מפענח לינק מול Real-Debrid..." else "Resolving link...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(source.filename, color = TextPrimary, fontSize = 13.sp, maxLines = 2, lineHeight = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamBadge(source.resolution, if (source.resolution == "4K") Color(0xFFE50914) else Color(0xFF1E88E5))
                StreamBadge(source.codec, Color(0xFF424242))
                StreamBadge(source.audioFormat, Color(0xFF512DA8))

                if (source.isDV) StreamBadge("Dolby Vision", Color(0xFF000000))
                else if (source.isHDR10) StreamBadge("HDR10", Color(0xFF000000))

                if (source.hasBuiltInSubs) StreamBadge("Subs", Color.DarkGray)
            }
        }
    }
}

@Composable
fun StreamBadge(text: String, bgColor: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bgColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}