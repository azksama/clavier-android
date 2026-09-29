package fr.azks.clavier

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class Language(val code: String, val name: String)
val translationLanguages: List<Language> by lazy {
    TranslateLanguage.getAllLanguages().map { Language(it, Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { c -> c.titlecase(Locale.FRENCH) }) }.sortedBy { it.name }
}
class MissingLanguages : Exception("Téléchargez d’abord les langues sélectionnées.")
class LocalCorrectionUnavailable : Exception("Correction locale indisponible sur cet appareil. Le clavier et la traduction restent disponibles.")

object TranslationEngine {
    suspend fun downloaded(): Set<String> = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet() + "en"
    suspend fun download(source: String, target: String, wifi: Boolean) {
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        try { withTimeout(900_000) { client.downloadModelIfNeeded(DownloadConditions.Builder().apply { if (wifi) requireWifi() }.build()).await() } }
        finally { client.close() }
    }
    suspend fun remove(code: String) { if (code != "en") RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(code).build()).await() }
    suspend fun translate(text: String, source: String, target: String): String {
        require(text.isNotBlank() && text.length <= 4000) { "Sélectionnez entre 1 et 4 000 caractères." }
        if (source == target) return text
        val models = downloaded()
        if (source !in models || target !in models) throw MissingLanguages()
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        return try { withTimeout(60_000) { client.translate(text).await() } } finally { client.close() }
    }
}

object ProofreadingEngine {
    suspend fun prepare(context: Context, language: String, download: Boolean): String {
        val lang = if (language == "fr") ProofreaderOptions.Language.FRENCH else ProofreaderOptions.Language.ENGLISH
        val client = Proofreading.getClient(ProofreaderOptions.builder(context).setLanguage(lang).setInputType(ProofreaderOptions.InputType.KEYBOARD).build())
        try {
            return when (client.checkFeatureStatus().await()) {
                FeatureStatus.UNAVAILABLE -> "Correction indisponible sur cet appareil. AICore et un appareil compatible sont nécessaires."
                FeatureStatus.AVAILABLE -> "Prêt pour la correction locale."
                else -> if (!download) "Un modèle doit être téléchargé. Sa taille dépend de votre appareil." else {
                    withTimeout(900_000) { client.downloadFeature(object : com.google.mlkit.genai.common.DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) {}
                        override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                        override fun onDownloadFailed(e: com.google.mlkit.genai.common.GenAiException) {}
                        override fun onDownloadCompleted() {}
                    }).await() }
                    "Modèle préparé. Vérifiez sa disponibilité avant la première correction."
                }
            }
        } finally { client.close() }
    }
    suspend fun correct(context: Context, text: String, language: String): String {
        require(text.isNotBlank() && text.codePointCount(0, text.length) <= 180) { "Sélectionnez un texte court (180 caractères maximum)." }
        val lang = if (language == "fr") ProofreaderOptions.Language.FRENCH else ProofreaderOptions.Language.ENGLISH
        val client = Proofreading.getClient(ProofreaderOptions.builder(context).setLanguage(lang).setInputType(ProofreaderOptions.InputType.KEYBOARD).build())
        return try {
            when (client.checkFeatureStatus().await()) {
                FeatureStatus.UNAVAILABLE -> throw LocalCorrectionUnavailable()
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> throw IllegalStateException("Le modèle de correction doit être préparé dans les réglages.")
            }
            withTimeout(60_000) { client.runInference(ProofreadingRequest.builder(text).build()).await().results.firstOrNull()?.text ?: text }
        } finally { client.close() }
    }
}
