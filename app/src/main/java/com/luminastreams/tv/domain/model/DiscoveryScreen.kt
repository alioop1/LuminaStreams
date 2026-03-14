@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package com.luminastreams.tv.presentation.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.presentation.home.HomeState
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.presentation.home.NetflixContentRow

@Composable
fun DiscoveryScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    mediaType: String,
    onMovieClick: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(mediaType) {
        val targetTab = if (mediaType == "tv") "סדרות" else "סרטים"
        if (state.selectedTab != targetTab) viewModel.selectTab(targetTab)
    }

    val movieGenres = listOf(
        "28" to "פעולה", "12" to "הרפתקאות", "16" to "אנימציה", "35" to "קומדיה",
        "80" to "פשע", "99" to "דוקו", "18" to "דרמה", "878" to "מדע בדיוני",
        "53" to "מותחן", "27" to "אימה", "10751" to "משפחה", "14" to "פנטזיה"
    )

    val tvGenres = listOf(
        "10759" to "אקשן והרפתקאות", "16" to "אנימציה", "35" to "קומדיה",
        "80" to "פשע", "99" to "דוקו", "18" to "דרמה", "10762" to "ילדים",
        "9648" to "מסתורין", "10765" to "מדע בדיוני", "10768" to "מלחמה ופוליטיקה"
    )

    val activeGenres = if (mediaType == "tv") tvGenres else movieGenres

    BackHandler(enabled = state.isFilterComplete) { viewModel.clearGenre() }

    val currentBg = state.focusedItem
    val imageRequest = remember(currentBg?.backdropUrl, currentBg?.posterUrl) {
        ImageRequest.Builder(context)
            .data(currentBg?.backdropUrl?.takeIf { it.isNotEmpty() } ?: currentBg?.posterUrl)
            .crossfade(800)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (state.isFilterComplete) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.45f
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black),
                        startY = 300f
                    )
                )
            )
        }

        Crossfade(
            targetState = state.isFilterComplete,
            animationSpec = tween(500),
            label = "discovery_crossfade"
        ) { showResults ->
            if (!showResults) {
                GenreSelectionGrid(mediaType = mediaType, activeGenres = activeGenres, viewModel = viewModel)
            } else {
                when {
                    state.isLoading -> DiscoveryLoadingView()
                    state.discoveryResults.isEmpty() -> DiscoveryEmptyStateView()
                    else -> DiscoveryResultsView(state = state, viewModel = viewModel, onMovieClick = onMovieClick)
                }
            }
        }
    }
}

@Composable
private fun GenreSelectionGrid(
    mediaType: String,
    activeGenres: List<Pair<String, String>>,
    viewModel: HomeViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 96.dp, start = 96.dp, end = 96.dp)
    ) {
        Text(
            text = if (mediaType == "tv") "בחר ז'אנר סדרות" else "בחר ז'אנר סרטים",
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(48.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 64.dp),
            modifier = Modifier.fillMaxSize().focusRestorer()
        ) {
            items(activeGenres) { (id, name) ->
                GenreCard(name = name, onClick = { viewModel.setGenreFilter(id, name) })
            }
        }
    }
}

@Composable
private fun GenreCard(name: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color(0xFF222222)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = Modifier
            .height(140.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0x60E50914), Color.Transparent))
                )
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = name, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun DiscoveryResultsView(
    state: HomeState,
    viewModel: HomeViewModel,
    onMovieClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp)) {
        NetflixContentRow(
            title = "תוצאות עבור: ${state.selectedGenreName}",
            movies = state.discoveryResults,
            onFocus = { movie -> viewModel.updateFocusedItem(movie, state.selectedGenreName, true) },
            onClick = onMovieClick
        )
    }
}

@Composable
private fun DiscoveryLoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "טוען כותרים...",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            val infiniteTransition = rememberInfiniteTransition(label = "loading_anim")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                label = "loading_alpha"
            )
            Box(
                modifier = Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xFFE50914).copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun DiscoveryEmptyStateView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "לא נמצאו תוצאות", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "נסה לבחור ז'אנר אחר מהתפריט.", color = Color.Gray, fontSize = 20.sp)
        }
    }
}
