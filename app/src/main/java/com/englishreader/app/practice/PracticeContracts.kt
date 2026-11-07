package com.englishreader.app.practice

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity

/*
 * Phase 2 — Personalised practice.
 *
 * These contracts are the extension points for the next update. Phase 1 does not
 * implement them. The database already contains generated_content, practice_sessions
 * and exercise_results tables, and every vocabulary row tracks review/mastery data,
 * so Phase 2 can be added as a new feature module without a destructive migration.
 */

/** Feature switch; flipped to true when Phase 2 ships. */
object PracticeFeature {
    const val ENABLED = false
}

data class GeneratedText(val title: String, val body: String, val highlightedWords: List<String>)

sealed interface Exercise {
    val targetWordId: Long?
    data class FillBlank(override val targetWordId: Long?, val sentenceWithBlank: String, val answer: String, val options: List<String>) : Exercise
    data class MultipleChoice(override val targetWordId: Long?, val question: String, val options: List<String>, val correctIndex: Int) : Exercise
    data class FreeWriting(override val targetWordId: Long?, val instruction: String) : Exercise
    data class Speaking(override val targetWordId: Long?, val targetSentence: String) : Exercise
}

data class AnswerFeedback(
    val isCorrect: Boolean,
    val correctedText: String? = null,
    val grammarExplanation: String? = null,
    val betterAlternatives: List<String> = emptyList(),
    val score: Float = if (isCorrect) 1f else 0f,
)

/** Generates sentences, paragraphs and stories from saved words (e.g. via an LLM or templates). */
interface ContentGenerator {
    suspend fun generateStory(words: List<VocabWordEntity>, level: Difficulty): GeneratedText
    suspend fun generateSentences(words: List<VocabWordEntity>, level: Difficulty): List<GeneratedText>
}

/** Builds exercises (fill-in-the-blank, multiple choice, comprehension) for a text. */
interface ExerciseGenerator {
    suspend fun exercisesFor(text: GeneratedText, words: List<VocabWordEntity>): List<Exercise>
}

/** Checks written answers and explains grammar mistakes. */
interface WritingEvaluator {
    suspend fun evaluate(exercise: Exercise, userAnswer: String): AnswerFeedback
}

/** Compares recognised speech with the target sentence. */
interface SpeechEvaluator {
    suspend fun compare(target: String, recognized: String): AnswerFeedback
}
