package com.luminastreams.tv.presentation.details

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.tr

private data class ReleaseVisuals(
    val accent: Color,
    val label: String,
    val headline: String,
    val sub: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val pulse: Boolean,
)

/**
 * "Broadcast strip" — a distinctive readout on every series' detail page telling you
 * whether the next episode is out, when it drops, or that the show has wrapped.
 * Purely informational; not focusable.
 */
@Composable
fun NextEpisodeStatusCard(
    media: MediaDetailsInfo,
    modifier: Modifier = Modifier,
) {
    val state = media.releaseState
    if (state == SeriesReleaseState.UNKNOWN) return

    val hebrew = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val next = media.nextEpisode
    val days = next?.daysUntilAir

    val v = remember(state, days, media.totalSeasons, hebrew, next) {
        when (state) {
            SeriesReleaseState.PREMIERES_SOON -> ReleaseVisuals(
                accent = Color(0xFFE2B258),
                label = if (hebrew) "פרק חדש" else "NEXT EPISODE",
                headline = countdownHeadline(days, hebrew),
                sub = buildString {
                    append(next?.label ?: "")
                    airDateLine(next?.airDate, hebrew).takeIf { it.isNotBlank() }?.let { append("   ·   $it") }
                },
                icon = Icons.Filled.Schedule,
                pulse = true,
            )
            SeriesReleaseState.AVAILABLE_NOW -> ReleaseVisuals(
                accent = Color(0xFF32D74B),
                label = if (hebrew) "פרק חדש זמין" else "NEW EPISODE OUT",
                headline = if (hebrew) "עכשיו" else "AVAILABLE NOW",
                sub = next?.let { "${it.label}   ·   ${it.name}" } ?: "",
                icon = Icons.Filled.NotificationsActive,
                pulse = true,
            )
            SeriesReleaseState.RETURNING_NO_DATE -> ReleaseVisuals(
                accent = Color(0xFF3B82F6),
                label = if (hebrew) "מתחדש" else "RENEWED",
                headline = if (hebrew) "טרם נקבע תאריך" else "DATE TBA",
                sub = if (hebrew) "העונה הבאה בהפקה" else "Next season in production",
                icon = Icons.Filled.Verified,
                pulse = false,
            )
            SeriesReleaseState.ENDED -> ReleaseVisuals(
                accent = Color(0xFF8A8A93),
                label = if (hebrew) "הסתיים" else "SERIES COMPLETE",
                headline = if (hebrew) "כל הפרקים שודרו" else "ALL EPISODES AIRED",
                sub = if (media.totalSeasons > 0) {
                    if (hebrew) "${media.totalSeasons} עונות" else "${media.totalSeasons} seasons"
                } else "",
                icon = Icons.Filled.CheckCircle,
                pulse = false,
            )
            SeriesReleaseState.CANCELED -> ReleaseVisuals(
                accent = Color(0xFFE5484D),
                label = if (hebrew) "בוטלה" else "CANCELED",
                headline = if (media.totalSeasons > 0) {
                    if (hebrew) "הסתיים אחרי ${media.totalSeasons} עונות" else "ENDED AFTER ${media.totalSeasons} SEASONS"
                } else if (hebrew) "לא תחודש" else "WILL NOT RETURN",
                sub = "",
                icon = Icons.Filled.CheckCircle,
                pulse = false,
            )
            SeriesReleaseState.UNKNOWN -> // unreachable — guarded above
                ReleaseVisuals(Color.Transparent, "", "", "", Icons.Filled.Schedule, false)
        }
    }

    val animate = v.pulse && !DeviceProfile.forceReduceMotion && DeviceProfile.tier != DeviceProfile.Tier.LOW
    val pulseAlpha = if (animate) {
        val t = rememberInfiniteTransition(label = "nextEpPulse")
        t.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse"
        ).value
    } else 1f

    Row(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(v.accent.copy(alpha = 0.16f), Color(0xFF0C0C12).copy(alpha = 0.92f), Color(0xFF0C0C12).copy(alpha = 0.92f))
                )
            )
            .border(1.dp, v.accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // State-color spine
        Box(Modifier.width(5.dp).fillMaxHeight().background(v.accent))

        // Pulsing status dot
        Box(
            Modifier.padding(start = 20.dp, end = 16.dp).size(12.dp).clip(RoundedCornerShape(50))
                .background(v.accent).alpha(pulseAlpha)
        )

        Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(v.icon, null, tint = v.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = v.label,
                    color = v.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = v.headline,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (v.sub.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = v.sub,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Upcoming episode still (premieres only), with a dim "soon" veil
        val still = next?.stillUrl
        if (state == SeriesReleaseState.PREMIERES_SOON && !still.isNullOrBlank()) {
            Box(
                Modifier.padding(end = 12.dp).width(112.dp).height(64.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.Black)
            ) {
                AsyncImage(
                    model = still, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().alpha(0.55f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().background(v.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("SOON", "בקרוב"),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
