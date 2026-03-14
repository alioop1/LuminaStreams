package com.luminastreams.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.luminastreams.tv.ui.theme.NetflixRed

@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    resolutionBadge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = modifier
    ) {

        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
            content()

            if (resolutionBadge != null) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = resolutionBadge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = isFocused, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                androidx.tv.material3.IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0x99000000))
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            if (progress != null) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart).background(Color(0x80000000))) {
                    Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(NetflixRed))
                }
            }
        }
    }
}