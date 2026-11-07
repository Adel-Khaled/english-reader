package com.englishreader.app.data.vocab

import com.englishreader.app.data.db.DailyStatEntity
import com.englishreader.app.data.db.StatsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsRepository(private val dao: StatsDao) {
    private val mutex = Mutex()

    fun observeAll(): Flow<List<DailyStatEntity>> = dao.observeAll()

    private suspend fun update(block: (DailyStatEntity) -> DailyStatEntity) = mutex.withLock {
        val d = today()
        dao.upsert(block(dao.get(d) ?: DailyStatEntity(day = d)))
    }

    suspend fun addReadingSeconds(seconds: Long) { if (seconds > 0) update { it.copy(secondsRead = it.secondsRead + seconds) } }
    suspend fun recordLookup() = update { it.copy(wordsLookedUp = it.wordsLookedUp + 1) }
    suspend fun recordWordSaved() = update { it.copy(wordsSaved = it.wordsSaved + 1) }
    suspend fun recordWordReviewed() = update { it.copy(wordsReviewed = it.wordsReviewed + 1) }

    companion object {
        private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun today(): String = fmt().format(Date())
        fun dayOffset(daysAgo: Int): String {
            val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -daysAgo); return fmt().format(c.time)
        }

        /** Consecutive days (ending today or yesterday) with any activity. */
        fun streak(stats: List<DailyStatEntity>): Int {
            val active = stats.filter { it.secondsRead >= 60 || it.wordsSaved > 0 || it.wordsReviewed > 0 || it.wordsLookedUp > 0 }
                .map { it.day }.toSet()
            var start = if (today() in active) 0 else 1
            var n = 0
            while (dayOffset(start + n) in active) n++
            return n
        }
    }
}
