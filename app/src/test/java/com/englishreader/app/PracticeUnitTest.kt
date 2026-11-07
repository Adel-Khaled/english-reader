package com.englishreader.app

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.practice.LocalPracticeGenerator
import com.englishreader.app.practice.MatchStatus
import com.englishreader.app.practice.QuestionType
import com.englishreader.app.practice.SpeechComparer
import com.englishreader.app.practice.WordForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PracticeUnitTest {
    private val words = listOf(
        VocabWordEntity(id = 1, word = "curious", translationAr = "فضولي", definition = "1. (adjective) Eager to know or learn something.",
            examples = "Young children are naturally curious about the world.", sourceSentence = "People said it was abandoned, but Mira was curious."),
        VocabWordEntity(id = 2, word = "fragile", translationAr = "هش", definition = "Easily broken.", examples = "The pages were yellow and fragile."),
        VocabWordEntity(id = 3, word = "struggle", translationAr = "يكافح", definition = "To try hard.", examples = "The boat was struggling against the storm."),
        VocabWordEntity(id = 4, word = "harbor", translationAr = "ميناء", definition = "A place for ships."),
    )

    @Test fun findsInflectedForms() {
        assertNotNull(WordForms.find("The boat was struggling against the storm.", "struggle"))
        assertNotNull(WordForms.find("She studied hard.", "study"))
        assertNotNull(WordForms.find("He stopped suddenly.", "stop"))
        assertNotNull(WordForms.find("Two harbors were full.", "harbor"))
        assertFalse(WordForms.contains("A curiosity shop.", "curious"))
        assertTrue(WordForms.matchesAnswer("Struggling", "struggle"))
        assertTrue(WordForms.matchesAnswer(" curious. ", "curious"))
        assertFalse(WordForms.matchesAnswer("curiously strong", "curious"))
    }

    @Test fun comparesSpeech() {
        val perfect = SpeechComparer.compare("The pages were fragile.", "the pages were fragile")
        assertEquals(100, perfect.score)
        val missing = SpeechComparer.compare("The pages were fragile", "the pages fragile")
        assertTrue(missing.matches.any { it.status == MatchStatus.MISSING && it.expected == "were" })
        assertTrue(missing.score in 60..80)
        val close = SpeechComparer.compare("curious", "curius")
        assertEquals(MatchStatus.CLOSE, close.matches.single().status)
        val wrong = SpeechComparer.compare("lighthouse", "bicycle")
        assertEquals(0, wrong.score)
        val best = SpeechComparer.best("fragile", listOf("fridge isle", "fragile", "fragile it"))!!
        assertEquals("fragile", best.first)
    }

    @Test fun generatesStoryWithAllWordsAndValidQuestions() {
        val gen = LocalPracticeGenerator(Random(42))
        for (level in Difficulty.values()) {
            val story = gen.story(words, level, words)
            val text = story.paragraphs.joinToString(" ")
            for (w in words) assertTrue("${w.word} missing in: $text", WordForms.contains(text, w.word))
            assertTrue(story.questions.size >= 5)
            for (q in story.questions) {
                assertTrue(q.prompt, q.options.size >= 2)
                assertTrue(q.correctIndex in q.options.indices)
                assertEquals(q.options.size, q.options.distinct().size)
            }
            assertTrue(story.questions.any { it.type == QuestionType.COMPREHENSION })
            assertTrue(story.questions.any { it.type == QuestionType.VOCABULARY })
        }
    }

    @Test fun buildsBlanksOnlyFromRealExamples() {
        val blanks = LocalPracticeGenerator(Random(1)).blanks(words, words)
        assertEquals(setOf("curious", "fragile", "struggle"), blanks.map { it.word }.toSet())
        for (b in blanks) {
            assertTrue(b.sentenceWithBlank.contains("_____"))
            assertFalse(WordForms.contains(b.sentenceWithBlank, b.word))
            assertTrue(b.word in b.options)
            assertEquals(4, b.options.size)
        }
    }
}
