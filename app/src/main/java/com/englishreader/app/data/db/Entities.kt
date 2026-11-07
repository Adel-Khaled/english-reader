package com.englishreader.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookSource { CATALOG, GUTENBERG, IMPORTED, SAMPLE }
enum class BookFormat { TXT, EPUB, PDF, HTML }
enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED, UNKNOWN }

/**
 * A book the user owns on the device (downloaded from the free library or imported).
 * Every format is normalised into a plain-text paragraph file ([contentPath]) so the
 * reader, word lookup and future Phase 2 features work the same way for all formats.
 */
@Entity(tableName = "books", indices = [Index("lastOpenedAt")])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val coverUrl: String? = null,
    val category: String = "",
    val difficulty: Difficulty = Difficulty.UNKNOWN,
    val source: BookSource,
    val format: BookFormat,
    /** Original file copied into app storage (imported PDF/EPUB/TXT). */
    val originalPath: String? = null,
    /** Normalised text: paragraphs separated by a blank line. */
    val contentPath: String? = null,
    val totalParagraphs: Int = 0,
    val lastParagraph: Int = 0,
    val lastScrollOffset: Int = 0,
    val progressPercent: Int = 0,
    /** For PDFs without a text layer (scanned) we render page images instead. */
    val isImageOnlyPdf: Boolean = false,
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0,
)

/** A word in "My Dictionary". Review/mastery columns are used by Phase 2 practice. */
@Entity(
    tableName = "vocabulary",
    indices = [Index(value = ["word"], unique = true), Index("dateAdded"), Index("isLearned")],
)
data class VocabWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val translationAr: String = "",
    val definition: String = "",
    val partOfSpeech: String = "",
    val phonetic: String = "",
    val audioUrl: String? = null,
    /** Example sentences separated by '\n'. */
    val examples: String = "",
    val notes: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isLearned: Boolean = false,
    val sourceBookId: String? = null,
    val sourceBookTitle: String? = null,
    val sourceSentence: String? = null,
    // --- spaced repetition & Phase 2 readiness ---
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val lastReviewedAt: Long = 0,
    val nextReviewAt: Long = 0,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    /** 0..5 mastery used to pick words for generated exercises. */
    val masteryLevel: Int = 0,
) {
    val exampleList: List<String> get() = examples.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Cache of every lookup so words work offline once seen. */
@Entity(tableName = "lookup_cache")
data class LookupCacheEntity(
    @PrimaryKey val word: String,
    val translationAr: String = "",
    val translationSource: String = "",
    val definition: String = "",
    val partOfSpeech: String = "",
    val phonetic: String = "",
    val audioUrl: String? = null,
    val examples: String = "",
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** One row per day (yyyy-MM-dd) for the progress screen. */
@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val day: String,
    val secondsRead: Long = 0,
    val wordsLookedUp: Int = 0,
    val wordsSaved: Int = 0,
    val wordsReviewed: Int = 0,
)

// ---------------------------------------------------------------------------
// Phase 2 tables. Created now so the update ships without a destructive
// migration. They are not used by Phase 1 UI.
// ---------------------------------------------------------------------------

enum class PracticeType { READING, FILL_BLANK, WRITING, SPEAKING, COMPREHENSION }

/** A generated sentence / paragraph / story built from saved words. */
@Entity(tableName = "generated_content", indices = [Index("createdAt")])
data class GeneratedContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PracticeType,
    val title: String = "",
    val body: String,
    /** Comma-separated vocabulary ids targeted by this content. */
    val targetWordIds: String,
    val difficulty: Difficulty = Difficulty.UNKNOWN,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A practice session (one sitting of exercises). */
@Entity(tableName = "practice_sessions", indices = [Index("startedAt")])
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PracticeType,
    val contentId: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
    val totalQuestions: Int = 0,
    val correctAnswers: Int = 0,
)

/** A single answered question inside a session. */
@Entity(tableName = "exercise_results", indices = [Index("sessionId"), Index("wordId")])
data class ExerciseResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val wordId: Long? = null,
    val prompt: String,
    val expectedAnswer: String = "",
    val userAnswer: String = "",
    val isCorrect: Boolean = false,
    /** Grammar explanation / pronunciation feedback. */
    val feedback: String = "",
    val score: Float = 0f,
    val answeredAt: Long = System.currentTimeMillis(),
)
