package fr.azks.clavier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as ClavierApp
            val prefs by app.settings.state.collectAsState()
            ClavierTheme(prefs.theme) { SettingsApp(app, prefs) }
        }
    }
}

private enum class Page(val title: String) { HOME("Clavier"), THEMES("Thèmes et couleurs"), VOICE("Dictée Whisper"), LANGUAGES("Traduction"), CORRECTION("Correction locale"), PRIVACY("Confidentialité"), GIF("GIF Tenor"), TYPING("Saisie") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsApp(app: ClavierApp, prefs: Preferences) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    BackHandler(page != Page.HOME) { page = Page.HOME }
    Scaffold(topBar = { TopAppBar(title = { Text(page.title) }, navigationIcon = {
        if (page != Page.HOME) IconButton(onClick = { page = Page.HOME }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (page) {
                Page.HOME -> Home { page = it }
                Page.THEMES -> Themes(app, prefs)
                Page.VOICE -> VoiceSettings(app, prefs)
                Page.LANGUAGES -> LanguageSettings(app, prefs)
                Page.CORRECTION -> CorrectionSettings(prefs)
                Page.PRIVACY -> PrivacySettings(app, prefs)
                Page.GIF -> GifSettings(app)
                Page.TYPING -> TypingSettings(app, prefs)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Home(navigate: (Page) -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val manager = context.getSystemService(InputMethodManager::class.java)
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    fun refresh() {
        enabled = manager.enabledInputMethodList.any { it.packageName == context.packageName }
        selected = AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/') == context.packageName
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        activity.lifecycle.addObserver(observer); refresh()
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Keyboard, null, Modifier.size(36.dp))
            Text(if (selected) "Votre clavier est prêt." else "À vous d’écrire.", style = MaterialTheme.typography.headlineSmall)
            Text("Traduisez, dictez et personnalisez chaque touche.")
            if (!enabled) Button(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) }) { Text("1. Activer Clavier") }
            Button(onClick = { manager.showInputMethodPicker() }, enabled = enabled) { Text(if (selected) "Changer de clavier" else "2. Choisir Clavier") }
        }
    }
    var test by remember { mutableStateOf("") }
    OutlinedTextField(test, { test = it.take(4000) }, label = { Text("Essayez votre clavier ici") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Text("VOTRE CLAVIER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    SettingLink("Thèmes et couleurs", "Classique, verre et relief", Icons.Rounded.Palette) { navigate(Page.THEMES) }
    SettingLink("Saisie", "AZERTY, QWERTY, hauteur et vibrations", Icons.Rounded.Keyboard) { navigate(Page.TYPING) }
    SettingLink("Traduction", "Langues ML Kit à télécharger", Icons.Rounded.Translate) { navigate(Page.LANGUAGES) }
    SettingLink("Dictée Whisper", "Votre voix en texte, sur le téléphone", Icons.Rounded.Mic) { navigate(Page.VOICE) }
    SettingLink("Correction locale", "Disponibilité selon votre appareil", Icons.Rounded.Spellcheck) { navigate(Page.CORRECTION) }
    SettingLink("GIF Tenor", "Configurer une clé existante", Icons.Rounded.GifBox) { navigate(Page.GIF) }
    SettingLink("Confidentialité", "Presse-papier et mots de passe", Icons.Rounded.Lock) { navigate(Page.PRIVACY) }
    Text("Version ${BuildConfig.VERSION_NAME} · version de découverte", style = MaterialTheme.typography.labelSmall)
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/azksama/clavier-android"))) }) { Text("Code source et maquettes") }
}

@Composable
private fun SettingLink(title: String, subtitle: String, icon: ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = click).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Rounded.ChevronRight, null)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, change)
    }
}

@Composable
private fun Themes(app: ClavierApp, prefs: Preferences) {
    var draft by remember { mutableStateOf(prefs.theme) }
    var colorIndex by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    Text("Un clavier à votre image", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyStyle.entries.forEach { style -> FilterChip(selected = draft.style == style, onClick = { draft = ThemeConfig.preset(style).copy(mode = draft.mode); message = "" }, label = { Text(when (style) { KeyStyle.CLASSIC -> "Classique"; KeyStyle.GLASS -> "Verre"; KeyStyle.RELIEF -> "Relief" }) }) }
    }
    KeyboardBackground(draft, Modifier.clip(RoundedCornerShape(20.dp))) { TypingKeyboard(prefs.copy(theme = draft, vibrate = false, height = .85f, numbers = false), {}, {}, privateMode = true) }
    Text("APERÇU DU THÈME", style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorMode.entries.forEach { mode -> FilterChip(selected = draft.mode == mode, onClick = { draft = draft.copy(mode = mode) }, label = { Text(when (mode) { ColorMode.SYSTEM -> "Système"; ColorMode.LIGHT -> "Clair"; ColorMode.DARK -> "Sombre" }) }) }
    }
    Text("Couleurs personnalisées", style = MaterialTheme.typography.titleMedium)
    val colors = listOf(draft.background, draft.key, draft.ink, draft.accent)
    listOf("Fond", "Touches", "Texte", "Accent").forEachIndexed { i, label ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { colorIndex = i }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(colors[i])))
            Text(label, Modifier.weight(1f).padding(start = 14.dp)); Text(hex(colors[i]), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (draft.style == KeyStyle.GLASS) { Text("Opacité des touches : ${(draft.opacity * 100).toInt()} %"); Slider(draft.opacity, { draft = draft.copy(opacity = it) }, valueRange = .2f..1f) }
    Text("Le texte des touches est ajusté si nécessaire pour rester lisible. Une palette personnalisée conserve vos couleurs en mode sombre.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = { app.settings.update(app.settings.state.value.copy(theme = draft)); message = "Thème appliqué." }, modifier = Modifier.fillMaxWidth()) { Text("Appliquer le thème") }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    if (colorIndex >= 0) ColorDialog(colors[colorIndex], { colorIndex = -1 }) { color ->
        draft = when (colorIndex) { 0 -> draft.copy(background = color); 1 -> draft.copy(key = color); 2 -> draft.copy(ink = color); else -> draft.copy(accent = color) }.copy(custom = true)
        colorIndex = -1; message = ""
    }
}

@Composable
private fun ColorDialog(value: Long, dismiss: () -> Unit, apply: (Long) -> Unit) {
    var input by remember { mutableStateOf(hex(value)) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Choisir une couleur") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf(0xFFE7ECF2, 0xFF202832, 0xFFFFFFFF, 0xFF34465B), listOf(0xFF93BBE3, 0xFF286A83, 0xFFC7B7EA, 0xFFEAB3B3), listOf(0xFFB4D8C4, 0xFFFFD795, 0xFF0B57D0, 0xFF202124)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { color -> IconButton(onClick = { input = hex(color) }) { Box(Modifier.size(36.dp).clip(CircleShape).background(Color(color))) } } }
            }
            OutlinedTextField(input, { input = it.take(7) }, label = { Text("Code HEX, par exemple #286A83") }, singleLine = true, isError = parseHex(input) == null, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(parseHex(input) ?: value)))
        }
    }, confirmButton = { TextButton(onClick = { parseHex(input)?.let(apply) }, enabled = parseHex(input) != null) { Text("Choisir") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Annuler") } })
}

@Composable
private fun VoiceSettings(app: ClavierApp, prefs: Preferences) {
    val context = LocalContext.current
    var microphone by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { microphone = it }
    var available by remember { mutableStateOf(app.models.available()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val operation = rememberOperation()
    Text("Parlez. Relisez. Insérez.", style = MaterialTheme.typography.headlineSmall)
    Text("Whisper Tiny transcrit jusqu’à 60 secondes de parole en français ou en anglais. L’audio reste en mémoire sur ce téléphone. La vitesse et la précision dépendent de l’appareil.")
    Text(if (available) "Modèle prêt · 78 Mo" else "Modèle à télécharger · 78 Mo", style = MaterialTheme.typography.titleMedium)
    Toggle("Wi-Fi uniquement", "Pour Whisper et les langues de traduction", prefs.wifiOnly) { app.settings.update(prefs.copy(wifiOnly = it)) }
    if (operation.busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { operation.run { app.models.download(prefs.wifiOnly) { progress = it }; available = app.models.available(); "Whisper est prêt." } }, enabled = !operation.busy && !available, modifier = Modifier.fillMaxWidth()) { Text("Télécharger Whisper Tiny") }
    if (available) TextButton(onClick = { operation.run { app.models.remove(); available = app.models.available(); "Modèle supprimé." } }, enabled = !operation.busy) { Text("Supprimer le modèle") }
    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !microphone) { Text(if (microphone) "Microphone autorisé" else "Autoriser le microphone") }
    if (!microphone) TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Autorisations Android") }
    OperationStatus(operation)
}

@Composable
private fun LanguageSettings(app: ClavierApp, prefs: Preferences) {
    val operation = rememberOperation()
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { runCatching { TranslationEngine.downloaded() }.onSuccess { downloaded = it } }
    Text("Traduisez hors ligne", style = MaterialTheme.typography.headlineSmall)
    Text("Téléchargez vos langues une fois, puis traduisez directement depuis le clavier. Chaque langue occupe environ 30 Mo. Les traductions peuvent contenir des erreurs.")
    Row(verticalAlignment = Alignment.CenterVertically) { Text(if (prefs.language == "fr") "Français →" else "English →"); LanguagePicker(prefs.translationTarget, !operation.busy, { app.settings.update(prefs.copy(translationTarget = it)) }) }
    Toggle("Wi-Fi uniquement", "Pour le téléchargement des langues", prefs.wifiOnly) { app.settings.update(prefs.copy(wifiOnly = it)) }
    Button(onClick = { operation.run { TranslationEngine.download(prefs.language, prefs.translationTarget, prefs.wifiOnly); downloaded = TranslationEngine.downloaded(); "Langues disponibles hors ligne." } }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("Télécharger les langues") }
    OperationStatus(operation)
    Text("LANGUES DISPONIBLES", style = MaterialTheme.typography.labelMedium)
    translationLanguages.filter { it.code in downloaded }.forEach { lang -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(lang.name, Modifier.weight(1f)); if (lang.code != "en") IconButton(onClick = { operation.run { TranslationEngine.remove(lang.code); downloaded = TranslationEngine.downloaded(); "Langue supprimée." } }, enabled = !operation.busy) { Icon(Icons.Rounded.DeleteOutline, "Supprimer ${lang.name}") }
    } }
    Text("Traduction fournie par Google · ML Kit", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun CorrectionSettings(prefs: Preferences) {
    val context = LocalContext.current
    val operation = rememberOperation()
    Text("Une seconde lecture", style = MaterialTheme.typography.headlineSmall)
    Text("Correction facultative avec Gemini Nano, sur les appareils compatibles avec Android AICore. Cette fonctionnalité est en bêta. Français et anglais, jusqu’à 180 caractères par sélection.")
    Text("La compatibilité est vérifiée sur ce téléphone. La correction n’est pas disponible sur tous les appareils. Aucun abonnement ChatGPT n’est nécessaire.")
    Button(onClick = { operation.run { ProofreadingEngine.prepare(context, prefs.language, false) } }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("Vérifier la compatibilité") }
    Text("Le téléchargement ci-dessous peut utiliser le réseau mobile ; sa taille est gérée par AICore.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { operation.run { ProofreadingEngine.prepare(context, prefs.language, true) } }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("Préparer le modèle") }
    OperationStatus(operation)
}

@Composable
private fun GifSettings(app: ClavierApp) {
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(app.settings.tenorKey().isNotBlank()) }
    var message by remember { mutableStateOf("") }
    Text("Une réaction en GIF", style = MaterialTheme.typography.headlineSmall)
    Text("Tenor exige une clé API existante et n’accepte plus de nouveaux clients API depuis janvier 2026. La recherche reste indisponible sans clé valide.")
    Text(if (saved) "Une clé est enregistrée sur ce téléphone." else "Aucune clé enregistrée.")
    OutlinedTextField(key, { key = it.take(256) }, label = { Text("Votre clé Tenor existante") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = { app.settings.saveTenorKey(key); saved = true; key = ""; message = "Clé enregistrée, à vérifier par une recherche dans le clavier." }, enabled = key.isNotBlank()) { Text("Enregistrer la clé") }
    if (saved) TextButton(onClick = { app.settings.saveTenorKey(""); saved = false; message = "Clé supprimée." }) { Text("Supprimer la clé") }
    if (message.isNotBlank()) Text(message)
    Text("Seuls les mots saisis dans la recherche GIF sont envoyés à Tenor. Une application doit accepter les images pour insérer un GIF ; un lien peut aussi être inséré.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PrivacySettings(app: ClavierApp, prefs: Preferences) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    Text("Vous gardez la main", style = MaterialTheme.typography.headlineSmall)
    Toggle("Historique du presse-papier", "Chiffré sur ce téléphone. 20 extraits, conservés une heure sauf épinglage.", prefs.clipboard) { app.settings.update(prefs.copy(clipboard = it)) }
    TextButton(onClick = { app.settings.saveClips(emptyList()); message = "Historique effacé." }) { Text("Effacer tous les extraits, même épinglés") }
    if (message.isNotBlank()) Text(message)
    Text("Gestionnaires de mots de passe", style = MaterialTheme.typography.titleMedium)
    Text("Clavier prend en charge le remplissage automatique Android. À partir d’Android 11, les suggestions du gestionnaire compatible peuvent apparaître au-dessus des touches. Activez votre gestionnaire dans les paramètres Android.")
    Text("Dans les champs signalés comme sensibles, les outils IA, les suggestions de mots et l’historique du presse-papier sont désactivés. Le gestionnaire de mots de passe reste accessible.")
    Text("Vos textes sont traités localement pour la traduction, la correction et la dictée. Les téléchargements de modèles, les diagnostics des SDK Google et les recherches Tenor utilisent une connexion. Clavier n’ajoute aucun outil publicitaire ni analytique.")
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/azksama/clavier-android/blob/main/PRIVACY.md"))) }) { Text("Politique de confidentialité complète") }
}

@Composable
private fun TypingSettings(app: ClavierApp, prefs: Preferences) {
    Text("Votre confort de saisie", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("fr" to "Français · AZERTY", "en" to "English · QWERTY").forEach { (code, label) -> FilterChip(selected = prefs.language == code, onClick = { app.settings.update(prefs.copy(language = code)) }, label = { Text(label) }) } }
    Toggle("Ligne de chiffres", "Toujours afficher 1 à 0", prefs.numbers) { app.settings.update(prefs.copy(numbers = it)) }
    Toggle("Vibrations des touches", "Suit aussi les réglages système", prefs.vibrate) { app.settings.update(prefs.copy(vibrate = it)) }
    Text("Hauteur des touches : ${(prefs.height * 100).toInt()} %")
    Slider(prefs.height, { app.settings.update(prefs.copy(height = it)) }, valueRange = .85f..1.3f)
    Text("Appui long sur une lettre pour ses accents. Appui long sur la touche majuscule pour verrouiller les capitales. Appui long sur le globe pour changer de clavier.", style = MaterialTheme.typography.bodyMedium)
}

@Stable
private class UiOperation(private val scope: kotlinx.coroutines.CoroutineScope) {
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    private var job: Job? = null
    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true; message = ""
        job = scope.launch {
            try { message = block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = if (e is IllegalStateException || e is IllegalArgumentException) e.message ?: "Opération indisponible." else "Opération indisponible. Vérifiez votre connexion puis réessayez." }
            finally { busy = false }
        }
    }
    fun cancel() { job?.cancel(); message = "Opération interrompue. Un téléchargement géré par Android peut continuer en arrière-plan." }
}
@Composable private fun rememberOperation(): UiOperation { val scope = rememberCoroutineScope(); return remember(scope) { UiOperation(scope) } }
@Composable private fun OperationStatus(operation: UiOperation) {
    if (operation.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = operation::cancel) { Text("Annuler") } }
    if (operation.message.isNotBlank()) Text(operation.message, style = MaterialTheme.typography.bodyMedium)
}
