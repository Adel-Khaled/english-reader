package com.englishreader.app.practice

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.ExerciseResultEntity
import com.englishreader.app.data.db.GeneratedContentEntity
import com.englishreader.app.data.db.PracticeDao
import com.englishreader.app.data.db.PracticeSessionEntity
import com.englishreader.app.data.db.PracticeType
import com.englishreader.app.data.db.VocabDao
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.db.exampleList
import com.englishreader.app.data.vocab.VocabRepository
import kotlinx.coroutines.flow.Flow

data class PracticeStats(val sessions: Int, val questions: Int, val correct: Int) {
    val accuracy: Int get() = if (questions == 0) 0 else correct * 100 / questions
}

class PracticeRepository(
    private val dao: PracticeDao,
    private val vocabDao: VocabDao,
    private val vocab: VocabRepository,
) {
    private val local = LocalPracticeGenerator()

    fun observeSessions(): Flow<List<PracticeSessionEntity>> = dao.observeSessions()

    suspend fun allWords(): List<VocabWordEntity> = vocabDao.getAllAlphabetical()

    suspend fun wordsByIds(ids: List<Long>): List<VocabWordEntity> = ids.mapNotNull { vocabDao.get(it) }

    /** Picks words that need practice most: not learned, low mastery, due for review. */
    suspend fun pickWords(count: Int): List<VocabWordEntity> {
        val all = vocabDao.getAllAlphabetical()
        val now = System.currentTimeMillis()
        return all.sortedWith(compareBy<VocabWordEntity>({ it.isLearned }, { it.masteryLevel }, { if (it.nextReviewAt <= now) 0 else 1 }, { -it.dateAdded }))
            .take(count * 2).shuffled().take(count)
    }

    suspend fun story(words: List<VocabWordEntity>, level: Difficulty, aiKey: String): Story {
        if (aiKey.isNotBlank()) {
            runCatching { return GeminiClient.story(aiKey, words, level) }
        }
        return local.story(words, level, allWords())
    }

    suspend fun blanks(words: List<VocabWordEntity>, level: Difficulty): List<BlankItem> = local.blanks(words, allWords(), level)

    fun exampleFor(w: VocabWordEntity, level: Difficulty): String? = local.exampleFor(w, level)

    suspend fun checkWriting(word: VocabWordEntity, sentence: String, aiKey: String): WritingResult {
        val text = sentence.trim()
        val usesWord = WordForms.contains(text, word.word)
        val basic = ArrayList<GrammarIssue>()
        if (text.isNotEmpty() && text.first().isLowerCase()) basic += GrammarIssue("Start the sentence with a capital letter.", 0, 1, listOf(text.first().uppercase()), "Capitalization")
        if (text.isNotEmpty() && text.last() !in ".!?") basic += GrammarIssue("End the sentence with a full stop (.), question mark (?) or exclamation mark (!).", text.length, 0, listOf("."), "Punctuation")
        if (text.split(Regex("\\s+")).size < 3) basic += GrammarIssue("Write a complete sentence (at least 3 words).", 0, 0, emptyList(), "Length")

        var online = false
        val ltIssues = try { LanguageToolApi.check(text).also { online = true } } catch (e: Exception) { emptyList() }
        // Merge, avoiding duplicates of the same position.
        val issues = (ltIssues + basic.filter { b -> ltIssues.none { it.offset == b.offset && it.length == b.length } })
            .sortedBy { it.offset }
        var corrected: String? = LanguageToolApi.applyFixes(text, issues.filter { it.length > 0 || it.offset == text.length })
            .let { if (it.isNotEmpty() && it.last() !in ".!?") "$it." else it }
            .replaceFirstChar { it.uppercase() }
            .takeIf { it != text }
        var explanation: String? = null
        var alternatives = (word.exampleList + listOfNotNull(word.sourceSentence)).filter { WordForms.contains(it, word.word) }.distinct().take(2)
        if (aiKey.isNotBlank()) {
            runCatching { GeminiClient.reviewSentence(aiKey, word.word, text) }.getOrNull()?.let { (c, e, a) ->
                if (c != null && c.trim() != text) corrected = c
                explanation = e
                if (a.isNotEmpty()) alternatives = a
                online = true
            }
        }
        return WritingResult(usesWord, issues, corrected, explanation, alternatives, online)
    }

    suspend fun saveContent(type: PracticeType, title: String, body: String, words: List<VocabWordEntity>, level: Difficulty): Long =
        dao.insertContent(GeneratedContentEntity(type = type, title = title, body = body, targetWordIds = words.joinToString(",") { it.id.toString() }, difficulty = level))

    suspend fun startSession(type: PracticeType, contentId: Long? = null): PracticeSessionEntity {
        val s = PracticeSessionEntity(type = type, contentId = contentId)
        return s.copy(id = dao.insertSession(s))
    }

    /** Stores the answer and updates spaced-repetition / mastery for the word. */
    suspend fun recordAnswer(
        session: PracticeSessionEntity, wordId: Long?, prompt: String, expected: String, answer: String,
        correct: Boolean, feedback: String = "", score: Float = if (correct) 1f else 0f,
    ) {
        dao.insertResult(ExerciseResultEntity(sessionId = session.id, wordId = wordId, prompt = prompt, expectedAnswer = expected,
            userAnswer = answer, isCorrect = correct, feedback = feedback, score = score))
        if (wordId != null) vocabDao.get(wordId)?.let { vocab.recordReview(it, correct) }
    }

    suspend fun finishSession(session: PracticeSessionEntity, total: Int, correct: Int) {
        dao.updateSession(session.copy(finishedAt = System.currentTimeMillis(), totalQuestions = total, correctAnswers = correct))
    }

    companion object {
        fun stats(sessions: List<PracticeSessionEntity>): PracticeStats {
            val done = sessions.filter { it.finishedAt > 0 }
            return PracticeStats(done.size, done.sumOf { it.totalQuestions }, done.sumOf { it.correctAnswers })
        }
    }
}
