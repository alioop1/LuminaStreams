@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie

@Composable
fun HomeScreen(state: HomeState, viewModel: HomeViewModel, navController: NavController, onMovieClick: (String) -> Unit) {
    val displayItem = state.focusedItem ?: state.movieTrending.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black).focusRestorer(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // --- 1. HERO BANNER ענקי (80vh) ---
        item {
            HeroBanner4K(displayItem, onMovieClick)
        }

        // --- 2. שורות תוכן (פוסטרים קטנים ב-50%) ---
        val rows = listOf("TRENDING NOW" to state.movieTrending, "PREMIERES" to state.moviePremieres, "ACTION" to state.movieAction)
        rows.forEach { (title, movies) ->
            if (movies.isNotEmpty()) {
                item { ContentRow4K(title, movies, viewModel, onMovieClick) }
            }
        }
    }
}

@Composable
fun HeroBanner4K(movie: Movie?, onClick: (String) -> Unit) {
    val config = LocalConfiguration.current
    val heroHeight = (config.screenHeightDp * 0.8f).dp // בדיוק 80vh

    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        AsyncImage(
            model = movie?.backdropUrl ?: movie?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.8f
        )

        // גרדיאנטים מה-React
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f), Color.Black))))
        Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent))))

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 60.dp).fillMaxWidth(0.65f)
        ) {
            Text(text = movie?.title ?: "", color = Color.White, fontSize = 90.sp, fontWeight = FontWeight.Black, lineHeight = 96.sp, maxLines = 2)
            Spacer(Modifier.height(20.dp))
            Text(text = movie?.overview ?: "", color = Color.LightGray, fontSize = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(40.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroButton("Play", Icons.Default.PlayArrow, true) { onClick(movie?.id ?: "") }
                Spacer(Modifier.width(20.dp))
                HeroButton("Details", Icons.Default.Add, false) { onClick(movie?.id ?: "") }
            }
        }
    }
}

@Composable
fun HeroButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color(0xFFE50914) else Color(0x33FFFFFF),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = Modifier.height(64.dp).width(180.dp).onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isFocused) Color.Black else Color.White)
            Spacer(Modifier.width(10.dp))
            Text(text, color = if (isFocused) Color.Black else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContentRow4K(title: String, movies: List<Movie>, viewModel: HomeViewModel, onClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 30.dp)) {
        Text(text = title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 96.dp, bottom = 20.dp))

        LazyRow(contentPadding = PaddingValues(horizontal = 96.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            items(movies) { movie ->
                var isFocused by remember { mutableStateOf(false) }
                Column(modifier = Modifier.width(100.dp)) { // פוסטרים קטנים ב-50% (95-100dp)
                    Surface(
                        onClick = { onClick(movie.id) },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.aspectRatio(2/3f).onFocusChanged {
                            isFocused = it.isFocused
                            if(it.isFocused) viewModel.updateFocusedItem(movie, title, true)
                        }.border(2.dp, if(isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(model = movie.posterUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.height(40.dp)) {
                        Text(movie.title, color = if(isFocused) Color.White else Color.Gray, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}