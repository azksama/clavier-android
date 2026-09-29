package fr.azks.clavier

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class Gif(val id: String, val description: String, val preview: String, val image: String, val page: String)

object Tenor {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    suspend fun search(key: String, query: String): List<Gif> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Ajoutez une clé Tenor existante dans les réglages." }
        val endpoint = if (query.isBlank()) "featured" else "search"
        val url = "https://tenor.googleapis.com/v2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("key", key).addQueryParameter("client_key", "clavier_android")
            .addQueryParameter("locale", "fr_FR").addQueryParameter("country", "FR")
            .addQueryParameter("contentfilter", "high").addQueryParameter("media_filter", "tinygif,gif")
            .addQueryParameter("limit", "12").apply { if (query.isNotBlank()) addQueryParameter("q", query.take(120)) }.build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Tenor est indisponible ou la clé est invalide." }
            val array = JSONObject(response.body?.string() ?: "{}").optJSONArray("results") ?: return@use emptyList()
            (0 until array.length()).mapNotNull { i -> runCatching {
                val item = array.getJSONObject(i); val media = item.getJSONObject("media_formats")
                val preview = media.getJSONObject("tinygif").getString("url"); val image = media.getJSONObject("gif").getString("url")
                require(mediaUrl(preview) && mediaUrl(image))
                val page = item.getString("itemurl"); require(page.toHttpUrl().host in setOf("tenor.com", "www.tenor.com"))
                Gif(item.getString("id"), item.optString("content_description", "GIF Tenor"), preview, image, page)
            }.getOrNull() }
        }
    }
    private fun mediaUrl(value: String): Boolean = runCatching { value.toHttpUrl().let { it.scheme == "https" && it.host == "media.tenor.com" } }.getOrDefault(false)
    suspend fun download(context: Context, gif: Gif): File = withContext(Dispatchers.IO) {
        require(mediaUrl(gif.image))
        val dir = File(context.cacheDir, "shared_gifs").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val file = File(dir, "${System.currentTimeMillis()}.gif")
        try {
            client.newCall(Request.Builder().url(gif.image).build()).execute().use { response ->
                check(response.isSuccessful && response.request.url.host == "media.tenor.com") { "GIF indisponible." }
                val body = response.body ?: error("GIF vide.")
                var size = 0L
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; size += n; check(size <= 15_000_000) { "Ce GIF est trop volumineux (15 Mo maximum)." }; output.write(buffer, 0, n) }
                } }
                val signature = java.io.DataInputStream(file.inputStream()).use { input -> ByteArray(6).also { input.readFully(it) }.toString(Charsets.US_ASCII) }
                check(signature == "GIF87a" || signature == "GIF89a") { "Format GIF invalide." }
            }
            file
        } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun shared(key: String, id: String, query: String) = withContext(Dispatchers.IO) {
        val url = "https://tenor.googleapis.com/v2/registershare".toHttpUrl().newBuilder().addQueryParameter("key", key)
            .addQueryParameter("client_key", "clavier_android").addQueryParameter("id", id).addQueryParameter("q", query.take(120)).build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
    }
}
