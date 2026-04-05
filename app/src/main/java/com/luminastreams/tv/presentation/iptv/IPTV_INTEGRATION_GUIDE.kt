// ══════════════════════════════════════════════════════════════════════════════
// IPTV INTEGRATION — Add these changes to your existing files
// ══════════════════════════════════════════════════════════════════════════════

// ─── 1. In MainActivity.kt → AppNavHostContainer → inside NavHost block ─────
// Add this composable route AFTER the "watchlist" route:

/*
        composable("iptv") {
            val vm: IptvViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            IptvScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onPlayChannel = { streamUrl, title ->
                    val safeUrl   = URLEncoder.encode(streamUrl, "UTF-8")
                    val safeTitle = URLEncoder.encode(title, "UTF-8")
                    navController.navigate(
                        "player?videoUrl=$safeUrl&imdbId=_&title=$safeTitle&backdropUrl=none&logoUrl=none"
                    )
                }
            )
        }
*/

// ─── 2. In HomeScreen.kt → TwoRowNavBar → NavPill list ─────────────────────
// Add this NavPill entry AFTER the "Fuzer" pill:

/*
    NavPill(
        tr("Live TV", "טלוויזיה חיה"),
        Icons.Default.LiveTv,
        false,
        null,
        onIptv
    ) { o, w -> tabPositions["iptv"] = o; tabWidths["iptv"] = w }
*/

// ─── 3. In HomeScreen.kt → TwoRowNavBar function signature ─────────────────
// Add parameter:   onIptv: () -> Unit,

// ─── 4. In HomeScreen.kt → ContentLayer function signature ─────────────────
// Add parameter:   onIptv: () -> Unit,

// ─── 5. In HomeScreen.kt → ContentLayer call site ───────────────────────────
// Add:   onIptv = { navController.navigate("iptv") },

// ─── 6. In HomeScreen.kt → HomeScreen composable → ContentLayer call ────────
// Add:   onIptv = { navController.navigate("iptv") },

// ══════════════════════════════════════════════════════════════════════════════
// REQUIRED IMPORTS for MainActivity.kt:
// import com.luminastreams.tv.presentation.iptv.IptvViewModel
// import com.luminastreams.tv.presentation.iptv.IptvScreen
// ══════════════════════════════════════════════════════════════════════════════
