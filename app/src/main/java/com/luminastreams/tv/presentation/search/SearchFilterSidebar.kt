@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
package com.luminastreams.tv.presentation.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

// ── Filter Chip Data ─────────────────────────────────────────────────────
private data class FilterChipDef(
    val label: String,
    val icon: ImageVector,
    val isSelected: (SearchFilters) -> Boolean,
    val toggle: (SearchFilters) -> SearchFilters
)

// ── Genre chips ──
private val GENRE_CHIPS = listOf(
    FilterChipDef("Action", Icons.Default.Whatshot, { it.genre == "Action" }, { it.copy(genre = if (it.genre == "Action") null else "Action") }),
    FilterChipDef("Sci-Fi", Icons.Default.FlightTakeoff, { it.genre == "Sci-Fi" }, { it.copy(genre = if (it.genre == "Sci-Fi") null else "Sci-Fi") }),
    FilterChipDef("Comedy", Icons.Default.TagFaces, { it.genre == "Comedy" }, { it.copy(genre = if (it.genre == "Comedy") null else "Comedy") }),
    FilterChipDef("Drama", Icons.Default.Theaters, { it.genre == "Drama" }, { it.copy(genre = if (it.genre == "Drama") null else "Drama") }),
    FilterChipDef("Horror", Icons.Default.VisibilityOff, { it.genre == "Horror" }, { it.copy(genre = if (it.genre == "Horror") null else "Horror") }),
    FilterChipDef("Fantasy", Icons.Default.Stars, { it.genre == "Fantasy" }, { it.copy(genre = if (it.genre == "Fantasy") null else "Fantasy") }),
    FilterChipDef("Thriller", Icons.Default.Warning, { it.genre == "Thriller" }, { it.copy(genre = if (it.genre == "Thriller") null else "Thriller") }),
    FilterChipDef("Crime", Icons.Default.Gavel, { it.genre == "Crime" }, { it.copy(genre = if (it.genre == "Crime") null else "Crime") }),
    FilterChipDef("Romance", Icons.Default.Favorite, { it.genre == "Romance" }, { it.copy(genre = if (it.genre == "Romance") null else "Romance") }),
    FilterChipDef("Animation", Icons.Default.Brush, { it.genre == "Animation" }, { it.copy(genre = if (it.genre == "Animation") null else "Animation") }),
    FilterChipDef("Documentary", Icons.Default.Videocam, { it.genre == "Documentary" }, { it.copy(genre = if (it.genre == "Documentary") null else "Documentary") }),
    FilterChipDef("Mystery", Icons.Default.HelpOutline, { it.genre == "Mystery" }, { it.copy(genre = if (it.genre == "Mystery") null else "Mystery") }),
    FilterChipDef("War", Icons.Default.Security, { it.genre == "War" }, { it.copy(genre = if (it.genre == "War") null else "War") }),
    FilterChipDef("Family", Icons.Default.People, { it.genre == "Family" }, { it.copy(genre = if (it.genre == "Family") null else "Family") }),
    FilterChipDef("Adventure", Icons.Default.Explore, { it.genre == "Adventure" }, { it.copy(genre = if (it.genre == "Adventure") null else "Adventure") }),
    FilterChipDef("History", Icons.Default.AccountBalance, { it.genre == "History" }, { it.copy(genre = if (it.genre == "History") null else "History") }),
    FilterChipDef("Music", Icons.Default.MusicNote, { it.genre == "Music" }, { it.copy(genre = if (it.genre == "Music") null else "Music") }),
    FilterChipDef("Western", Icons.Default.Landscape, { it.genre == "Western" }, { it.copy(genre = if (it.genre == "Western") null else "Western") }),
)

// ── Type chips ──
private val TYPE_CHIPS = listOf(
    FilterChipDef("Movies", Icons.Default.Movie, { it.typeFilter == MediaTypeFilter.MOVIE }, { it.copy(typeFilter = if (it.typeFilter == MediaTypeFilter.MOVIE) MediaTypeFilter.ANY else MediaTypeFilter.MOVIE) }),
    FilterChipDef("Series", Icons.Default.Tv, { it.typeFilter == MediaTypeFilter.TV_SHOW }, { it.copy(typeFilter = if (it.typeFilter == MediaTypeFilter.TV_SHOW) MediaTypeFilter.ANY else MediaTypeFilter.TV_SHOW) }),
    FilterChipDef("Anime", Icons.Default.Brush, { it.typeFilter == MediaTypeFilter.ANIME }, { it.copy(typeFilter = if (it.typeFilter == MediaTypeFilter.ANIME) MediaTypeFilter.ANY else MediaTypeFilter.ANIME) }),
)

// ── Sort chips ──
private val SORT_CHIPS = listOf(
    FilterChipDef("Popular", Icons.Default.TrendingUp, { it.sortBy == SortBy.POPULARITY }, { it.copy(sortBy = SortBy.POPULARITY) }),
    FilterChipDef("Top Rated", Icons.Default.Star, { it.sortBy == SortBy.RATING }, { it.copy(sortBy = SortBy.RATING) }),
    FilterChipDef("Newest", Icons.Default.NewReleases, { it.sortBy == SortBy.NEWEST }, { it.copy(sortBy = SortBy.NEWEST) }),
    FilterChipDef("A → Z", Icons.Default.SortByAlpha, { it.sortBy == SortBy.TITLE }, { it.copy(sortBy = SortBy.TITLE) }),
)

// ── Rating chips ──
private val RATING_CHIPS = listOf(
    FilterChipDef("9+ Masterpiece", Icons.Default.EmojiEvents, { it.ratingTier == RatingTier.MASTERPIECE }, { it.copy(ratingTier = if (it.ratingTier == RatingTier.MASTERPIECE) RatingTier.ANY else RatingTier.MASTERPIECE, minRating = if (it.ratingTier == RatingTier.MASTERPIECE) 0f else 9f) }),
    FilterChipDef("7+ Great", Icons.Default.ThumbUp, { it.ratingTier == RatingTier.GREAT }, { it.copy(ratingTier = if (it.ratingTier == RatingTier.GREAT) RatingTier.ANY else RatingTier.GREAT, minRating = if (it.ratingTier == RatingTier.GREAT) 0f else 7f) }),
    FilterChipDef("5+ Good", Icons.Default.ThumbsUpDown, { it.ratingTier == RatingTier.GOOD }, { it.copy(ratingTier = if (it.ratingTier == RatingTier.GOOD) RatingTier.ANY else RatingTier.GOOD, minRating = if (it.ratingTier == RatingTier.GOOD) 0f else 5f) }),
)

// ── Era chips ──
private val ERA_CHIPS = listOf(
    FilterChipDef("2020s", Icons.Default.DateRange, { it.minYear == 2020 && it.maxYear == 2026 }, { if (it.minYear == 2020) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 2020, maxYear = 2026) }),
    FilterChipDef("2010s", Icons.Default.DateRange, { it.minYear == 2010 && it.maxYear == 2019 }, { if (it.minYear == 2010) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 2010, maxYear = 2019) }),
    FilterChipDef("2000s", Icons.Default.EventNote, { it.minYear == 2000 && it.maxYear == 2009 }, { if (it.minYear == 2000) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 2000, maxYear = 2009) }),
    FilterChipDef("90s", Icons.Default.History, { it.minYear == 1990 && it.maxYear == 1999 }, { if (it.minYear == 1990) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 1990, maxYear = 1999) }),
    FilterChipDef("80s", Icons.Default.History, { it.minYear == 1980 && it.maxYear == 1989 }, { if (it.minYear == 1980) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 1980, maxYear = 1989) }),
    FilterChipDef("Classics", Icons.Default.Schedule, { it.minYear == 1920 && it.maxYear == 1979 }, { if (it.maxYear == 1979) it.copy(minYear = 1920, maxYear = 2026) else it.copy(minYear = 1920, maxYear = 1979) }),
)

// ── Runtime chips ──
private val RUNTIME_CHIPS = listOf(
    FilterChipDef("< 90 min", Icons.Default.Timer, { it.runtime == RuntimeFilter.SHORT }, { it.copy(runtime = if (it.runtime == RuntimeFilter.SHORT) RuntimeFilter.ANY else RuntimeFilter.SHORT) }),
    FilterChipDef("90-120 min", Icons.Default.Schedule, { it.runtime == RuntimeFilter.MEDIUM }, { it.copy(runtime = if (it.runtime == RuntimeFilter.MEDIUM) RuntimeFilter.ANY else RuntimeFilter.MEDIUM) }),
    FilterChipDef("2-3 Hours", Icons.Default.HourglassEmpty, { it.runtime == RuntimeFilter.LONG }, { it.copy(runtime = if (it.runtime == RuntimeFilter.LONG) RuntimeFilter.ANY else RuntimeFilter.LONG) }),
    FilterChipDef("3+ Hours", Icons.Default.HourglassFull, { it.runtime == RuntimeFilter.EPIC }, { it.copy(runtime = if (it.runtime == RuntimeFilter.EPIC) RuntimeFilter.ANY else RuntimeFilter.EPIC) }),
)

// ── Language chips ──
private val LANG_CHIPS = listOf(
    FilterChipDef("English", Icons.Default.Language, { it.language == LanguageFilter.ENGLISH }, { it.copy(language = if (it.language == LanguageFilter.ENGLISH) LanguageFilter.ANY else LanguageFilter.ENGLISH) }),
    FilterChipDef("Hebrew", Icons.Default.Language, { it.language == LanguageFilter.HEBREW }, { it.copy(language = if (it.language == LanguageFilter.HEBREW) LanguageFilter.ANY else LanguageFilter.HEBREW) }),
    FilterChipDef("Korean", Icons.Default.Language, { it.language == LanguageFilter.KOREAN }, { it.copy(language = if (it.language == LanguageFilter.KOREAN) LanguageFilter.ANY else LanguageFilter.KOREAN) }),
    FilterChipDef("Japanese", Icons.Default.Language, { it.language == LanguageFilter.JAPANESE }, { it.copy(language = if (it.language == LanguageFilter.JAPANESE) LanguageFilter.ANY else LanguageFilter.JAPANESE) }),
    FilterChipDef("Spanish", Icons.Default.Language, { it.language == LanguageFilter.SPANISH }, { it.copy(language = if (it.language == LanguageFilter.SPANISH) LanguageFilter.ANY else LanguageFilter.SPANISH) }),
    FilterChipDef("French", Icons.Default.Language, { it.language == LanguageFilter.FRENCH }, { it.copy(language = if (it.language == LanguageFilter.FRENCH) LanguageFilter.ANY else LanguageFilter.FRENCH) }),
)

// ── Platform chips ──
private val NETWORK_CHIPS = listOf(
    FilterChipDef("Netflix", Icons.Default.PlayArrow, { it.network == NetworkFilter.NETFLIX }, { it.copy(network = if (it.network == NetworkFilter.NETFLIX) NetworkFilter.ANY else NetworkFilter.NETFLIX) }),
    FilterChipDef("HBO", Icons.Default.LiveTv, { it.network == NetworkFilter.HBO }, { it.copy(network = if (it.network == NetworkFilter.HBO) NetworkFilter.ANY else NetworkFilter.HBO) }),
    FilterChipDef("Apple TV+", Icons.Default.DesktopWindows, { it.network == NetworkFilter.APPLE_TV }, { it.copy(network = if (it.network == NetworkFilter.APPLE_TV) NetworkFilter.ANY else NetworkFilter.APPLE_TV) }),
    FilterChipDef("Disney+", Icons.Default.Face, { it.network == NetworkFilter.DISNEY_PLUS }, { it.copy(network = if (it.network == NetworkFilter.DISNEY_PLUS) NetworkFilter.ANY else NetworkFilter.DISNEY_PLUS) }),
    FilterChipDef("Amazon", Icons.Default.ShoppingCart, { it.network == NetworkFilter.AMAZON }, { it.copy(network = if (it.network == NetworkFilter.AMAZON) NetworkFilter.ANY else NetworkFilter.AMAZON) }),
    FilterChipDef("Hulu", Icons.Default.Tv, { it.network == NetworkFilter.HULU }, { it.copy(network = if (it.network == NetworkFilter.HULU) NetworkFilter.ANY else NetworkFilter.HULU) }),
)

// ── Quality chips ──
private val QUALITY_CHIPS = listOf(
    FilterChipDef("4K UHD", Icons.Default.Hd, { it.quality == QualityFilter.UHD }, { it.copy(quality = if (it.quality == QualityFilter.UHD) QualityFilter.ANY else QualityFilter.UHD) }),
    FilterChipDef("1080p FHD", Icons.Default.Hd, { it.quality == QualityFilter.FHD }, { it.copy(quality = if (it.quality == QualityFilter.FHD) QualityFilter.ANY else QualityFilter.FHD) }),
    FilterChipDef("720p HD", Icons.Default.Hd, { it.quality == QualityFilter.HD }, { it.copy(quality = if (it.quality == QualityFilter.HD) QualityFilter.ANY else QualityFilter.HD) }),
)

// ── Mood chips ──
private val MOOD_CHIPS = listOf(
    FilterChipDef("Feel Good", Icons.Default.WbSunny, { it.mood == "Feel Good" }, { it.copy(mood = if (it.mood == "Feel Good") null else "Feel Good") }),
    FilterChipDef("Dark & Gritty", Icons.Default.Brightness2, { it.mood == "Dark" }, { it.copy(mood = if (it.mood == "Dark") null else "Dark") }),
    FilterChipDef("Mind-Bending", Icons.Default.Lens, { it.mood == "Mind-Bending" }, { it.copy(mood = if (it.mood == "Mind-Bending") null else "Mind-Bending") }),
    FilterChipDef("Epic", Icons.Default.Public, { it.mood == "Epic" }, { it.copy(mood = if (it.mood == "Epic") null else "Epic") }),
    FilterChipDef("Romantic", Icons.Default.FavoriteBorder, { it.mood == "Romantic" }, { it.copy(mood = if (it.mood == "Romantic") null else "Romantic") }),
)

// ── Tab navigation sections ──
private data class FilterTab(val title: String, val icon: ImageVector, val chips: List<FilterChipDef>) {
    fun activeCount(f: SearchFilters) = chips.count { it.isSelected(f) }
}

private val FILTER_TABS = listOf(
    FilterTab("Genre", Icons.Default.List, GENRE_CHIPS),
    FilterTab("Type", Icons.Default.VideoLibrary, TYPE_CHIPS),
    FilterTab("Sort", Icons.Default.Sort, SORT_CHIPS),
    FilterTab("Rating", Icons.Default.Star, RATING_CHIPS),
    FilterTab("Era", Icons.Default.DateRange, ERA_CHIPS),
    FilterTab("Runtime", Icons.Default.AccessTime, RUNTIME_CHIPS),
    FilterTab("Quality", Icons.Default.Hd, QUALITY_CHIPS),
    FilterTab("Mood", Icons.Default.Face, MOOD_CHIPS),
    FilterTab("Language", Icons.Default.Language, LANG_CHIPS),
    FilterTab("Platform", Icons.Default.Tv, NETWORK_CHIPS),
)

// ══════════════════════════════════════════════════════════════════════════
// ── TWO-PANEL FILTER SIDEBAR ─────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════════════════

private val PANEL_BG = Color(0xFF0C0C10)
private val FILTER_CARD_BG = Color(0xFF16161C)

// Quick preset combos
private data class QuickPreset(
    val label: String, val icon: ImageVector,
    val apply: (SearchFilters) -> SearchFilters
)
private val QUICK_PRESETS = listOf(
    QuickPreset("Action Movies", Icons.Default.Whatshot) { SearchFilters(genre = "Action", typeFilter = MediaTypeFilter.MOVIE) },
    QuickPreset("Comedy Series", Icons.Default.TagFaces) { SearchFilters(genre = "Comedy", typeFilter = MediaTypeFilter.TV_SHOW) },
    QuickPreset("Award Winners", Icons.Default.Star) { SearchFilters(ratingTier = RatingTier.MASTERPIECE, minRating = 9f, sortBy = SortBy.RATING) },
    QuickPreset("New Releases", Icons.Default.NewReleases) { SearchFilters(minYear = 2024, maxYear = 2026, sortBy = SortBy.NEWEST) },
    QuickPreset("Anime", Icons.Default.Brush) { SearchFilters(typeFilter = MediaTypeFilter.ANIME) },
    QuickPreset("Horror Night", Icons.Default.VisibilityOff) { SearchFilters(genre = "Horror", mood = "Dark") },
)

// Section descriptions
private val SECTION_HINTS = mapOf(
    "Genre" to "Pick a genre to explore",
    "Type" to "Movies, series, or anime",
    "Sort" to "How to order results",
    "Rating" to "Filter by quality score",
    "Era" to "Pick a time period",
    "Runtime" to "How long do you have?",
    "Quality" to "Stream resolution",
    "Mood" to "What are you in the mood for?",
    "Language" to "Original language",
    "Platform" to "Streaming service",
)

@Composable
fun FilterSidebar(
    filters: SearchFilters, isFuzer: Boolean, firstFilterFR: FocusRequester,
    onUpdate: (SearchFilters) -> Unit, onClear: () -> Unit, onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val totalActive = remember(filters) {
        FILTER_TABS.sumOf { it.activeCount(filters) } + if (filters.dubbedOnly) 1 else 0
    }

    Row(Modifier.fillMaxSize()) {

        // ═══════════════════════════════════════════════════════════════════
        // ── LEFT: Tab Navigation Rail ─────────────────────────────────────
        // ═══════════════════════════════════════════════════════════════════
        Column(
            Modifier
                .fillMaxHeight()
                .width(200.dp)
                .background(Brush.verticalGradient(listOf(PANEL_BG, Color(0xFF08080C))))
                .padding(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text("Filters", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (totalActive > 0) {
                Text(
                    "$totalActive active",
                    color = RED, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Tab items
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(FILTER_TABS) { idx, tab ->
                    val active = tab.activeCount(filters)
                    var focused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { selectedTab = idx },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selectedTab == idx) FILTER_CARD_BG else Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor = if (selectedTab == idx) Color.White else Color.White.copy(0.4f),
                            focusedContentColor = Color.Black
                        ),
                        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                            .then(if (idx == 0) Modifier.focusRequester(firstFilterFR) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Accent indicator bar for selected tab
                            if (selectedTab == idx && !focused) {
                                Box(
                                    Modifier.width(3.dp).height(20.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(RED)
                                )
                                Spacer(Modifier.width(9.dp))
                            }
                            Icon(tab.icon, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(tab.title, fontSize = 13.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (active > 0 && !focused) {
                                Box(
                                    Modifier.size(18.dp).clip(CircleShape).background(RED),
                                    Alignment.Center
                                ) {
                                    Text("$active", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom actions
            if (filters.isActive) {
                Surface(
                    onClick = onClear,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = RED.copy(0.15f), focusedContainerColor = RED,
                        contentColor = RED, focusedContentColor = Color.White
                    ),
                    border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(36.dp)
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Surface(
                onClick = onClose,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(0.06f), focusedContainerColor = Color.White,
                    contentColor = Color.White.copy(0.6f), focusedContentColor = Color.Black
                ),
                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(36.dp)
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // ── RIGHT: Chip Content Area ──────────────────────────────────────
        // ═══════════════════════════════════════════════════════════════════
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color(0xFF0A0A0E), Color(0xFF060608))))
                .padding(start = 24.dp, end = 48.dp, top = 20.dp, bottom = 16.dp)
        ) {
            val currentTab = FILTER_TABS.getOrNull(selectedTab) ?: FILTER_TABS.first()

            Column {
                // Section header with description
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(currentTab.icon, null, tint = RED, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentTab.title,
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                        val hint = SECTION_HINTS[currentTab.title]
                        if (hint != null) {
                            Text(hint, color = Color.White.copy(0.3f), fontSize = 11.sp)
                        }
                    }
                    val cnt = currentTab.activeCount(filters)
                    if (cnt > 0) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(RED.copy(0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$cnt selected",
                                color = RED, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Gradient divider
                Box(Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(listOf(RED.copy(0.3f), Color.White.copy(0.04f), Color.Transparent))
                ))

                Spacer(Modifier.height(14.dp))

                // Quick Presets (only show on Genre tab)
                if (selectedTab == 0) {
                    Text("Quick Presets", color = Color.White.copy(0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(QUICK_PRESETS) { _, preset ->
                            Surface(
                                onClick = { onUpdate(preset.apply(filters)) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(0.04f),
                                    focusedContainerColor = RED,
                                    contentColor = Color.White.copy(0.7f),
                                    focusedContentColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(preset.icon, null, Modifier.size(13.dp))
                                    Text(preset.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Chip grid
                ChipFlowGrid(
                    chips = currentTab.chips,
                    filters = filters,
                    onUpdate = onUpdate
                )

                // Fuzer special
                if (isFuzer && selectedTab == 0) {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.04f)))
                    Spacer(Modifier.height(16.dp))
                    Text("Fuzer", color = Color.White.copy(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SingleChip(
                        chip = FilterChipDef("Hebrew Dubbed", Icons.Default.RecordVoiceOver, { it.dubbedOnly }, { it.copy(dubbedOnly = !it.dubbedOnly) }),
                        filters = filters,
                        onUpdate = onUpdate
                    )
                }

                // Active filters summary bar at bottom
                if (totalActive > 0) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FILTER_CARD_BG)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                buildFilterSummary(filters),
                                color = Color.White.copy(0.5f), fontSize = 11.sp, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Chip Flow Grid (wrapping horizontally) ───────────────────────────────
@Composable
private fun ChipFlowGrid(
    chips: List<FilterChipDef>,
    filters: SearchFilters,
    onUpdate: (SearchFilters) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        chips.forEach { chip ->
            SingleChip(chip, filters, onUpdate)
        }
    }
}

@Composable
private fun SingleChip(
    chip: FilterChipDef,
    filters: SearchFilters,
    onUpdate: (SearchFilters) -> Unit
) {
    val selected = chip.isSelected(filters)
    var focused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            selected -> RED.copy(0.18f)
            else -> FILTER_CARD_BG
        },
        animationSpec = tween(200), label = "chipBg"
    )

    Surface(
        onClick = { onUpdate(chip.toggle(filters)) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = Color.White,
            contentColor = if (selected) Color.White else Color.White.copy(0.6f),
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.height(42.dp)
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                chip.icon, null,
                modifier = Modifier.size(16.dp),
                tint = when {
                    focused -> Color.Black
                    selected -> RED
                    else -> Color.White.copy(0.35f)
                }
            )
            Text(
                chip.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (selected && !focused) {
                Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = RED)
            }
        }
    }
}

// ── Build human-readable filter summary ──────────────────────────────────
private fun buildFilterSummary(filters: SearchFilters): String {
    val parts = mutableListOf<String>()
    filters.genre?.let { parts.add(it) }
    if (filters.typeFilter != MediaTypeFilter.ANY) parts.add(
        when (filters.typeFilter) { MediaTypeFilter.MOVIE -> "Movies"; MediaTypeFilter.TV_SHOW -> "Series"; MediaTypeFilter.ANIME -> "Anime"; else -> "" }
    )
    if (filters.ratingTier != RatingTier.ANY) parts.add(
        when (filters.ratingTier) { RatingTier.MASTERPIECE -> "9+★"; RatingTier.GREAT -> "7+★"; RatingTier.GOOD -> "5+★"; else -> "" }
    )
    if (filters.minYear > 1920 || filters.maxYear < 2026) parts.add("${filters.minYear}-${filters.maxYear}")
    if (filters.quality != QualityFilter.ANY) parts.add(
        when (filters.quality) { QualityFilter.UHD -> "4K"; QualityFilter.FHD -> "1080p"; QualityFilter.HD -> "720p"; else -> "" }
    )
    filters.mood?.let { parts.add(it) }
    if (filters.language != LanguageFilter.ANY) parts.add(filters.language.name.lowercase().replaceFirstChar { it.uppercase() })
    if (filters.network != NetworkFilter.ANY) parts.add(filters.network.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() })
    if (filters.sortBy != SortBy.POPULARITY) parts.add("Sort: ${filters.sortBy.name.lowercase().replaceFirstChar { it.uppercase() }}")
    if (filters.dubbedOnly) parts.add("Dubbed")
    return parts.joinToString(" · ")
}
