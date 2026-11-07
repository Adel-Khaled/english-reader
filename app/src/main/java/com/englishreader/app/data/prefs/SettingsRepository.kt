package com.englishreader.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.englishreader.app.data.db.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class ReaderTheme { LIGHT, SEPIA, DARK }

data class AppSettings(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val readerFontSize: Float = 19f,
    val readerLineSpacing: Float = 1.6f,
    val userLevel: Difficulty = Difficulty.BEGINNER,
    /** TTS voice name chosen by the user; null = automatic (female English voice). */
    val voiceName: String? = null,
    val speechRate: Float = 0.9f,
    val autoSpeak: Boolean = true,
    /** Optional free Google Gemini API key for AI stories and grammar explanations. */
    val aiApiKey: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("app_theme")
        val readerTheme = stringPreferencesKey("reader_theme")
        val fontSize = floatPreferencesKey("reader_font_size")
        val lineSpacing = floatPreferencesKey("reader_line_spacing")
        val level = stringPreferencesKey("user_level")
        val voice = stringPreferencesKey("tts_voice")
        val rate = floatPreferencesKey("tts_rate")
        val autoSpeak = booleanPreferencesKey("auto_speak")
        val aiKey = stringPreferencesKey("ai_api_key")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            appTheme = p[Keys.theme]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            readerTheme = p[Keys.readerTheme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: ReaderTheme.LIGHT,
            readerFontSize = p[Keys.fontSize] ?: 19f,
            readerLineSpacing = p[Keys.lineSpacing] ?: 1.6f,
            userLevel = p[Keys.level]?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() } ?: Difficulty.BEGINNER,
            voiceName = p[Keys.voice]?.takeIf { it.isNotBlank() },
            speechRate = p[Keys.rate] ?: 0.9f,
            autoSpeak = p[Keys.autoSpeak] ?: true,
            aiApiKey = p[Keys.aiKey].orEmpty(),
        )
    }

    suspend fun setAppTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.theme] = theme.name }
    suspend fun setReaderTheme(theme: ReaderTheme) = context.dataStore.edit { it[Keys.readerTheme] = theme.name }
    suspend fun setFontSize(size: Float) = context.dataStore.edit { it[Keys.fontSize] = size.coerceIn(12f, 34f) }
    suspend fun setLineSpacing(v: Float) = context.dataStore.edit { it[Keys.lineSpacing] = v.coerceIn(1.2f, 2.2f) }
    suspend fun setUserLevel(level: Difficulty) = context.dataStore.edit { it[Keys.level] = level.name }
    suspend fun setVoice(name: String?) = context.dataStore.edit { it[Keys.voice] = name.orEmpty() }
    suspend fun setSpeechRate(v: Float) = context.dataStore.edit { it[Keys.rate] = v.coerceIn(0.5f, 1.5f) }
    suspend fun setAutoSpeak(v: Boolean) = context.dataStore.edit { it[Keys.autoSpeak] = v }
    suspend fun setAiApiKey(key: String) = context.dataStore.edit { it[Keys.aiKey] = key.trim() }
}
