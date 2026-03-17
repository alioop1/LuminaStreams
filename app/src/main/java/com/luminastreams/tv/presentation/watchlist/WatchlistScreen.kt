@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Text
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.presentation.home.PosterCard
import kotlinx.coroutines.delay

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onNavigateBack: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    val movies by viewModel.movies.collectAsState()
    val backFR = remember { FocusRequester() }
    val firstItemFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadWatchlist()
        delay(150)
        runCatching { backFR.requestFocus() }
    }

    BackHandler { onNavigateBack() }

    Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
        Column(Modifier.fillMaxSize()) {

            // ── HEADER: Back Button + Logo + Title ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 40.dp, end = 48.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp).focusRequester(backFR),
                    colors = IconButtonDefaults.colors(
                        containerColor = Color(0x1AFFFFFF),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp))
                }

                Spacer(Modifier.width(32.dp))

                // Lumina Logo
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE50914)), Alignment.Center) {
                        Text("L", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("LUMINA",  color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 13.sp)
                        Text("STREAMS", color = Color(0xFFE50914), fontSize = 7.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 9.sp)
                    }
                }

                Spacer(Modifier.width(32.dp))
                // Divider Line
                Box(Modifier.width(2.dp).height(28.dp).background(Color(0x33FFFFFF)))
                Spacer(Modifier.width(32.dp))

                // Page Title
                Text(
                    text = "My Watchlist",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // ── CONTENT ──
            if (movies.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Your Watchlist is Empty", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Save shows and movies to watch later.", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
                        PosterCard(
                            movie = movie,
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFR) else Modifier,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }
    }
}