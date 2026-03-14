@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.domain.model.SearchResult
import com.luminastreams.tv.ui.components.CustomMicIcon
import com.luminastreams.tv.ui.theme.NetflixRed

private val StremioBackground = Color(0xFF141414)
private val StremioSearchBar = Color(0xFF262626)

@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val backButtonFocus = remember { FocusRequester() }
    val searchBarFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onIntent(SearchIntent.SetVoiceListeningState(false))
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            onIntent(SearchIntent.UpdateQuery(spokenText))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StremioBackground)) {

        // התיקון הקריטי: רקע סטטי עם מעבר Fade רך. ללא טשטוש או קנה מידה משתנה שחונקים את המעבד.
        AnimatedContent(
            targetState = state.focusedItemUrl,
            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
            label = "ambient_bg"
        ) { url ->
            if (!url.isNullOrEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.2f
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(StremioBackground.copy(alpha = 0.8f), StremioBackground))))

        Column(modifier = Modifier.fillMaxSize()) {
            // --- שורת חיפוש עליונה ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 48.dp, end = 48.dp, bottom = 12.dp)
            ) {
                Surface(
                    onClick = onNavigateBack,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = StremioSearchBar, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                    modifier = Modifier.size(64.dp).focusRequester(backButtonFocus).focusProperties {
                        if (isRtl) left = searchBarFocus else right = searchBarFocus
                        down = gridFocus
                    }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזור", modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f).height(64.dp).clip(RoundedCornerShape(8.dp)).background(StremioSearchBar)
                        .focusRequester(searchBarFocus)
                        .focusProperties {
                            if (isRtl) right = backButtonFocus else left = backButtonFocus
                            down = gridFocus
                        }
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))

                    BasicTextField(
                        value = state.query,
                        onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) },
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                gridFocus.requestFocus(); return@onPreviewKeyEvent true
                            }
                            false
                        },
                        decorationBox = { inner ->
                            if (state.query.isEmpty()) Text("חיפוש סרטים, סדרות או שחקנים...", color = Color.Gray, fontSize = 22.sp)
                            inner()
                        }
                    )

                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(SearchIntent.UpdateQuery("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "נקה", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color.DarkGray))
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (!state.isVoiceListening) {
                                onIntent(SearchIntent.SetVoiceListeningState(true))
                                try { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isRtl) "he-IL" else "en-US") }) }
                                catch (e: Exception) { onIntent(SearchIntent.SetVoiceListeningState(false)) }
                            }
                        }
                    ) {
                        if (state.isVoiceListening) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(0, 1, 2).forEach { index ->
                                    val infiniteTransition = rememberInfiniteTransition(label = "wave")
                                    val height by infiniteTransition.animateFloat(
                                        initialValue = 0.3f, targetValue = 1f,
                                        animationSpec = infiniteRepeatable(tween(250 + (index * 50), easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                        label = "wave_anim"
                                    )
                                    Box(modifier = Modifier.width(4.dp).height((20 * height).dp).background(NetflixRed, CircleShape))
                                }
                            }
                        } else {
                            Icon(CustomMicIcon, null, tint = Color.White)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.autocompleteSuggestions.isNotEmpty() && !state.isSearching) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(state.autocompleteSuggestions) { suggestion ->
                        StremioFilterChip(suggestion, isSelected = false) { onIntent(SearchIntent.UpdateQuery(suggestion)) }
                    }
                }
            }

            if (state.results.isNotEmpty() && !state.isSearching) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp, top = 12.dp)
                ) {
                    items(state.filters) { filter ->
                        StremioFilterChip(filter, state.selectedFilter == filter) { onIntent(SearchIntent.SelectFilter(filter)) }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().focusRequester(gridFocus)) {
                when {
                    state.containsHebrew -> StremioMessage("נא לחפש באנגלית בלבד")
                    state.isSearching -> StremioSkeletonGrid()
                    state.query.isEmpty() -> HistoryAndTrendingDashboard(state, onIntent, onResultClick)
                    state.results.isEmpty() -> SmartNotFoundState(state, onIntent, onResultClick)
                    else -> StremioGrid(state.results, null, onIntent, onResultClick)
                }
            }
        }
    }
}

@Composable
fun HistoryAndTrendingDashboard(state: SearchState, onIntent: (SearchIntent) -> Unit, onResultClick: (SearchResult) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.searchHistory.isNotEmpty()) {
            Text("חיפושים אחרונים", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                items(state.searchHistory) { item ->
                    HistoryChip(text = item, onClick = { onIntent(SearchIntent.UpdateQuery(item)) }, onDelete = { onIntent(SearchIntent.RemoveHistoryItem(item)) })
                }
            }
        }
        StremioGrid(state.trendingSearches, "פופולרי עכשיו", onIntent, onResultClick)
    }
}

@Composable
fun HistoryChip(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = StremioSearchBar, focusedContainerColor = Color.LightGray, contentColor = Color.White, focusedContentColor = Color.Black),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = Modifier.height(48.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "מחק", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SmartNotFoundState(state: SearchState, onIntent: (SearchIntent) -> Unit, onResultClick: (SearchResult) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("לא מצאנו תוצאות עבור '${state.query}'. הנה כמה דברים שאולי תאהב:", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.padding(start = 48.dp, bottom = 24.dp))
        StremioGrid(items = state.trendingSearches, title = null, onIntent = onIntent, onResultClick = onResultClick)
    }
}

@Composable
fun StremioSkeletonGrid() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.2f, // הורדתי מעט את האטימות כדי להקל עוד קצת על הרינדור
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer_alpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(14) { Box(modifier = Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = alpha))) }
    }
}

@Composable
fun StremioGrid(items: List<SearchResult>, title: String?, onIntent: (SearchIntent) -> Unit, onResultClick: (SearchResult) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (title != null) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { result ->
                StremioPosterCard(result, onClick = { onResultClick(result) }, onFocus = { onIntent(SearchIntent.SetFocusedBackground(result.backdropUrl)) })
            }
        }
    }
}

@Composable
fun StremioPosterCard(result: SearchResult, onClick: () -> Unit, onFocus: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .zIndex(if (isFocused) 10f else 0f)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = result.posterUrl, contentDescription = result.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

            AnimatedVisibility(visible = isFocused, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFA000000)))).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(result.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        if (result.rating > 0f) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(result.rating.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(" • ", color = Color.Gray, fontSize = 12.sp)
                        }
                        Text(if (result.releaseYear.isNotEmpty()) result.releaseYear else "N/A", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StremioFilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White else StremioSearchBar,
            focusedContainerColor = Color.LightGray,
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        modifier = Modifier.height(36.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun StremioMessage(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}