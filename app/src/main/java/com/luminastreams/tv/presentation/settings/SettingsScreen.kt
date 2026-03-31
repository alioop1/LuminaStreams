@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG_DARK       = Color(0xFF040405)
private val PANEL_BG      = Color(0xEB0A0A0C)
private val CARD_IDLE     = Color(0x0CFFFFFF)
private val CARD_FOCUSED  = Color(0xFF282832)
private val TEXT_PRIMARY  = Color(0xFFFFFFFF)
private val TEXT_MUTED    = Color(0xFF8A8A93)
private val ACCENT_RED    = Color(0xFFE50914)
private val ACCENT_GOLD   = Color(0xFFE5C07B)
private val ACCENT_BLUE   = Color(0xFF4D90FE)
private val ACCENT_GREEN  = Color(0xFF43A047)

private data class CatMeta(val cat: SettingsCategory, val icon: ImageVector, val titleEn: String, val titleHe: String, val descEn: String, val descHe: String)

private val CATS = listOf(
    CatMeta(SettingsCategory.ACCOUNT,  Icons.Default.VpnKey,       "Real-Debrid & Network", "חשבון ורשת", "Account setup and connectivity", "חיבור חשבונות ורשת"),
    CatMeta(SettingsCategory.PLAYBACK, Icons.Default.HighQuality,  "HDR, Audio & Player", "וידאו וסאונד", "Playback and audio options", "אפשרויות ניגון ושמע"),
    CatMeta(SettingsCategory.PRIVACY,  Icons.Default.Style,        "Subtitles & Interface", "כתוביות וממשק", "Subtitles and search history", "כתוביות והיסטוריית חיפוש"),
    CatMeta(SettingsCategory.SYSTEM,   Icons.Default.Memory,       "Performance & Display", "ביצועים ותצוגה", "App behavior and storage", "אחסון, מטמון ואודות")
)

// Helper for Translation
@Composable
fun tr(en: String, he: String): String {
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en
}

// ══════════════════════════════════════════════════════════════════
//  ROOT SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    isRtl: Boolean, // נשאר עבור תאימות לחתימת הפונקציה בלבד
    onNavigateBack: () -> Unit,
) {
    val isRtlLocal = LocalLayoutDirection.current == LayoutDirection.Rtl
    var isRailFocused by remember { mutableStateOf(false) }

    val railFR = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    val activeCategoryFR = remember { FocusRequester() } // פוקוס ייעודי לקטגוריה הפעילה בתפריט

    // משיכת פוקוס לתפריט (הקטגוריה שנבחרה) מיד עם פתיחת המסך
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { activeCategoryFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BG_DARK)) {
        val glowColor by animateColorAsState(
            targetValue = when (state.selectedCategory) {
                SettingsCategory.LANGUAGE -> ACCENT_GREEN.copy(alpha = 0.05f)
                SettingsCategory.ACCOUNT  -> ACCENT_GOLD.copy(alpha = 0.05f)
                SettingsCategory.PLAYBACK -> ACCENT_BLUE.copy(alpha = 0.05f)
                SettingsCategory.SYSTEM   -> ACCENT_RED.copy(alpha = 0.05f)
                else                      -> Color.White.copy(alpha = 0.03f)
            },
            animationSpec = tween(800),
            label = "bgGlow"
        )
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(glowColor, Color.Transparent), radius = 1500f)
        ))

        Row(Modifier.fillMaxSize()) {
            val railWidth by animateDpAsState(
                targetValue   = if (isRailFocused) 280.dp else 88.dp,
                animationSpec = tween(300, easing = LinearOutSlowInEasing),
                label         = "railWidth"
            )
            val railShape = if (isRtlLocal)
                RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            else
                RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

            // ── Rail (Menu) ─────────────────────────────────────────
            Box(
                Modifier
                    .width(railWidth).fillMaxHeight()
                    .clip(railShape).background(PANEL_BG)
                    .onFocusChanged { isRailFocused = it.hasFocus }
                    .focusProperties {
                        // היפוך כיווני הניווט לפי שפה!
                        if (isRtlLocal) {
                            left = contentFR
                            right = FocusRequester.Cancel
                        } else {
                            right = contentFR
                            left = FocusRequester.Cancel
                        }
                    }
            ) {
                Column(
                    Modifier.fillMaxSize().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    LuminaLogo(isExpanded = isRailFocused)
                    Spacer(Modifier.height(48.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick  = onNavigateBack,
                                modifier = Modifier.size(48.dp).focusRequester(railFR),
                                colors   = IconButtonDefaults.colors(
                                    containerColor        = Color.Transparent,
                                    focusedContainerColor = TEXT_PRIMARY,
                                    contentColor          = TEXT_MUTED,
                                    focusedContentColor   = BG_DARK
                                )
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(24.dp)) }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    SmartRailItem(
                        title      = tr("Languages", "שפות"),
                        icon       = Icons.Default.Language,
                        isSelected = state.selectedCategory == SettingsCategory.LANGUAGE,
                        isExpanded = isRailFocused,
                        highlightColor = ACCENT_GREEN,
                        focusRequester = activeCategoryFR, // הצמדת הפוקוס ההתחלתי
                        onClick    = { viewModel.setCategory(SettingsCategory.LANGUAGE) }
                    )

                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 24.dp).background(Color.White.copy(alpha = 0.1f)))
                    Spacer(Modifier.height(16.dp))

                    CATS.forEach { meta ->
                        SmartRailItem(
                            title      = tr(meta.titleEn, meta.titleHe),
                            icon       = meta.icon,
                            isSelected = state.selectedCategory == meta.cat,
                            isExpanded = isRailFocused,
                            focusRequester = activeCategoryFR, // הצמדת הפוקוס ההתחלתי
                            onClick    = { viewModel.setCategory(meta.cat) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ── Content Area ────────────────────────────────────────
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .focusRequester(contentFR)
                    .focusProperties {
                        // כשיוצאים מהתוכן חזרה לתפריט - נחזור לפריט האחרון שסומן בתפריט
                        if (isRtlLocal) {
                            right = activeCategoryFR
                        } else {
                            left = activeCategoryFR
                        }
                    }
            ) {
                AnimatedContent(
                    targetState   = state.selectedCategory,
                    transitionSpec = {
                        fadeIn(tween(400, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutLinearInEasing))
                    },
                    label    = "content",
                    modifier = Modifier.fillMaxSize()
                ) { cat ->
                    key(cat.name) {
                        DashboardContent(cat, state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartRailItem(
    title: String, icon: ImageVector, isSelected: Boolean, isExpanded: Boolean,
    highlightColor: Color? = null, focusRequester: FocusRequester, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseBg = if (highlightColor != null && isSelected) highlightColor.copy(alpha = 0.2f) else if (isSelected) CARD_IDLE else Color.Transparent
    val bg by animateColorAsState(
        targetValue   = if (focused) highlightColor ?: TEXT_PRIMARY else baseBg,
        animationSpec = tween(150), label = "railBg"
    )
    val tint by animateColorAsState(
        targetValue   = if (focused) BG_DARK else if (isSelected) highlightColor ?: TEXT_PRIMARY else TEXT_MUTED,
        animationSpec = tween(150), label = "railTint"
    )
    val textAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(200, easing = LinearEasing),
        label = "textAlpha"
    )
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier
            .fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (isSelected) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Box(Modifier.fillMaxSize().background(bg, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp).fillMaxHeight(), Alignment.Center) {
                    Icon(icon, null, Modifier.size(24.dp), tint = tint)
                }
                Text(
                    text       = title,
                    color      = tint.copy(alpha = textAlpha),
                    fontSize   = 16.sp,
                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines   = 1,
                    softWrap   = false,
                    modifier   = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LuminaLogo(isExpanded: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Image(
            painter = painterResource(id = com.luminastreams.tv.R.drawable.logo_lumina_unified),
            contentDescription = "Lumina Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 26.dp)
                .height(48.dp)
                .requiredWidth(220.dp)
        )
    }
}

@Composable
private fun DashboardContent(
    cat: SettingsCategory,
    state: SettingsState, viewModel: SettingsViewModel
) {
    val title = when (cat) {
        SettingsCategory.LANGUAGE -> tr("Languages", "שפות")
        else -> {
            val meta = CATS.first { it.cat == cat }
            tr(meta.titleEn, meta.titleHe)
        }
    }
    val desc = when (cat) {
        SettingsCategory.LANGUAGE -> tr("Change the app's display language", "שנה את שפת התצוגה של האפליקציה")
        else -> {
            val meta = CATS.first { it.cat == cat }
            tr(meta.descEn, meta.descHe)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 56.dp, end = 80.dp, top = 48.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().focusProperties {
            up = FocusRequester.Cancel; down = FocusRequester.Cancel
        }
    ) {
        item {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(desc, color = TEXT_MUTED, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        when (cat) {
            SettingsCategory.LANGUAGE -> buildLanguageDashboard(state, viewModel)
            SettingsCategory.ACCOUNT  -> buildAccountDashboard(state, viewModel)
            SettingsCategory.PLAYBACK -> buildPlaybackDashboard(state, viewModel)
            SettingsCategory.PRIVACY  -> buildPersonalizationDashboard(state, viewModel)
            SettingsCategory.SYSTEM   -> buildSystemDashboard(state, viewModel)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  0. LANGUAGES
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildLanguageDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle(tr("APP LANGUAGE", "שפת האפליקציה")) }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardRadioCard(
                label = "English",
                sub = "LTR",
                isSelected = state.appLanguage == "en",
                modifier = Modifier.weight(1f)
            ) { viewModel.updateStringSetting("app_lang", "en") }

            DashboardRadioCard(
                label = "עברית",
                sub = "RTL",
                isSelected = state.appLanguage == "he",
                modifier = Modifier.weight(1f)
            ) { viewModel.updateStringSetting("app_lang", "he") }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  1. ACCOUNT & NETWORK
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildAccountDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item {
        if (state.rdToken.isNotEmpty()) {
            RdConnectedPremiumCard(state, viewModel)
        } else {
            when (val a = state.authStatus) {
                is SettingsAuthStatus.WaitingForUser -> RdAuthCard(a)
                is SettingsAuthStatus.Loading        -> RdLoadingCard()
                is SettingsAuthStatus.Error -> {
                    RdErrorCard(a.message)
                    Spacer(Modifier.height(16.dp))
                    RdConnectCard(viewModel)
                }
                else -> RdConnectCard(viewModel)
            }
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
    item { SectionTitle(tr("NETWORK DIAGNOSTICS", "אבחון רשת")) }
    item {
        DashboardActionCard(
            title     = tr("RD Server Speed Test", "בדיקת מהירות לשרתי RD"),
            desc      = if (state.rdSpeedTesting) tr("Pinging Real-Debrid API...", "בודק חיבור לשרתי Real-Debrid...")
            else state.rdSpeedTestResult ?: tr("Measure latency to Real-Debrid CDN", "מדוד את זמן התגובה לשרתי ההורדה."),
            icon      = if (state.rdSpeedTesting) Icons.Default.HourglassEmpty else Icons.Default.NetworkCheck,
            value     = if (state.rdSpeedTesting) tr("Testing...", "בודק...") else tr("Run Test", "התחל בדיקה"),
            highlight = if (state.rdSpeedTestResult?.contains("🟢") == true) ACCENT_GREEN else null
        ) { if (!state.rdSpeedTesting) viewModel.runSpeedTest() }
    }
}

@Composable
private fun RdConnectedPremiumCard(state: SettingsState, viewModel: SettingsViewModel) {
    var focused by remember { mutableStateOf(false) }
    val actionColor by animateColorAsState(if (focused) ACCENT_RED else TEXT_MUTED, label = "rdActionColor")
    Surface(
        onClick  = { viewModel.logoutRealDebrid() },
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color.Transparent, ACCENT_GOLD.copy(0.05f))))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = ACCENT_GOLD, modifier = Modifier.size(20.dp))
                    Text(tr("PREMIUM ACTIVE", "מנוי פרימיום פעיל"), color = ACCENT_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("Real-Debrid Account Linked", "חשבון Real-Debrid מקושר"), color = TEXT_PRIMARY, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(tr("Token:", "טוקן:") + " ${state.rdToken.take(5)}••••••••${state.rdToken.takeLast(4)}", color = TEXT_MUTED, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LinkOff, null, tint = actionColor, modifier = Modifier.size(18.dp))
                    Text(tr("Press OK to Disconnect Device", "לחץ OK כדי לנתק חשבון"), color = actionColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RdConnectCard(viewModel: SettingsViewModel) {
    DashboardActionCard(
        title     = tr("Unlock Premium Streaming", "פתיחת סטרימינג פרימיום"),
        desc      = tr("Connect Real-Debrid for 4K zero-buffering playback from cached torrents.", "התחבר ל-Real-Debrid לצפייה ב-4K מטורנטים ללא טעינות."),
        icon      = Icons.Default.VpnKey,
        value     = tr("Link Account", "קשר חשבון"),
        highlight = ACCENT_GOLD
    ) { viewModel.startRealDebridAuth() }
}

@Composable
private fun RdAuthCard(auth: SettingsAuthStatus.WaitingForUser) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CARD_IDLE).padding(28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Device Authorization Required", "נדרש אישור מכשיר"), color = TEXT_PRIMARY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text(tr("1. Visit this URL on your phone or PC:", "1. היכנס לכתובת הבאה בנייד או במחשב:"), color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.url, color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(1.dp).background(Color(0x1AFFFFFF)))
            Spacer(Modifier.height(24.dp))
            Text(tr("2. Enter this code:", "2. הזן את הקוד:"), color = TEXT_MUTED, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(auth.userCode, color = ACCENT_GOLD, fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp)
        }
    }
}

@Composable
private fun RdLoadingCard() {
    Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(CARD_IDLE), Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = ACCENT_GOLD, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun RdErrorCard(msg: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E0C0C)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = ACCENT_RED)
            Text(msg, color = TEXT_PRIMARY, fontSize = 14.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  2. PLAYBACK & HOME THEATER
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildPlaybackDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle(tr("HOME THEATER AUDIO", "שמע וקולנוע ביתי")) }
    item {
        DashboardToggleCard(
            title     = tr("Audio Passthrough (Bitstream)", "העברת שמע (Passthrough)"),
            desc      = tr("Pass Dolby Atmos, TrueHD and DTS-HD MA raw to your AV receiver without software decoding.", "העבר סאונד ישירות לרסיבר ללא פענוח תוכנתי."),
            icon      = Icons.Default.SurroundSound,
            isChecked = state.audioPassthrough
        ) { viewModel.updateToggleSetting("audio_passthrough", !state.audioPassthrough) }
    }
    item {
        Column {
            SectionTitle(tr("PREFERRED AUDIO LANGUAGE", "שפת שמע מועדפת"))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "original" to tr("🎬 Original", "🎬 שפת מקור"),
                    "he" to "🇮🇱 Hebrew",
                    "en" to "🇺🇸 English"
                ).forEach { (v, l) ->
                    DashboardRadioCard(
                        label = l,
                        sub = if (v == "original") tr("Default track", "רצועה מובנית") else tr("Prefer $l track", "העדף רצועה זו"),
                        isSelected = state.preferredAudioLang == v,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.updateStringSetting("preferred_audio_lang", v)
                    }
                }
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("CINEMATIC VIDEO", "וידאו קולנועי")) }
    item {
        DashboardToggleCard(
            title     = tr("Auto Frame Rate (AFR)", "התאמת קצב רענון (AFR)"),
            desc      = tr("Switch TV to 24 / 25 / 30 Hz to match content and eliminate judder.", "התאם את קצב הרענון של הטלוויזיה לתוכן למניעת ריצוד."),
            icon      = Icons.Default.Monitor,
            isChecked = state.autoFrameRate
        ) { viewModel.updateToggleSetting("afr", !state.autoFrameRate) }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("PLAYER BEHAVIOR", "התנהגות נגן")) }
    item {
        DashboardToggleCard(
            title     = tr("Hardware Acceleration", "האצת חומרה"),
            desc      = tr("Use the device's hardware video decoders. Disable only if you see playback glitches.", "השתמש במפענחי חומרה. כבה רק במקרה של תקלות וידאו."),
            icon      = Icons.Default.Memory,
            isChecked = state.hwAcceleration
        ) { viewModel.updateToggleSetting("hw_accel", !state.hwAcceleration) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  3. SUBTITLES & PERSONALIZATION
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildPersonalizationDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle(tr("DEFAULT SUBTITLE LANGUAGE", "שפת כתוביות ברירת מחדל")) }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardRadioCard("עברית", tr("Hebrew", "עברית"), state.defaultSubtitles == "Hebrew", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "Hebrew") }
            DashboardRadioCard("English", tr("English", "אנגלית"), state.defaultSubtitles == "English", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "English") }
            DashboardRadioCard(tr("Off", "כבוי"), tr("No subtitles", "ללא כתוביות"), state.defaultSubtitles == "None", Modifier.weight(1f)) { viewModel.updateStringSetting("def_subs", "None") }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("SUBTITLE APPEARANCE", "מראה כתוביות")) }
    item {
        DashboardToggleCard(
            title     = tr("Yellow Subtitles", "כתוביות צהובות"),
            desc      = tr("Render subtitles in classic cinema yellow with black outline instead of white.", "הצג כתוביות בצבע צהוב קולנועי במקום לבן."),
            icon      = Icons.Default.FormatColorText,
            isChecked = state.yellowSubtitles
        ) { viewModel.updateToggleSetting("yellow_subs", !state.yellowSubtitles) }
    }
    item {
        Column {
            SectionTitle(tr("SUBTITLE SIZE", "גודל כתוביות"))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "small" to tr("Small", "קטן"),
                    "medium" to tr("Medium", "בינוני"),
                    "large" to tr("Large", "גדול"),
                    "xlarge" to tr("XL", "ענק")
                ).forEach { (v, l) ->
                    DashboardRadioCard(l, mapOf("small" to "75%", "medium" to "100%", "large" to "130%", "xlarge" to "160%")[v] ?: "", state.subtitleFontScale == v, Modifier.weight(1f)) {
                        viewModel.updateStringSetting("subtitle_font_scale", v)
                    }
                }
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("SEARCH & HISTORY", "חיפוש והיסטוריה")) }
    item {
        DashboardToggleCard(
            title     = tr("Save Search History", "שמור היסטוריית חיפושים"),
            desc      = tr("Remember recent searches for autocomplete and quick re-search.", "זכור חיפושים קודמים להשלמה אוטומטית."),
            icon      = Icons.Default.History,
            isChecked = state.saveSearchHistory
        ) { viewModel.updateToggleSetting("save_history", !state.saveSearchHistory) }
    }
    item {
        DashboardActionCard(
            title = tr("Clear Search History", "מחק היסטוריית חיפוש"),
            desc  = if (state.searchHistoryStatus == "Clear") tr("Remove all saved search queries from this device.", "מחק את כל החיפושים השמורים במכשיר זה.")
            else state.searchHistoryStatus,
            icon  = Icons.Default.DeleteSweep,
            value = if (state.searchHistoryStatus == "Clear") tr("Clear Now", "מחק עכשיו") else state.searchHistoryStatus
        ) { if (state.searchHistoryStatus == "Clear") viewModel.clearSearchHistory() }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("PERFORMANCE TWEAKS", "שיפורי ביצועים")) }
    item {
        DashboardToggleCard(
            title     = tr("Skip Embedded Subtitle Tracks", "דלג על כתוביות מובנות"),
            desc      = tr("Don't load subtitle tracks from the video stream itself — use only downloaded .srt files.", "השתמש רק בכתוביות חיצוניות שהורדו, כדי למנוע תקיעות."),
            icon      = Icons.Default.ClosedCaptionDisabled,
            isChecked = state.subtitleCacheOnly
        ) { viewModel.updateToggleSetting("subtitle_cache_only", !state.subtitleCacheOnly) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  4. SYSTEM & PERFORMANCE
// ══════════════════════════════════════════════════════════════════
private fun LazyListScope.buildSystemDashboard(
    state: SettingsState, viewModel: SettingsViewModel
) {
    item { SectionTitle(tr("PERFORMANCE", "ביצועים")) }
    item {
        DashboardToggleCard(
            title     = tr("Lite UI Mode", "ממשק משתמש קל (Lite)"),
            desc      = tr("Forces LOW device tier: disables backdrop parallax, row cross-fades and heavy animations.", "מבטל אנימציות רקע ושקיפויות כדי להקל על מכשירים חלשים."),
            icon      = Icons.Default.Speed,
            isChecked = state.liteUiMode
        ) { viewModel.updateToggleSetting("lite_ui", !state.liteUiMode) }
    }
    item {
        DashboardToggleCard(
            title     = tr("Reduce Motion", "הפחתת תנועה"),
            desc      = tr("Skip all transition animations instantly. Improves responsiveness on weak or older streamers.", "ביטול מלא של אנימציות מעבר לשיפור מהירות התגובה."),
            icon      = Icons.Default.FlashOff,
            isChecked = state.reduceMotion
        ) { viewModel.updateToggleSetting("reduce_motion", !state.reduceMotion) }
    }
    item {
        DashboardToggleCard(
            title     = tr("Pre-allocate Video Buffer (64 MB)", "שריון זיכרון וידאו (64MB)"),
            desc      = tr("Reserve memory for ExoPlayer before playback starts. Reduces rebuffering on 4K streams.", "שמור מראש זיכרון לנגן לפני תחילת הצפייה כדי למנוע תקיעות ב-4K."),
            icon      = Icons.Default.Storage,
            isChecked = state.preAllocateBuffer
        ) { viewModel.updateToggleSetting("pre_buffer", !state.preAllocateBuffer) }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("STORAGE", "אחסון")) }
    item {
        DashboardActionCard(
            title = tr("Clear Image Cache", "ניקוי מטמון תמונות"),
            desc  = tr("Free up device storage. Currently using:", "פנה מקום אחסון במכשיר. מנצל כרגע:") + " ${state.cacheSizeStr}",
            icon  = Icons.Default.DeleteSweep,
            value = tr("Clear Now", "נקה עכשיו")
        ) { viewModel.clearCache() }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item { SectionTitle(tr("ABOUT", "אודות")) }
    item {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(CARD_IDLE).padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow(icon = Icons.Default.Info,      label = tr("Version", "גרסה"),           value = state.appVersion)
                AboutRow(icon = Icons.Default.Devices,   label = tr("Device Tier", "מצב מכשיר"),   value = state.deviceTier.ifEmpty { tr("Detecting…", "מזהה...") })
                AboutRow(icon = Icons.Default.Shield,    label = tr("Stream Engine", "מנוע הזרמה"), value = "Torrentio + Real-Debrid")
                AboutRow(icon = Icons.Default.Movie,     label = tr("Metadata", "מידע סרטים"),      value = "TMDB API + Cinemeta")
                AboutRow(icon = Icons.Default.Subtitles, label = tr("Subtitles", "כתוביות"),     value = "Ktuvit + Wizdom + OpenSubtitles")
            }
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TEXT_MUTED, modifier = Modifier.size(18.dp))
        Text(label, color = TEXT_MUTED, fontSize = 14.sp, modifier = Modifier.width(130.dp))
        Text(value, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ══════════════════════════════════════════════════════════════════
//  REUSABLE DASHBOARD COMPONENTS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(title: String) {
    Text(title, color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun DashboardToggleCard(
    title: String, desc: String, icon: ImageVector, isChecked: Boolean, onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val iconBgColor   by animateColorAsState(if (focused) TEXT_PRIMARY else Color(0x1AFFFFFF), label = "iconBg")
    val iconTintColor by animateColorAsState(if (focused) BG_DARK else TEXT_PRIMARY, label = "iconTint")
    Surface(
        onClick  = onToggle,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(iconBgColor, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = iconTintColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc,  color = TEXT_MUTED,   fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))

            val trackColor by animateColorAsState(if (isChecked) ACCENT_BLUE else Color(0x33FFFFFF), label = "track")
            val thumbBias by animateFloatAsState(if (isChecked) 1f else -1f, tween(250), label = "thumbBias")

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(trackColor)
                    .padding(3.dp),
                contentAlignment = androidx.compose.ui.BiasAlignment(horizontalBias = thumbBias, verticalBias = 0f)
            ) {
                Box(Modifier.size(18.dp).background(Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String, desc: String, icon: ImageVector,
    value: String, highlight: Color? = null, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val iconBgColor   by animateColorAsState(if (focused) highlight ?: TEXT_PRIMARY else Color(0x1AFFFFFF), label = "actionIconBg")
    val iconTintColor by animateColorAsState(if (focused) BG_DARK else highlight ?: TEXT_PRIMARY, label = "actionIconTint")
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_IDLE, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = Modifier.fillMaxWidth().height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(iconBgColor, CircleShape), Alignment.Center) {
                Icon(icon, null, tint = iconTintColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(desc,  color = TEXT_MUTED,   fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(16.dp))
                val btnBg   by animateColorAsState(if (focused) highlight ?: TEXT_PRIMARY else Color(0x1AFFFFFF), label = "btnBg")
                val btnText by animateColorAsState(if (focused) BG_DARK else TEXT_PRIMARY, label = "btnText")
                Box(
                    modifier = Modifier.background(btnBg, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(value, color = btnText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardRadioCard(
    label: String, sub: String, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) Color(0x2AFFFFFF) else if (focused) CARD_FOCUSED else CARD_IDLE,
        animationSpec = tween(150),
        label         = "radioBg"
    )
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = bgColor, focusedContainerColor = CARD_FOCUSED),
        border   = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.8f), 20.dp)),
        modifier = modifier.height(76.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color      = TEXT_PRIMARY,
                    fontSize   = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = ACCENT_BLUE)
            }
            Text(sub, color = TEXT_MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}