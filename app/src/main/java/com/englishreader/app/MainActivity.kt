package com.englishreader.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.ui.navigation.AppNavHost
import com.englishreader.app.ui.theme.EnglishReaderTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as EnglishReaderApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            EnglishReaderTheme(appTheme = settings.appTheme) {
                AppNavHost()
            }
        }
    }
}
