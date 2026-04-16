package com.luminastreams.tv.presentation.iptv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun QrSetupOverlay(
    ipAddress: String,
    onClose: () -> Unit
) {
    val qrBitmap = remember(ipAddress) {
        QrCodeGenerator.generate(ipAddress, 400).asImageBitmap()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFF1A1A24), RoundedCornerShape(24.dp))
                .padding(48.dp)
        ) {
            Text("הגדרת Lumina IPTV", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("סרוק את הקוד או היכנס לכתובת:\n$ipAddress", color = Color.LightGray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Image(bitmap = qrBitmap, contentDescription = "QR Code", modifier = Modifier.size(250.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onClose) { Text("ביטול") }
        }
    }
}