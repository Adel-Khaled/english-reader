package com.englishreader.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.PracticeSessionEntity
import com.englishreader.app.data.db.PracticeType
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.practice.BlankItem
import com.englishreader.app.practice.WordForms
import com.englishreader.app.practice.WritingResult
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CompleteState(val index: Int = 0, val typed: String = "", val checked: Boolean = false, val correct: Boolean = false)
data class FreeState(val index: Int = 0, val text: String = "", val checking: Boolean = false, val result: WritingResult? = null)

class WritingPracticeViewModel(private val c: AppContainer, private val ids: List<Long>) : ViewModel() {
    val tab = MutableStateFlow(0)
    val words = MutableStateFlow<List<VocabWordEntity>?>(null)
    val blanks = MutableStateFlow<List<BlankItem>>(emptyList())
    val complete = MutableStateFlow(CompleteState())
    val free = MutableStateFlow(FreeState())
    private var session: PracticeSessionEntity? = null
    private var total = 0
    private var correct = 0

    init {
        viewModelScope.launch {
            val level = c.settings.settings.first().userLevel
            val w = c.practice.wordsByIds(ids)
            blanks.value = c.practice.blanks(w, level).shuffled()
            if (blanks.value.isEmpty()) tab.value = 1
            words.value = w
        }
    }

    private suspend fun session(): PracticeSessionEntity = session ?: c.practice.startSession(PracticeType.WRITING).also { session = it }

    fun type(s: String) { complete.value = complete.value.copy(typed = s) }

    fun checkComplete() {
        val st = complete.value
        val b = blanks.value.getOrNull(st.index) ?: return
        val ok = WordForms.matchesAnswer(st.typed, b.word)
        complete.value = st.copy(checked = true, correct = ok)
        total++; if (ok) correct++
        viewModelScope.launch { c.practice.recordAnswer(session(), b.wordId, b.sentenceWithBlank, b.word, st.typed, ok) }
    }

    fun nextComplete() {
        val next = complete.value.index + 1
        complete.value = if (next >= blanks.value.size) CompleteState(index = 0).also { blanks.value = blanks.value.shuffled() } else CompleteState(index = next)
    }

    fun write(s: String) { free.value = free.value.copy(text = s, result = null) }

    fun checkFree() {
        val st = free.value
        val w = words.value?.getOrNull(st.index) ?: return
        if (st.text.isBlank()) return
        free.value = st.copy(checking = true)
        viewModelScope.launch {
            val key = c.settings.settings.first().aiApiKey
            val r = c.practice.checkWriting(w, st.text, key)
            free.value = free.value.copy(checking = false, result = r)
            total++; if (r.isCorrect) correct++
            c.practice.recordAnswer(session(), w.id, "Write a sentence using ${w.word}", w.word, st.text, r.isCorrect,
                feedback = r.issues.joinToString("\n") { it.message } + (r.explanation?.let { "\n$it" } ?: ""))
        }
    }

    fun nextFree() {
        val n = words.value?.size ?: 0
        free.value = FreeState(index = if (n == 0) 0 else (free.value.index + 1) % n)
    }

    fun speak(text: String) = c.pronouncer.speak(text)

    override fun onCleared() {
        val s = session ?: return
        c.appScopeLaunch { c.practice.finishSession(s, total, correct) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingPracticeScreen(ids: List<Long>, onBack: () -> Unit) {
    val vm = appViewModel(key = "writing-" + ids.joinToString(",")) { WritingPracticeViewModel(it, ids) }
    val tab by vm.tab.collectAsStateWithLifecycle()
    val words by vm.words.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_writing_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { vm.tab.value = 0 }, text = { Text(stringResource(R.string.writing_complete_tab)) })
                Tab(selected = tab == 1, onClick = { vm.tab.value = 1 }, text = { Text(stringResource(R.string.writing_free_tab)) })
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (words == null) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Box }
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (tab == 0) CompleteTab(vm) else FreeTab(vm)
                }
            }
        }
    }
}

@Composable
private fun CompleteTab(vm: WritingPracticeViewModel) {
    val blanks by vm.blanks.collectAsStateWithLifecycle()
    val st by vm.complete.collectAsStateWithLifecycle()
    val b = blanks.getOrNull(st.index)
    if (b == null) { EmptyState(Icons.Outlined.EditNote, stringResource(R.string.practice_no_examples)); return }
    LinearProgressIndicator(progress = { st.index.toFloat() / blanks.size }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.practice_question_of, st.index + 1, blanks.size), style = MaterialTheme.typography.labelLarge)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("“${b.sentenceWithBlank}”", style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr))
            if (b.translationAr.isNotBlank()) Text("💡 " + b.translationAr, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    OutlinedTextField(
        value = st.typed, onValueChange = vm::type, enabled = !st.checked, singleLine = true,
        label = { Text(stringResource(R.string.writing_type_answer)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!st.checked) {
        Button(onClick = vm::checkComplete, enabled = st.typed.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.writing_check)) }
    } else {
        ResultLine(st.correct, if (st.correct) stringResource(R.string.practice_correct) else stringResource(R.string.practice_wrong, b.word))
        Text("“${b.fullSentence}”", style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.speak(b.fullSentence) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.practice_listen)) }
            Button(onClick = vm::nextComplete, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.practice_next)) }
        }
    }
}

@Composable
private fun FreeTab(vm: WritingPracticeViewModel) {
    val words by vm.words.collectAsStateWithLifecycle()
    val st by vm.free.collectAsStateWithLifecycle()
    val w = words?.getOrNull(st.index) ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.word, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.speak(w.word) }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.word_pronounce)) }
            }
            if (w.translationAr.isNotBlank()) Text(w.translationAr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (w.definition.isNotBlank()) Text(w.definition.lines().first(), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
        }
    }
    Text(stringResource(R.string.writing_prompt, w.word), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = st.text, onValueChange = vm::write, minLines = 2,
        label = { Text(stringResource(R.string.writing_hint)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    val r = st.result
    when {
        st.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.writing_checking))
        }
        r == null -> Button(onClick = vm::checkFree, enabled = st.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.writing_check)) }
        else -> {
            ResultLine(r.usesWord, stringResource(if (r.usesWord) R.string.writing_uses_word else R.string.writing_missing_word, w.word))
            if (r.isCorrect) ResultLine(true, stringResource(R.string.writing_great))
            if (r.issues.isNotEmpty()) {
                Text(stringResource(R.string.writing_issues), style = MaterialTheme.typography.titleSmall)
                r.issues.forEach { issue ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (issue.length > 0 && issue.offset + issue.length <= st.text.length) {
                                Text("“" + st.text.substring(issue.offset, issue.offset + issue.length) + "”", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = WrongRed)
                            }
                            Text(issue.message, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                            if (issue.replacements.isNotEmpty()) Text(stringResource(R.string.writing_suggestion, issue.replacements.joinToString(" / ")),
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = CorrectGreen)
                        }
                    }
                }
            }
            r.corrected?.let {
                Text(stringResource(R.string.writing_corrected), style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr), color = CorrectGreen)
            }
            r.explanation?.let {
                Text(stringResource(R.string.writing_explanation), style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            }
            if (r.alternatives.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.writing_alternatives), style = MaterialTheme.typography.titleSmall)
                r.alternatives.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)) }
            }
            if (!r.checkedOnline) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.writing_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.write(st.text) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.edit)) }
                Button(onClick = vm::nextFree, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.practice_next)) }
            }
        }
    }
}

@Composable
fun ResultLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = if (ok) CorrectGreen else WrongRed)
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (ok) CorrectGreen else WrongRed, fontWeight = FontWeight.SemiBold)
    }
}
