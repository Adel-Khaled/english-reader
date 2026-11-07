package com.englishreader.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Plays dictionary audio when available, otherwise uses the device's offline text-to-speech. */
class Pronouncer(context: Context) {
    private var ready = false
    private var player: MediaPlayer? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready = true
    }.also { t ->
        // Language is applied lazily in speakTts() once the engine is ready.
        t.setSpeechRate(0.9f)
    }

    fun speak(text: String, audioUrl: String? = null) {
        if (!audioUrl.isNullOrBlank()) {
            try {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(audioUrl)
                    setOnPreparedListener { it.start() }
                    setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; speakTts(text); true }
                    setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                    prepareAsync()
                }
                return
            } catch (e: Exception) {
                player = null
            }
        }
        speakTts(text)
    }

    private fun speakTts(text: String) {
        if (!ready) return
        tts.setLanguage(Locale.US)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    fun shutdown() {
        player?.release(); player = null
        tts.stop(); tts.shutdown()
    }
}
