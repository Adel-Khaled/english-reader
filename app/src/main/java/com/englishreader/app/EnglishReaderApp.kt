package com.englishreader.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EnglishReaderApp : Application() {
    lateinit var container: AppContainer
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            runCatching { container.books.ensureSampleBook() }
            runCatching { container.offlineTranslator.downloadOnWifiIfMissing() }
        }
    }
}
