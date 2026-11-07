package com.englishreader.app.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.practice.PracticeRepository
import com.englishreader.app.practice.PracticeStats
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import com.englishreader.app.ui.components.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun wordsPerLevel(level: Difficulty) = when (level) { Difficulty.BEGINNER -> 4; Difficulty.ADVANCED -> 6; else -> 5 }

class PracticeHubViewModel(private val c: AppContainer) : ViewModel() {
    val allWords: StateFlow<List<VocabWordEntity>?> = c.vocab.observeAll().map { it.sortedBy { w -> w.word } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selected = MutableStateFlow<List<Long>>(emptyList())
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val stats: StateFlow<PracticeStats> = c.practice.observeSessions().map { PracticeRepository.stats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PracticeStats(0, 0, 0))

    fun autoPick() {
        viewModelScope.launch { selected.value = c.practice.pickWords(wordsPerLevel(settings.value.userLevel)).map { it.id } }
    }

    fun toggle(id: Long) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun setLevel(d: Difficulty) {
        viewModelScope.launch { c.settings.setUserLevel(d) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeHubScreen(onOpen: (kind: String, ids: List<Long>) -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val vm = appViewModel { PracticeHubViewModel(it) }
    val all by vm.allWords.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }

    LaunchedEffect(all?.size) {
        val words = all ?: return@LaunchedEffect
        val valid = selected.filter { id -> words.any { it.id == id } }
        if (valid.isEmpty() && words.isNotEmpty()) vm.autoPick()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_title)) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings)) } },
            )
        },
    ) { padding ->
        val words = all
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (words != null && words.isEmpty()) {
                EmptyState(Icons.Outlined.School, stringResource(R.string.practice_empty)) {
                    Button(onClick = onLibrary) { Text(stringResource(R.string.home_browse_library)) }
                }
                return@Box
            }
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.ADVANCED).forEach { d ->
                                FilterChip(selected = settings.userLevel == d, onClick = { vm.setLevel(d) }, label = { Text(stringResource(d.labelRes())) })
                            }
                        }
                        Text(stringResource(R.string.practice_words_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val chosenWords = words.orEmpty().filter { it.id in selected }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            chosenWords.forEach { w ->
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(w.word, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = vm::autoPick, label = { Text(stringResource(R.string.practice_shuffle)) }, leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp)) })
                            AssistChip(onClick = { chooser = true }, label = { Text(stringResource(R.string.practice_choose)) }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) })
                        }
                    }
                }

                val ids = selected
                ActivityCard(Icons.AutoMirrored.Outlined.MenuBook, R.string.practice_reading_title, R.string.practice_reading_desc, ids.isNotEmpty()) { onOpen("reading", ids) }
                ActivityCard(Icons.Outlined.Checklist, R.string.practice_blanks_title, R.string.practice_blanks_desc, ids.isNotEmpty()) { onOpen("blanks", ids) }
                ActivityCard(Icons.Outlined.EditNote, R.string.practice_writing_title, R.string.practice_writing_desc, ids.isNotEmpty()) { onOpen("writing", ids) }
                ActivityCard(Icons.Outlined.Mic, R.string.practice_speaking_title, R.string.practice_speaking_desc, ids.isNotEmpty()) { onOpen("speaking", ids) }

                if (stats.sessions > 0) {
                    Text(stringResource(R.string.practice_stats, stats.sessions, stats.accuracy), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }

    if (chooser) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text(stringResource(R.string.practice_choose) + " · " + stringResource(R.string.practice_selected, selected.size)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(all.orEmpty(), key = { it.id }) { w ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggle(w.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = w.id in selected, onCheckedChange = { vm.toggle(w.id) })
                            Text(w.word, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                            if (w.translationAr.isNotBlank()) Text(w.translationAr.split('،', ',').first(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooser = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun ActivityCard(icon: ImageVector, title: Int, desc: Int, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
