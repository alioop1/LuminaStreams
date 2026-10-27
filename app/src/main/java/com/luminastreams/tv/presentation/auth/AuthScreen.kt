package com.luminastreams.tv.presentation.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    state: AuthState,
    onEvent: (AuthEvent) -> Unit,
    onSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val emailFR = remember { FocusRequester() }
    val passFR = remember { FocusRequester() }
    val confirmFR = remember { FocusRequester() }
    val submitFR = remember { FocusRequester() }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            delay(1000)
            onSuccess()
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        emailFR.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(800.dp)
                .alpha(0.15f)
                .blur(100.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF00D4FF), Color.Transparent)))
        )

        if (state.loggedInEmail != null) {
            LoggedInView(email = state.loggedInEmail, onLogout = { onEvent(AuthEvent.Logout) })
        } else {
            Row(
                modifier = Modifier
                    .width(920.dp)
                    .height(600.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: QR CODE + DEVICE LINKING
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.isLinking && state.deviceLinkCode != null) {
                        // DEVICE LINKING MODE — show the code
                        Text(
                            tr("Link Device", "חיבור מכשיר"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            tr("Enter this code on the website", "הקלד קוד זה באתר"),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )

                        // The big code display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0A1628).copy(0.9f))
                                .border(2.dp, Color(0xFF00D4FF).copy(0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Spaced code: "6 5 6 P U V"
                            val spaced = state.deviceLinkCode.toList().joinToString(" ")
                            Text(
                                text = spaced,
                                color = Color(0xFF00D4FF),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Status indicator
                        val dots = remember { mutableStateOf(1) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(600)
                                dots.value = (dots.value % 3) + 1
                            }
                        }
                        Text(
                            text = state.linkStatus ?: (tr("Waiting for link", "ממתין לחיבור") + ".".repeat(dots.value)),
                            color = Color(0xFF00D4FF).copy(0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(16.dp))

                        // QR code linking to the web app
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://luminastreams-1a474.web.app",
                                contentDescription = "QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            "luminastreams-1a474.web.app",
                            fontSize = 11.sp,
                            color = Color(0xFF00D4FF).copy(0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        // DEFAULT MODE — scan QR to go to web, then start linking
                        Text(
                            tr("Link Device", "חבר מכשיר"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            tr("Scan to login from your phone", "סרוק כדי להתחבר מהנייד"),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            AsyncImage(
                                model = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=https://luminastreams-1a474.web.app",
                                contentDescription = "QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            "luminastreams-1a474.web.app",
                            fontSize = 14.sp,
                            color = Color(0xFF00D4FF),
                            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                        )

                        // START DEVICE LINK BUTTON
                        val linkFR = remember { FocusRequester() }
                        Surface(
                            onClick = { onEvent(AuthEvent.StartDeviceLink) },
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF00D4FF).copy(0.15f),
                                contentColor = Color(0xFF00D4FF),
                                focusedContainerColor = Color(0xFF00D4FF),
                                focusedContentColor = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth(0.8f).height(48.dp).focusRequester(linkFR)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    tr("Generate Link Code", "צור קוד חיבור"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color.White.copy(alpha = 0.1f)))

                // RIGHT SIDE: FORM
                Column(
                    modifier = Modifier.weight(1.3f).fillMaxHeight().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.mode == AuthMode.LOGIN) tr("Manual Login", "התחברות ידנית") else tr("Create Account", "יצירת חשבון"),
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Spacer(Modifier.height(32.dp))

                    AuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = tr("Email", "אימייל"),
                        focusRequester = emailFR,
                        imeAction = ImeAction.Next,
                        onImeAction = { passFR.requestFocus() }
                    )

                    Spacer(Modifier.height(16.dp))

                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = tr("Password", "סיסמה"),
                        isPassword = true,
                        focusRequester = passFR,
                        imeAction = if (state.mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                        onImeAction = { if (state.mode == AuthMode.REGISTER) confirmFR.requestFocus() else submitFR.requestFocus() }
                    )

                    if (state.mode == AuthMode.REGISTER) {
                        Spacer(Modifier.height(16.dp))
                        AuthTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = tr("Confirm Password", "אימות סיסמה"),
                            isPassword = true,
                            focusRequester = confirmFR,
                            imeAction = ImeAction.Done,
                            onImeAction = { submitFR.requestFocus() }
                        )
                    }

                    if (state.error != null) {
                        Text(state.error, color = Color(0xFFFF3B5C), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    }

                    Spacer(Modifier.height(24.dp))

                    Surface(
                        onClick = {
                            if (state.mode == AuthMode.LOGIN) onEvent(AuthEvent.Login(email, password))
                            else onEvent(AuthEvent.Register(email, password, confirmPassword))
                        },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF00D4FF), contentColor = Color.Black, focusedContainerColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(54.dp).focusRequester(submitFR)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (state.isLoading) tr("Please Wait...", "אנא המתן...") else if (state.mode == AuthMode.LOGIN) tr("Login", "התחבר") else tr("Register", "הרשם"),
                                fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = { onEvent(AuthEvent.ToggleMode) },
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Color.White.copy(0.5f), focusedContainerColor = Color.White.copy(0.1f)),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.mode == AuthMode.LOGIN) tr("Switch to Register", "עבור להרשמה") else tr("Switch to Login", "עבור להתחברות"),
                            fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    focusRequester: FocusRequester,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = { focusRequester.requestFocus() },
        interactionSource = interactionSource,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.05f), focusedContainerColor = Color.White.copy(0.1f), contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(54.dp)
            .border(1.dp, if (isFocused) Color(0xFF00D4FF) else Color.Transparent, RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(label, color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text, imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00D4FF)),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun LoggedInView(email: String, onLogout: () -> Unit) {
    val logoutFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        logoutFR.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(600.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF00D4FF).copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                .border(2.dp, Color(0xFF00D4FF), RoundedCornerShape(50.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = email.firstOrNull()?.uppercase() ?: "U",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D4FF)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = tr("Welcome Back", "ברוך שובך"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = email,
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(Modifier.height(48.dp))
        
        Surface(
            onClick = onLogout,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFFFF3B5C).copy(alpha = 0.2f),
                contentColor = Color(0xFFFF3B5C),
                focusedContainerColor = Color(0xFFFF3B5C),
                focusedContentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(54.dp)
                .focusRequester(logoutFR)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = tr("Logout", "התנתק"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
