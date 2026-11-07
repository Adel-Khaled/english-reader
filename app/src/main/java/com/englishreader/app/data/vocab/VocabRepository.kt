package com.englishreader.app.data.vocab

import com.englishreader.app.data.db.VocabDao
import com.englishreader.app.data.db.VocabWordEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class VocabRepository(private val dao: VocabDao, private val stats: StatsRepository) {
    fun observeAll(): Flow<List<VocabWordEntity>> = dao.observeAll()
    fun observeSavedWords(): Flow<List<String>> = dao.observeSavedWords()
    fun observeCount(): Flow<Int> = dao.observeCount()
    fun observeLearnedCount(): Flow<Int> = dao.observeLearnedCount()
    fun observeDueCount(): Flow<Int> = dao.observeDueCount(System.currentTimeMillis())

    suspend fun get(id: Long) = dao.get(id)
    suspend fun findByWord(word: String) = dao.findByWord(word.trim().lowercase())
    suspend fun allAlphabetical() = dao.getAllAlphabetical()

    /** Saves a lookup result; if the word already exists it is updated instead. Returns its id. */
    suspend fun saveFromLookup(
        lookup: WordLookup,
        sourceBookId: String?,
        sourceBookTitle: String?,
        sourceSentence: String?,
    ): Long {
        val existing = dao.findByWord(lookup.word)
        val examples = buildList {
            addAll(lookup.examples)
            if (!sourceSentence.isNullOrBlank()) add(sourceSentence)
        }.distinct().joinToString("\n")
        if (existing != null) {
            dao.update(
                existing.copy(
                    translationAr = existing.translationAr.ifEmpty { lookup.translationAr },
                    definition = existing.definition.ifEmpty { lookup.definition },
                    phonetic = existing.phonetic.ifEmpty { lookup.phonetic },
                    examples = existing.examples.ifEmpty { examples },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            return existing.id
        }
        val id = dao.insert(
            VocabWordEntity(
                word = lookup.word,
                translationAr = lookup.translationAr,
                definition = lookup.definition,
                partOfSpeech = lookup.partOfSpeech,
                phonetic = lookup.phonetic,
                audioUrl = lookup.audioUrl,
                examples = examples,
                sourceBookId = sourceBookId,
                sourceBookTitle = sourceBookTitle,
                sourceSentence = sourceSentence,
            )
        )
        stats.recordWordSaved()
        return id
    }

    /** Insert or update from the edit screen. */
    suspend fun saveManual(entity: VocabWordEntity): Long {
        val clean = entity.copy(word = entity.word.trim().lowercase(), updatedAt = System.currentTimeMillis())
        return if (clean.id == 0L) {
            val dup = dao.findByWord(clean.word)
            if (dup != null) {
                dao.update(clean.copy(id = dup.id, dateAdded = dup.dateAdded)); dup.id
            } else {
                dao.insert(clean).also { stats.recordWordSaved() }
            }
        } else {
            dao.update(clean); clean.id
        }
    }

    suspend fun setLearned(id: Long, learned: Boolean) = dao.setLearned(id, learned, System.currentTimeMillis())
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun dueForReview(limit: Int = 20): List<VocabWordEntity> {
        val due = dao.dueForReview(System.currentTimeMillis(), limit)
        return due.ifEmpty { dao.dueForReview(Long.MAX_VALUE, limit) }
    }

    /** Simple SM-2 style scheduling; also feeds mastery used by Phase 2. */
    suspend fun recordReview(word: VocabWordEntity, remembered: Boolean) {
        val now = System.currentTimeMillis()
        val interval = when {
            !remembered -> 0
            word.intervalDays == 0 -> 1
            word.intervalDays == 1 -> 3
            else -> (word.intervalDays * word.easeFactor).toInt().coerceAtLeast(word.intervalDays + 1)
        }
        val ease = (word.easeFactor + if (remembered) 0.1f else -0.2f).coerceIn(1.3f, 3.0f)
        val mastery = (word.masteryLevel + if (remembered) 1 else -1).coerceIn(0, 5)
        dao.update(
            word.copy(
                reviewCount = word.reviewCount + 1,
                correctCount = word.correctCount + if (remembered) 1 else 0,
                lastReviewedAt = now,
                intervalDays = interval,
                easeFactor = ease,
                masteryLevel = mastery,
                nextReviewAt = if (interval == 0) now + TimeUnit.MINUTES.toMillis(10) else now + TimeUnit.DAYS.toMillis(interval.toLong()),
                isLearned = word.isLearned || mastery >= 5,
                updatedAt = now,
            )
        )
        stats.recordWordReviewed()
    }
}
