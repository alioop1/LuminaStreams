@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale as ContentScaleImport
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

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.material.icons.filled.PlayArrow
import com.luminastreams.tv.presentation.details.BackgroundTrailerView
import kotlinx.coroutines.delay

private val TMDB_WIDTH_REGEX = Regex("/w\\d+/")

@Composable
fun BackdropLayer(
    hero: Movie?,
    trailerKey: String? = null,
    isNavFocused: Boolean = false,
    onTrailerPlayingChanged: ((Boolean) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val heroUrl = (hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl)
        ?.replace(TMDB_WIDTH_REGEX, "/original/")

    var isTrailerActive by remember(hero?.id) { mutableStateOf(false) }

    // Start trailer ONLY if user stands on this card for >= 3.5s (smart debounce)
    LaunchedEffect(hero?.id, trailerKey, isNavFocused) {
        isTrailerActive = false
        if (!trailerKey.isNullOrBlank() && !isNavFocused) {
            val dwellTimeMs = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 4000L else 3500L
            delay(dwellTimeMs)
            isTrailerActive = true
        }
    }

    LaunchedEffect(isTrailerActive) {
        onTrailerPlayingChanged?.invoke(isTrailerActive)
    }

    val backdropAlpha by animateFloatAsState(
        targetValue = if (isTrailerActive) 0f else 1f,
        animationSpec = tween(durationMillis = 1200),
        label = "home_backdrop_alpha"
    )

    Box(Modifier.fillMaxSize().background(BG)) {
        // 1. Live Trailer Layer (Plays in background behind hero)
        if (isTrailerActive && !trailerKey.isNullOrBlank() && !isNavFocused) {
            key(trailerKey) {
                BackgroundTrailerView(
                    videoId = trailerKey,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                )
            }
        }

        // 2. High-res Backdrop Image (Smoothly fades out over 1.2s when trailer starts)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha)
        ) {
            if (!heroUrl.isNullOrBlank()) {
                val heroRequest = remember(heroUrl) {
                    ImageRequest.Builder(ctx).data(heroUrl)
                        .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 500)
                        .build()
                }
                AsyncImage(
                    model = heroRequest,
                    contentDescription = null,
                    contentScale = ContentScaleImport.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Cinematic Gradients (Ensures Hebrew/RTL text & bottom card rows are ALWAYS 100% crisp)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val isRtl = layoutDirection == LayoutDirection.Rtl

            // Horizontal shadow behind text (Right side for RTL, Left side for LTR)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (isRtl) listOf(Color.Transparent, Color(0x33060608), Color(0xCC060608), Color(0xF5060608))
                             else listOf(Color(0xF5060608), Color(0xCC060608), Color(0x33060608), Color.Transparent),
                    startX = if (isRtl) size.width * 0.25f else 0f,
                    endX = if (isRtl) size.width else size.width * 0.75f
                )
            )

            // Vertical vignette at the bottom (protects cards from video background)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent, 
                        Color.Black.copy(alpha = 0.35f), 
                        Color.Black.copy(alpha = 0.85f),
                        Color(0xFF060608)
                    ),
                    startY = h * 0.35f,
                    endY = h
                )
            )
        }

        // 4. Subtle Top Bar Vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun HeroOverlay(
    hero: Movie?,
    isEnriching: Boolean = false,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    runtime: Int? = null,
    cast: List<CastMember> = emptyList(),
    localizedOverview: String? = null,
    localizedGenre: String? = null,
    isTrailerPlaying: Boolean = false
) {
    val ctx = LocalContext.current
    val align = Alignment.Start

    Box(
        modifier = modifier
            .fillMaxWidth(0.55f)
            .fillMaxHeight()
            // We use padding to perfectly place the text at the bottom
            .padding(bottom = 32.dp, start = 56.dp, end = 56.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Crossfade(
            targetState = hero,
            animationSpec = tween(400),
            label = "hero_cross"
        ) { m ->
            if (m != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = align
                ) {
                    // Title or Logo
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (!logoUrl.isNullOrBlank()) {
                            val logoRequest = remember(logoUrl) {
                                ImageRequest.Builder(ctx).data(logoUrl).crossfade(300).build()
                            }
                            AsyncImage(
                                model = logoRequest,
                                contentDescription = m.title,
                                contentScale = ContentScaleImport.Fit,
                                modifier = Modifier
                                    .heightIn(max = 140.dp)
                                    .widthIn(max = 480.dp)
                            )
                        } else if (!isEnriching) {
                            val tsz = when {
                                m.title.length > 26 -> 36.sp
                                m.title.length > 16 -> 48.sp
                                else -> 64.sp
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
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(color = Color.Black, blurRadius = 16f, offset = Offset(0f, 4f))
                                )
                            )
                        }
                    }

                    // Clean Cinematic Metadata Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        @Composable
                        fun Separator() {
                            Text(
                                text = "|",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        var needsSeparator = false

                        // Trailer Active Badge
                        if (isTrailerPlaying) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE50914))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = tr("TRAILER", "טריילר"),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            needsSeparator = true
                        }

                        // Rating
                        if (m.rating > 0f) {
                            if (needsSeparator) Separator()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("★", color = Color.White, fontSize = 14.sp)
                                Text(
                                    text = "%.1f".format(m.rating), 
                                    color = Color.White.copy(alpha = 0.9f), 
                                    fontSize = 15.sp, 
                                    fontWeight = FontWeight.Bold,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                    )
                                )
                            }
                            needsSeparator = true
                        }
                        
                        // Runtime
                        val runtimeStr = if (runtime != null && runtime > 0) {
                            val h = runtime / 60
                            val min = runtime % 60
                            if (h > 0) "${h}${tr("h", "שע")} ${min}${tr("m", "ד")}" else "${min}${tr("m", "ד")}"
                        } else null

                        if (runtimeStr != null) {
                            if (needsSeparator) Separator()
                            Text(
                                text = runtimeStr, 
                                color = Color.White.copy(alpha = 0.85f), 
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                )
                            )
                            needsSeparator = true
                        }

                        // Year
                        if (m.year > 0) {
                            if (needsSeparator) Separator()
                            Text(
                                text = m.year.toString(), 
                                color = Color.White.copy(alpha = 0.85f), 
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                )
                            )
                            needsSeparator = true
                        }

                        // Age Rating
                        if (needsSeparator) Separator()
                        Text(
                            text = "16+", 
                            color = Color.White.copy(alpha = 0.85f), 
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(color = Color.Black, blurRadius = 8f)
                            )
                        )
                        needsSeparator = true

                        // Genre
                        val genreText = localizedGenre ?: m.genre
                        if (genreText.isNotBlank()) {
                            if (needsSeparator) Separator()
                            Text(
                                text = (genreText.split(",", "·", "•", "/", "|").firstOrNull()?.trim() ?: genreText).uppercase(), 
                                color = Color.White.copy(alpha = 0.85f), 
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(color = Color.Black, blurRadius = 8f)
                                )
                            )
                        }
                    }

                    // Synopsis with HBO Max Style Dynamic Gradient Accent Line
                    val synopsisText = localizedOverview ?: m.overview
                    if (synopsisText.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min), // Forces the Row to be exactly as tall as the text
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Dynamic Gradient Accent Line
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight() // Stretches exactly to the intrinsic height of the text
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF00E5FF), // Cyan
                                                Color(0xFFD500F9), // Purple/Magenta
                                                Color(0xFFFF3D00)  // Deep Orange
                                            )
                                        )
                                    )
                            )
                            
                            // Text
                            Text(
                                text = synopsisText,
                                color = Color.White.copy(alpha = 0.95f), // Highly readable
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 4, // Expanded to 4 lines to fit complex synopses
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f), 
                                        blurRadius = 16f, 
                                        offset = Offset(0f, 4f)
                                    ),
                                    textDirection = androidx.compose.ui.text.style.TextDirection.Content
                                )
                            )
                        }
                    }

                    // Credits
                    if (cast.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Prefix
                            Text(
                                text = tr("Starring:", "בכיכובם של:"),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
                                )
                            )
                            
                            // Actors with photos
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                cast.take(4).forEach { actor ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!actor.profileUrl.isNullOrBlank()) {
                                            val actorRequest = remember(actor.profileUrl) {
                                                ImageRequest.Builder(ctx).data(actor.profileUrl).crossfade(true).build()
                                            }
                                            AsyncImage(
                                                model = actorRequest,
                                                contentDescription = actor.name,
                                                contentScale = ContentScaleImport.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            // Fallback Icon
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        
                                        Text(
                                            text = actor.name,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = androidx.compose.ui.text.TextStyle(
                                                shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 8f)
                                            )
                                        )
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