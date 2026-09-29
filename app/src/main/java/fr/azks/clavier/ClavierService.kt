package fr.azks.clavier

import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Build
import android.util.Size
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

data class EditSnapshot(val generation: Long, val source: String, val start: Int, val end: Int, val before: String, val after: String, val whole: String? = null, val cursorStart: Int = start, val cursorEnd: Int = end)
data class UndoEdit(val generation: Long, val original: String, val replacement: String, val start: Int, val after: String)

class ClavierService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val app get() = application as ClavierApp
    val prefs get() = app.settings.state.value
    var tool by mutableStateOf<Tool?>(null); private set
    var privateMode by mutableStateOf(false); private set
    var numeric by mutableStateOf(false); private set
    var action by mutableStateOf("↵"); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var notice by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var result by mutableStateOf(""); private set
    var original by mutableStateOf(""); private set
    var needsLanguages by mutableStateOf(false); private set
    var recording by mutableStateOf(false); private set
    var level by mutableFloatStateOf(0f); private set
    var seconds by mutableIntStateOf(0); private set
    var clips by mutableStateOf<List<Clip>>(emptyList()); private set
    var gifs by mutableStateOf<List<Gif>>(emptyList()); private set
    var selectedGif by mutableStateOf<Gif?>(null); private set
    var gifQuery by mutableStateOf(""); private set
    var gifSearchTyping by mutableStateOf(false); private set
    var undo by mutableStateOf<UndoEdit?>(null); private set
    private var generation = 0L
    private var selectionStart = -1
    private var selectionEnd = -1
    private var operation: Job? = null
    private var operationSequence = 0L
    private var autofillSequence = 0L
    var autofillViews by mutableStateOf<List<View>>(emptyList()); private set
    private var lastClipboardStamp = Long.MIN_VALUE
    private var pending: EditSnapshot? = null
    private var recorder: VoiceRecorder? = null
    private var composeView: ComposeView? = null
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { if (isInputViewShown && !privateMode && prefs.clipboard) readClipboard() }

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        clipboard.addPrimaryClipChangedListener(clipListener)
    }
    override fun onCreateInputView(): View {
        composeView?.disposeComposition()
        window?.window?.decorView?.let { it.setViewTreeLifecycleOwner(this); it.setViewTreeSavedStateRegistryOwner(this); it.setViewTreeViewModelStoreOwner(this) }
        return ComposeView(this).also { composeView = it }.apply {
            setViewTreeLifecycleOwner(this@ClavierService); setViewTreeSavedStateRegistryOwner(this@ClavierService); setViewTreeViewModelStoreOwner(this@ClavierService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val preferences by app.settings.state.collectAsState()
                ClavierTheme(preferences.theme) {
                    KeyboardBackground(preferences.theme, Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                        ImeContent(this@ClavierService, preferences)
                    }
                }
            }
        }
    }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetInput()
        selectionStart = attribute?.initialSelStart ?: -1; selectionEnd = attribute?.initialSelEnd ?: -1
        val type = attribute?.inputType ?: 0
        val number = type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
        privateMode = isSensitiveInput(type, attribute?.imeOptions ?: 0)
        numeric = number || type and InputType.TYPE_MASK_CLASS in setOf(InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)
        action = when ((attribute?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Envoyer"; EditorInfo.IME_ACTION_SEARCH -> "Chercher"; EditorInfo.IME_ACTION_NEXT -> "Suivant"; EditorInfo.IME_ACTION_DONE -> "OK"; EditorInfo.IME_ACTION_GO -> "Aller"; else -> "↵"
        }
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        registry.currentState = Lifecycle.State.RESUMED
        updateSuggestions()
    }
    override fun onFinishInputView(finishingInput: Boolean) { resetInput(); registry.currentState = Lifecycle.State.CREATED; super.onFinishInputView(finishingInput) }
    override fun onFinishInput() { resetInput(); super.onFinishInput() }
    override fun onEvaluateFullscreenMode() = false
    @RequiresApi(30)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!UiVersions.getVersions(uiExtras).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        val density = resources.displayMetrics.density
        val height = (48 * density).toInt()
        val style = UiVersions.newStylesBuilder().addStyle(InlineSuggestionUi.newStyleBuilder().build()).build()
        val spec = InlinePresentationSpec.Builder(Size((48 * density).toInt(), height), Size(resources.displayMetrics.widthPixels, height)).setStyle(style).build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(6).build()
    }
    @RequiresApi(30)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val token = ++autofillSequence
        val session = generation
        autofillViews = emptyList()
        val views = arrayOfNulls<View>(response.inlineSuggestions.size)
        response.inlineSuggestions.forEachIndexed { index, suggestion ->
            val size = Size(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, (48 * resources.displayMetrics.density).toInt())
            suggestion.inflate(this, size, mainExecutor) { view ->
                if (token == autofillSequence && session == generation && view != null) {
                    views[index] = view
                    autofillViews = views.filterNotNull()
                }
            }
        }
        return true
    }
    override fun onDestroy() {
        cancelOperation(); scope.cancel(); clipboard.removePrimaryClipChangedListener(clipListener)
        composeView?.disposeComposition(); composeView = null
        registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); super.onDestroy()
    }
    private fun resetInput() {
        autofillSequence++; autofillViews = emptyList()
        generation++; cancelOperation(); tool = null; result = ""; original = ""; notice = ""; pending = null; undo = null
        clips = emptyList(); gifs = emptyList(); selectedGif = null; gifQuery = ""; gifSearchTyping = false; suggestions = emptyList()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionStart = newSelStart; selectionEnd = newSelEnd; updateSuggestions()
    }
    fun key(value: String) {
        val input = currentInputConnection ?: return
        undo = null
        when (value) {
            "DELETE" -> if (!input.getSelectedText(0).isNullOrEmpty()) input.commitText("", 1) else {
                val before = input.getTextBeforeCursor(128, 0)?.toString().orEmpty()
                if (before.isNotEmpty()) input.deleteSurroundingText(lastGraphemeLength(before), 0)
            }
            "ENTER" -> {
                val options = currentInputEditorInfo?.imeOptions ?: 0
                if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 && options and EditorInfo.IME_MASK_ACTION !in setOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)) input.performEditorAction(options and EditorInfo.IME_MASK_ACTION)
                else input.commitText("\n", 1)
            }
            "LANGUAGE" -> app.settings.update(prefs.copy(language = if (prefs.language == "fr") "en" else "fr"))
            "PICK_IME" -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            else -> input.commitText(value, 1)
        }
        updateSuggestions()
    }
    private fun updateSuggestions() {
        if (privateMode || numeric || (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) { suggestions = emptyList(); return }
        val prefix = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty().takeLastWhile { it.isLetter() }
        val words = if (prefs.language == "fr") frenchWords else englishWords
        suggestions = if (prefix.length < 2) emptyList() else words.filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, true) }.take(3).map { if (prefix.first().isUpperCase()) it.replaceFirstChar(Char::uppercase) else it }
    }
    fun completeWord(word: String) {
        if (privateMode) return
        val input = currentInputConnection ?: return
        val prefix = input.getTextBeforeCursor(40, 0)?.toString().orEmpty().takeLastWhile { it.isLetter() }
        if (word.startsWith(prefix, true) && prefix.isNotEmpty()) { input.beginBatchEdit(); input.deleteSurroundingText(prefix.length, 0); input.commitText("$word ", 1); input.endBatchEdit() }
        updateSuggestions()
    }
    fun openTool(value: Tool) {
        if (privateMode) return
        cancelOperation(); tool = value; notice = ""; result = ""; original = ""; pending = null; needsLanguages = false; gifSearchTyping = false; selectedGif = null
        if (value == Tool.CLIPBOARD) readClipboard()
        if (value == Tool.GIF && app.settings.tenorKey().isBlank()) notice = "Ajoutez une clé Tenor existante dans les réglages. Tenor n’accepte plus de nouveaux clients API."
        if (value in setOf(Tool.TRANSLATE, Tool.CORRECT)) {
            runCatching { capture(true) }.onSuccess { pending = it; original = it.source }.onFailure { notice = it.message.orEmpty() }
        }
    }
    fun closeTool() { cancelOperation(); tool = null; pending = null; original = ""; result = ""; notice = ""; selectedGif = null; gifs = emptyList() }
    fun openSettings() { cancelOperation(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun cancelOperation() { operationSequence++; operation?.cancel(); operation = null; recorder?.stop(); recorder = null; if (recording || busy && tool == Tool.VOICE) WhisperNative.cancel(); recording = false; busy = false; level = 0f }
    private fun capture(replaceWhole: Boolean): EditSnapshot {
        check(!privateMode) { "Cette fonction est désactivée dans les champs sensibles." }
        val input = currentInputConnection ?: error("Aucun champ de saisie actif.")
        val selected = input.getSelectedText(0)?.toString().orEmpty()
        val before = input.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = input.getTextAfterCursor(64, 0)?.toString().orEmpty()
        if (selected.isNotEmpty() || !replaceWhole) {
            check(selected.length <= 4000 && selectionStart >= 0 && selectionEnd >= 0) { "Sélectionnez un texte plus court." }
            return EditSnapshot(generation, selected, minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd), before, after)
        }
        val extracted = input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4097; hintMaxLines = 100 }, 0) ?: error("Sélectionnez le texte à traiter dans l’application.")
        val text = extracted.text?.toString().orEmpty()
        check(extracted.startOffset == 0 && extracted.partialStartOffset < 0 && text.length in 1..4000) { "Sélectionnez entre 1 et 4 000 caractères dans l’application." }
        return EditSnapshot(generation, text, 0, text.length, before, after, text, selectionStart, selectionEnd)
    }
    private fun stillValid(snapshot: EditSnapshot): Boolean {
        if (snapshot.generation != generation || privateMode) return false
        val input = currentInputConnection ?: return false
        if (snapshot.whole != null) return selectionStart == snapshot.cursorStart && selectionEnd == snapshot.cursorEnd && input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 4097 }, 0)?.let { it.startOffset == 0 && it.text?.toString() == snapshot.whole } == true
        return minOf(selectionStart, selectionEnd) == snapshot.start && maxOf(selectionStart, selectionEnd) == snapshot.end &&
            input.getSelectedText(0)?.toString().orEmpty() == snapshot.source && input.getTextBeforeCursor(64, 0)?.toString().orEmpty() == snapshot.before && input.getTextAfterCursor(64, 0)?.toString().orEmpty() == snapshot.after
    }
    private fun runOperation(block: suspend () -> Unit) {
        cancelOperation(); val token = generation; val sequence = operationSequence; busy = true; notice = ""; result = ""
        operation = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: MissingLanguages) { if (token == generation && sequence == operationSequence) { needsLanguages = true; notice = e.message.orEmpty() } }
            catch (e: Exception) { if (token == generation && sequence == operationSequence) notice = when (e) { is IllegalStateException, is IllegalArgumentException, is LocalCorrectionUnavailable -> e.message ?: "Opération indisponible."; else -> "L’opération n’a pas abouti. Vérifiez la connexion ou réessayez." } }
            finally { if (token == generation && sequence == operationSequence) { busy = false; recording = false } }
        }
    }
    fun processText() {
        if (tool !in setOf(Tool.TRANSLATE, Tool.CORRECT) || privateMode) return
        val targetTool = tool
        runOperation {
            val snapshot = capture(true); pending = snapshot; original = snapshot.source
            val output = if (targetTool == Tool.TRANSLATE) TranslationEngine.translate(snapshot.source, prefs.language, prefs.translationTarget) else ProofreadingEngine.correct(this@ClavierService, snapshot.source, prefs.language)
            check(stillValid(snapshot)) { "Le texte a changé. Relancez le traitement." }; result = output
        }
    }
    fun downloadLanguages() = runOperation { TranslationEngine.download(prefs.language, prefs.translationTarget, prefs.wifiOnly); needsLanguages = false; notice = "Langues prêtes. Vous pouvez traduire hors ligne." }
    fun setTarget(code: String) { app.settings.update(prefs.copy(translationTarget = code)); result = ""; needsLanguages = false; notice = "" }
    fun applyResult() {
        val snapshot = pending ?: return
        if (result.isBlank()) return
        if (!stillValid(snapshot)) { notice = "Le champ ou le texte a changé. Relancez le traitement."; result = ""; return }
        val input = currentInputConnection ?: return
        val replacement = result
        input.beginBatchEdit()
        val committed = try { input.setSelection(snapshot.start, snapshot.end) && input.commitText(replacement, 1) } finally { input.endBatchEdit() }
        if (!committed) { notice = "Cette application n’autorise pas le remplacement."; return }
        val after = input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 8192 }, 0)
        undo = if (after?.startOffset == 0 && (after.text?.length ?: Int.MAX_VALUE) <= 8000) UndoEdit(generation, snapshot.source, replacement, snapshot.start, after.text.toString()) else null
        closeTool()
    }
    fun undoEdit() {
        val change = undo ?: return; val input = currentInputConnection ?: return
        val current = input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 8192 }, 0)
        if (change.generation == generation && current?.startOffset == 0 && current.text?.toString() == change.after) {
            input.beginBatchEdit(); try { if (input.setSelection(change.start, change.start + change.replacement.length)) input.commitText(change.original, 1) } finally { input.endBatchEdit() }
        }
        undo = null
    }
    fun startVoice() {
        if (privateMode) return
        if (!app.models.available()) { notice = "Téléchargez Whisper Tiny (78 Mo) dans les réglages avant de dicter."; return }
        runOperation {
            val snapshot = capture(false); pending = snapshot
            val voice = VoiceRecorder(this@ClavierService); recorder = voice; seconds = 0; recording = true
            val audio = voice.capture { volume, elapsed -> level = volume; seconds = elapsed }
            recording = false; recorder = null; notice = "Transcription sur le téléphone…"
            try {
                val text = WhisperNative.mutex.withLock {
                    currentCoroutineContext().ensureActive(); WhisperNative.prepare()
                    withContext(Dispatchers.Default) { WhisperNative.transcribe(app.models.file.absolutePath, audio, prefs.language).toString(Charsets.UTF_8).trim() }
                }
                check(text.isNotBlank()) { "Aucune parole détectée. Réessayez." }
                check(stillValid(snapshot)) { "Le champ a changé. Dictez à nouveau dans le champ souhaité." }
                notice = "Relisez votre message avant de l’insérer."; result = text
            } finally { audio.fill(0f) }
        }
    }
    fun stopVoice() { recorder?.stop() }
    fun readClipboard() {
        if (privateMode || !prefs.clipboard) { clips = emptyList(); return }
        val data = clipboard.primaryClip
        if (data != null && data.itemCount > 0 && data.description.timestamp != lastClipboardStamp && System.currentTimeMillis() - data.description.timestamp in 0..3_600_000 && data.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) != true) {
            lastClipboardStamp = data.description.timestamp
            data.getItemAt(0).text?.toString()?.let { app.settings.addClip(it, data.description.timestamp) }
        }
        clips = app.settings.clips()
    }
    fun refreshClips() { clips = if (privateMode) emptyList() else app.settings.clips() }
    fun enableClipboard() { app.settings.update(prefs.copy(clipboard = true)); readClipboard() }
    fun pinClip(clip: Clip) { app.settings.saveClips(clips.map { if (it.id == clip.id) it.copy(pinned = !it.pinned) else it }); clips = app.settings.clips() }
    fun deleteClip(clip: Clip) { app.settings.saveClips(clips.filterNot { it.id == clip.id }); clips = app.settings.clips() }
    fun clearClips() { app.settings.saveClips(emptyList()); clips = emptyList() }
    fun typeGifQuery() { gifSearchTyping = true }
    fun gifKey(value: String) {
        when (value) { "ENTER" -> searchGifs(); "DELETE" -> if (gifQuery.isNotEmpty()) gifQuery = gifQuery.dropLast(lastGraphemeLength(gifQuery)); "LANGUAGE", "PICK_IME" -> key(value); else -> if (gifQuery.length < 120) gifQuery += value }
    }
    fun searchGifs() { gifSearchTyping = false; selectedGif = null; runOperation { gifs = Tenor.search(app.settings.tenorKey(), gifQuery); if (gifs.isEmpty()) notice = "Aucun GIF trouvé. Essayez un autre mot." } }
    fun trendingGifs() { gifQuery = ""; searchGifs() }
    fun selectGif(gif: Gif) { selectedGif = gif }
    fun insertGif(asLink: Boolean = false) {
        val gif = selectedGif ?: return
        if (privateMode) return
        if (asLink) { key(gif.page); closeTool(); return }
        runOperation {
            val snapshot = capture(false)
            val info = currentInputEditorInfo ?: error("Aucun champ actif.")
            check(EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes("image/gif", it) }) { "Cette application n’accepte pas les GIF. Vous pouvez insérer le lien." }
            val file = Tenor.download(this@ClavierService, gif)
            check(stillValid(snapshot)) { "Le champ a changé. Choisissez à nouveau le GIF." }
            val uri = FileProvider.getUriForFile(this@ClavierService, "$packageName.gifs", file)
            val content = InputContentInfoCompat(uri, ClipDescription(gif.description, arrayOf("image/gif")), null)
            check(InputConnectionCompat.commitContent(currentInputConnection, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, Bundle())) { "L’application a refusé le GIF. Vous pouvez insérer le lien." }
            val key = app.settings.tenorKey(); val query = gifQuery
            tool = null; selectedGif = null; gifs = emptyList()
            runCatching { Tenor.shared(key, gif.id, query) }
        }
    }
}

private val frenchWords = "bonjour bonsoir bonne bonnes beaucoup bienvenue bientôt café comment comprendre correction demain dimanche disponible ensemble faire famille français heure hier journée maintenant maison merci message nouveau nouvelle personne plaisir pourquoi pouvoir quand quelque rendezvous réponse retrouver samedi semaine seulement soir toujours travail traduction vraiment vendredi voulez voulezvous".split(' ')
private val englishWords = "about after again because before between could different friend good hello later little message morning please should something thank thanks their there these think tomorrow translation want welcome where which would".split(' ')
