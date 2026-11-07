package com.englishreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.englishreader.app.data.prefs.AppTheme
import com.englishreader.app.data.prefs.ReaderTheme

val Teal = Color(0xFF1F5F5B)
val TealLight = Color(0xFF7FC4BC)
val Amber = Color(0xFFF2B544)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE6),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B0),
    onSecondaryContainer = Color(0xFF2B1800),
    tertiary = Color(0xFF4F5B92),
    tertiaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE8E3DA),
    onSurfaceVariant = Color(0xFF4B4740),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EB),
    surfaceContainer = Color(0xFFF1ECE4),
    surfaceContainerHigh = Color(0xFFEBE6DE),
    surfaceContainerHighest = Color(0xFFE5E0D8),
    outline = Color(0xFF7C776E),
    outlineVariant = Color(0xFFD6D0C6),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF13504B),
    onPrimaryContainer = Color(0xFFCDEBE6),
    secondary = Color(0xFFF2BE5C),
    onSecondary = Color(0xFF462B00),
    secondaryContainer = Color(0xFF654000),
    onSecondaryContainer = Color(0xFFFFE2B0),
    tertiary = Color(0xFFB8C3FF),
    tertiaryContainer = Color(0xFF374379),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE3E2DE),
    surface = Color(0xFF121614),
    onSurface = Color(0xFFE3E2DE),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF0D110F),
    surfaceContainerLow = Color(0xFF1A1E1C),
    surfaceContainer = Color(0xFF1E2220),
    surfaceContainerHigh = Color(0xFF282D2A),
    surfaceContainerHighest = Color(0xFF333835),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun EnglishReaderTheme(appTheme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Colours for the reading surface, independent of the app theme. */
data class ReaderColors(val background: Color, val text: Color, val highlight: Color, val saved: Color)

fun readerColors(theme: ReaderTheme): ReaderColors = when (theme) {
    ReaderTheme.LIGHT -> ReaderColors(Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFFFFE08A), Color(0xFF1F5F5B))
    ReaderTheme.SEPIA -> ReaderColors(Color(0xFFF4ECD8), Color(0xFF3B2F22), Color(0xFFF2C46B), Color(0xFF7A4B00))
    ReaderTheme.DARK -> ReaderColors(Color(0xFF151816), Color(0xFFD9D6CF), Color(0xFF6B5310), Color(0xFF7FC4BC))
}

val ReaderBodyStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp)
