package com.luminastreams.tv.presentation.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.data.local.iptv.ChannelEntity


@Composable
fun IptvScreen(
    viewModel: IptvViewModel,
    onPlayChannel: (String) -> Unit
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val showQr by viewModel.showQrScreen.collectAsStateWithLifecycle()
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()
    val focusedEpg by viewModel.focusedEpg.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (channels.isEmpty() && !showQr) {
            EmptyStateSetup(onSetupClick = { viewModel.openQrSetup() })
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                CategoriesSidebar(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    onGroupSelect = { viewModel.selectGroup(it) },
                    modifier = Modifier.weight(0.25f)
                )

                Column(modifier = Modifier.weight(0.75f).fillMaxHeight()) {
                    // Guide Banner
                    Box(modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 24.dp, end = 24.dp)
                        .background(Color(0xFF121212), MaterialTheme.shapes.medium).padding(16.dp)) {
                        focusedEpg?.let { epg ->
                            Column {
                                Text(text = "משודר כעת: ${epg.title}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text(text = epg.description, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } ?: Text("אין מידע EPG", color = Color.Gray)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(top = 16.dp, end = 24.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onPlayChannel(channel.streamUrl) },
                                onFocus = { viewModel.onChannelFocused(channel.id) }
                            )
                        }
                    }
                }
            }
        }
        if (showQr) QrSetupOverlay(ipAddress, onClose = { viewModel.closeQrSetup() })
    }
}

@Composable
private fun EmptyStateSetup(onSetupClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSetupClick) { Text("הוסף פלייליסט חדש") }
    }
}

@Composable
private fun CategoriesSidebar(
    groups: List<String>,
    selectedGroup: String,
    onGroupSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TvLazyColumn(modifier = modifier.fillMaxHeight().padding(16.dp)) {
        items(groups, key = { it }) { group ->
            val isSelected = group == selectedGroup
            Surface(
                onClick = { onGroupSelect(group) },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Color.White.copy(0.1f) else Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(text = group, color = if (isSelected) Color.White else Color.Gray, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelEntity,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) onFocus() },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A24))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit)
            } else {
                Text(text = channel.name, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}