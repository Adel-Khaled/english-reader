package com.englishreader.app.ui.progress

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.vocab.StatsRepository
import com.englishreader.app.ui.components.BookCover
import com.englishreader.app.ui.components.SectionTitle
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale

data class DayBar(val label: String, val minutes: Int)

data class ProgressState(
    val saved: Int = 0,
    val learned: Int = 0,
    val booksStarted: Int = 0,
    val lookups: Int = 0,
    val minutes: Long = 0,
    val streak: Int = 0,
    val last7: List<DayBar> = emptyList(),
    val reading: List<BookEntity> = emptyList(),
    val practice: com.englishreader.app.practice.PracticeStats = com.englishreader.app.practice.PracticeStats(0, 0, 0),
)

class ProgressViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressState> = combine(
        c.stats.observeAll(), c.vocab.observeCount(), c.vocab.observeLearnedCount(), c.books.observeStarted(), c.practice.observeSessions(),
    ) { stats, saved, learned, started, sessions ->
        val byDay = stats.associateBy { it.day }
        val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val label = SimpleDateFormat("EEE", Locale.getDefault())
        val last7 = (6 downTo 0).map { ago ->
            val d = StatsRepository.dayOffset(ago)
            DayBar(label.format(parse.parse(d)!!), ((byDay[d]?.secondsRead ?: 0) / 60).toInt())
        }
        ProgressState(
            saved = saved,
            learned = learned,
            booksStarted = started.size,
            lookups = stats.sumOf { it.wordsLookedUp },
            minutes = stats.sumOf { it.secondsRead } / 60,
            streak = StatsRepository.streak(stats),
            last7 = last7,
            reading = started.filter { it.progressPercent < 100 }.take(5),
            practice = com.englishreader.app.practice.PracticeRepository.stats(sessions),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenBook: (String) -> Unit, onSettings: () -> Unit = {}) {
    val vm = appViewModel { ProgressViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
                actions = { androidx.compose.material3.IconButton(onClick = onSettings) { Icon(androidx.compose.material.icons.Icons.Outlined.Settings, stringResource(R.string.nav_settings)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(s.saved.toString(), stringResource(R.string.progress_words_saved), Modifier.weight(1f))
                    Tile(s.learned.toString(), stringResource(R.string.progress_words_learned), Modifier.weight(1f))
                    Tile(s.streak.toString(), stringResource(R.string.progress_streak), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(s.minutes.toString(), stringResource(R.string.progress_minutes), Modifier.weight(1f))
                    Tile(s.lookups.toString(), stringResource(R.string.progress_lookups), Modifier.weight(1f))
                    Tile(s.booksStarted.toString(), stringResource(R.string.progress_books_started), Modifier.weight(1f))
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(stringResource(R.string.progress_last7))
                        WeekChart(s.last7)
                    }
                }

                if (s.saved > 0) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle(stringResource(R.string.progress_words_learned) + " · ${s.learned}/${s.saved}")
                            LinearProgressIndicator(progress = { s.learned.toFloat() / s.saved }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small))
                        }
                    }
                }

                if (s.reading.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.progress_reading))
                    s.reading.forEach { b ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenBook(b.id) }, verticalAlignment = Alignment.CenterVertically) {
                            BookCover(b.title, b.coverUrl, width = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                LinearProgressIndicator(progress = { b.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.reader_progress, b.progressPercent), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                SectionTitle(stringResource(R.string.progress_practice))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(s.practice.sessions.toString(), stringResource(R.string.progress_sessions), Modifier.weight(1f))
                    Tile(s.practice.questions.toString(), stringResource(R.string.progress_answers), Modifier.weight(1f))
                    Tile("${s.practice.accuracy}%", stringResource(R.string.progress_accuracy), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun WeekChart(bars: List<DayBar>) {
    val max = (bars.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(10)
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { b ->
                Text(b.minutes.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val bw = slot * 0.5f
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(8f, 8f))
                val h = size.height * b.minutes / max
                if (h > 0f) drawRoundRect(barColor, Offset(x, size.height - h), Size(bw, h), CornerRadius(8f, 8f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { b ->
                Text(b.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
