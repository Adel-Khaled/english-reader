package com.englishreader.app.ui.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.db.exampleList
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class ReviewState(
    val loading: Boolean = true,
    val words: List<VocabWordEntity> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val reviewed: Int = 0,
) {
    val current: VocabWordEntity? get() = words.getOrNull(index)
    val finished: Boolean get() = !loading && words.isNotEmpty() && index >= words.size
}

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow(ReviewState())

    init { load() }

    fun load() {
        state.value = ReviewState()
        viewModelScope.launch { state.value = ReviewState(loading = false, words = c.vocab.dueForReview(20).shuffled()) }
    }

    fun reveal() { state.value = state.value.copy(revealed = true) }

    fun answer(remembered: Boolean) {
        val s = state.value
        val w = s.current ?: return
        viewModelScope.launch { c.vocab.recordReview(w, remembered) }
        state.value = s.copy(index = s.index + 1, revealed = false, reviewed = s.reviewed + 1)
    }

    fun pronounce(w: VocabWordEntity) = c.pronouncer.speak(w.word, w.audioUrl)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReviewViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { if (s.words.isNotEmpty() && !s.finished) Text(stringResource(R.string.review_counter, s.index + 1, s.words.size), modifier = Modifier.padding(end = 16.dp)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
            when {
                s.loading -> CircularProgressIndicator()
                s.words.isEmpty() -> EmptyState(Icons.Outlined.Replay, stringResource(R.string.review_empty))
                s.finished -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(72.dp))
                    Text(stringResource(R.string.review_finished), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.review_result, s.reviewed))
                    Button(onClick = vm::load) { Text(stringResource(R.string.dict_review)) }
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.done)) }
                }
                else -> {
                    val w = s.current!!
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        LinearProgressIndicator(progress = { s.index.toFloat() / s.words.size }, modifier = Modifier.fillMaxWidth())
                        Card(Modifier.fillMaxWidth().heightIn(min = 280.dp).clickable { vm.reveal() }) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Spacer(Modifier.height(16.dp))
                                Text(w.word, style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Ltr))
                                if (w.phonetic.isNotBlank()) Text(w.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FilledTonalIconButton(onClick = { vm.pronounce(w) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.word_pronounce)) }
                                AnimatedContent(targetState = s.revealed, label = "reveal") { revealed ->
                                    if (!revealed) {
                                        Text(stringResource(R.string.review_tap_reveal), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (w.translationAr.isNotBlank()) Text(w.translationAr, style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                                            if (w.definition.isNotBlank()) Text(w.definition, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr), textAlign = TextAlign.Center)
                                            w.exampleList.firstOrNull()?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr, fontStyle = FontStyle.Italic), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                        if (s.revealed) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { vm.answer(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.review_again)) }
                                Button(onClick = { vm.answer(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.review_good)) }
                            }
                        } else {
                            Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_tap_reveal)) }
                        }
                    }
                }
            }
        }
    }
}
