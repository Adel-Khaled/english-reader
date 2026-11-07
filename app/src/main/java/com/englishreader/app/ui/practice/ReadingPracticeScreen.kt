package com.englishreader.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.PracticeSessionEntity
import com.englishreader.app.data.db.PracticeType
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.practice.ContentSource
import com.englishreader.app.practice.Story
import com.englishreader.app.practice.WordForms
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReadingPracticeViewModel(private val c: AppContainer, private val ids: List<Long>) : ViewModel() {
    val story = MutableStateFlow<Story?>(null)
    val quizMode = MutableStateFlow(false)
    val generation = MutableStateFlow(0)
    var words: List<VocabWordEntity> = emptyList(); private set
    private var session: PracticeSessionEntity? = null

    init { generate() }

    fun generate() {
        story.value = null
        quizMode.value = false
        session = null
        viewModelScope.launch {
            val s = c.settings.settings.first()
            words = c.practice.wordsByIds(ids).ifEmpty { c.practice.pickWords(5) }
            val st = c.practice.story(words, s.userLevel, s.aiApiKey)
            val contentId = runCatching {
                c.practice.saveContent(PracticeType.READING, st.title, st.paragraphs.joinToString("\n\n"), words, s.userLevel)
            }.getOrNull()
            session = c.practice.startSession(PracticeType.COMPREHENSION, contentId)
            generation.value++
            story.value = st
        }
    }

    fun listen(text: String) = c.pronouncer.speak(text)
    fun stop() = c.pronouncer.stop()

    fun answered(index: Int, chosen: Int, correct: Boolean) {
        val st = story.value ?: return
        val s = session ?: return
        val q = st.questions[index]
        val wordId = q.word?.let { w -> words.firstOrNull { it.word.equals(w, true) }?.id }
        viewModelScope.launch { c.practice.recordAnswer(s, wordId, q.prompt, q.options[q.correctIndex], q.options[chosen], correct) }
    }

    fun finished(correct: Int, total: Int) {
        val s = session ?: return
        viewModelScope.launch { c.practice.finishSession(s, total, correct) }
    }

    override fun onCleared() { c.pronouncer.stop() }
}

fun highlight(text: String, words: List<String>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (w in words) {
        var start = 0
        while (true) {
            val sub = text.substring(start)
            val r = WordForms.find(sub, w) ?: break
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, background = color.copy(alpha = 0.12f)), start + r.first, start + r.last + 1)
            start += r.last + 1
            if (start >= text.length) break
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReadingPracticeScreen(ids: List<Long>, onBack: () -> Unit) {
    val vm = appViewModel(key = "reading-" + ids.joinToString(",")) { ReadingPracticeViewModel(it, ids) }
    val story by vm.story.collectAsStateWithLifecycle()
    val quiz by vm.quizMode.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_reading_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { if (story != null) IconButton(onClick = { vm.stop(); playing = false; vm.generate() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.practice_new_story)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val st = story
            if (st == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.practice_generating))
                }
                return@Box
            }
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!quiz) {
                    Text(st.title, style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (st.source == ContentSource.AI) Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            stringResource(if (st.source == ContentSource.AI) R.string.practice_source_ai else R.string.practice_source_local),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val hl = MaterialTheme.colorScheme.primary
                            st.paragraphs.forEach { p ->
                                Text(
                                    highlight(p, st.targetWords, hl),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 1.55.em, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            if (playing) { vm.stop(); playing = false } else { vm.listen(st.title + ". " + st.paragraphs.joinToString(" ")); playing = true }
                        }) {
                            Icon(if (playing) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, null); Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (playing) R.string.practice_stop else R.string.practice_listen))
                        }
                        Button(onClick = { vm.stop(); playing = false; vm.quizMode.value = true }, enabled = st.questions.isNotEmpty()) {
                            Icon(Icons.Outlined.Quiz, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.practice_start_questions))
                        }
                    }
                    Text(stringResource(R.string.practice_target_words), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.words.forEach { w ->
                            SuggestionChip(
                                onClick = { vm.listen(w.word) },
                                label = { Text(w.word + if (w.translationAr.isNotBlank()) "  ·  " + w.translationAr.split('،', ',').first() else "") },
                            )
                        }
                    }
                } else {
                    androidx.compose.runtime.key(generation) {
                        QuizRunner(
                            questions = st.questions,
                            onAnswered = vm::answered,
                            onFinished = vm::finished,
                            finishedContent = { _, _ -> FinishButtons(onAgain = { vm.generate() }, onBack = onBack) },
                        )
                    }
                }
            }
        }
    }
}
