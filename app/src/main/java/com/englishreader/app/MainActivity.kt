package com.englishreader.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.data.prefs.AppTheme
import com.englishreader.app.ui.navigation.AppNavHost
import com.englishreader.app.ui.theme.EnglishReaderTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as EnglishReaderApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.appTheme) {
                AppTheme.SYSTEM -> systemDark
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            // Keep status/navigation bar icons readable when the in-app theme differs from the system.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)) { dark },
                )
                onDispose {}
            }
            EnglishReaderTheme(appTheme = settings.appTheme) {
                AppNavHost()
            }
        }
    }
}
