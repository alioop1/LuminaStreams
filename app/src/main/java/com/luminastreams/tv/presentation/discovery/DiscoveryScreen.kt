@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package com.luminastreams.tv.presentation.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.presentation.home.*

private val C_BG  = Color(0xFF000000)
private val C_RED = Color(0xFFE50914)
private val C_WH  = Color(0xFFFFFFFF)
private val C_DIM = Color(0xAAFFFFFF)

@Composable
fun DiscoveryScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    mediaType: String,
    onMovieClick: (String) -> Unit
) {
    val ctx = LocalContext.current

    LaunchedEffect(mediaType) {
        val target = if (mediaType == "tv") "סדרות" else "סרטים"
        if (state.selectedTab != target) viewModel.selectTab(target)
    }

    val movieGenres = listOf(
        "28" to "פעולה", "12" to "הרפתקאות", "16" to "אנימציה", "35" to "קומדיה",
        "80" to "פשע",         "99" to "דוקו",         "18" to "דרמה",         "878" to "מדע בדיוני",
        "53" to "מותחן",      "27" to "אימה",         "10751" to "משפחה", "14" to "פנטזיה"
    )
    val tvGenres = listOf(
        "10759" to "אקשן",    "16" to "אנימציה",  "35" to "קומדיה",
        "80" to "פשע",          "99" to "דוקו",          "18" to "דרמה",
        "10762" to "ילדים",  "9648" to "מסתורין",   "10765" to "מדע בדיוני"
    )
    val genres = if (mediaType == "tv") tvGenres else movieGenres

    BackHandler(enabled = state.sFilterComplete) { viewModel.clearGenre() }

    Box(Modifier.fillMaxSize().background(C_BG)) {
        if (state.isFilterComplete && state.focusedItem != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(state.focusedItem.backdropUrl ?: state.focusedItem.posterUrl)
                    .crossfade(600).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
                alpha = 0.30f
            )
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(C_BG.copy(0.6f), C_BG.copy(0.95f), C_BG))
            ))
        }

        AnimatedContent(
            targetState    = state.isFilterComplete,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(360)) { 50 }) togetherWith
                (fadeOut(tween(200)) + slideOutVertically(tween(240)) { -30 })
            },
            label = "disc_content"
        ) { showResults ->
            if (!showResults) {
                GenreGrid(
                    mediaType = mediaType,
                    genres    = genres,
                    onPick    = { id, name -> viewModel.setGenreFilter(id, name) }
                )
            } else {
                when {
                    state.isLoading                  -> DiscLoader()
                    state.discoveryResults.isEmpty() -> DiscEmpty()
                    else -> DiscResults(
                        state     = state,
                        viewModel = viewModel,
                        onClick   = onMovieClick
                    )
                }
            }
        }
    }
}

// ─── Genre Grid ──────────────────────────────────────────────────────────────
@Composable
private fun GenreGrid(
    mediaType: String,
    genres: List<Pair<String, String>>,
    onPick: (String, String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 80.dp, start = 64.dp, end = 64.dp)
    ) {
        Text(
            if (mediaType == "tv") "בחר ז'אנר סדרות" else "בחר ז'אנר סרטים",
            color = C_WH, fontSize = 42.sp, fontWeight = FontWeight.Black
        )
        Box(Modifier.padding(top = 6.dp, bottom = 28.dp).width(52.dp).height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(C_RED))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement   = Arrangement.spacedBy(16.dp),
            contentPadding        = PaddingValues(bottom = 48.dp),
            modifier              = Modifier.fillMaxSize().focusRestorer()
        ) {
            items(genres) { (id, name) ->
                GenreCard(name = name, onClick = { onPick(id, name) })
            }
        }
    }
}

@Composable
private fun GenreCard(name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    )
    val borderColor by animateColorAsState(if (focused) C_WH else C_WH.copy(0.15f), tween(150))
    val cardShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp), RoundedCornerShape(14.dp))

    Box(
        Modifier.height(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(
                containerColor        = C_WH.copy(0.07f),
                focusedContainerColor = C_WH.copy(0.13f)
            ),
            shape   = cardShape,
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(C_RED.copy(0.45f), 18.dp)),
            modifier = Modifier.fillMaxSize()
                .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(C_RED.copy(if (focused) 0.22f else 0.08f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end   = androidx.compose.ui.geometry.Offset(300f, 300f)
                    )
                ))
                Text(
                    name,
                    color      = if (focused) C_WH else C_WH.copy(0.80f),
                    fontSize   = 22.sp,
                    fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.align(Alignment.Center).padding(12.dp)
                )
                AnimatedVisibility(
                    visible  = focused,
                    enter    = fadeIn(tween(130)) + expandHorizontally(tween(200)),
                    exit     = fadeOut(tween(100)) + shrinkHorizontally(tween(150)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(
                        Brush.horizontalGradient(listOf(Color.Transparent, C_RED, Color.Transparent))
                    ))
                }
            }
        }
    }
}

// ─── Results ─────────────────────────────────────────────────────────────────
@Composable
private fun DiscResults(state: HomeState, viewModel: HomeViewModel, onClick: (String) -> Unit) {
    var focusedIdx by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 56.dp)) {
        Text(
            text       = "תוצאות: ${state.selectedGenreName}",
            color      = C_WH,
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(bottom = 16.dp, end = 56.dp)
        )
        LazyRow(
            contentPadding        = PaddingValues(end = 56.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(state.discoveryResults, key = { _, m -> m.id }) { idx, movie ->
                NfCard(
                    movie             = movie,
                    isFocusedOverride = idx == focusedIdx,
                    onFocused         = {
                        focusedIdx = idx
                        viewModel.updateFocusedItem(movie, state.selectedGenreName, true)
                    },
                    onClick = { onClick(movie.id) }
                )
            }
        }
    }
}

// ─── Loader ──────────────────────────────────────────────────────────────────
@Composable
private fun DiscLoader() {
    val inf = rememberInfiniteTransition(label = "dl")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "dp"
    )
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("טוען...", color = C_DIM, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier.width(180.dp).height(4.dp)
                    .clip(RoundedCornerShape(50)).background(C_WH.copy(0.12f))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p)
                    .clip(RoundedCornerShape(50)).background(C_RED))
            }
        }
    }
}

// ─── Empty ───────────────────────────────────────────────────────────────────
@Composable
private fun DiscEmpty() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🎦", fontSize = 44.sp)
            Text("לא נמצאו תוצאות", color = C_WH, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("נסה ז'אנר אחר", color = C_DIM, fontSize = 16.sp)
        }
    }
}
