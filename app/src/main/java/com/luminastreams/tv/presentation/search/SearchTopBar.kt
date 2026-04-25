package com.luminastreams.tv.presentation.search

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.luminastreams.tv.R
import kotlinx.coroutines.delay

@Composable
fun SearchTopBar(
    state: SearchState, backFR: FocusRequester, inputFR: FocusRequester, firstTabFR: FocusRequester,
    onBack: () -> Unit, onIntent: (SearchIntent) -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val imm = remember { ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val firstSuggestFR = remember { FocusRequester() }

    var inputFocused by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    var hintIdx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(4000); hintIdx = (hintIdx + 1) % HINTS.size } }

    // PS5 Premium Float Effect
    val searchScale by animateFloatAsState(targetValue = if (inputFocused) 1.02f else 1f, tween(300, easing = FastOutSlowInEasing), label = "scale")
    val glowAlpha by animateFloatAsState(targetValue = if (inputFocused) 0.8f else 0f, tween(300), label = "glow")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 48.dp, end = 48.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. FLOATING SEARCH PILL ---
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Back Button (Ultra Minimal)
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = WHITE),
                modifier = Modifier.size(48.dp).focusRequester(backFR).focusProperties { down = firstTabFR; right = inputFR }
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.ArrowBackIosNew, null, Modifier.size(20.dp), tint = if (inputFocused) BG else WHITE)
                }
            }

            // The Premium Search Bar
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .graphicsLayer { scaleX = searchScale; scaleY = searchScale }
                    .shadow(if (inputFocused) 24.dp else 8.dp, RoundedCornerShape(32.dp), ambientColor = RED, spotColor = RED)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF0A0A0F).copy(alpha = 0.85f)) // Deep Glassmorphism
                    .border(
                        width = if (inputFocused) 2.dp else 1.dp,
                        color = if (inputFocused) RED.copy(alpha = glowAlpha) else DIM2,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val iconTint by animateColorAsState(if (inputFocused) RED else DIM, tween(200), label = "ic")
                    Icon(Icons.Default.Search, null, Modifier.size(28.dp), tint = iconTint)

                    BasicTextField(
                        value = state.query,
                        onValueChange = { v -> onIntent(SearchIntent.UpdateQuery(v)); showDropdown = v.isNotBlank() },
                        singleLine = true,
                        textStyle = TextStyle(color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(RED),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { imm.hideSoftInputFromWindow(view.windowToken, 0); showDropdown = false }),
                        decorationBox = { inner ->
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    AnimatedContent(targetState = hintIdx, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "hint") { i ->
                                        Text(HINTS[i], color = DIM.copy(0.4f), fontSize = 20.sp)
                                    }
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(inputFR).focusProperties { up = backFR }
                            .onFocusChanged { inputFocused = it.isFocused }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    when (ev.key) {
                                        Key.DirectionCenter -> { imm.showSoftInput(view, 0); true }
                                        Key.DirectionDown -> {
                                            if (showDropdown && state.autocompleteSuggestions.isNotEmpty()) runCatching { firstSuggestFR.requestFocus() }
                                            else runCatching { firstTabFR.requestFocus() }
                                            true
                                        }
                                        Key.DirectionLeft -> { if (state.query.isEmpty()) { runCatching { backFR.requestFocus() }; true } else false }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                }
            }

            // Results Counter & Logo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedVisibility(state.activeResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Text("${state.activeResults.size} Results", color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Image(painterResource(R.drawable.logo_lumina_unified), null, contentScale = ContentScale.Fit, modifier = Modifier.height(36.dp))
            }
        }

        // --- 2. SUGGESTIONS DROPDOWN (PS5 Style Glass) ---
        AnimatedVisibility(showDropdown && state.autocompleteSuggestions.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, start = 72.dp, end = 180.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F0F14).copy(0.95f)).border(1.dp, DIM3, RoundedCornerShape(20.dp)).padding(8.dp)) {
                state.autocompleteSuggestions.forEachIndexed { i, s ->
                    Surface(
                        onClick = { onIntent(SearchIntent.UpdateQuery(s)); showDropdown = false },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = RED.copy(0.15f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp).let { if (i == 0) it.focusRequester(firstSuggestFR) else it }.focusProperties { up = if (i == 0) inputFR else FocusRequester.Default; down = if (i == state.autocompleteSuggestions.lastIndex) firstTabFR else FocusRequester.Default }
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(16.dp), tint = RED)
                            Text(s, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// --- PS5 STYLE TABS ---
@Composable
fun SearchTabRow(state: SearchState, firstTabFR: FocusRequester, backFR: FocusRequester, firstResultFR: FocusRequester, onIntent: (SearchIntent) -> Unit) {
    val tabs = remember { listOf(SearchSource.ALL to "All", SearchSource.MOVIES to "Movies", SearchSource.SERIES to "Series", SearchSource.FUZER to "Fuzer") }

    Row(Modifier.fillMaxWidth().padding(horizontal = 72.dp).onPreviewKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { runCatching { firstResultFR.requestFocus() }; true } else false }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        tabs.forEachIndexed { idx, tab ->
            val isSel = state.source == tab.first
            var isFocused by remember { mutableStateOf(false) }
            val alpha by animateFloatAsState(if (isFocused || isSel) 1f else 0.5f, label = "alpha")

            Surface(
                onClick = { onIntent(SearchIntent.SelectSource(tab.first)) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                modifier = Modifier.let { if (idx == 0) it.focusRequester(firstTabFR) else it }.focusProperties { if (idx == 0) up = backFR }.onFocusChanged { isFocused = it.isFocused }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
                    Text(tab.second, color = WHITE.copy(alpha = alpha), fontSize = 18.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                    // PS5 Underline Indicator
                    Box(Modifier.width(24.dp).height(3.dp).clip(CircleShape).background(if (isSel) RED else if (isFocused) WHITE else Color.Transparent))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Premium Filter Button
        Surface(
            onClick = { onIntent(SearchIntent.ToggleFilters) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(containerColor = if (state.filters.isActive) RED.copy(0.2f) else DIM3, focusedContainerColor = WHITE, contentColor = if (state.filters.isActive) RED else WHITE, focusedContentColor = BG),
            modifier = Modifier.height(40.dp)
        ) {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, null, Modifier.size(16.dp))
                Text("Filters", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}