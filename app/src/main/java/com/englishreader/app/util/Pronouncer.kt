package com.englishreader.app.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class TtsStatus { INITIALIZING, READY, NO_ENGINE, MISSING_DATA }
enum class VoiceGender { FEMALE, MALE, UNKNOWN }

data class VoiceOption(
    val name: String,
    val locale: Locale,
    val gender: VoiceGender,
    val quality: Int,
    val needsNetwork: Boolean,
) {
    val accent: String get() = when (locale.country.uppercase()) {
        "US" -> "US"; "GB" -> "UK"; "AU" -> "Australia"; "IN" -> "India"; "CA" -> "Canada"; "IE" -> "Ireland"
        else -> locale.displayCountry.ifBlank { "English" }
    }
}

/**
 * Pronunciation using the phone's offline text-to-speech engine.
 *
 * Fixes from v1.0: the engine is created at app start (not on first tap, which used to drop
 * the first word while the engine was still loading), requests made before the engine is
 * ready are queued, an offline (local) female English voice is preferred to avoid network
 * latency, and slow online dictionary audio is no longer used.
 */
class Pronouncer(context: Context) {
    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    val status: StateFlow<TtsStatus> = _status
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices
    private val _activeVoice = MutableStateFlow<String?>(null)
    val activeVoice: StateFlow<String?> = _activeVoice

    @Volatile private var preferredVoice: String? = null
    @Volatile private var rate: Float = 0.9f
    @Volatile private var pending: Pair<String, Float?>? = null
    private val ids = AtomicInteger()

    /** Called when an utterance finishes (used by tests to measure latency). */
    @Volatile var onDone: ((String) -> Unit)? = null
    @Volatile var onStart: ((String) -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { result -> onInit(result) }

    private fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            _status.value = TtsStatus.NO_ENGINE
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { onStart?.invoke(utteranceId) }
            override fun onDone(utteranceId: String) { onDone?.invoke(utteranceId) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { onDone?.invoke(utteranceId) }
        })
        configure()
        pending?.let { (text, r) -> pending = null; speak(text, r) }
    }

    private fun configure() {
        val langOk = listOf(Locale.US, Locale.UK, Locale.ENGLISH).any { loc ->
            val r = tts.setLanguage(loc)
            r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
        val options = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "en" && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .map { VoiceOption(it.name, it.locale, genderOf(it), it.quality, it.isNetworkConnectionRequired) }
            .sortedWith(compareBy<VoiceOption>({ it.needsNetwork }, { it.gender != VoiceGender.FEMALE }, { it.locale.country != "US" }, { -it.quality }, { it.name }))
        _voices.value = options
        _status.value = if (langOk || options.isNotEmpty()) TtsStatus.READY else TtsStatus.MISSING_DATA
        applyVoice()
    }

    private fun applyVoice() {
        if (_status.value != TtsStatus.READY) return
        val all = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val chosenName = preferredVoice?.takeIf { n -> all.any { it.name == n } } ?: bestVoice(_voices.value)?.name
        val voice: Voice? = all.firstOrNull { it.name == chosenName }
        if (voice != null) {
            runCatching { tts.voice = voice }
            _activeVoice.value = voice.name
        }
        Log.i("Pronouncer", "voice=${_activeVoice.value} of ${_voices.value.size} English voices")
    }

    /** Applies the user's settings; called whenever settings change. */
    fun configure(voiceName: String?, speechRate: Float) {
        rate = speechRate
        if (voiceName != preferredVoice) {
            preferredVoice = voiceName
            applyVoice()
        }
    }

    fun speak(text: String, speechRate: Float? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        when (_status.value) {
            TtsStatus.INITIALIZING -> { pending = clean to speechRate; return }
            TtsStatus.READY, TtsStatus.MISSING_DATA -> {}
            TtsStatus.NO_ENGINE -> return
        }
        tts.setSpeechRate(speechRate ?: rate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, "u${ids.incrementAndGet()}")
    }

    fun stop() { runCatching { tts.stop() } }

    fun shutdown() { runCatching { tts.stop(); tts.shutdown() } }

    companion object {
        // Google TTS voice codes (en-us-x-XXX-local / en-gb-x-XXX-local).
        private val googleFemale = setOf("sfg", "tpc", "tpf", "iob", "iog", "gba", "gbc", "gbg", "fis", "aua", "auc", "cxx")
        private val googleMale = setOf("iol", "iom", "tpd", "gbb", "gbd", "rjs", "aub", "aud", "ahp")

        fun genderOf(v: Voice): VoiceGender = genderOf(v.name, v.features.orEmpty())

        fun genderOf(name: String, features: Set<String>): VoiceGender {
            val n = name.lowercase()
            val f = features.joinToString(" ").lowercase()
            if ("female" in n || "female" in f || "gender=female" in f) return VoiceGender.FEMALE
            if (Regex("(^|[^e])male").containsMatchIn(n) || "gender=male" in f) return VoiceGender.MALE
            Regex("-x-([a-z]{3})").find(n)?.groupValues?.get(1)?.let { code ->
                if (code in googleFemale) return VoiceGender.FEMALE
                if (code in googleMale) return VoiceGender.MALE
            }
            // Samsung voices: SMTf00 = female, SMTm00 = male
            if (Regex("smtf\\d").containsMatchIn(n)) return VoiceGender.FEMALE
            if (Regex("smtm\\d").containsMatchIn(n)) return VoiceGender.MALE
            return VoiceGender.UNKNOWN
        }

        /** Offline first, then female, then US English, then highest quality. */
        fun bestVoice(options: List<VoiceOption>): VoiceOption? = options.minWithOrNull(
            compareBy<VoiceOption>({ it.needsNetwork }, { it.gender != VoiceGender.FEMALE }, { it.locale.country != "US" }, { -it.quality })
        )
    }
}
