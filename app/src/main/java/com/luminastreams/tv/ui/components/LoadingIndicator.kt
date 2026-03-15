package com.luminastreams.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminastreams.tv.ui.theme.NetflixRed

/**
 * ✅ FIXED: No longer uses fillMaxSize() which caused layout issues inside
 * small containers like LazyRow items and Box with limited height.
 * Use [size] param to control indicator size (default 56dp for TV).
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(modifier = modifier.wrapContentSize(Alignment.Center)) {
        CircularProgressIndicator(
            color    = NetflixRed,
            modifier = Modifier.size(size),
            strokeWidth = 3.dp
        )
    }
}