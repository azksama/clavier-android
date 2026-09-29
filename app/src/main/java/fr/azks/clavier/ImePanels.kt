package fr.azks.clavier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import android.annotation.SuppressLint
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
@SuppressLint("ConfigurationScreenWidthHeight") // IME container measures its own content; use display to bound panels.
fun ImeContent(service: ClavierService, preferences: Preferences) {
    val tool = service.tool
    if (tool == null) {
        AutofillStrip(service.autofillViews)
        service.undo?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Texte inséré", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = service::undoEdit) { Text("Annuler") }
            }
        }
        TypingKeyboard(preferences, service::key, service::openTool, service.suggestions, service::completeWord, service.privateMode, service.numeric, service.action)
        return
    }
    if (service.gifSearchTyping) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { service.openTool(Tool.GIF) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Text(service.gifQuery.ifBlank { "Rechercher un GIF…" }, Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = service::searchGifs) { Icon(Icons.Rounded.Search, "Rechercher sur Tenor") }
        }
        TypingKeyboard(preferences, service::gifKey, { service.openTool(it) }, action = "Chercher")
        return
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .58f).coerceIn(220f, 440f).dp
    Column(Modifier.fillMaxWidth().height(maxHeight)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = service::closeTool) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Revenir au clavier") }
            Icon(tool.icon, null, Modifier.size(21.dp)); Spacer(Modifier.width(10.dp))
            Text(tool.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = service::openSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
        }
        if (service.busy && !service.recording) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (tool) {
            Tool.EMOJI -> EmojiPanel { service.key(it) }
            Tool.CLIPBOARD -> ClipboardPanel(service, preferences)
            Tool.GIF -> GifPanel(service)
            Tool.TOOLS -> Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tool.entries.filter { it != Tool.TOOLS }.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item -> FilledTonalButton(onClick = { service.openTool(item) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), shape = RoundedCornerShape(16.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(item.icon, null); Text(item.label, fontSize = 11.sp) } } }
                } }
                OutlinedButton(onClick = service::openSettings, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Palette, null); Spacer(Modifier.width(8.dp)); Text("Thèmes et couleurs") }
            }
            else -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tool == Tool.TRANSLATE) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (preferences.language == "fr") "Français" else "English", Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                        LanguagePicker(preferences.translationTarget, !service.busy, service::setTarget, Modifier.weight(1f))
                    }
                }
                if (tool == Tool.VOICE && service.recording) {
                    Text("Je vous écoute…", style = MaterialTheme.typography.titleLarge)
                    Text("${service.seconds} s / 60 s · sur le téléphone", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (service.level * 8).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary)
                    Button(onClick = service::stopVoice, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("Terminer la dictée") }
                    TextButton(onClick = service::cancelOperation, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Annuler") }
                } else {
                    if (service.original.isNotBlank() && service.result.isBlank()) Text(service.original, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                    if (service.notice.isNotBlank()) Text(service.notice, style = MaterialTheme.typography.bodyMedium)
                    if (service.result.isNotBlank()) {
                        SelectionContainer { Text(service.result, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) }
                        Button(onClick = service::applyResult, modifier = Modifier.fillMaxWidth()) { Text(if (tool == Tool.VOICE) "Insérer le texte" else "Remplacer le texte") }
                    } else if (!service.busy) {
                        when {
                            service.needsLanguages -> Button(onClick = service::downloadLanguages, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Download, null); Text(" Télécharger les langues") }
                            tool == Tool.VOICE -> {
                                if (!service.app.models.available()) {
                                    Text("Whisper transforme votre voix en texte, directement sur le téléphone. Le modèle Tiny multilingue occupe 78 Mo.", style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = service::openSettings, modifier = Modifier.fillMaxWidth()) { Text("Préparer la dictée") }
                                } else Button(onClick = service::startVoice, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Mic, null); Text(" Commencer à dicter") }
                            }
                            else -> Button(onClick = service::processText, modifier = Modifier.fillMaxWidth()) { Text(if (tool == Tool.TRANSLATE) "Traduire le texte" else "Proposer une correction") }
                        }
                    }
                    if (service.busy) TextButton(onClick = service::cancelOperation, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Annuler") }
                    Text(when (tool) {
                        Tool.TRANSLATE -> "Traduction fournie par Google · ML Kit · hors ligne après téléchargement"
                        Tool.CORRECT -> "Correction locale Gemini Nano · selon compatibilité · texte court"
                        else -> "Whisper Tiny · l’audio reste en mémoire sur ce téléphone"
                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LanguagePicker(code: String, enabled: Boolean, choose: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text(translationLanguages.firstOrNull { it.code == code }?.name ?: code, maxLines = 1); Icon(Icons.Rounded.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            translationLanguages.forEach { lang -> DropdownMenuItem(text = { Text(lang.name) }, onClick = { expanded = false; choose(lang.code) }) }
        }
    }
}

@Composable
private fun ColumnScope.EmojiPanel(onEmoji: (String) -> Unit) {
    val categories = listOf("Visages", "Gestes", "Cœurs", "Objets")
    val emojis = listOf(
        "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😋 😛 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫡 🤫 🤭 😶 🫠 😐 😑 🙄 😬 😮‍💨 😴 🥱 🤤 🤢 🤧 😷 🤒 🤕 🤑 😈 👻 💀 👽 🤖 💩",
        "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 💪 🦾 ✍️ 💅 🤳 👀 👂 🧠 🦷 👣",
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 🩷 🩵 🩶 💔 ❤️‍🔥 ❤️‍🩹 💕 💞 💓 💗 💖 💘 💝 💟 ❣️ 💌 💋 🌹 🌸 🌻 🌈 ⭐ 🌟 ✨ 💫 ☀️ 🌙 🔥 💯 ✅ ❌ 🎉 🎊 🎈 🎁 🏆",
        "☕ 🍵 🍕 🍔 🍟 🍣 🍜 🍰 🍪 🍫 🍎 🍓 🍌 🥑 🥐 🧀 🍷 🍺 🥂 🏠 🏡 🚗 🚕 🚲 🚌 🚆 ✈️ 🚀 🛸 🏖️ 🏔️ 🌍 📱 💻 ⌨️ 🎮 🎧 🎵 📷 🎬 📚 ✏️ 💡 🕐 📅 📍 🔑 🔒 💼 💰 🛒 🐶 🐱 🐼 🦊 🐻 🐸 🦋 🌿 🌲 🌺"
    )
    var category by remember { mutableIntStateOf(0) }
    var tone by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { categories.forEachIndexed { i, name -> TextButton(onClick = { category = i }) { Text(name, fontSize = 12.sp, color = if (category == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } } }
    if (category == 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { listOf("✋", "✋🏻", "✋🏼", "✋🏽", "✋🏾", "✋🏿").forEachIndexed { i, hand -> TextButton(onClick = { tone = i }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text(hand, fontSize = if (tone == i) 26.sp else 21.sp) } } }
    LazyVerticalGrid(GridCells.Adaptive(44.dp), modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
        items(emojis[category].split(' ')) { emoji -> TextButton(onClick = {
            val toned = if (category == 1 && tone > 0 && emoji.codePointAt(0) in skinToneBases) emoji.replace("\uFE0F", "") + String(Character.toChars(0x1F3FA + tone)) else emoji
            onEmoji(toned)
        }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(46.dp)) { Text(emoji, fontSize = 28.sp) } }
    }
}
private val skinToneBases = setOf(0x1F44B,0x1F91A,0x1F590,0x270B,0x1F596,0x1F44C,0x1F90C,0x1F90F,0x270C,0x1F91E,0x1FAF0,0x1F91F,0x1F918,0x1F919,0x1F448,0x1F449,0x1F446,0x1F447,0x261D,0x1F44D,0x1F44E,0x270A,0x1F44A,0x1F91B,0x1F91C,0x1F44F,0x1F64C,0x1FAF6,0x1F450,0x1F932,0x1F64F,0x1F4AA,0x270D,0x1F485,0x1F933,0x1F442)

@Composable
private fun ColumnScope.ClipboardPanel(service: ClavierService, preferences: Preferences) {
    if (!preferences.clipboard) Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gardez vos extraits à portée de main", style = MaterialTheme.typography.titleMedium)
        Text("L’historique est chiffré sur ce téléphone. Les extraits non épinglés expirent après une heure. Les contenus signalés comme sensibles sont ignorés.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = service::enableClipboard, modifier = Modifier.fillMaxWidth()) { Text("Activer l’historique") }
    } else {
        LaunchedEffect(Unit) { while (true) { service.refreshClips(); delay(30_000) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Conservation : 1 heure", Modifier.weight(1f), fontSize = 12.sp); TextButton(onClick = service::clearClips) { Text("Tout effacer") } }
        if (service.clips.isEmpty()) Text("Copiez du texte dans une application pour le retrouver ici.", Modifier.padding(20.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(service.clips, key = { it.id }) { clip ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clip.text, Modifier.weight(1f).clickable { service.key(clip.text) }.padding(vertical = 12.dp), maxLines = 2)
                    IconButton(onClick = { service.pinClip(clip) }) { Icon(Icons.Rounded.PushPin, if (clip.pinned) "Désépingler" else "Épingler", tint = if (clip.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { service.deleteClip(clip) }) { Icon(Icons.Rounded.DeleteOutline, "Supprimer") }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GifPanel(service: ClavierService) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = service::typeGifQuery, modifier = Modifier.weight(1f), enabled = !service.busy) { Icon(Icons.Rounded.Search, null); Text(service.gifQuery.ifBlank { "Rechercher un GIF" }, maxLines = 1) }
        TextButton(onClick = service::trendingGifs, enabled = !service.busy) { Text("Tendances") }
    }
    if (service.notice.isNotBlank()) Text(service.notice, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp)
    val selected = service.selectedGif
    if (selected != null) Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(selected.preview, selected.description, Modifier.weight(1f).fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { service.insertGif() }, enabled = !service.busy) { Text("Insérer le GIF") }
            TextButton(onClick = { service.insertGif(true) }, enabled = !service.busy) { Text("Insérer le lien") }
        }
    } else LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.weight(1f).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(service.gifs, key = { it.id }) { gif -> AsyncImage(gif.preview, gif.description, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)).clickable { service.selectGif(gif) }) }
    }
    Text("Powered by Tenor", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), fontSize = 12.sp)
}
