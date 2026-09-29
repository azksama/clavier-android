package fr.azks.clavier

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ClavierApp : Application(), coil.ImageLoaderFactory {
    val settings by lazy { Settings(this) }
    val models by lazy { WhisperModels(this) }
    override fun newImageLoader() = coil.ImageLoader.Builder(this).components {
        if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
    }.build()
}

enum class KeyStyle { CLASSIC, GLASS, RELIEF }
enum class ColorMode { SYSTEM, LIGHT, DARK }

data class ThemeConfig(
    val style: KeyStyle = KeyStyle.CLASSIC,
    val mode: ColorMode = ColorMode.SYSTEM,
    val background: Long = 0xFFE8EAED,
    val key: Long = 0xFFFFFFFF,
    val ink: Long = 0xFF202124,
    val accent: Long = 0xFF0B57D0,
    val opacity: Float = .44f,
    val custom: Boolean = false,
) {
    companion object {
        fun preset(style: KeyStyle) = when (style) {
            KeyStyle.CLASSIC -> ThemeConfig()
            KeyStyle.GLASS -> ThemeConfig(style, background = 0xFF93BBE3, ink = 0xFF183B50, accent = 0xFF286A83)
            KeyStyle.RELIEF -> ThemeConfig(style, background = 0xFFE7ECF2, key = 0xFFE7ECF2, ink = 0xFF34465B, accent = 0xFF286A83)
        }
    }
    fun resolved(dark: Boolean): ThemeConfig = if (!custom && (mode == ColorMode.DARK || mode == ColorMode.SYSTEM && dark)) {
        copy(background = 0xFF202832, key = 0xFF35414F, ink = 0xFFF1F3F4, accent = 0xFF8BCBDC)
    } else this
}

data class Preferences(
    val theme: ThemeConfig = ThemeConfig(),
    val language: String = "fr",
    val translationTarget: String = "en",
    val vibrate: Boolean = true,
    val numbers: Boolean = false,
    val height: Float = 1f,
    val clipboard: Boolean = false,
    val wifiOnly: Boolean = true,
)

data class Clip(val id: Long, val text: String, val pinned: Boolean = false)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("clavier", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read(): Preferences {
        val style = KeyStyle.entries.getOrElse(prefs.getInt("style", 0)) { KeyStyle.CLASSIC }
        val preset = ThemeConfig.preset(style)
        return Preferences(
            theme = preset.copy(mode = ColorMode.entries.getOrElse(prefs.getInt("mode", 0)) { ColorMode.SYSTEM },
                background = prefs.getLong("background", preset.background), key = prefs.getLong("key", preset.key),
                ink = prefs.getLong("ink", preset.ink), accent = prefs.getLong("accent", preset.accent),
                opacity = prefs.getFloat("opacity", .44f).coerceIn(.2f, 1f), custom = prefs.getBoolean("custom", false)),
            language = prefs.getString("language", "fr") ?: "fr", translationTarget = prefs.getString("target", "en") ?: "en",
            vibrate = prefs.getBoolean("vibrate", true), numbers = prefs.getBoolean("numbers", false),
            height = prefs.getFloat("height", 1f).coerceIn(.85f, 1.3f), clipboard = prefs.getBoolean("clipboard", false), wifiOnly = prefs.getBoolean("wifi", true),
        )
    }
    fun update(value: Preferences) {
        val t = value.theme
        prefs.edit().putInt("style", t.style.ordinal).putInt("mode", t.mode.ordinal)
            .putLong("background", t.background).putLong("key", t.key).putLong("ink", t.ink).putLong("accent", t.accent)
            .putFloat("opacity", t.opacity.coerceIn(.2f, 1f)).putBoolean("custom", t.custom)
            .putString("language", value.language).putString("target", value.translationTarget)
            .putBoolean("vibrate", value.vibrate).putBoolean("numbers", value.numbers).putFloat("height", value.height)
            .putBoolean("clipboard", value.clipboard).putBoolean("wifi", value.wifiOnly).apply()
        mutable.value = value
        if (!value.clipboard) secrets.remove("clips")
    }
    fun tenorKey() = secrets.get("tenor")
    fun saveTenorKey(value: String) { if (value.isBlank()) secrets.remove("tenor") else secrets.put("tenor", value.trim()) }
    fun clips(): List<Clip> {
        if (!mutable.value.clipboard) return emptyList()
        val array = runCatching { JSONArray(secrets.get("clips").ifBlank { "[]" }) }.getOrElse { JSONArray() }
        val now = System.currentTimeMillis()
        val clips = (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).let { Clip(it.getLong("id"), it.getString("text"), it.getBoolean("pinned")) } }.getOrNull()
        }.filter { it.pinned || now - it.id in 0..3_600_000 }.take(20)
        saveClips(clips)
        return clips
    }
    fun saveClips(clips: List<Clip>) {
        if (!mutable.value.clipboard) return
        val array = JSONArray()
        clips.take(20).forEach { array.put(JSONObject().put("id", it.id).put("text", it.text.take(4000)).put("pinned", it.pinned)) }
        secrets.put("clips", array.toString())
    }
    fun addClip(text: String, copiedAt: Long = System.currentTimeMillis()) {
        if (text.isBlank() || !mutable.value.clipboard) return
        val current = clips()
        if (current.any { it.text == text }) return
        var id = copiedAt
        while (current.any { it.id == id }) id++
        saveClips((current.filter { it.pinned } + Clip(id, text.take(4000)) + current.filterNot { it.pinned }).take(20))
    }
}

private class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("private", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("clavier.local", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("clavier.local", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun put(name: String, text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }
    fun get(name: String): String = runCatching {
        val encoded = prefs.getString(name, null) ?: return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }.getOrElse { "" }
    fun remove(name: String) { prefs.edit().remove(name).apply() }
}

fun parseHex(value: String): Long? = value.trim().removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLongOrNull(16)?.or(0xFF000000)
fun hex(value: Long) = "#%06X".format(value and 0xFFFFFF)
fun contrastRatio(a: Long, b: Long): Double {
    fun luminance(value: Long): Double {
        fun channel(shift: Int): Double { val n = ((value shr shift) and 255) / 255.0; return if (n <= .04045) n / 12.92 else Math.pow((n + .055) / 1.055, 2.4) }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    val x = luminance(a); val y = luminance(b)
    return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
}
fun readableInk(background: Long, preferred: Long): Color = Color(if (contrastRatio(background, preferred) >= 4.5) preferred else if (contrastRatio(background, 0xFF000000) > contrastRatio(background, 0xFFFFFFFF)) 0xFF000000 else 0xFFFFFFFF)
