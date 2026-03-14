package com.luminastreams.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

val CustomMicIcon: ImageVector
    get() = ImageVector.Builder("CustomMic", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f); lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f); curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f); curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
            moveTo(17f, 11f); curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f); lineTo(5f, 11f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f); lineTo(11f, 21f)
            lineTo(13f, 21f); lineTo(13f, 17.92f); curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            lineTo(17f, 11f); close()
        }
    }.build()

// Premium Color Palette
val OledBlack = Color(0xFF000000)
val NetflixRed = Color(0xFFE50914)
val GlassWhite = Color(0x22FFFFFF)
val PureWhite = Color(0xFFFFFFFF)

@Composable
fun TopNavBar(
    rdStatus: Boolean = true,
    hasNotifications: Boolean = true,
    onVoiceSearchClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Instantiate the heavy object ONCE outside the loop
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = dateFormat.format(Date())
            delay(60000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp) // Sleeker height
            .background(
                Brush.verticalGradient(
                    colors = listOf(OledBlack.copy(alpha = 0.95f), OledBlack.copy(alpha = 0.5f), Color.Transparent)
                )
            )
            .padding(horizontal = 64.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- LOGO & STATUS SECTION ---
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "LUMINA",
                color = NetflixRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp // Cinematic spacing
            )

            // Micro RD+ Status Pill
            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassWhite).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    text = if (rdStatus) "RD+" else "RD Disconnected",
                    color = if (rdStatus) Color(0xFF4CAF50) else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // --- ICONS & CLOCK SECTION ---
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {

            // Sleek Clock
            Text(text = currentTime, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 16.dp))

            // TV Native Icon Buttons (Handles D-Pad automatically)
            val iconBtnColors = IconButtonDefaults.colors(containerColor = Color.Transparent, contentColor = Color.White, focusedContainerColor = PureWhite, focusedContentColor = OledBlack)

            IconButton(onClick = onVoiceSearchClick, colors = iconBtnColors) { Icon(CustomMicIcon, "Voice Search", modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onSearchClick, colors = iconBtnColors) { Icon(Icons.Default.Search, "Search", modifier = Modifier.size(20.dp)) }

            // Notifications
            Box {
                IconButton(onClick = { /* Notifications */ }, colors = iconBtnColors) { Icon(Icons.Default.Notifications, "Notifications", modifier = Modifier.size(20.dp)) }
                if (hasNotifications) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NetflixRed).align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp))
                }
            }

            // Profile
            IconButton(
                onClick = onProfileClick,
                colors = IconButtonDefaults.colors(containerColor = GlassWhite, contentColor = Color.White, focusedContainerColor = PureWhite, focusedContentColor = OledBlack)
            ) {
                Icon(Icons.Default.Person, "Profile", modifier = Modifier.size(20.dp))
            }
        }
    }
}