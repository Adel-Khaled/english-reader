package com.englishreader.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.PracticeSessionEntity
import com.englishreader.app.data.db.PracticeType
import com.englishreader.app.practice.BlankItem
import com.englishreader.app.practice.Question
import com.englishreader.app.practice.QuestionType
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FillBlanksViewModel(private val c: AppContainer, private val ids: List<Long>) : ViewModel() {
    val items = MutableStateFlow<List<BlankItem>?>(null)
    val round = MutableStateFlow(0)
    private var session: PracticeSessionEntity? = null

    init { load() }

    fun load() {
        items.value = null
        viewModelScope.launch {
            val level = c.settings.settings.first().userLevel
            val words = c.practice.wordsByIds(ids)
            val list = c.practice.blanks(words, level).shuffled()
            session = if (list.isNotEmpty()) c.practice.startSession(PracticeType.FILL_BLANK) else null
            round.value++
            items.value = list
        }
    }

    fun questions(list: List<BlankItem>): List<Question> = list.map { b ->
        Question(
            QuestionType.FILL_BLANK, "“${b.sentenceWithBlank}”", b.options, b.options.indexOf(b.word),
            listOf("“${b.fullSentence}”", listOf(b.word, b.translationAr).filter { it.isNotBlank() }.joinToString(" = "), b.definition)
                .filter { it.isNotBlank() }.joinToString("\n"),
            b.word,
        )
    }

    fun answered(index: Int, chosen: Int, correct: Boolean) {
        val s = session ?: return
        val b = items.value?.getOrNull(index) ?: return
        viewModelScope.launch { c.practice.recordAnswer(s, b.wordId, b.sentenceWithBlank, b.word, b.options[chosen], correct) }
    }

    fun finished(correct: Int, total: Int) {
        val s = session ?: return
        viewModelScope.launch { c.practice.finishSession(s, total, correct) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillBlanksScreen(ids: List<Long>, onBack: () -> Unit) {
    val vm = appViewModel(key = "blanks-" + ids.joinToString(",")) { FillBlanksViewModel(it, ids) }
    val items by vm.items.collectAsStateWithLifecycle()
    val round by vm.round.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_blanks_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val list = items
            when {
                list == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                list.isEmpty() -> EmptyState(Icons.Outlined.Checklist, stringResource(R.string.practice_no_examples))
                else -> Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.runtime.key(round) {
                        QuizRunner(
                            questions = vm.questions(list),
                            onAnswered = vm::answered,
                            onFinished = vm::finished,
                            finishedContent = { _, _ -> FinishButtons(onAgain = vm::load, onBack = onBack) },
                        )
                    }
                }
            }
        }
    }
}
