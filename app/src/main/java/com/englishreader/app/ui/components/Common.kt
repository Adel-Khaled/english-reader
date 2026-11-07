package com.englishreader.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.englishreader.app.R
import com.englishreader.app.data.db.Difficulty
import kotlin.math.absoluteValue

@StringRes
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.BEGINNER -> R.string.level_beginner
    Difficulty.INTERMEDIATE -> R.string.level_intermediate
    Difficulty.ADVANCED -> R.string.level_advanced
    Difficulty.UNKNOWN -> R.string.level_unknown
}

@Composable
fun DifficultyBadge(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val (bg, fg) = when (difficulty) {
        Difficulty.BEGINNER -> Color(0xFFD7F0DD) to Color(0xFF1B5E2B)
        Difficulty.INTERMEDIATE -> Color(0xFFFFE9B8) to Color(0xFF6B4A00)
        Difficulty.ADVANCED -> Color(0xFFF8D6D2) to Color(0xFF8A1F14)
        Difficulty.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(difficulty.labelRes()),
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Book cover from URL with a generated typographic fallback (used offline / for imports). */
@Composable
fun BookCover(title: String, coverUrl: String?, modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val height = width * 1.5f
    val palette = listOf(
        Color(0xFF1F5F5B) to Color(0xFF2E8A83), Color(0xFF6B3E26) to Color(0xFFA0603A),
        Color(0xFF34406F) to Color(0xFF5363A8), Color(0xFF5B2A49) to Color(0xFF8E4374),
        Color(0xFF3F5A2A) to Color(0xFF66903F),
    )
    val (c1, c2) = palette[title.hashCode().absoluteValue % palette.size]
    val fallback: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2))).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(width / 4))
                Text(
                    title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = (width.value / 8).coerceIn(9f, 18f).sp, textAlign = TextAlign.Center,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Surface(
        modifier = modifier.size(width, height),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 3.dp,
    ) {
        if (coverUrl.isNullOrBlank()) fallback()
        else SubcomposeAsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) }
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}
