package fr.azks.clavier

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class Tool(val label: String, val icon: ImageVector) {
    TOOLS("Outils", Icons.Rounded.Apps), EMOJI("Émojis", Icons.Rounded.Mood), GIF("GIF Tenor", Icons.Rounded.GifBox),
    CLIPBOARD("Presse-papier", Icons.Rounded.ContentPaste), TRANSLATE("Traduire", Icons.Rounded.Translate),
    CORRECT("Corriger", Icons.Rounded.Spellcheck), VOICE("Dicter", Icons.Rounded.Mic),
}

@Composable
fun ClavierTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val dark = config.mode == ColorMode.DARK || config.mode == ColorMode.SYSTEM && isSystemInDarkTheme()
    val resolved = config.resolved(isSystemInDarkTheme())
    val scheme = if (dark) darkColorScheme(primary = Color(resolved.accent)) else lightColorScheme(primary = Color(resolved.accent))
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun KeyboardBackground(config: ThemeConfig, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = config.resolved(isSystemInDarkTheme())
    val background = Color(t.background)
    val gradient = remember(t) { Brush.linearGradient(listOf(lerp(background, Color(0xFFBAC8ED), .25f), background, lerp(background, Color(0xFF7ECDB8), .4f))) }
    Column(modifier.then(if (t.style == KeyStyle.GLASS) Modifier.background(gradient) else Modifier.background(background)), content = content)
}

@Composable
fun TypingKeyboard(
    prefs: Preferences,
    onKey: (String) -> Unit,
    onTool: (Tool) -> Unit,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    privateMode: Boolean = false,
    numeric: Boolean = false,
    action: String = "↵",
) {
    val t = prefs.theme.resolved(isSystemInDarkTheme())
    val foreground = readableInk(t.background, t.ink)
    var shift by remember { mutableIntStateOf(0) }
    var symbols by remember { mutableIntStateOf(0) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyHeight = ((if (landscape) 39 else 48) * prefs.height).dp
    fun type(s: String) { onKey(if (shift > 0 && symbols == 0) s.uppercase() else s); if (shift == 1) shift = 0 }
    Column(Modifier.fillMaxWidth().widthIn(max = 800.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!privateMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tool.entries.forEach { tool -> IconButton(onClick = { onTool(tool) }, modifier = Modifier.size(if (landscape) 40.dp else 46.dp)) { Icon(tool.icon, tool.label, tint = foreground) } }
        } else Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = foreground); Spacer(Modifier.width(8.dp)); Text("Saisie privée", color = foreground, fontSize = 12.sp)
        }
        if (!privateMode && !numeric && !landscape) Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (suggestions.isEmpty()) Text(if (prefs.language == "fr") "Français · AZERTY" else "English · QWERTY", color = foreground, fontSize = 13.sp)
            else suggestions.take(3).forEach { word -> TextButton(onClick = { onSuggestion(word) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text(word, color = foreground, fontSize = 15.sp) } }
        }
        val rows = when {
            numeric -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", ","))
            symbols == 1 -> listOf("1234567890".map { "$it" }, listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/"))
            symbols == 2 -> listOf(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"), listOf("£", "$", "¥", "^", "°", "=", "%", "[", "]", "\\"))
            prefs.language == "en" -> listOf("qwertyuiop".map { "$it" }, "asdfghjkl".map { "$it" })
            else -> listOf("azertyuiop".map { "$it" }, "qsdfghjklm".map { "$it" })
        }
        if (prefs.numbers && symbols == 0 && !numeric) KeyRow("1234567890".map { "$it" }, keyHeight, t, prefs.vibrate, ::type)
        rows.forEach { KeyRow(it, keyHeight, t, prefs.vibrate, ::type, shift > 0 && symbols == 0) }
        if (!numeric) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyCap(if (symbols > 0) "=\\<" else if (shift == 2) "⇪" else "⇧", Modifier.weight(1.25f).height(keyHeight), t, prefs.vibrate,
                onClick = { if (symbols > 0) symbols = if (symbols == 1) 2 else 1 else shift = if (shift == 0) 1 else 0 }, onLong = { shift = 2 }, label = "Majuscule", icon = if (symbols == 0) Icons.Rounded.KeyboardArrowUp else null)
            val third = if (symbols > 0) listOf("*", "\"", "'", ":", ";", "!", "?") else (if (prefs.language == "fr") "wxcvbn’" else "zxcvbnm").map { "$it" }
            third.forEach { key -> KeyCap(if (shift > 0 && symbols == 0) key.uppercase() else key, Modifier.weight(1f).height(keyHeight), t, prefs.vibrate, onClick = { type(key) },
                variants = accents[key].orEmpty().let { if (shift > 0 && symbols == 0) it.uppercase() else it }, onVariant = ::type) }
            KeyCap("⌫", Modifier.weight(1.25f).height(keyHeight), t, prefs.vibrate, onClick = { onKey("DELETE") }, repeating = true, label = "Effacer", icon = Icons.AutoMirrored.Rounded.Backspace)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!numeric) KeyCap(if (symbols == 0) "?123" else "ABC", Modifier.weight(1.25f).height(keyHeight), t, prefs.vibrate, onClick = { symbols = if (symbols == 0) 1 else 0 })
            if (!numeric) KeyCap(",", Modifier.weight(.8f).height(keyHeight), t, prefs.vibrate, onClick = { type(",") })
            KeyCap("🌐", Modifier.weight(1f).height(keyHeight), t, prefs.vibrate, onClick = { onKey("LANGUAGE") }, onLong = { onKey("PICK_IME") }, label = "Changer de langue", icon = Icons.Rounded.Language)
            if (!numeric) KeyCap(if (prefs.language == "fr") "Français" else "English", Modifier.weight(4f).height(keyHeight), t, prefs.vibrate, onClick = { type(" ") }, label = "Espace")
            if (!numeric) KeyCap(".", Modifier.weight(.8f).height(keyHeight), t, prefs.vibrate, onClick = { type(".") })
            if (numeric) KeyCap("⌫", Modifier.weight(1f).height(keyHeight), t, prefs.vibrate, onClick = { onKey("DELETE") }, repeating = true, label = "Effacer", icon = Icons.AutoMirrored.Rounded.Backspace)
            KeyCap(action, Modifier.weight(1.4f).height(keyHeight), t, prefs.vibrate, onClick = { onKey("ENTER") }, accent = true, label = if (action == "↵") "Retour à la ligne" else action, icon = if (action == "↵") Icons.AutoMirrored.Rounded.KeyboardReturn else null)
        }
    }
}

@Composable
private fun KeyRow(keys: List<String>, height: androidx.compose.ui.unit.Dp, theme: ThemeConfig, vibrate: Boolean, onKey: (String) -> Unit, upper: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key -> KeyCap(if (upper) key.uppercase() else key, Modifier.weight(1f).height(height), theme, vibrate, onClick = { onKey(key) }, variants = accents[key].orEmpty().let { if (upper) it.uppercase() else it }, onVariant = { onKey(it) }) }
    }
}
private val accents = mapOf("a" to "àâäáãåæ", "e" to "éèêë", "i" to "îïíì", "o" to "ôöóòœø", "u" to "ùûüú", "c" to "ç", "n" to "ñ", "y" to "ÿ", "0" to "+", "1" to "*#", "." to "-")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCap(text: String, modifier: Modifier, theme: ThemeConfig, vibrate: Boolean, onClick: () -> Unit, onLong: (() -> Unit)? = null, accent: Boolean = false, label: String = text, icon: ImageVector? = null, repeating: Boolean = false, variants: String = "", onVariant: (String) -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click by rememberUpdatedState(onClick)
    var repeated by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed && repeating) {
            repeated = false
            delay(400)
            while (true) { repeated = true; click(); delay(65) }
        }
    }
    val background = when {
        accent -> Color(theme.accent)
        pressed -> lerp(Color(theme.key), Color(theme.accent), .25f)
        theme.style == KeyStyle.GLASS -> Color(theme.key).copy(alpha = theme.opacity)
        else -> Color(theme.key)
    }
    val composed = background.compositeOver(Color(theme.background)).toArgb().toLong() and 0xFFFFFFFF
    val ink = readableInk(composed, if (accent) 0xFFFFFFFF else theme.ink)
    val shape = RoundedCornerShape(if (accent || icon != null) 24.dp else 10.dp)
    var surface = modifier
    if (theme.style == KeyStyle.RELIEF && !pressed && !accent) {
        surface = surface.dropShadow(shape, Shadow(4.dp, Color.Black.copy(alpha = .16f), offset = DpOffset(2.dp, 3.dp)))
            .dropShadow(shape, Shadow(3.dp, Color.White.copy(alpha = .75f), offset = DpOffset((-2).dp, (-2).dp)))
    }
    if (theme.style == KeyStyle.GLASS && !accent) surface = surface.border(1.dp, Color.White.copy(alpha = .5f), shape)
    Box(surface.background(background, shape).clip(shape).semantics { contentDescription = label }
        .combinedClickable(interactionSource = interaction, indication = null, onClick = { if (!repeated) { if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap); click() }; repeated = false },
            onLongClick = if (variants.isNotEmpty() || onLong != null) ({ if (variants.isNotEmpty()) expanded = true else onLong?.invoke() }) else null), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = ink)
        else Text(text, color = ink, fontSize = if (text.length == 1) 22.sp else if (text.length > 5) 12.sp else 13.sp, maxLines = 1)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) { variants.forEach { c -> TextButton(onClick = { onVariant(c.toString()); expanded = false }, modifier = Modifier.sizeIn(minWidth = 40.dp)) { Text(c.toString(), fontSize = 22.sp) } } }
        }
    }
}
