package com.luminastreams.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import com.luminastreams.tv.ui.theme.*

/**
 * Netflix-style poster card.
 * • 2:3 aspect ratio
 * • Spring-animated scale on focus (1.0 → 1.18)
 * • White border ring on focus
 * • Action overlay (Play + Add) slides up on focus
 * • Optional resolution badge (4K / HD)
 * • Optional progress bar (continue watching)
 * • Title visible only when focused
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    title: String = "",
    resolutionBadge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        )
    )

    Column(
        modifier = modifier
            .scale(cardScale)
            .padding(vertical = 18.dp)
    ) {
        // ── Card body ─────────────────────────────────────────────
        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f), // manual via cardScale
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor        = NfDarkBg,
                focusedContainerColor = NfDarkBg
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    BorderStroke(2.5.dp, TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                )
            ),
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .androidx.compose.ui.focus.onFocusChanged { isFocused = it.isFocused }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main artwork
                content()

                // Resolution badge
                if (resolutionBadge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OledBlack.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = resolutionBadge,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Focus overlay: action buttons
                AnimatedVisibility(
                    visible = isFocused,
                    enter = fadeIn(tween(150)) + slideInVertically { it / 3 },
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, OledBlack.copy(alpha = 0.9f))
                                )
                            )
                            .padding(bottom = 10.dp, top = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Play mini-button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TextPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = OledBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Add mini-button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassBackground)
                                    .border(1.dp, GlassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Continue-watching progress bar
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomStart)
                            .background(TextMuted.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(NetflixRed)
                        )
                    }
                }
            }
        }

        // Title below card — visible only on focus
        AnimatedVisibility(
            visible = isFocused,
            enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(100))
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}
