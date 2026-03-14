@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.tv.material3.Text

// ───────────────────────────────────────────────────────────────────────────
// HomeScreen — WIPED. Ready for redesign.
// ───────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Wiped — choose a design", color = Color.White)
    }
}

// Stubs so other files compile
@Composable fun NfContentRow(title: String, movies: List<com.luminastreams.tv.domain.model.Movie>, onFocus: (com.luminastreams.tv.domain.model.Movie) -> Unit = {}, onClick: (String) -> Unit) {}
@Composable fun NfLoadingSkeleton() {}
@Composable fun NfErrorScreen(message: String, onRetry: () -> Unit) {}
@Composable fun NfSidebar(open: Boolean, activeId: String, sidebarFirstFR: androidx.compose.ui.focus.FocusRequester, onFocusLanded: () -> Unit, onClose: () -> Unit, onNavSelect: (String) -> Unit) {}
