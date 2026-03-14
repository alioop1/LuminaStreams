package com.luminastreams.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.theme.NetflixRed
import com.luminastreams.tv.ui.theme.OledBlack
import com.luminastreams.tv.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border

@Composable
fun HeroBanner(
    focusedMovie: Movie?,
    modifier: Modifier = Modifier
) {
    var dominantColor by remember { mutableStateOf(Color(0xFF0A0A0A)) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val animatedColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "hero_color")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp) // UI Scale: מותאם למסכי טלוויזיה גדולים
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedColor.copy(alpha = 0.6f), OledBlack),
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        Crossfade(targetState = focusedMovie, animationSpec = tween(800), label = "hero_crossfade") { movie ->
            if (movie != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // כאן יוכנס ה-AndroidView של ExoPlayer עבור Auto-play במקום ה-AsyncImage (כשיש URL של סרטון)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(movie.backdropUrl)
                            .crossfade(true)
                            .size(1920, 1080) // Constrain the VRAM allocation to standard 1080p
                            .allowHardware(true) // Offload rendering back to the GPU
                            .listener(
                                onSuccess = { _, result ->
                                    coroutineScope.launch(Dispatchers.Default) {
                                        val bitmap = result.drawable.toBitmap().copy(Bitmap.Config.RGB_565, false)
                                        val palette = Palette.from(bitmap).generate()
                                        val colorInt = palette.getDarkVibrantColor(android.graphics.Color.parseColor("#0A0A0A"))
                                        withContext(Dispatchers.Main) { dominantColor = Color(colorInt) }
                                    }
                                }
                            ).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().align(Alignment.TopEnd)
                    )

                    // שכבת רשת גריד (Scrim) להשחרה הדרגתית (OLED True Black)
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(OledBlack, Color.Transparent), startX = 0f, endX = 1500f)
                    ))

                    Column(
                        modifier = Modifier.fillMaxSize().padding(start = 56.dp, bottom = 48.dp, end = 56.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.Start
                    ) {
                        // תגיות איכות (4K/HDR) וז'אנרים (Chips)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (movie.is4K) QualityBadge("4K HDR")
                            QualityBadge("IMDb ${movie.rating}")
                            GenreChip("Action")
                            GenreChip("Sci-Fi")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(text = movie.title, color = TextPrimary, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(text = movie.overview, color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(0.5f), maxLines = 3)

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { /* Play */ }, colors = ButtonDefaults.colors(containerColor = NetflixRed, focusedContainerColor = Color.White, focusedContentColor = NetflixRed)) {
                                Text("▶ נגן עכשיו", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { /* Add to Watchlist */ }, colors = ButtonDefaults.colors(containerColor = Color(0x4D000000), focusedContainerColor = Color.White, focusedContentColor = OledBlack)) {
                                Text("+ הוסף לרשימה", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QualityBadge(text: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text = text, color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GenreChip(text: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x4DFFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}