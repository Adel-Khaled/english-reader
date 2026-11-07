package com.englishreader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastOpenedAt = 0 THEN addedAt ELSE lastOpenedAt END DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT 1")
    fun observeLastOpened(): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC")
    fun observeStarted(): Flow<List<BookEntity>>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET lastParagraph = :paragraph, lastScrollOffset = :offset, progressPercent = :percent, lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun updateProgress(id: String, paragraph: Int, offset: Int, percent: Int, openedAt: Long)

    @Query("UPDATE books SET lastPage = :page, progressPercent = :percent, lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun updatePageProgress(id: String, page: Int, percent: Int, openedAt: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocabulary")
    fun observeAll(): Flow<List<VocabWordEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY word COLLATE NOCASE")
    suspend fun getAllAlphabetical(): List<VocabWordEntity>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun get(id: Long): VocabWordEntity?

    @Query("SELECT * FROM vocabulary WHERE word = :word COLLATE NOCASE LIMIT 1")
    suspend fun findByWord(word: String): VocabWordEntity?

    @Query("SELECT word FROM vocabulary")
    fun observeSavedWords(): Flow<List<String>>

    @Query("SELECT * FROM vocabulary WHERE isLearned = 0 AND nextReviewAt <= :now ORDER BY nextReviewAt, dateAdded LIMIT :limit")
    suspend fun dueForReview(now: Long, limit: Int): List<VocabWordEntity>

    @Query("SELECT COUNT(*) FROM vocabulary WHERE isLearned = 0 AND nextReviewAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary WHERE isLearned = 1")
    fun observeLearnedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: VocabWordEntity): Long

    @Update
    suspend fun update(word: VocabWordEntity)

    @Query("UPDATE vocabulary SET isLearned = :learned, updatedAt = :now WHERE id = :id")
    suspend fun setLearned(id: Long, learned: Boolean, now: Long)

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LookupCacheDao {
    @Query("SELECT * FROM lookup_cache WHERE word = :word")
    suspend fun get(word: String): LookupCacheEntity?

    @Upsert
    suspend fun upsert(entry: LookupCacheEntity)

    @Query("DELETE FROM lookup_cache")
    suspend fun clear()
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM daily_stats WHERE day = :day")
    suspend fun get(day: String): DailyStatEntity?

    @Upsert
    suspend fun upsert(stat: DailyStatEntity)

    @Query("SELECT * FROM daily_stats ORDER BY day DESC")
    fun observeAll(): Flow<List<DailyStatEntity>>
}

/** Phase 2 DAO — defined now, used by the practice module in the next update. */
@Dao
interface PracticeDao {
    @Insert
    suspend fun insertContent(content: GeneratedContentEntity): Long

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Update
    suspend fun updateSession(session: PracticeSessionEntity)

    @Insert
    suspend fun insertResult(result: ExerciseResultEntity): Long

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM exercise_results WHERE wordId = :wordId ORDER BY answeredAt DESC")
    suspend fun resultsForWord(wordId: Long): List<ExerciseResultEntity>
}
