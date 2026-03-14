package com.luminastreams.tv.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.luminastreams.tv.domain.model.StreamSource
import com.luminastreams.tv.ui.theme.NetflixRed
import com.luminastreams.tv.ui.theme.OledBlack
import com.luminastreams.tv.ui.theme.TextPrimary

@Composable
fun StreamItem(
    stream: StreamSource,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isFocused) NetflixRed else Color(0xFF1A1A1A)

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bgColor).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = stream.filename, color = TextPrimary, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityBadgeLocal(text = stream.resolution)
                if (stream.isHDR10) QualityBadgeLocal(text = "HDR10+") // תוקן מ-isHDR ל-isHDR10
                if (stream.isDV) QualityBadgeLocal(text = "DV")
                if (stream.codec == "AV1") QualityBadgeLocal(text = "AV1", color = Color(0xFF007ACC))
            }
        }
        val cacheColor = if (stream.isCached) Color(0xFF4CAF50) else Color(0xFFFF9800)
        Text(text = if (stream.isCached) "RD+" else "RD Download", color = cacheColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QualityBadgeLocal(text: String, color: Color = OledBlack) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text = text, color = TextPrimary, style = androidx.tv.material3.MaterialTheme.typography.labelSmall)
    }
}