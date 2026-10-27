@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

// Shared IPTV color constants
internal val ColorOledBlack = Color(0xFF000000)
internal val ColorPs5Gray = Color(0xFF1F1F23)
internal val ColorGlass = Color.White.copy(alpha = 0.08f)
internal val ColorTextMain = Color.White
internal val ColorLiveBlue = Color(0xFF00E5FF)

@Composable
internal fun EmptyStateView(onSetupClick: () -> Unit) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(24.dp)); Text(if (isHeb) "לא נמצאו ערוצים" else "No Channels Found", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "הגדר את הפלייליסט שלך כדי להתחיל לצפות." else "Configure your playlist to start watching.", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(40.dp))
            Surface(onClick = onSetupClick, colors = ClickableSurfaceDefaults.colors(containerColor = ColorGlass, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)) {
                Text(if (isHeb) "הגדר IPTV" else "Setup IPTV", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp))
            }
        }
    }
}

@Composable
fun CrispTextField(value: String, label: String, onValueChange: (String) -> Unit, isLast: Boolean = false) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.Bold, color = Color.White.copy(0.6f)) },
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown) { when (event.nativeKeyEvent.keyCode) { KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }; KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }; else -> false } } else false },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.4f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White, focusedContainerColor = Color.White.copy(alpha = 0.1f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f))
    )
}

@Composable
fun IptvSetupOverlay(ipAddress: String, onClose: () -> Unit, onManualSubmit: (String, String, String) -> Unit) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    val isError = ipAddress.contains("ERROR", ignoreCase = true)
    val qrBitmap = remember(ipAddress) { if (!isError && ipAddress.isNotBlank()) { QrCodeGenerator.generate(ipAddress, 512).asImageBitmap() } else null }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xD9000000)).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)) { onClose(); true } else false }, contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.width(880.dp).height(460.dp).background(ColorPs5Gray, RoundedCornerShape(32.dp)).border(1.dp, ColorGlass, RoundedCornerShape(32.dp)).padding(32.dp)) {
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight().padding(end = 32.dp), verticalArrangement = Arrangement.Center) {
                Text(if (isHeb) "הגדרה ידנית" else "Manual Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "הזן את פרטי ספק ה-IPTV שלך למטה." else "Enter your IPTV provider details below.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp); Spacer(modifier = Modifier.height(24.dp))
                CrispTextField(value = name, label = if (isHeb) "שם פלייליסט (אופציונלי)" else "Playlist Name (Optional)", onValueChange = { name = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = url, label = if (isHeb) "קישור M3U (נדרש)" else "M3U Link (Required)", onValueChange = { url = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = epg, label = if (isHeb) "קישור EPG (אופציונלי)" else "EPG Link (Optional)", onValueChange = { epg = it }, isLast = true); Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { onManualSubmit(name, url, epg) }, colors = ButtonDefaults.colors(containerColor = ColorGlass, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (isHeb) "התחבר עכשיו" else "Connect Now", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ColorGlass))
            Column(modifier = Modifier.weight(0.8f).fillMaxHeight().padding(start = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isHeb) "הגדרה מהירה" else "Quick Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "סרוק את קוד ה-QR עם הטלפון שלך." else "Scan the QR code with your phone.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(12.dp)).border(2.dp, if (isError) Color(0xFFFF453A) else Color(0xFF32D74B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (isError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tv, contentDescription = "Error", tint = Color(0xFFFF453A), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "אין רשת" else "No Network", color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    } else if (qrBitmap != null) { Image(bitmap = qrBitmap, contentDescription = "QR Code", modifier = Modifier.padding(8.dp).fillMaxSize()) }
                    else { Text(if (isHeb) "טוען..." else "Loading...", color = Color.Black.copy(0.4f), fontWeight = FontWeight.Medium, fontSize = 14.sp) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (!isError) { Text(if (isHeb) "או פתח את הכתובת הזו בטלפון:" else "Or open this address on your phone:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp); Spacer(modifier = Modifier.height(4.dp)); Text(text = ipAddress, color = Color(0xFF32D74B), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                else { Text(if (isHeb) "חבר את הטלוויזיה ל-Wi-Fi" else "Please connect TV to Wi-Fi", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) }
            }
        }
    }
}
