package fr.azks.clavier

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

object WhisperNative {
    init { System.loadLibrary("clavier_whisper") }
    external fun prepare()
    external fun cancel()
    external fun transcribe(path: String, samples: FloatArray, language: String): ByteArray
    val mutex = Mutex()
}

class WhisperModels(private val context: Context) {
    val file = File(context.noBackupFilesDir, "models/ggml-tiny.bin")
    private val mutex = Mutex()
    private val expectedBytes = 77_691_713L
    private val sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
    private val url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin"
    fun available() = file.exists() && file.length() == expectedBytes
    suspend fun download(wifiOnly: Boolean, progress: (Float) -> Unit) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (available()) { progress(1f); return@withContext }
            if (wifiOnly) {
                val network = context.getSystemService(ConnectivityManager::class.java)
                check(network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) { "Connectez-vous au Wi-Fi ou désactivez l’option Wi-Fi uniquement." }
            }
            file.parentFile?.mkdirs()
            val part = File(file.parentFile, "tiny.download")
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).build()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Téléchargement indisponible. Réessayez plus tard." }
                    val body = response.body ?: error("Téléchargement vide.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    var lastProgress = -1
                    body.byteStream().use { input -> part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            check(count <= expectedBytes) { "Taille du modèle incorrecte." }
                            digest.update(buffer, 0, read); output.write(buffer, 0, read)
                            val percent = (count * 100 / expectedBytes).toInt()
                            if (percent != lastProgress) { lastProgress = percent; withContext(Dispatchers.Main) { progress(count.toFloat() / expectedBytes) } }
                        }
                    } }
                    check(count == expectedBytes && digest.digest().joinToString("") { "%02x".format(it) } == sha256) { "Le modèle téléchargé est endommagé. Réessayez." }
                    check(part.renameTo(file)) { "Impossible d’enregistrer le modèle." }
                }
            } finally { part.delete(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }
    suspend fun remove() = mutex.withLock { withContext(Dispatchers.IO) { file.delete() } }
}

class VoiceRecorder(private val context: Context) {
    private val stopping = AtomicBoolean(false)
    fun stop() { stopping.set(true) }
    @SuppressLint("MissingPermission")
    suspend fun capture(level: (Float, Int) -> Unit): FloatArray = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Autorisez le microphone dans les réglages de Clavier." }
        stopping.set(false)
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "Le microphone ne prend pas en charge cet enregistrement." }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
        val audio = FloatArray(16000 * 60)
        var count = 0
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone indisponible." }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Impossible de démarrer le microphone." }
            val chunk = ShortArray(1600)
            while (!stopping.get() && count < audio.size) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(chunk, 0, minOf(chunk.size, audio.size - count), AudioRecord.READ_NON_BLOCKING)
                check(read >= 0) { "L’enregistrement a été interrompu." }
                var sum = 0.0
                for (i in 0 until read) { val sample = chunk[i] / 32768f; audio[count++] = sample; sum += sample * sample }
                if (read > 0) withContext(Dispatchers.Main) { level(sqrt(sum / read).toFloat().coerceIn(0f, 1f), count / 16000) }
                delay(20)
            }
            val output = audio.copyOf(count)
            if (count < 8000 || sqrt(output.sumOf { (it * it).toDouble() } / count) < .003) { output.fill(0f); error("Aucune parole détectée. Rapprochez-vous du microphone et réessayez.") }
            output
        } finally {
            audio.fill(0f)
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
}
