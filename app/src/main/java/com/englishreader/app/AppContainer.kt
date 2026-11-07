package com.englishreader.app

import android.content.Context
import com.englishreader.app.data.books.BookRepository
import com.englishreader.app.data.books.PdfTextExtractor
import com.englishreader.app.data.db.AppDatabase
import com.englishreader.app.data.prefs.SettingsRepository
import com.englishreader.app.data.translate.OfflineTranslator
import com.englishreader.app.data.vocab.LookupRepository
import com.englishreader.app.data.vocab.StatsRepository
import com.englishreader.app.data.vocab.VocabRepository
import com.englishreader.app.util.Pronouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency injection: one place that wires the app together. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val offlineTranslator = OfflineTranslator()
    val stats = StatsRepository(database.statsDao())
    val books = BookRepository(appContext, database.bookDao(), PdfTextExtractor(appContext))
    val vocab = VocabRepository(database.vocabDao(), stats)
    val lookup = LookupRepository(database.lookupCacheDao(), offlineTranslator)
    /** Created eagerly so the speech engine is warm before the first word is tapped. */
    val pronouncer: Pronouncer = Pronouncer(appContext)
    /** Books found through online search, kept in memory so the detail screen can show them. */
    val remoteBooks = java.util.concurrent.ConcurrentHashMap<String, com.englishreader.app.data.catalog.CatalogBook>()
    /** Scope for work that must finish even if a screen is closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun appScopeLaunch(block: suspend () -> Unit) { appScope.launch { block() } }
    val practice = com.englishreader.app.practice.PracticeRepository(database.practiceDao(), database.vocabDao(), vocab)
}
