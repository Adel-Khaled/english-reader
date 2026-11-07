package com.englishreader.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.ui.components.BookCover
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class HomeState(
    val lastBook: BookEntity? = null,
    val bookCount: Int = 0,
    val savedCount: Int = 0,
    val learnedCount: Int = 0,
    val dueCount: Int = 0,
    val featuredWord: VocabWordEntity? = null,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        c.books.observeLastOpened(),
        c.books.observeMyBooks(),
        c.vocab.observeAll(),
        c.vocab.observeDueCount(),
    ) { last, books, words, due ->
        // Stable "word of the day" chosen from the user's own dictionary.
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val pool = words.filter { !it.isLearned }.ifEmpty { words }.sortedBy { it.id }
        HomeState(
            lastBook = last ?: books.firstOrNull(),
            bookCount = books.size,
            savedCount = words.size,
            learnedCount = words.count { it.isLearned },
            dueCount = due,
            featuredWord = if (pool.isEmpty()) null else pool[day % pool.size],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onBrowseLibrary: () -> Unit,
    onReview: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onPractice: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineSmall)

                ContinueCard(state.lastBook, onOpenBook, onBrowseLibrary)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(state.savedCount.toString(), stringResource(R.string.home_stats_saved), Modifier.weight(1f))
                    StatTile(state.learnedCount.toString(), stringResource(R.string.home_stats_learned), Modifier.weight(1f))
                    StatTile(state.bookCount.toString(), stringResource(R.string.home_stats_books), Modifier.weight(1f))
                }

                if (state.savedCount > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Replay, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pluralStringResource(R.plurals.home_review_due, state.dueCount, state.dueCount),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                            FilledTonalButton(onClick = onReview) { Text(stringResource(R.string.home_review_now)) }
                        }
                    }
                }

                if (state.savedCount > 0) {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onPractice), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.School, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.home_practice_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(stringResource(R.string.home_practice_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }

                state.featuredWord?.let { w ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenWord(w.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.home_word_of_day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(w.word, style = MaterialTheme.typography.headlineMedium)
                                if (w.phonetic.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(w.phonetic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (w.translationAr.isNotBlank()) Text(
                                w.translationAr, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl),
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(),
                            )
                            if (w.definition.isNotBlank()) Text(w.definition, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_tip), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ContinueCard(book: BookEntity?, onOpenBook: (String) -> Unit, onBrowse: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (book == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.home_no_books), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick = onBrowse) { Text(stringResource(R.string.home_browse_library)) }
            }
        } else {
            Row(Modifier.padding(16.dp).clickable { onOpenBook(book.id) }, verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.title, book.coverUrl, width = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (book.lastOpenedAt > 0) R.string.home_continue else R.string.home_start_reading),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    LinearProgressIndicator(
                        progress = { book.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    )
                    Text(stringResource(R.string.reader_progress, book.progressPercent), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
