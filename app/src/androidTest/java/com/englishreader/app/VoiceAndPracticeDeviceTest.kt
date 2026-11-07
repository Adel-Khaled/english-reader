package com.englishreader.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.util.TtsStatus
import com.englishreader.app.util.VoiceGender
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VoiceAndPracticeDeviceTest {
    private val c = container()

    private fun waitReady() {
        val start = System.currentTimeMillis()
        while (c.pronouncer.status.value == TtsStatus.INITIALIZING && System.currentTimeMillis() - start < 15_000) Thread.sleep(100)
    }

    @Test fun voiceIsFemaleAndSpeaksQuickly() {
        waitReady()
        val status = c.pronouncer.status.value
        val voices = c.pronouncer.voices.value
        Log.i("VoiceTest", "status=$status active=${c.pronouncer.activeVoice.value} voices=${voices.joinToString { "${it.name}/${it.gender}/${if (it.needsNetwork) "net" else "local"}" }}")
        assertEquals(TtsStatus.READY, status)
        val active = voices.firstOrNull { it.name == c.pronouncer.activeVoice.value }
        if (voices.any { it.gender == VoiceGender.FEMALE && !it.needsNetwork }) {
            assertEquals("active voice should be female", VoiceGender.FEMALE, active?.gender)
        }

        // Measure latency of 3 consecutive taps (the second and third must be fast too).
        repeat(3) { i ->
            val started = CountDownLatch(1)
            val done = CountDownLatch(1)
            c.pronouncer.onStart = { started.countDown() }
            c.pronouncer.onDone = { done.countDown() }
            val t0 = System.currentTimeMillis()
            c.pronouncer.speak(listOf("curious", "lighthouse", "fragile")[i])
            assertTrue("speech did not start", started.await(5, TimeUnit.SECONDS))
            val latency = System.currentTimeMillis() - t0
            assertTrue("speech did not finish", done.await(8, TimeUnit.SECONDS))
            Log.i("VoiceTest", "tap ${i + 1}: started after ${latency} ms")
            assertTrue("latency $latency ms too high", latency < 3000)
        }
        c.pronouncer.onStart = null
        c.pronouncer.onDone = null
    }

    @Test fun practiceEngineOnDevice() = runBlocking {
        val words = listOf(
            VocabWordEntity(id = 901, word = "curious", translationAr = "فضولي", examples = "Young children are naturally curious about the world."),
            VocabWordEntity(id = 902, word = "fragile", translationAr = "هش", examples = "The pages were yellow and fragile."),
        )
        val story = c.practice.story(words, Difficulty.BEGINNER, aiKey = "")
        Log.i("LookupTest", "story: ${story.title} | ${story.paragraphs.joinToString(" ")}")
        assertTrue(story.questions.isNotEmpty())

        val w = VocabWordEntity(id = 903, word = "school", examples = "She walks to school every day.")
        val r = c.practice.checkWriting(w, "she go to school yesterday", "")
        Log.i("LookupTest", "writing: online=${r.checkedOnline} issues=${r.issues.map { it.message }} corrected=${r.corrected}")
        assertTrue(r.usesWord)
        assertTrue(r.issues.isNotEmpty())
        assertTrue(r.corrected != null)

        val good = c.practice.checkWriting(w, "She walks to school every morning.", "")
        Log.i("LookupTest", "good writing: issues=${good.issues.map { it.message }}")
        Unit
    }
}
