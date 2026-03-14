@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.components.TopNavBar
import kotlinx.coroutines.delay

// ─── Palette ───────────────────────────────────────────────────────────────
private val BK   = Color(0xFF000000)
private val RD   = Color(0xFFE50914)
private val DRD  = Color(0xFFB20710)
private val WH   = Color(0xFFFFFFFF)
private val DM   = Color(0xAAFFFFFF)
private val GL   = Color(0x18FFFFFF)
private val MGN  = Color(0xFF46D369)
private val DG   = Color(0xFF0A0A0A)
private val GOLD = Color(0xFFFFC107)

// ─── Vector Icons ──────────────────────────────────────────────────────────
private val IconFilm: ImageVector get() = ImageVector.Builder("Film",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.White)) {
        moveTo(18f,4f);lineTo(6f,4f);curveTo(4.9f,4f,4f,4.9f,4f,6f);lineTo(4f,18f)
        curveTo(4f,19.1f,4.9f,20f,6f,20f);lineTo(18f,20f);curveTo(19.1f,20f,20f,19.1f,20f,18f)
        lineTo(20f,6f);curveTo(20f,4.9f,19.1f,4f,18f,4f);close()
        moveTo(10f,14.5f);lineTo(10f,9.5f);lineTo(15f,12f);lineTo(10f,14.5f);close()
    }
}.build()
private val IconTv: ImageVector get() = ImageVector.Builder("Tv",24.dp,24.dp,24f,24f).apply {
    path(fill=SolidColor(Color.White)) {
        moveTo(21f,3f);lineTo(3f,3f);curveTo(1.9f,3f,1f,3.9f,1f,5f);lineTo(1f,17f)
        curveTo(1f,18.1f,1.9f,19f,3f,19f);lineTo(10f,19f);lineTo(10f,21f);lineTo(14f,21f)
        lineTo(14f,19f);lineTo(21f,19f);curveTo(22.1f,19f,23f,18.1f,23f,17f);lineTo(23f,5f)
        curveTo(22.1f,3f,21.1f,3f,21f,3f);close()
        moveTo(21f,17f);lineTo(3f,17f);lineTo(3f,5f);lineTo(21f,5f);lineTo(21f,17f);close()
    }
}.build()

private data class NavItem(val id:String,val label:String,val icon:ImageVector)
private val navItems = listOf(
    NavItem("home","בית",Icons.Default.Home),
    NavItem("movies","סרטים",IconFilm),
    NavItem("series","סדרות",IconTv),
    NavItem("search","חיפוש",Icons.Default.Search),
    NavItem("favorites","מועדפים",Icons.Default.Favorite),
    NavItem("settings","הגדרות",Icons.Default.Settings)
)

private enum class TileSize { LARGE, MEDIUM, SMALL }
private fun tilePattern(i:Int) = when(i%7){ 0->TileSize.LARGE; 3->TileSize.MEDIUM; else->TileSize.SMALL }

private fun buildPool(state:HomeState):List<Movie> {
    val src = if(state.selectedTab=="סרטים")
        (state.movieTrending+state.movieAction+state.movieTopRated+state.moviePremieres+state.movieScifi+state.movieDrama).distinctBy{it.id}
    else
        (state.tvTrending+state.tvDrama+state.tvCrime+state.tvTopRated+state.tvPremieres+state.tvScifi).distinctBy{it.id}
    return src.take(40)
}
private fun FocusRequester.safeRequest(){ try{ requestFocus() }catch(_:Exception){} }

// ═══════════════════════════════════════════════════════════════════════════
// HomeScreen
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String)->Unit
) {
    val navBarFR    = remember { FocusRequester() }
    val firstTileFR = remember { FocusRequester() }
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen  by remember { mutableStateOf(false) }
    var activeNavId  by remember { mutableStateOf("home") }
    val pool = remember(state) { buildPool(state) }
    var breathePulse by remember { mutableStateOf(0) }
    LaunchedEffect(pool) { while(true){ delay(3_200); breathePulse++ } }
    LaunchedEffect(state.isLoading) { if(!state.isLoading){ delay(200); navBarFR.safeRequest() } }
    val heroMovie = focusedMovie ?: pool.firstOrNull()

    Box(Modifier.fillMaxSize().background(BK)) {
        when {
            state.isLoading     -> NfLoadingSkeleton()
            state.error != null -> NfErrorScreen(state.error){ viewModel.selectTab(state.selectedTab) }
            else -> {
                // ── Cinematic BG ──
                CinematicBackground(pool=pool, focusedMovie=focusedMovie)

                // ── Hero panel ──
                AnimatedVisibility(
                    visible  = focusedMovie!=null,
                    enter    = fadeIn(tween(300,easing=FastOutSlowInEasing))+slideInHorizontally(tween(380,easing=FastOutSlowInEasing)){-120},
                    exit     = fadeOut(tween(200))+slideOutHorizontally(tween(240)){-80},
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(5f)
                ) { heroMovie?.let{ HeroPanel(it){ onMovieClick(it.id) } } }

                // ── Mosaic grid ──
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if(focusedMovie!=null) 0.50f else 1f)
                        .align(Alignment.CenterEnd)
                        .padding(top=104.dp)
                ) {
                    MosaicGrid(
                        pool=pool, breathePulse=breathePulse, firstTileFR=firstTileFR,
                        onFocus={ focusedMovie=it }, onUpFromGrid={ navBarFR.safeRequest() },
                        onLeftEdge={ if(focusedMovie==null) sidebarOpen=true }, onClick=onMovieClick
                    )
                }

                // ── Top nav ──
                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(10f)) {
                    CinematicTopNav(
                        state=state, navBarFR=navBarFR, firstTileFR=firstTileFR,
                        onTabSelect={ viewModel.selectTab(it) },
                        onSearchClick={ navController.navigate("search") },
                        onOpenSidebar={ sidebarOpen=true }
                    )
                }
            }
        }
        NfSidebar(
            open=sidebarOpen, activeId=activeNavId,
            sidebarFirstFR=remember{ FocusRequester() },
            onFocusLanded={},
            onClose={ sidebarOpen=false; navBarFR.safeRequest() },
            onNavSelect={ id->
                activeNavId=id; sidebarOpen=false
                when(id){
                    "movies"->viewModel.selectTab("סרטים")
                    "series"->viewModel.selectTab("סדרות")
                    "search"->navController.navigate("search")
                }
                navBarFR.safeRequest()
            }
        )
    }
}

// ─── Cinematic Background ─────────────────────────────────────────────────
@Composable
private fun CinematicBackground(pool:List<Movie>, focusedMovie:Movie?) {
    val ctx = LocalContext.current
    val bgMovie = focusedMovie ?: pool.firstOrNull()
    val overlayAlpha by animateFloatAsState(
        if(focusedMovie!=null) 0.82f else 0.60f,
        tween(500,easing=FastOutSlowInEasing)
    )
    val blurRadius by animateDpAsState(
        if(focusedMovie!=null) 0.dp else 28.dp,
        tween(500,easing=FastOutSlowInEasing)
    )
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState    = bgMovie?.backdropUrl ?: bgMovie?.posterUrl,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(700)) },
            label          = "cinematic_bg"
        ) { url ->
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url)
                    .size(1920,1080).scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(blurRadius)
            )
        }
        // deep vignette
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                0.0f to BK.copy(0f),
                0.6f to BK.copy(0.25f),
                1.0f to BK.copy(0.75f)
            )
        ))
        Box(Modifier.fillMaxSize().background(BK.copy(overlayAlpha)))
    }
}

// ─── Mosaic Grid ──────────────────────────────────────────────────────────
@Composable
private fun MosaicGrid(
    pool:List<Movie>, breathePulse:Int, firstTileFR:FocusRequester,
    onFocus:(Movie)->Unit, onUpFromGrid:()->Unit, onLeftEdge:()->Unit, onClick:(String)->Unit
) {
    val breatheTargets = remember(breathePulse) {
        if(pool.isEmpty()) emptySet() else (0 until 5).map{ pool.indices.random() }.toSet()
    }
    LazyVerticalGrid(
        columns=GridCells.Fixed(5),
        contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp),
        modifier=Modifier.fillMaxSize().focusRestorer()
    ) {
        itemsIndexed(pool, key={ _,m->m.id }) { index, movie ->
            val h = when(tilePattern(index)){ TileSize.LARGE->240.dp; TileSize.MEDIUM->180.dp; TileSize.SMALL->140.dp }
            val ba by animateFloatAsState(if(index in breatheTargets) 0.5f else 1f, tween(1600,easing=FastOutSlowInEasing))
            CinematicTile(
                movie=movie, tileHeight=h, bgAlpha=ba,
                isFirst=index==0, firstTileFR=firstTileFR,
                onFocus={ onFocus(movie) },
                onUpFromRow=if(index<5) onUpFromGrid else null,
                onLeftEdge=if(index%5==0) onLeftEdge else null,
                onClick={ onClick(movie.id) }
            )
        }
    }
}

// ─── Cinematic Tile ───────────────────────────────────────────────────────
@Composable
private fun CinematicTile(
    movie:Movie, tileHeight:androidx.compose.ui.unit.Dp, bgAlpha:Float,
    isFirst:Boolean, firstTileFR:FocusRequester,
    onFocus:()->Unit, onUpFromRow:(()->Unit)?, onLeftEdge:(()->Unit)?, onClick:()->Unit
) {
    val ctx = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(isFocused) 1.10f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    val alpha by animateFloatAsState(if(isFocused) 1f else bgAlpha, tween(300))
    val borderAlpha by animateFloatAsState(if(isFocused) 1f else 0f, tween(180))
    val shape = RoundedCornerShape(10.dp)
    val tileShape = ClickableSurfaceDefaults.shape(shape=shape, focusedShape=shape)

    Box(
        Modifier.height(tileHeight)
            .graphicsLayer{ scaleX=scale; scaleY=scale; this.alpha=alpha }
            .zIndex(if(isFocused) 20f else 0f)
    ) {
        Surface(
            onClick=onClick,
            colors=ClickableSurfaceDefaults.colors(containerColor=DG, focusedContainerColor=DG),
            shape=tileShape,
            scale=ClickableSurfaceDefaults.scale(focusedScale=1.0f),
            border=ClickableSurfaceDefaults.border(
                border=Border.None,
                focusedBorder=Border(BorderStroke(2.5.dp, WH.copy(borderAlpha)), 10.dp)
            ),
            glow=ClickableSurfaceDefaults.glow(focusedGlow=Glow(RD.copy(0.6f), 24.dp)),
            modifier=Modifier.fillMaxSize()
                .then(if(isFirst) Modifier.focusRequester(firstTileFR) else Modifier)
                .onFocusChanged{ fs-> isFocused=fs.isFocused; if(fs.isFocused) onFocus() }
                .onPreviewKeyEvent{ kev->
                    if(kev.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when{
                        kev.key==Key.DirectionUp   && onUpFromRow!=null -> { onUpFromRow(); true }
                        kev.key==Key.DirectionLeft && onLeftEdge!=null  -> { onLeftEdge();  true }
                        else -> false
                    }
                }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model=ImageRequest.Builder(ctx).data(movie.posterUrl)
                        .size(300,450).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(250).build(),
                    contentDescription=movie.title,
                    contentScale=ContentScale.Crop,
                    modifier=Modifier.fillMaxSize()
                )
                // gradient overlay
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.85f)))
                ))
                // rating badge
                if(movie.rating>0f)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BK.copy(0.70f))
                            .padding(horizontal=5.dp,vertical=2.dp)
                    ) {
                        Text("%.1f".format(movie.rating), color=GOLD, fontSize=10.sp, fontWeight=FontWeight.Bold)
                    }
                // title on focus
                AnimatedVisibility(
                    visible=isFocused,
                    enter=fadeIn(tween(160))+slideInVertically(tween(200,easing=FastOutSlowInEasing)){ it/2 },
                    exit=fadeOut(tween(120)),
                    modifier=Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        movie.title, color=WH, fontSize=12.sp, fontWeight=FontWeight.Bold,
                        maxLines=2, overflow=TextOverflow.Ellipsis,
                        modifier=Modifier.padding(horizontal=8.dp,vertical=6.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfContentRow — horizontal scroll row (used by DiscoveryScreen)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfContentRow(
    title: String,
    movies: List<Movie>,
    onFocus: (Movie)->Unit = {},
    onClick: (String)->Unit
) {
    if(movies.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom=36.dp)) {
        // section label with red accent
        Row(
            Modifier.padding(start=48.dp,bottom=16.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(RD))
            Spacer(Modifier.width(10.dp))
            Text(title, color=WH, fontSize=20.sp, fontWeight=FontWeight.Bold, letterSpacing=0.5.sp)
        }
        LazyRow(
            contentPadding=PaddingValues(horizontal=48.dp),
            horizontalArrangement=Arrangement.spacedBy(14.dp),
            modifier=Modifier.fillMaxWidth().focusRestorer()
        ) {
            items(movies, key={ it.id }) { movie ->
                ContentCard(movie=movie, onFocus={ onFocus(movie) }, onClick={ onClick(movie.id) })
            }
        }
    }
}

@Composable
private fun ContentCard(movie:Movie, onFocus:()->Unit, onClick:()->Unit) {
    val ctx = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(isFocused) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
    val cardShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))

    Box(
        Modifier.width(148.dp).height(222.dp)
            .graphicsLayer{ scaleX=scale; scaleY=scale }
            .zIndex(if(isFocused) 5f else 0f)
    ) {
        Surface(
            onClick=onClick,
            colors=ClickableSurfaceDefaults.colors(containerColor=DG, focusedContainerColor=DG),
            shape=cardShape,
            scale=ClickableSurfaceDefaults.scale(focusedScale=1.0f),
            border=ClickableSurfaceDefaults.border(
                border=Border.None,
                focusedBorder=Border(BorderStroke(2.dp,WH),10.dp)
            ),
            glow=ClickableSurfaceDefaults.glow(focusedGlow=Glow(RD.copy(0.55f),20.dp)),
            modifier=Modifier.fillMaxSize()
                .onFocusChanged{ fs-> isFocused=fs.isFocused; if(fs.isFocused) onFocus() }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model=ImageRequest.Builder(ctx).data(movie.posterUrl)
                        .size(300,450).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(250).build(),
                    contentDescription=movie.title,
                    contentScale=ContentScale.Crop,
                    modifier=Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent,BK.copy(0.88f)))
                ))
                // rating
                if(movie.rating>0f)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BK.copy(0.72f))
                            .padding(horizontal=5.dp,vertical=2.dp)
                    ) {
                        Text("%.1f".format(movie.rating), color=GOLD, fontSize=10.sp, fontWeight=FontWeight.Bold)
                    }
                AnimatedVisibility(
                    visible=isFocused,
                    enter=fadeIn(tween(150))+slideInVertically(tween(190,easing=FastOutSlowInEasing)){ it/2 },
                    exit=fadeOut(tween(110)),
                    modifier=Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        movie.title, color=WH, fontSize=11.sp, fontWeight=FontWeight.Bold,
                        maxLines=2, overflow=TextOverflow.Ellipsis,
                        modifier=Modifier.padding(8.dp,0.dp,8.dp,8.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Hero Panel
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroPanel(movie:Movie, onPlay:()->Unit) {
    val playShape = ClickableSurfaceDefaults.shape(shape=RoundedCornerShape(50.dp),focusedShape=RoundedCornerShape(50.dp))
    val inf = rememberInfiniteTransition(label="shimmer")
    val shimmerX by inf.animateFloat(0f,1f, infiniteRepeatable(tween(2200,easing=LinearEasing)), label="shimX")

    Box(
        Modifier.width(540.dp).fillMaxHeight()
            .drawWithContent{
                drawContent()
                // right-side feather
                drawRect(
                    brush=Brush.horizontalGradient(listOf(Color.Transparent,BK.copy(0f))),
                    topLeft=Offset(size.width*0.75f,0f),
                    size=androidx.compose.ui.geometry.Size(size.width*0.25f,size.height)
                )
            }
            .background(Brush.horizontalGradient(listOf(BK.copy(0.97f),BK.copy(0f))))
            .padding(start=60.dp,end=40.dp)
            .zIndex(5f),
        contentAlignment=Alignment.CenterStart
    ) {
        Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
            // poster with shimmer border
            Box(
                Modifier.width(170.dp).aspectRatio(2f/3f)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AnimatedContent(
                    targetState=movie.posterUrl,
                    transitionSpec={ fadeIn(tween(500)) togetherWith fadeOut(tween(350)) },
                    label="hero_poster"
                ) { url ->
                    AsyncImage(
                        model=url, contentDescription=movie.title,
                        contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize()
                    )
                }
                // shimmer border
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(
                        colors=listOf(Color.Transparent,WH.copy(0.4f),Color.Transparent),
                        start=Offset(shimmerX*800f-400f,0f),
                        end=Offset(shimmerX*800f,500f)
                    ))
                )
            }

            // title
            AnimatedContent(
                targetState=movie.title,
                transitionSpec={ (fadeIn(tween(350))+slideInVertically(tween(380)){30}) togetherWith (fadeOut(tween(200))+slideOutVertically(tween(220)){-20}) },
                label="hero_title"
            ) { t ->
                Text(t, color=WH, fontSize=38.sp, fontWeight=FontWeight.ExtraBold, lineHeight=44.sp, maxLines=3, overflow=TextOverflow.Ellipsis)
            }

            // meta row
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MGN).padding(horizontal=8.dp,vertical=3.dp)) {
                    Text("97% Match", color=BK, fontSize=13.sp, fontWeight=FontWeight.Black)
                }
                if(movie.rating>0f)
                    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint=GOLD)
                        Text("%.1f".format(movie.rating), color=GOLD, fontSize=14.sp, fontWeight=FontWeight.Bold)
                    }
            }

            // overview
            AnimatedContent(
                targetState=movie.overview,
                transitionSpec={ fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label="hero_ov"
            ) { ov ->
                Text(ov, color=DM, fontSize=14.sp, lineHeight=22.sp, maxLines=4, overflow=TextOverflow.Ellipsis)
            }

            // play button
            Surface(
                onClick=onPlay,
                colors=ClickableSurfaceDefaults.colors(
                    containerColor=WH, contentColor=BK,
                    focusedContainerColor=RD, focusedContentColor=WH
                ),
                shape=playShape,
                scale=ClickableSurfaceDefaults.scale(focusedScale=1.06f),
                glow=ClickableSurfaceDefaults.glow(focusedGlow=Glow(RD.copy(0.65f),22.dp)),
                modifier=Modifier.wrapContentWidth().height(54.dp)
            ) {
                Row(Modifier.padding(horizontal=32.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow,null,Modifier.size(22.dp))
                    Text("נגן עכשיו",fontSize=17.sp,fontWeight=FontWeight.Black)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Top Nav
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun CinematicTopNav(
    state:HomeState, navBarFR:FocusRequester, firstTileFR:FocusRequester,
    onTabSelect:(String)->Unit, onSearchClick:()->Unit, onOpenSidebar:()->Unit
) {
    val firstTabFR = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(0f to BK.copy(0.95f), 0.65f to BK.copy(0.45f), 1f to Color.Transparent)
        )
    ) {
        TopNavBar(
            rdStatus=true, hasNotifications=false, searchFR=navBarFR,
            onVoiceSearchClick={}, onSearchClick=onSearchClick, onProfileClick={},
            onDownPress={ try{ firstTabFR.requestFocus() }catch(_:Exception){ firstTileFR.safeRequest() } },
            onLeftEdge={ onOpenSidebar() }
        )
        Row(
            Modifier.fillMaxWidth().padding(start=60.dp,bottom=10.dp),
            horizontalArrangement=Arrangement.spacedBy(36.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            listOf("סרטים","סדרות").forEachIndexed{ idx,tab->
                CinematicTab(
                    label=tab, isSelected=state.selectedTab==tab,
                    focusRequester=if(idx==0) firstTabFR else null,
                    onUpPress={ navBarFR.safeRequest() },
                    onDownPress={ firstTileFR.safeRequest() },
                    onClick={ onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun CinematicTab(
    label:String, isSelected:Boolean, focusRequester:FocusRequester?,
    onUpPress:()->Unit, onDownPress:()->Unit, onClick:()->Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val color by animateColorAsState(if(isSelected||isFocused) WH else WH.copy(0.45f), tween(150))
    val lineW by animateDpAsState(if(isSelected) 28.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy))
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(
            onClick=onClick,
            colors=ClickableSurfaceDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color.Transparent),
            scale=ClickableSurfaceDefaults.scale(focusedScale=1.06f),
            modifier=Modifier
                .then(if(focusRequester!=null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged{ isFocused=it.isFocused }
                .onPreviewKeyEvent{ kev->
                    if(kev.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when(kev.key){ Key.DirectionUp->{ onUpPress(); true }; Key.DirectionDown->{ onDownPress(); true }; else->false }
                }
        ) {
            Text(label, color=color, fontSize=17.sp,
                fontWeight=if(isSelected||isFocused) FontWeight.Bold else FontWeight.Normal,
                modifier=Modifier.padding(horizontal=6.dp,vertical=10.dp)
            )
        }
        Box(Modifier.height(3.dp).width(lineW).clip(RoundedCornerShape(2.dp)).background(RD))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Sidebar
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfSidebar(
    open:Boolean, activeId:String, sidebarFirstFR:FocusRequester,
    onFocusLanded:()->Unit, onClose:()->Unit, onNavSelect:(String)->Unit
) {
    AnimatedVisibility(visible=open, enter=fadeIn(tween(200)), exit=fadeOut(tween(200)), modifier=Modifier.zIndex(19f)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.75f)))
    }
    AnimatedVisibility(
        visible=open,
        enter=slideInHorizontally(tween(240,easing=FastOutSlowInEasing)){ -it },
        exit=slideOutHorizontally(tween(200,easing=FastOutLinearInEasing)){ -it },
        modifier=Modifier.zIndex(20f)
    ) {
        LaunchedEffect(Unit){ delay(60); try{ sidebarFirstFR.requestFocus(); onFocusLanded() }catch(_:Exception){} }
        Box(
            Modifier.fillMaxHeight().width(280.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF080808),Color(0xFF0E0E0E))))
        ) {
            // vertical red accent line
            Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterEnd).background(
                Brush.verticalGradient(listOf(Color.Transparent,RD.copy(0.5f),Color.Transparent))
            ))
            Column(Modifier.fillMaxSize().padding(vertical=48.dp)) {
                Text("LUMINA", color=RD, fontSize=22.sp, fontWeight=FontWeight.Black, letterSpacing=7.sp,
                    modifier=Modifier.padding(start=28.dp,bottom=40.dp))
                navItems.forEachIndexed{ idx,item->
                    SidebarItem(
                        item=item, isActive=item.id==activeId,
                        modifier=if(idx==0) Modifier.focusRequester(sidebarFirstFR) else Modifier,
                        onRightPress=onClose, onClick={ onNavSelect(item.id) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("▶  לחץ ימין לתוכן", color=WH.copy(0.3f), fontSize=11.sp,
                    modifier=Modifier.padding(start=28.dp,bottom=16.dp))
            }
        }
    }
}

@Composable
private fun SidebarItem(
    item:NavItem, isActive:Boolean, modifier:Modifier=Modifier,
    onRightPress:()->Unit, onClick:()->Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg    by animateColorAsState(when{ isActive->RD.copy(0.18f); isFocused->WH.copy(0.08f); else->Color.Transparent },tween(130))
    val tc    by animateColorAsState(if(isFocused||isActive) WH else WH.copy(0.55f),tween(130))
    val barH  by animateDpAsState(if(isActive) 36.dp else 0.dp,spring(Spring.DampingRatioMediumBouncy))

    Row(
        modifier=modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=2.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .onFocusChanged{ isFocused=it.isFocused },
        verticalAlignment=Alignment.CenterVertically
    ) {
        // active bar
        Box(Modifier.width(3.dp).height(barH).clip(RoundedCornerShape(2.dp)).background(RD))
        Spacer(Modifier.width(if(isActive) 12.dp else 18.dp))
        Surface(
            onClick=onClick,
            colors=ClickableSurfaceDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color.Transparent),
            scale=ClickableSurfaceDefaults.scale(focusedScale=1.0f),
            modifier=Modifier.fillMaxWidth().padding(vertical=14.dp)
                .onPreviewKeyEvent{ kev->
                    if(kev.type!=KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when{ kev.key==Key.DirectionRight->{ onRightPress(); true }; kev.key==Key.Back||kev.key==Key.Escape->{ onRightPress(); true }; else->false }
                }
        ) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(item.icon,null, tint=if(isActive) RD else tc, modifier=Modifier.size(22.dp))
                Text(item.label, color=tc, fontSize=17.sp, fontWeight=if(isActive) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Loading + Error
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfLoadingSkeleton() {
    val inf = rememberInfiniteTransition(label="shimmer")
    val p by inf.animateFloat(0f,1f, infiniteRepeatable(tween(1200,easing=LinearEasing),RepeatMode.Restart), "sp")
    Box(Modifier.fillMaxSize().background(BK)) {
        Column(Modifier.fillMaxSize().padding(top=100.dp,start=10.dp,end=10.dp)) {
            repeat(3){ row->
                Row(Modifier.fillMaxWidth().padding(bottom=6.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    repeat(5){ col->
                        val h=when{ (row*5+col)%7==0->240.dp; (row*5+col)%3==0->180.dp; else->140.dp }
                        Box(Modifier.weight(1f).height(h).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(
                                listOf(Color(0xFF1A1A1A),Color(0xFF2E2E2E),Color(0xFF1A1A1A)),
                                start=Offset(p*2000f-1000f,0f), end=Offset(p*2000f,500f)
                            ))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfErrorScreen(message:String, onRetry:()->Unit) {
    val errShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    Box(Modifier.fillMaxSize().background(BK), Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("⚠️",fontSize=52.sp)
            Text(message, color=DM, fontSize=18.sp)
            Surface(
                onClick=onRetry,
                colors=ClickableSurfaceDefaults.colors(containerColor=RD,focusedContainerColor=DRD),
                shape=errShape,
                scale=ClickableSurfaceDefaults.scale(focusedScale=1.06f),
                glow=ClickableSurfaceDefaults.glow(focusedGlow=Glow(RD.copy(0.5f),16.dp)),
                modifier=Modifier.height(50.dp).width(170.dp)
            ) {
                Box(Modifier.fillMaxSize(),Alignment.Center) {
                    Text("נסה שוב", color=WH, fontSize=16.sp, fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}
