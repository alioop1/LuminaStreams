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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.presentation.home.HomeState
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.presentation.home.NfContentRow

private val BK   = Color(0xFF000000)
private val RD   = Color(0xFFE50914)
private val WH   = Color(0xFFFFFFFF)
private val DG   = Color(0xFF0A0A0A)
private val GOLD = Color(0xFFFFC107)

@Composable
fun DiscoveryScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    mediaType: String,
    onMovieClick: (String)->Unit
) {
    val ctx = LocalContext.current
    LaunchedEffect(mediaType) {
        val t = if(mediaType=="tv") "סדרות" else "סרטים"
        if(state.selectedTab!=t) viewModel.selectTab(t)
    }

    val movieGenres = listOf(
        "28" to "פעולה","12" to "הרפתקאות","16" to "אנימציה","35" to "קומדיה",
        "80" to "פשע","99" to "דוקו","18" to "דרמה","878" to "מדע בדיוני",
        "53" to "מותחן","27" to "אימה","10751" to "משפחה","14" to "פנטזיה"
    )
    val tvGenres = listOf(
        "10759" to "אקשן","16" to "אנימציה","35" to "קומדיה",
        "80" to "פשע","99" to "דוקו","18" to "דרמה","10762" to "ילדים",
        "9648" to "מסתורין","10765" to "מדע בדיוני","10768" to "מלחמה"
    )
    val activeGenres = if(mediaType=="tv") tvGenres else movieGenres
    BackHandler(enabled=state.isFilterComplete){ viewModel.clearGenre() }

    val currentBg = state.focusedItem
    val imageRequest = remember(currentBg?.backdropUrl,currentBg?.posterUrl) {
        ImageRequest.Builder(ctx)
            .data(currentBg?.backdropUrl?.takeIf{it.isNotEmpty()} ?: currentBg?.posterUrl)
            .crossfade(800).build()
    }

    Box(Modifier.fillMaxSize().background(BK)) {
        // ── Cinematic backdrop ──
        if(state.isFilterComplete) {
            AsyncImage(
                model=imageRequest, contentDescription=null,
                contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().blur(8.dp), alpha=0.35f
            )
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(BK.copy(0.55f),BK.copy(0.90f),BK))
            ))
        }

        AnimatedContent(
            targetState=state.isFilterComplete,
            transitionSpec={ (fadeIn(tween(380))+slideInVertically(tween(420,easing=FastOutSlowInEasing)){60}) togetherWith (fadeOut(tween(250))+slideOutVertically(tween(280)){-40}) },
            label="discovery_content"
        ) { showResults ->
            if(!showResults) {
                GenreGrid(mediaType=mediaType, genres=activeGenres, viewModel=viewModel)
            } else when {
                state.isLoading -> DiscoveryLoader()
                state.discoveryResults.isEmpty() -> DiscoveryEmpty()
                else -> DiscoveryResults(state=state, viewModel=viewModel, onClick=onMovieClick)
            }
        }
    }
}

// ─── Genre Grid ────────────────────────────────────────────────────────────
@Composable
private fun GenreGrid(mediaType:String, genres:List<Pair<String,String>>, viewModel:HomeViewModel) {
    Column(Modifier.fillMaxSize().padding(top=90.dp,start=80.dp,end=80.dp)) {
        // big heading
        Column(Modifier.padding(bottom=8.dp)) {
            Text(
                if(mediaType=="tv") "בחר" else "בחר",
                color=WH.copy(0.45f), fontSize=22.sp, fontWeight=FontWeight.Normal
            )
            Text(
                if(mediaType=="tv") "ז'אנר סדרות" else "ז'אנר סרטים",
                color=WH, fontSize=56.sp, fontWeight=FontWeight.Black, letterSpacing=(-1).sp
            )
            Box(Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RD))
        }
        Spacer(Modifier.height(36.dp))
        LazyVerticalGrid(
            columns=GridCells.Fixed(4),
            horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp),
            contentPadding=PaddingValues(bottom=60.dp),
            modifier=Modifier.fillMaxSize().focusRestorer()
        ) {
            items(genres){ (id,name)->
                GenreCard(name=name, onClick={ viewModel.setGenreFilter(id,name) })
            }
        }
    }
}

@Composable
private fun GenreCard(name:String, onClick:()->Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(isFocused) 1.07f else 1f, spring(Spring.DampingRatioMediumBouncy,Spring.StiffnessMedium))
    val bgAlpha by animateFloatAsState(if(isFocused) 0.22f else 0.10f, tween(160))
    val borderColor by animateColorAsState(if(isFocused) WH else WH.copy(0.18f), tween(160))
    val borderW by animateDpAsState(if(isFocused) 2.5.dp else 1.dp, tween(160))
    val cardShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp),RoundedCornerShape(18.dp))
    val textGlow by animateColorAsState(if(isFocused) RD else Color.Transparent, tween(180))

    Box(
        Modifier.height(130.dp)
            .graphicsLayer{ scaleX=scale; scaleY=scale }
            .zIndex(if(isFocused) 5f else 0f)
    ) {
        Surface(
            onClick=onClick,
            colors=ClickableSurfaceDefaults.colors(
                containerColor=WH.copy(bgAlpha),
                focusedContainerColor=WH.copy(bgAlpha)
            ),
            shape=cardShape,
            scale=ClickableSurfaceDefaults.scale(focusedScale=1.0f),
            glow=ClickableSurfaceDefaults.glow(focusedGlow=Glow(RD.copy(0.50f),20.dp)),
            modifier=Modifier.fillMaxSize()
                .border(borderW, borderColor, RoundedCornerShape(18.dp))
                .onFocusChanged{ isFocused=it.isFocused }
        ) {
            Box(Modifier.fillMaxSize()) {
                // subtle red sweep gradient
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(RD.copy(if(isFocused) 0.30f else 0.12f), Color.Transparent),
                        start=Offset(0f,0f), end=Offset(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY)
                    )
                ))
                Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) {
                    Text(
                        name, fontWeight=FontWeight.ExtraBold, fontSize=24.sp,
                        textAlign=TextAlign.Center,
                        color=if(isFocused) WH else WH.copy(0.85f),
                        modifier=Modifier.padding(horizontal=16.dp)
                    )
                }
                // focused bottom accent
                AnimatedVisibility(
                    visible=isFocused,
                    enter=fadeIn(tween(140))+expandVertically(tween(200),expandFrom=Alignment.Bottom),
                    exit=fadeOut(tween(100))+shrinkVertically(tween(150),shrinkTowards=Alignment.Bottom),
                    modifier=Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(
                        Brush.horizontalGradient(listOf(Color.Transparent,RD,Color.Transparent))
                    ))
                }
            }
        }
    }
}

// ─── Results ───────────────────────────────────────────────────────────────
@Composable
private fun DiscoveryResults(state:HomeState, viewModel:HomeViewModel, onClick:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(top=96.dp)) {
        NfContentRow(
            title="תוצאות: ${state.selectedGenreName}",
            movies=state.discoveryResults,
            onFocus={ movie:Movie -> viewModel.updateFocusedItem(movie,state.selectedGenreName,true) },
            onClick=onClick
        )
    }
}

// ─── Loader ────────────────────────────────────────────────────────────────
@Composable
private fun DiscoveryLoader() {
    val inf = rememberInfiniteTransition(label="dl")
    val p by inf.animateFloat(0f,1f, infiniteRepeatable(tween(900,easing=LinearEasing),RepeatMode.Reverse), "dp")
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text("טוען...", color=WH.copy(0.75f), fontSize=22.sp, fontWeight=FontWeight.Medium)
            Box(Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(50))
                .background(Color(0xFFE50914).copy(0.20f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(50))
                    .background(RD))
            }
        }
    }
}

// ─── Empty ─────────────────────────────────────────────────────────────────
@Composable
private fun DiscoveryEmpty() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("🎬", fontSize=52.sp)
            Text("לא נמצאו תוצאות", color=WH, fontSize=28.sp, fontWeight=FontWeight.Bold)
            Text("נסה ז'אנר אחר", color=WH.copy(0.45f), fontSize=18.sp)
        }
    }
}
