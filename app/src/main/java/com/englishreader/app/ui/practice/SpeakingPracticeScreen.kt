package com.englishreader.app.ui.practice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.PracticeSessionEntity
import com.englishreader.app.data.db.PracticeType
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.practice.MatchStatus
import com.englishreader.app.practice.SpeechComparer
import com.englishreader.app.practice.SpeechScore
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SpeakingItem(val word: VocabWordEntity, val sentence: String?)
data class SpeakingState(val index: Int = 0, val sentenceMode: Boolean = false, val heard: String? = null, val score: SpeechScore? = null, val error: Boolean = false)

class SpeakingPracticeViewModel(private val c: AppContainer, private val ids: List<Long>) : ViewModel() {
    val items = MutableStateFlow<List<SpeakingItem>?>(null)
    val state = MutableStateFlow(SpeakingState())
    private var session: PracticeSessionEntity? = null
    private var total = 0
    private var correct = 0

    init {
        viewModelScope.launch {
            val level = c.settings.settings.first().userLevel
            items.value = c.practice.wordsByIds(ids).map { SpeakingItem(it, c.practice.exampleFor(it, level)) }
        }
    }

    fun target(): String? {
        val item = items.value?.getOrNull(state.value.index) ?: return null
        return if (state.value.sentenceMode && item.sentence != null) item.sentence else item.word.word
    }

    fun setSentenceMode(on: Boolean) { state.value = state.value.copy(sentenceMode = on, heard = null, score = null) }
    fun listen(slow: Boolean) { target()?.let { c.pronouncer.speak(it, if (slow) 0.55f else null) } }
    fun unavailable() { state.value = state.value.copy(error = true) }

    fun onRecognized(results: List<String>) {
        val t = target() ?: return
        val item = items.value?.getOrNull(state.value.index) ?: return
        val (heard, score) = SpeechComparer.best(t, results) ?: return
        state.value = state.value.copy(heard = heard, score = score, error = false)
        val ok = score.score >= 80
        total++; if (ok) correct++
        viewModelScope.launch {
            val s = session ?: c.practice.startSession(PracticeType.SPEAKING).also { session = it }
            c.practice.recordAnswer(s, item.word.id, t, t, heard, ok, feedback = "score ${score.score}", score = score.score / 100f)
        }
    }

    fun next() {
        val n = items.value?.size ?: return
        state.value = SpeakingState(index = (state.value.index + 1) % n, sentenceMode = state.value.sentenceMode)
    }

    override fun onCleared() {
        c.pronouncer.stop()
        val s = session ?: return
        c.appScopeLaunch { c.practice.finishSession(s, total, correct) }
    }
}

fun coloredSpeech(score: SpeechScore): AnnotatedString = buildAnnotatedString {
    score.matches.forEachIndexed { i, m ->
        if (i > 0) append(" ")
        when (m.status) {
            MatchStatus.CORRECT -> withStyle(SpanStyle(color = CorrectGreen, fontWeight = FontWeight.SemiBold)) { append(m.expected.orEmpty()) }
            MatchStatus.CLOSE -> withStyle(SpanStyle(color = CloseOrange, fontWeight = FontWeight.SemiBold)) { append(m.expected.orEmpty()) }
            MatchStatus.WRONG -> withStyle(SpanStyle(color = WrongRed, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) { append(m.expected.orEmpty()) }
            MatchStatus.MISSING -> withStyle(SpanStyle(color = WrongRed, textDecoration = TextDecoration.LineThrough)) { append(m.expected.orEmpty()) }
            MatchStatus.EXTRA -> withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Gray)) { append("(" + m.heard.orEmpty() + ")") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakingPracticeScreen(ids: List<Long>, onBack: () -> Unit) {
    val vm = appViewModel(key = "speaking-" + ids.joinToString(",")) { SpeakingPracticeViewModel(it, ids) }
    val items by vm.items.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val list = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if (list.isNotEmpty()) vm.onRecognized(list)
        }
    }
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, vm.target().orEmpty())
        try { recognizer.launch(intent) } catch (e: ActivityNotFoundException) { vm.unavailable() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_speaking_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val list = items
            if (list == null) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Box }
            val item = list.getOrNull(st.index) ?: return@Box
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(progress = { (st.index + 1f) / list.size }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !st.sentenceMode, onClick = { vm.setSentenceMode(false) }, label = { Text(stringResource(R.string.speaking_word_mode)) })
                    FilterChip(selected = st.sentenceMode, enabled = item.sentence != null, onClick = { vm.setSentenceMode(true) }, label = { Text(stringResource(R.string.speaking_sentence_mode)) })
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.speaking_say_this), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val target = vm.target().orEmpty()
                        val score = st.score
                        Text(
                            if (score != null) coloredSpeech(score) else AnnotatedString(target),
                            style = (if (st.sentenceMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall)
                                .copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Center),
                        )
                        if (item.word.phonetic.isNotBlank() && !st.sentenceMode) Text(item.word.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.word.translationAr.isNotBlank()) Text(item.word.translationAr, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { vm.listen(false) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.practice_listen)) }
                    OutlinedButton(onClick = { vm.listen(true) }) { Icon(Icons.Outlined.SlowMotionVideo, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.speaking_slow)) }
                }
                Spacer(Modifier.height(4.dp))
                LargeFloatingActionButton(onClick = { startListening() }) { Icon(Icons.Outlined.Mic, stringResource(R.string.speaking_mic), Modifier.size(36.dp)) }
                Text(stringResource(R.string.speaking_tap_mic), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (st.error) Text(stringResource(R.string.speaking_unavailable), color = WrongRed, textAlign = TextAlign.Center)
                st.score?.let { score ->
                    Text(stringResource(R.string.speaking_score, score.score), style = MaterialTheme.typography.headlineSmall,
                        color = when { score.score >= 90 -> CorrectGreen; score.score >= 70 -> CloseOrange; else -> WrongRed })
                    Text(stringResource(R.string.speaking_you_said, st.heard.orEmpty()), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr), textAlign = TextAlign.Center)
                    Text(stringResource(when { score.score >= 90 -> R.string.speaking_excellent; score.score >= 70 -> R.string.speaking_good; else -> R.string.speaking_try_again }), textAlign = TextAlign.Center)
                    Text(stringResource(R.string.speaking_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_next)) }
            }
        }
    }
}
