@file:OptIn(ExperimentalComposeUiApi::class)
package com.luminastreams.tv.presentation.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class CubeCategory(
    val title: String, val subtitle: String, val bgUrl: String,
    val accent: Color, val section: String,
    val apply: (SearchFilters) -> SearchFilters,
    val checkIsSelected: (SearchFilters) -> Boolean
)

private fun genreCube(t: String, sub: String, bg: String, c: Color) = CubeCategory(t, sub, bg, c, "🎭 Genres",
    { if (it.genre == t) it.copy(genre = null) else it.copy(genre = t) }, { it.genre == t })

val CUBE_CATEGORIES = listOf(
    // ── Type ──
    CubeCategory("Movies","Blockbusters","https://image.tmdb.org/t/p/w780/8pjWz2lt29KyVGoq1mEBtiD7BWE.jpg",Color(0xFFE50914),"🎬 Type",{if(it.typeFilter==MediaTypeFilter.MOVIE)it.copy(typeFilter=MediaTypeFilter.ANY)else it.copy(typeFilter=MediaTypeFilter.MOVIE)},{it.typeFilter==MediaTypeFilter.MOVIE}),
    CubeCategory("Series","Binge Worthy","https://image.tmdb.org/t/p/w780/uDgy6hyPd82kOHh6X8TITjzSMz1.jpg",Color(0xFF00D4FF),"🎬 Type",{if(it.typeFilter==MediaTypeFilter.TV_SHOW)it.copy(typeFilter=MediaTypeFilter.ANY)else it.copy(typeFilter=MediaTypeFilter.TV_SHOW)},{it.typeFilter==MediaTypeFilter.TV_SHOW}),
    CubeCategory("Anime","Japan's Best","https://image.tmdb.org/t/p/w780/yguqe2NEdmZtFjX9Pmb0Ehs1y.jpg",Color(0xFFFF2D78),"🎬 Type",{if(it.typeFilter==MediaTypeFilter.ANIME)it.copy(typeFilter=MediaTypeFilter.ANY)else it.copy(typeFilter=MediaTypeFilter.ANIME)},{it.typeFilter==MediaTypeFilter.ANIME}),

    // ── Sort ──
    CubeCategory("Popular","Trending Now","https://image.tmdb.org/t/p/w780/5YZbUmjbMa3ClvSW1Wj3D6XGolb.jpg",Color(0xFFFF3D00),"📊 Sort By",{it.copy(sortBy=SortBy.POPULARITY)},{it.sortBy==SortBy.POPULARITY}),
    CubeCategory("Top Rated","Masterpieces","https://image.tmdb.org/t/p/w780/rSPw7tgCH9c6NqICZef4kZjFOQ5.jpg",Color(0xFFFFCC00),"📊 Sort By",{it.copy(sortBy=SortBy.RATING)},{it.sortBy==SortBy.RATING}),
    CubeCategory("Newest","Fresh Releases","https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",Color(0xFF00E676),"📊 Sort By",{it.copy(sortBy=SortBy.NEWEST)},{it.sortBy==SortBy.NEWEST}),
    CubeCategory("A → Z","Alphabetical","https://image.tmdb.org/t/p/w780/mdfLIOQ5BxxA8F8Tf1fQmbcO26o.jpg",Color(0xFF00B0FF),"📊 Sort By",{it.copy(sortBy=SortBy.TITLE)},{it.sortBy==SortBy.TITLE}),

    // ── Genres ──
    genreCube("Action","Adrenaline Rush","https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",Color(0xFFFF3D00)),
    genreCube("Sci-Fi","Other Worlds","https://image.tmdb.org/t/p/w780/xJHokMbljvjEVAeUCNWeIHN0Vb.jpg",Color(0xFFB000FF)),
    genreCube("Comedy","Laugh Out Loud","https://image.tmdb.org/t/p/w780/en971MEXui9diirXlogOrPKmsJ.jpg",Color(0xFF00E676)),
    genreCube("Horror","Spooky Nights","https://image.tmdb.org/t/p/w780/5a4JdoFwll5DRtKMe7JLuZzNI6i.jpg",Color(0xFF6200EA)),
    genreCube("Drama","Deep Emotions","https://image.tmdb.org/t/p/w780/bOGkgRGdhrBYJSLpXaxhXVstddV.jpg",Color(0xFFE50914)),
    genreCube("Fantasy","Magic Realms","https://image.tmdb.org/t/p/w780/vI3aPTy8snXGQRxO1Ru6GklGzJ.jpg",Color(0xFF00B0FF)),
    genreCube("Crime","Underworld","https://image.tmdb.org/t/p/w780/vYnwGAiwB1z4kS0A22V1P81t9U.jpg",Color(0xFFD50000)),
    genreCube("Family","Kids & Co.","https://image.tmdb.org/t/p/w780/s51ChDMMXq0kGgeT6xN3pW17J5U.jpg",Color(0xFF00E5FF)),
    genreCube("Romance","Love Stories","https://image.tmdb.org/t/p/w780/1X7vow16X7CnCoexXh4H4F2yDJv.jpg",Color(0xFFFF4081)),
    genreCube("Thriller","Edge of Seat","https://image.tmdb.org/t/p/w780/7I6VUdPj6tQECNHdviJkUHD2u89.jpg",Color(0xFFFF6D00)),
    genreCube("Adventure","Epic Journeys","https://image.tmdb.org/t/p/w780/5kMT3Z60vHla6eZ35Lp3aB4xZ5m.jpg",Color(0xFF00C853)),
    genreCube("Documentary","True Stories","https://image.tmdb.org/t/p/w780/mdfLIOQ5BxxA8F8Tf1fQmbcO26o.jpg",Color(0xFF78909C)),
    genreCube("Mystery","Whodunit","https://image.tmdb.org/t/p/w780/rSPw7tgCH9c6NqICZef4kZjFOQ5.jpg",Color(0xFF7C4DFF)),
    genreCube("War","Battlefields","https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",Color(0xFF455A64)),
    genreCube("Western","Wild Frontier","https://image.tmdb.org/t/p/w780/8pjWz2lt29KyVGoq1mEBtiD7BWE.jpg",Color(0xFFBF360C)),
    genreCube("Music","Rhythm & Beats","https://image.tmdb.org/t/p/w780/en971MEXui9diirXlogOrPKmsJ.jpg",Color(0xFFE040FB)),
    genreCube("History","Past Echoes","https://image.tmdb.org/t/p/w780/5YZbUmjbMa3ClvSW1Wj3D6XGolb.jpg",Color(0xFF8D6E63)),

    // ── Eras ──
    CubeCategory("2020s","Modern Era","https://image.tmdb.org/t/p/w780/1X7vow16X7CnCoexXh4H4F2yDJv.jpg",Color(0xFF00D4FF),"📅 Era",{if(it.minYear==2020)it.copy(minYear=1920,maxYear=2026)else it.copy(minYear=2020,maxYear=2026)},{it.minYear==2020&&it.maxYear==2026}),
    CubeCategory("2010s","Last Decade","https://image.tmdb.org/t/p/w780/mdfLIOQ5BxxA8F8Tf1fQmbcO26o.jpg",Color(0xFF448AFF),"📅 Era",{if(it.minYear==2010)it.copy(minYear=1920,maxYear=2026)else it.copy(minYear=2010,maxYear=2019)},{it.minYear==2010&&it.maxYear==2019}),
    CubeCategory("2000s","Y2K Vibes","https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",Color(0xFF7C4DFF),"📅 Era",{if(it.minYear==2000)it.copy(minYear=1920,maxYear=2026)else it.copy(minYear=2000,maxYear=2009)},{it.minYear==2000&&it.maxYear==2009}),
    CubeCategory("90s","Nostalgia","https://image.tmdb.org/t/p/w780/5a4JdoFwll5DRtKMe7JLuZzNI6i.jpg",Color(0xFFFF4081),"📅 Era",{if(it.minYear==1990)it.copy(minYear=1920,maxYear=2026)else it.copy(minYear=1990,maxYear=1999)},{it.minYear==1990&&it.maxYear==1999}),
    CubeCategory("Classics","Before 1990","https://image.tmdb.org/t/p/w780/5kMT3Z60vHla6eZ35Lp3aB4xZ5m.jpg",Color(0xFFFFCC00),"📅 Era",{if(it.minYear==1920&&it.maxYear==1989)it.copy(minYear=1920,maxYear=2026)else it.copy(minYear=1920,maxYear=1989)},{it.minYear==1920&&it.maxYear==1989}),

    // ── Quality ──
    CubeCategory("4K UHD","Crystal Clear","https://image.tmdb.org/t/p/w780/7I6VUdPj6tQECNHdviJkUHD2u89.jpg",Color(0xFFE50914),"🎞 Quality",{if(it.quality==QualityFilter.UHD)it.copy(quality=QualityFilter.ANY)else it.copy(quality=QualityFilter.UHD)},{it.quality==QualityFilter.UHD}),
    CubeCategory("Full HD","1080p Sharp","https://image.tmdb.org/t/p/w780/rSPw7tgCH9c6NqICZef4kZjFOQ5.jpg",Color(0xFF00D4FF),"🎞 Quality",{if(it.quality==QualityFilter.FHD)it.copy(quality=QualityFilter.ANY)else it.copy(quality=QualityFilter.FHD)},{it.quality==QualityFilter.FHD}),
    CubeCategory("HD","720p Ready","https://image.tmdb.org/t/p/w780/xJHokMbljvjEVAeUCNWeIHN0Vb.jpg",Color(0xFF00E676),"🎞 Quality",{if(it.quality==QualityFilter.HD)it.copy(quality=QualityFilter.ANY)else it.copy(quality=QualityFilter.HD)},{it.quality==QualityFilter.HD}),

    // ── Rating Tier ──
    CubeCategory("9+ Stars","Masterpieces","https://image.tmdb.org/t/p/w780/8pjWz2lt29KyVGoq1mEBtiD7BWE.jpg",Color(0xFFFFD600),"⭐ Rating",{if(it.ratingTier==RatingTier.MASTERPIECE)it.copy(ratingTier=RatingTier.ANY,minRating=0f)else it.copy(ratingTier=RatingTier.MASTERPIECE,minRating=9f)},{it.ratingTier==RatingTier.MASTERPIECE}),
    CubeCategory("7+ Stars","Great Picks","https://image.tmdb.org/t/p/w780/uDgy6hyPd82kOHh6X8TITjzSMz1.jpg",Color(0xFFFFAB00),"⭐ Rating",{if(it.ratingTier==RatingTier.GREAT)it.copy(ratingTier=RatingTier.ANY,minRating=0f)else it.copy(ratingTier=RatingTier.GREAT,minRating=7f)},{it.ratingTier==RatingTier.GREAT}),
    CubeCategory("5+ Stars","Worth a Watch","https://image.tmdb.org/t/p/w780/bOGkgRGdhrBYJSLpXaxhXVstddV.jpg",Color(0xFFFF6D00),"⭐ Rating",{if(it.ratingTier==RatingTier.GOOD)it.copy(ratingTier=RatingTier.ANY,minRating=0f)else it.copy(ratingTier=RatingTier.GOOD,minRating=5f)},{it.ratingTier==RatingTier.GOOD}),

    // ── Language ──
    CubeCategory("English","Hollywood","https://image.tmdb.org/t/p/w780/5YZbUmjbMa3ClvSW1Wj3D6XGolb.jpg",Color(0xFF1565C0),"🌍 Language",{if(it.language==LanguageFilter.ENGLISH)it.copy(language=LanguageFilter.ANY)else it.copy(language=LanguageFilter.ENGLISH)},{it.language==LanguageFilter.ENGLISH}),
    CubeCategory("Hebrew","Israeli Cinema","https://image.tmdb.org/t/p/w780/2uNW4WbgBXL25BAbXGLnLqX71Sw.jpg",Color(0xFF0091EA),"🌍 Language",{if(it.language==LanguageFilter.HEBREW)it.copy(language=LanguageFilter.ANY)else it.copy(language=LanguageFilter.HEBREW)},{it.language==LanguageFilter.HEBREW}),
    CubeCategory("Korean","K-Drama Wave","https://image.tmdb.org/t/p/w780/vI3aPTy8snXGQRxO1Ru6GklGzJ.jpg",Color(0xFFD500F9),"🌍 Language",{if(it.language==LanguageFilter.KOREAN)it.copy(language=LanguageFilter.ANY)else it.copy(language=LanguageFilter.KOREAN)},{it.language==LanguageFilter.KOREAN}),
    CubeCategory("Japanese","J-Cinema","https://image.tmdb.org/t/p/w780/yguqe2NEdmZtFjX9Pmb0Ehs1y.jpg",Color(0xFFFF1744),"🌍 Language",{if(it.language==LanguageFilter.JAPANESE)it.copy(language=LanguageFilter.ANY)else it.copy(language=LanguageFilter.JAPANESE)},{it.language==LanguageFilter.JAPANESE}),
    CubeCategory("Spanish","Latino Heat","https://image.tmdb.org/t/p/w780/en971MEXui9diirXlogOrPKmsJ.jpg",Color(0xFFFF6D00),"🌍 Language",{if(it.language==LanguageFilter.SPANISH)it.copy(language=LanguageFilter.ANY)else it.copy(language=LanguageFilter.SPANISH)},{it.language==LanguageFilter.SPANISH}),

    // ── Mood ──
    CubeCategory("Feel Good","Happy Vibes","https://image.tmdb.org/t/p/w780/s51ChDMMXq0kGgeT6xN3pW17J5U.jpg",Color(0xFFFFD600),"🎭 Mood",{if(it.mood=="Feel Good")it.copy(mood=null)else it.copy(mood="Feel Good")},{it.mood=="Feel Good"}),
    CubeCategory("Dark","Gritty & Raw","https://image.tmdb.org/t/p/w780/5a4JdoFwll5DRtKMe7JLuZzNI6i.jpg",Color(0xFF37474F),"🎭 Mood",{if(it.mood=="Dark")it.copy(mood=null)else it.copy(mood="Dark")},{it.mood=="Dark"}),
    CubeCategory("Mind-Bending","Think Twice","https://image.tmdb.org/t/p/w780/xJHokMbljvjEVAeUCNWeIHN0Vb.jpg",Color(0xFF7C4DFF),"🎭 Mood",{if(it.mood=="Mind-Bending")it.copy(mood=null)else it.copy(mood="Mind-Bending")},{it.mood=="Mind-Bending"}),
    CubeCategory("Epic","Grand Scale","https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",Color(0xFFFF3D00),"🎭 Mood",{if(it.mood=="Epic")it.copy(mood=null)else it.copy(mood="Epic")},{it.mood=="Epic"}),

    // ── Runtime ──
    CubeCategory("< 90 min","Quick Watch","https://image.tmdb.org/t/p/w780/mdfLIOQ5BxxA8F8Tf1fQmbcO26o.jpg",Color(0xFF00BFA5),"⏱ Runtime",{if(it.runtime==RuntimeFilter.SHORT)it.copy(runtime=RuntimeFilter.ANY)else it.copy(runtime=RuntimeFilter.SHORT)},{it.runtime==RuntimeFilter.SHORT}),
    CubeCategory("90-120 min","Standard","https://image.tmdb.org/t/p/w780/1X7vow16X7CnCoexXh4H4F2yDJv.jpg",Color(0xFF00B0FF),"⏱ Runtime",{if(it.runtime==RuntimeFilter.MEDIUM)it.copy(runtime=RuntimeFilter.ANY)else it.copy(runtime=RuntimeFilter.MEDIUM)},{it.runtime==RuntimeFilter.MEDIUM}),
    CubeCategory("2-3 Hours","Long Form","https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",Color(0xFFFF6D00),"⏱ Runtime",{if(it.runtime==RuntimeFilter.LONG)it.copy(runtime=RuntimeFilter.ANY)else it.copy(runtime=RuntimeFilter.LONG)},{it.runtime==RuntimeFilter.LONG}),
    CubeCategory("3+ Hours","Epic Length","https://image.tmdb.org/t/p/w780/5kMT3Z60vHla6eZ35Lp3aB4xZ5m.jpg",Color(0xFFD50000),"⏱ Runtime",{if(it.runtime==RuntimeFilter.EPIC)it.copy(runtime=RuntimeFilter.ANY)else it.copy(runtime=RuntimeFilter.EPIC)},{it.runtime==RuntimeFilter.EPIC}),
)

data class PersonaCategory(
    val title: String, val subtitle: String, val bgUrl: String,
    val apply: (SearchFilters) -> SearchFilters
)

val PERSONAS = listOf(
    PersonaCategory("Late Night Thrills", "Dark, gritty & suspenseful", "https://image.tmdb.org/t/p/w780/5a4JdoFwll5DRtKMe7JLuZzNI6i.jpg", { it.copy(genre = "Thriller", mood = "Dark", ratingTier = RatingTier.GOOD) }),
    PersonaCategory("Sunday Family Couch", "Feel-good for everyone", "https://image.tmdb.org/t/p/w780/s51ChDMMXq0kGgeT6xN3pW17J5U.jpg", { it.copy(genre = "Family", mood = "Feel Good", typeFilter = MediaTypeFilter.MOVIE) }),
    PersonaCategory("Brain-Melting Sci-Fi", "Think twice", "https://image.tmdb.org/t/p/w780/xJHokMbljvjEVAeUCNWeIHN0Vb.jpg", { it.copy(genre = "Sci-Fi", mood = "Mind-Bending", ratingTier = RatingTier.GREAT) }),
    PersonaCategory("Award-Winning Tearjerkers", "Deep emotions", "https://image.tmdb.org/t/p/w780/bOGkgRGdhrBYJSLpXaxhXVstddV.jpg", { it.copy(genre = "Drama", ratingTier = RatingTier.MASTERPIECE, sortBy = SortBy.RATING) }),
    PersonaCategory("Adrenaline Rush", "Non-stop action", "https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg", { it.copy(genre = "Action", sortBy = SortBy.POPULARITY, quality = QualityFilter.UHD) })
)

@Composable
fun SmartSentence(filters: SearchFilters) {
    val parts = mutableListOf<Pair<String, Boolean>>()
    parts.add("I want to watch " to false)

    val hasMood = filters.mood != null
    val hasGenre = filters.genre != null
    val typeStr = when (filters.typeFilter) {
        MediaTypeFilter.MOVIE -> "Movie"
        MediaTypeFilter.TV_SHOW -> "Series"
        MediaTypeFilter.ANIME -> "Anime"
        else -> "Something"
    }

    if (hasMood) {
        parts.add("a " to false)
        parts.add("${filters.mood} " to true)
    } else if (hasGenre) {
        val startsWithVowel = listOf("A", "E", "I", "O", "U").any { filters.genre!!.startsWith(it, true) }
        parts.add((if (startsWithVowel) "an " else "a ") to false)
    } else {
        parts.add((if (typeStr == "Something") "" else "a ") to false)
    }

    if (hasGenre) {
        parts.add("${filters.genre} " to true)
    }

    parts.add("$typeStr " to (filters.typeFilter != MediaTypeFilter.ANY))

    if (filters.minYear != 1920 || filters.maxYear != 2026) {
        val era = if (filters.minYear == 1920 && filters.maxYear == 1989) "Classics"
                  else if (filters.minYear == 1990) "90s"
                  else "${filters.minYear}s"
        parts.add("from the " to false)
        parts.add("$era " to true)
    }

    if (filters.ratingTier != RatingTier.ANY) {
        val stars = when (filters.ratingTier) {
            RatingTier.MASTERPIECE -> "9+ Stars"
            RatingTier.GREAT -> "7+ Stars"
            RatingTier.GOOD -> "5+ Stars"
            else -> ""
        }
        parts.add("with " to false)
        parts.add("$stars " to true)
    }

    if (filters.quality != QualityFilter.ANY) {
        val qStr = when (filters.quality) {
            QualityFilter.UHD -> "4K UHD"
            QualityFilter.FHD -> "1080p"
            QualityFilter.HD -> "720p"
            else -> ""
        }
        parts.add("in " to false)
        parts.add("$qStr " to true)
    }

    val annotated = buildAnnotatedString {
        parts.forEach { (text, isHighlight) ->
            if (isHighlight) {
                withStyle(SpanStyle(color = Color(0xFFE50914), fontWeight = FontWeight.Black)) { append(text) }
            } else {
                withStyle(SpanStyle(color = Color.White)) { append(text) }
            }
        }
    }

    Text(annotated, fontSize = 38.sp, fontWeight = FontWeight.Medium, lineHeight = 44.sp)
}

@Composable
fun FilterSidebar(
    filters: SearchFilters, isFuzer: Boolean, firstFilterFR: FocusRequester,
    onUpdate: (SearchFilters) -> Unit, onClear: () -> Unit, onClose: () -> Unit
) {
    val sections = remember { CUBE_CATEGORIES.map { it.section }.distinct() }
    val grouped = remember { CUBE_CATEGORIES.groupBy { it.section } }
    val activeCount = remember(filters) { CUBE_CATEGORIES.count { it.checkIsSelected(filters) } }

    Column(Modifier.fillMaxSize()) {
        // ── Header ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth(0.75f)) {
                SmartSentence(filters)
                Text(if (activeCount > 0) "$activeCount filters active · Tap to toggle" else "Select categories or moods to find the perfect match", color = Color(0x99FFFFFF), fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filters.isActive) {
                    Surface(onClick = onClear, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFFE50914).copy(0.15f), focusedContainerColor = Color(0xFFE50914), contentColor = Color(0xFFE50914), focusedContentColor = Color.White), modifier = Modifier.height(48.dp)) {
                        Row(Modifier.padding(horizontal = 20.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FilterAltOff, null, Modifier.size(20.dp))
                            Text("Clear All", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(onClick = onClose, shape = ClickableSurfaceDefaults.shape(CircleShape), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A24), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), modifier = Modifier.size(48.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(22.dp)) }
                }
            }
        }

        // ── Grid with section headers ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("✨ Curated Personas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0x22FFFFFF)))
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        itemsIndexed(PERSONAS) { idx, p ->
                            PersonaCard(p, if (idx == 0) firstFilterFR else null) { onUpdate(p.apply(filters)) }
                        }
                    }
                }
            }

            var isFirst = true
            sections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = if (isFirst) 0.dp else 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(section, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0x22FFFFFF)))
                    }
                }
                isFirst = false
                val items = grouped[section] ?: emptyList()
                itemsIndexed(items) { idx, cat ->
                    val sel = cat.checkIsSelected(filters)
                    CubeCard(cat, sel, null) { onUpdate(cat.apply(filters)) }
                }
            }

            if (isFuzer) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("💎 Fuzer Special", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0x22FFFFFF)))
                    }
                }
                item {
                    CubeCard(CubeCategory("Hebrew Dubbed","מדובב לעברית","https://image.tmdb.org/t/p/w780/2uNW4WbgBXL25BAbXGLnLqX71Sw.jpg",Color(0xFFFF2D78),"💎 Fuzer Special",{it.copy(dubbedOnly=!it.dubbedOnly)},{it.dubbedOnly}), filters.dubbedOnly, null) { onUpdate(filters.copy(dubbedOnly = !filters.dubbedOnly)) }
                }
            }
        }
    }
}

@Suppress("ASSIGNED_BUT_NEVER_READ")
@Composable
private fun CubeCard(category: CubeCategory, isSelected: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when { isFocused -> Color.White; isSelected -> category.accent; else -> Color.Transparent }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(2.dp, borderColor), shape = RoundedCornerShape(18.dp)),
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(18.dp))
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(category.accent.copy(alpha = 0.5f), 28.dp)),
        modifier = Modifier.aspectRatio(1.15f).fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(category.bgUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val alpha by animateFloatAsState(if (isFocused) 0.45f else 0.8f, tween(300))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha))))
            if (isSelected && !isFocused) {
                Box(Modifier.fillMaxSize().background(category.accent.copy(0.35f)))
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(category.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(category.subtitle, color = Color(0xBBFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Suppress("ASSIGNED_BUT_NEVER_READ")
@Composable
fun PersonaCard(persona: PersonaCategory, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color.Transparent), shape = RoundedCornerShape(18.dp)),
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(18.dp))
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color(0xFFE50914).copy(alpha = 0.5f), 28.dp)),
        modifier = Modifier.width(260.dp).height(140.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(persona.bgUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val alpha by animateFloatAsState(if (isFocused) 0.35f else 0.7f, tween(300))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 1f to Color.Black.copy(alpha))))
            
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(persona.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(persona.subtitle, color = Color(0xBBFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}