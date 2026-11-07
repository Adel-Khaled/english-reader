package com.englishreader.app.practice

/*
 * Phase 2 — Personalised practice models.
 * Everything here is plain Kotlin so it can be unit tested on the JVM.
 */

enum class QuestionType { VOCABULARY, COMPREHENSION, FILL_BLANK }
enum class ContentSource { LOCAL, AI }

data class Question(
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val word: String? = null,
)

data class Story(
    val title: String,
    val paragraphs: List<String>,
    val targetWords: List<String>,
    val questions: List<Question>,
    val source: ContentSource,
)

/** A sentence with one saved word removed. */
data class BlankItem(
    val wordId: Long,
    val word: String,
    val sentenceWithBlank: String,
    val fullSentence: String,
    val options: List<String>,
    val translationAr: String,
    val definition: String,
)

data class GrammarIssue(
    val message: String,
    val offset: Int,
    val length: Int,
    val replacements: List<String>,
    val category: String,
)

data class WritingResult(
    val usesWord: Boolean,
    val issues: List<GrammarIssue>,
    val corrected: String?,
    val explanation: String?,
    val alternatives: List<String>,
    val checkedOnline: Boolean,
) {
    val isCorrect: Boolean get() = usesWord && issues.isEmpty()
}

enum class MatchStatus { CORRECT, CLOSE, WRONG, MISSING, EXTRA }

data class WordMatch(val expected: String?, val heard: String?, val status: MatchStatus)

data class SpeechScore(val matches: List<WordMatch>, val score: Int) {
    val problemWords: List<String> get() = matches.filter { it.status == MatchStatus.WRONG || it.status == MatchStatus.MISSING || it.status == MatchStatus.CLOSE }
        .mapNotNull { it.expected }
}
