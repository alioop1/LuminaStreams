@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.tr

// ─────────────────────────────────────────────────────────────────────
// ⚡ OPTION 4 — CLEAN VERTICAL SPLIT · Ultra Premium
//    Full backdrop on one side, SMOOTH horizontal fade to black,
//    NO diagonal lines. Content on pure black side.
//    Static layout — zero jumps between movies.
// ─────────────────────────────────────────────────────────────────────

private val CYAN        = Color(0xFF00E5FF)
private val CYAN_DIM    = Color(0xFF0091EA)
private val AMBER       = Color(0xFFFFB300)
private val GLASS_BG    = Color(0x14FFFFFF)

// ─────────────────────────────────────────────────────────────────────
// BACKDROP LAYER — Full-screen image + smooth horizontal fade
// ─────────────────────────────────────────────────────────────────────

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val heroUrl = (hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl)
        ?.replace("/w780/", "/original/")
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val dm = ctx.resources.displayMetrics
    val screenW = dm.widthPixels
    val screenH = dm.heightPixels

    Box(Modifier.fillMaxSize().background(BG)) {
        // 1. Full-screen backdrop image
        if (!heroUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(heroUrl)
                    .size(screenW, screenH)
                    .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 10% dark overlay over the entire image instead of horizontal fade
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 10% shadow over the whole thing
            drawRect(color = Color(0x1A000000))

            // Bottom fade — smooth transition to poster rows
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BG.copy(alpha = 0.7f), BG),
                    startY = h * 0.55f,
                    endY = h
                )
            )
        }

        // 3. Top vignette (navbar readability)
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(
                    0.0f to Color(0x66000000),
                    0.15f to Color.Transparent
                ))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// HERO OVERLAY — Static layout, zero jumps between movies
// ─────────────────────────────────────────────────────────────────────

@Composable
fun HeroOverlay(
    hero: Movie?,
    isEnriching: Boolean = false,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    runtime: Int? = null,
    cast: List<String> = emptyList(),
    localizedOverview: String? = null,
    localizedGenre: String? = null
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ctx = LocalContext.current
    // Compose natively handles RTL! 
    // Start = Right in RTL, Left in LTR. End = Left in RTL, Right in LTR.
    val align = Alignment.Start
    val textAlign = TextAlign.Start

    // Static outer container — FIXED SIZE, never changes between movies
    Box(
        modifier = modifier
            .fillMaxWidth(0.44f)  // Increased width to give more room
            .fillMaxHeight()
            .padding(bottom = 32.dp, start = 48.dp, end = 48.dp), // Move up and add side padding
        contentAlignment = Alignment.BottomStart
    ) {
        // Crossfade for smooth transitions without layout jumps
        Crossfade(
            targetState = hero,
            animationSpec = tween(400),
            label = "hero_cross"
        ) { m ->
            if (m != null) {
                // Inner column with FIXED spacer heights
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = align
                ) {
                    // ── 1. MOVIE LOGO or styled title — fixed height zone ──
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 130.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (!logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(logoUrl)
                                    .crossfade(300)
                                    .build(),
                                contentDescription = m.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .heightIn(max = 120.dp)
                                    .widthIn(max = 440.dp)
                            )
                        } else if (!isEnriching) {
                            val tsz = when {
                                m.title.length > 26 -> 28.sp
                                m.title.length > 16 -> 36.sp
                                else -> 44.sp
                            }
                            Text(
                                text = m.title,
                                color = WHITE,
                                fontSize = tsz,
                                fontWeight = FontWeight.Black,
                                lineHeight = (tsz.value * 1.1f).sp,
                                letterSpacing = (-0.5).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = textAlign
                            )
                        }
                    }

                    // ── Decorative cyan horizontal line ──
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(CYAN, CYAN.copy(alpha = 0.1f))
                                ),
                                RoundedCornerShape(1.dp)
                            )
                            .align(align)
                    )

                    // ── FLOATING METADATA AREA (No Background Frame) ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = align
                    ) {
                        // ── 2. AUTHENTIC METADATA TAGS ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            if (m.rating > 0f) {
                                // IMDB Style Rating
                                Text("TMDB", color = Color(0xFF01B4E4), fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text(
                                    text = "%.1f".format(m.rating),
                                    color = AMBER,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Year
                            Text(m.year.toString(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            
                            // Age Rating
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("16+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Quality Badges (Authentic from TMDB/IMDB logic)
                            if (m.is4K) {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("4K", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            if (m.resolutionBadge.isNotBlank() && m.resolutionBadge != "1080p") {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(m.resolutionBadge.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // HDR Badge (Authentic if available)
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("HDR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        // ── 3. GENRE ──
                        Text(
                            text = localizedGenre ?: m.genre,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // ── 4. PREMIUM GLASS SYNOPSIS BOX ──
                        val synopsisText = localizedOverview ?: m.overview
                        if (synopsisText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .drawBehind {
                                        // Glowing top border effect
                                        val strokeWidth = 1.dp.toPx()
                                        val gradient = Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.White, Color.White.copy(alpha = 0.5f), Color.Transparent)
                                        )
                                        drawLine(
                                            brush = gradient,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = strokeWidth
                                        )
                                    }
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = synopsisText,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── Premium Feature Badge ──
@Composable
private fun FeatureBadge(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color(0x12FFFFFF), RoundedCornerShape(6.dp))
            .border(0.5.dp, Color(0x20FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CYAN.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = Color(0x88FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MetaDot() = Text("  ·  ", color = DIM3, fontSize = 14.sp)