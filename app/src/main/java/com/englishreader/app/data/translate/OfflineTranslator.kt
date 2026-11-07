package com.englishreader.app.data.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

enum class OfflinePackState { UNKNOWN, NOT_INSTALLED, DOWNLOADING, READY, FAILED }

/** On-device English→Arabic translation using Google ML Kit (free, works offline after one download). */
class OfflineTranslator {
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.ARABIC)
        .build()
    private val translator by lazy { Translation.getClient(options) }
    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private val arabicModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()

    private val _state = MutableStateFlow(OfflinePackState.UNKNOWN)
    val state: StateFlow<OfflinePackState> = _state

    suspend fun refreshState() {
        _state.value = try {
            if (modelManager.isModelDownloaded(arabicModel).await()) OfflinePackState.READY else OfflinePackState.NOT_INSTALLED
        } catch (e: Exception) {
            OfflinePackState.NOT_INSTALLED
        }
    }

    suspend fun download(): Boolean {
        _state.value = OfflinePackState.DOWNLOADING
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            _state.value = OfflinePackState.READY
            true
        } catch (e: Exception) {
            _state.value = OfflinePackState.FAILED
            false
        }
    }

    suspend fun remove() {
        runCatching { modelManager.deleteDownloadedModel(arabicModel).await() }
        refreshState()
    }

    /** Returns null if the model is not installed or translation fails. */
    suspend fun translate(text: String): String? {
        if (_state.value == OfflinePackState.UNKNOWN) refreshState()
        if (_state.value != OfflinePackState.READY) return null
        return try {
            translator.translate(text).await()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
