package com.englishreader.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishreader.app.data.db.AppDatabase
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.BookFormat
import com.englishreader.app.data.db.BookSource
import com.englishreader.app.data.db.DailyStatEntity
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.db.exampleList
import com.englishreader.app.data.export.VocabExporter
import com.englishreader.app.data.net.DictionaryApi
import com.englishreader.app.data.vocab.StatsRepository
import com.englishreader.app.data.vocab.TranslationSource
import com.englishreader.app.data.vocab.VocabRepository
import com.englishreader.app.data.vocab.WordLookup
import com.englishreader.app.ui.dictionary.DictionaryFilter
import com.englishreader.app.ui.dictionary.DictionaryQuery
import com.englishreader.app.ui.dictionary.LearnFilter
import com.englishreader.app.ui.dictionary.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RobolectricTests {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun lookup(word: String) = WordLookup(word, "كلمة", TranslationSource.ONLINE, "a definition", "noun", "/wɜːd/", null, listOf("An example."), false)

    @Test fun savesWordAndKeepsItUnique() = runTest {
        val repo = VocabRepository(db.vocabDao(), StatsRepository(db.statsDao()))
        val id1 = repo.saveFromLookup(lookup("curious"), "b1", "Book", "The curious girl.")
        val id2 = repo.saveFromLookup(lookup("curious"), "b1", "Book", "Another sentence.")
        assertEquals(id1, id2)
        val all = db.vocabDao().observeAll().first()
        assertEquals(1, all.size)
        val w = all.first()
        assertEquals("كلمة", w.translationAr)
        assertTrue(w.exampleList.contains("The curious girl."))
        assertEquals(1, db.statsDao().get(StatsRepository.today())!!.wordsSaved)
    }

    @Test fun editLearnedAndDelete() = runTest {
        val repo = VocabRepository(db.vocabDao(), StatsRepository(db.statsDao()))
        val id = repo.saveManual(VocabWordEntity(word = "  Brave ", notes = "my note"))
        val saved = repo.get(id)!!
        assertEquals("brave", saved.word)
        repo.saveManual(saved.copy(translationAr = "شجاع"))
        assertEquals("شجاع", repo.get(id)!!.translationAr)
        repo.setLearned(id, true)
        assertTrue(repo.get(id)!!.isLearned)
        repo.delete(id)
        assertEquals(null, repo.get(id))
    }

    @Test fun reviewSchedulingMovesWordForward() = runTest {
        val repo = VocabRepository(db.vocabDao(), StatsRepository(db.statsDao()))
        val id = repo.saveManual(VocabWordEntity(word = "harbor"))
        repo.recordReview(repo.get(id)!!, remembered = true)
        val w = repo.get(id)!!
        assertEquals(1, w.reviewCount)
        assertEquals(1, w.intervalDays)
        assertTrue(w.nextReviewAt > System.currentTimeMillis())
    }

    @Test fun readingProgressIsStored() = runTest {
        val dao = db.bookDao()
        dao.upsert(BookEntity(id = "x", title = "T", author = "A", source = BookSource.IMPORTED, format = BookFormat.TXT, totalParagraphs = 10))
        dao.updateProgress("x", 4, 120, 50, 1234L)
        val b = dao.get("x")!!
        assertEquals(4, b.lastParagraph)
        assertEquals(120, b.lastScrollOffset)
        assertEquals(50, b.progressPercent)
        assertEquals("x", dao.observeLastOpened().first()?.id)
        dao.delete("x")
        assertEquals(null, dao.get("x"))
    }

    @Test fun parsesDictionaryApiResponse() {
        val json = """[{"word":"hello","phonetic":"/həˈləʊ/","phonetics":[{"text":"/həˈləʊ/","audio":"//ssl.gstatic.com/hello.mp3"}],
            "meanings":[{"partOfSpeech":"exclamation","definitions":[{"definition":"used as a greeting","example":"hello there, Katie!"}]},
            {"partOfSpeech":"noun","definitions":[{"definition":"an utterance of hello"}]}]}]"""
        val r = DictionaryApi.parse(json)
        assertNotNull(r)
        assertEquals("/həˈləʊ/", r!!.phonetic)
        assertEquals("https://ssl.gstatic.com/hello.mp3", r.audioUrl)
        assertTrue(r.definition.contains("used as a greeting"))
        assertEquals(listOf("hello there, Katie!"), r.examples)
    }

    @Test fun exportsCsvAndTxt() {
        val words = listOf(
            VocabWordEntity(id = 1, word = "brave", translationAr = "شجاع", definition = "Ready to face danger, \"bold\"", examples = "She was brave.\nA brave act."),
            VocabWordEntity(id = 2, word = "harbor", translationAr = "ميناء", definition = "A place for ships"),
        )
        val csv = VocabExporter.toCsv(words)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("شجاع"))
        assertTrue(csv.contains("\"Ready to face danger, \"\"bold\"\"\""))
        val txt = VocabExporter.toTxt(words)
        assertTrue(txt.contains("ميناء"))
        val out = ByteArrayOutputStream()
        VocabExporter.export(words, com.englishreader.app.data.export.ExportFormat.CSV, out)
        assertEquals(csv, out.toString(Charsets.UTF_8.name()))
    }

    @Test fun dictionaryFilterSortsAndFilters() {
        val words = listOf(
            VocabWordEntity(id = 1, word = "zebra", dateAdded = 1, isLearned = true),
            VocabWordEntity(id = 2, word = "apple", dateAdded = 3),
            VocabWordEntity(id = 3, word = "mango", dateAdded = 2, translationAr = "مانجو"),
        )
        assertEquals(listOf("apple", "mango", "zebra"), DictionaryFilter.apply(words, DictionaryQuery(sort = SortOrder.ALPHA)).map { it.word })
        assertEquals(listOf("apple", "mango", "zebra"), DictionaryFilter.apply(words, DictionaryQuery(sort = SortOrder.NEWEST)).map { it.word })
        assertEquals(listOf("zebra"), DictionaryFilter.apply(words, DictionaryQuery(filter = LearnFilter.LEARNED)).map { it.word })
        assertEquals(listOf("mango"), DictionaryFilter.apply(words, DictionaryQuery(text = "مانجو")).map { it.word })
        assertFalse(DictionaryFilter.apply(words, DictionaryQuery(filter = LearnFilter.LEARNING)).any { it.isLearned })
    }

    @Test fun parsesLanguageToolAndAppliesFixes() {
        val json = """{"matches":[{"message":"The verb 'go' does not agree with the subject 'she'.","offset":4,"length":2,
            "replacements":[{"value":"goes"},{"value":"went"}],"rule":{"category":{"name":"Grammar"}}}]}"""
        val issues = com.englishreader.app.practice.LanguageToolApi.parse(json)
        assertEquals(1, issues.size)
        assertEquals("Grammar", issues[0].category)
        assertEquals("She goes to school.", com.englishreader.app.practice.LanguageToolApi.applyFixes("She go to school.", issues))
    }

    @Test fun parsesGeminiStory() {
        val inner = """{"title":"T","paragraphs":["A curious cat."],"questions":[
            {"type":"vocabulary","question":"Q?","options":["a","b","c","d"],"answer_index":2,"explanation":"e"},
            {"type":"x","question":"bad","options":["a"],"answer_index":0}]}"""
        val resp = org.json.JSONObject().put("candidates", org.json.JSONArray().put(org.json.JSONObject().put("content",
            org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", "```json\n$inner\n```")))))).toString()
        val text = com.englishreader.app.practice.GeminiClient.extractText(resp)
        val story = com.englishreader.app.practice.GeminiClient.parseStory(org.json.JSONObject(text), listOf("curious"))
        assertEquals("T", story.title)
        assertEquals(1, story.questions.size)
        assertEquals(com.englishreader.app.practice.QuestionType.VOCABULARY, story.questions[0].type)
        assertEquals(2, story.questions[0].correctIndex)
    }

    @Test fun detectsVoiceGender() {
        val g = com.englishreader.app.util.Pronouncer.Companion
        assertEquals(com.englishreader.app.util.VoiceGender.FEMALE, g.genderOf("en-us-x-sfg-local", emptySet()))
        assertEquals(com.englishreader.app.util.VoiceGender.MALE, g.genderOf("en-us-x-iom-local", emptySet()))
        assertEquals(com.englishreader.app.util.VoiceGender.FEMALE, g.genderOf("en-US-SMTf00", emptySet()))
        assertEquals(com.englishreader.app.util.VoiceGender.FEMALE, g.genderOf("English Female", emptySet()))
        assertEquals(com.englishreader.app.util.VoiceGender.MALE, g.genderOf("english_male_1", emptySet()))
        val best = g.bestVoice(listOf(
            com.englishreader.app.util.VoiceOption("en-us-x-iom-local", java.util.Locale.US, com.englishreader.app.util.VoiceGender.MALE, 400, false),
            com.englishreader.app.util.VoiceOption("en-us-x-sfg-network", java.util.Locale.US, com.englishreader.app.util.VoiceGender.FEMALE, 500, true),
            com.englishreader.app.util.VoiceOption("en-gb-x-gba-local", java.util.Locale.UK, com.englishreader.app.util.VoiceGender.FEMALE, 400, false),
            com.englishreader.app.util.VoiceOption("en-us-x-tpf-local", java.util.Locale.US, com.englishreader.app.util.VoiceGender.FEMALE, 400, false),
        ))
        assertEquals("en-us-x-tpf-local", best!!.name)
    }

    @Test fun streakCountsConsecutiveDays() {
        val stats = listOf(
            DailyStatEntity(StatsRepository.dayOffset(0), secondsRead = 600),
            DailyStatEntity(StatsRepository.dayOffset(1), wordsSaved = 2),
            DailyStatEntity(StatsRepository.dayOffset(3), secondsRead = 600),
        )
        assertEquals(2, StatsRepository.streak(stats))
    }
}
