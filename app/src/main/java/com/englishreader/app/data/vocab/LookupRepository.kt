package com.englishreader.app.data.vocab

import com.englishreader.app.data.db.LookupCacheDao
import com.englishreader.app.data.db.LookupCacheEntity
import com.englishreader.app.data.net.DictionaryApi
import com.englishreader.app.data.net.OnlineTranslateApi
import com.englishreader.app.data.translate.OfflineTranslator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class TranslationSource { ONLINE, OFFLINE, CACHE, NONE }

data class WordLookup(
    val word: String,
    val translationAr: String,
    val translationSource: TranslationSource,
    val definition: String,
    val partOfSpeech: String,
    val phonetic: String,
    val audioUrl: String?,
    val examples: List<String>,
    /** True when the network failed (so the UI can explain missing data). */
    val offline: Boolean,
)

/**
 * Word lookup pipeline:
 * 1. cache (instant, offline) → 2. online dictionary + online translation in parallel →
 * 3. on-device ML Kit translation as fallback → 4. graceful "not available".
 */
class LookupRepository(
    private val cache: LookupCacheDao,
    private val offlineTranslator: OfflineTranslator,
) {
    suspend fun lookup(word: String): WordLookup {
        val key = word.lowercase()
        val cached = cache.get(key)
        if (cached != null && cached.translationAr.isNotEmpty() && cached.definition.isNotEmpty()) {
            return cached.toLookup(TranslationSource.CACHE, offline = false)
        }
        return coroutineScope {
            var networkFailed = false
            val dictJob = async { runCatching { DictionaryApi.lookup(key) }.onFailure { networkFailed = true }.getOrNull() }
            val onlineJob = async { runCatching { OnlineTranslateApi.translateEnToAr(key) }.onFailure { networkFailed = true }.getOrNull() }
            val deviceJob = async { offlineTranslator.translate(key) }
            val dict = dictJob.await()
            val online = onlineJob.await()
            val onDevice = deviceJob.await()
            // On-device model gives one clean meaning; MyMemory adds synonyms. Show both when they differ.
            var translation: String? = when {
                onDevice != null && online != null && !online.contains(onDevice) -> "$onDevice، $online"
                online != null -> online
                else -> onDevice
            }
            var source = when {
                online != null -> TranslationSource.ONLINE
                onDevice != null -> TranslationSource.OFFLINE
                else -> TranslationSource.NONE
            }
            if (translation == null && cached?.translationAr?.isNotEmpty() == true) {
                translation = cached.translationAr; source = TranslationSource.CACHE
            }
            val result = WordLookup(
                word = key,
                translationAr = translation.orEmpty(),
                translationSource = source,
                definition = dict?.definition ?: cached?.definition.orEmpty(),
                partOfSpeech = dict?.partOfSpeech ?: cached?.partOfSpeech.orEmpty(),
                phonetic = dict?.phonetic ?: cached?.phonetic.orEmpty(),
                audioUrl = dict?.audioUrl ?: cached?.audioUrl,
                examples = dict?.examples ?: cached?.examples?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
                offline = networkFailed,
            )
            if (result.translationAr.isNotEmpty() || result.definition.isNotEmpty()) {
                cache.upsert(
                    LookupCacheEntity(
                        word = key,
                        translationAr = result.translationAr,
                        translationSource = result.translationSource.name,
                        definition = result.definition,
                        partOfSpeech = result.partOfSpeech,
                        phonetic = result.phonetic,
                        audioUrl = result.audioUrl,
                        examples = result.examples.joinToString("\n"),
                    )
                )
            }
            result
        }
    }

    /** Translates a whole sentence (online first, then offline). */
    suspend fun translateSentence(text: String): String? =
        runCatching { OnlineTranslateApi.translateEnToAr(text) }.getOrNull() ?: offlineTranslator.translate(text)

    suspend fun clearCache() = cache.clear()

    private fun LookupCacheEntity.toLookup(source: TranslationSource, offline: Boolean) = WordLookup(
        word = word,
        translationAr = translationAr,
        translationSource = source,
        definition = definition,
        partOfSpeech = partOfSpeech,
        phonetic = phonetic,
        audioUrl = audioUrl,
        examples = examples.split('\n').filter { it.isNotBlank() },
        offline = offline,
    )
}
