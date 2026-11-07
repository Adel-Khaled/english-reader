package com.englishreader.app.ui.dictionary

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.VocabWordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class WordForm(
    val word: String = "",
    val translation: String = "",
    val definition: String = "",
    val phonetic: String = "",
    val examples: String = "",
    val notes: String = "",
    val learned: Boolean = false,
)

class WordEditViewModel(private val c: AppContainer, private val wordId: Long) : ViewModel() {
    val form = MutableStateFlow(WordForm())
    val original = MutableStateFlow<VocabWordEntity?>(null)
    val autoFilling = MutableStateFlow(false)
    val loaded = MutableStateFlow(wordId == 0L)

    init {
        if (wordId != 0L) viewModelScope.launch {
            c.vocab.get(wordId)?.let { w ->
                original.value = w
                form.value = WordForm(w.word, w.translationAr, w.definition, w.phonetic, w.examples, w.notes, w.isLearned)
            }
            loaded.value = true
        }
    }

    fun update(f: WordForm) { form.value = f }

    fun autoFill() {
        val word = form.value.word.trim()
        if (word.isEmpty()) return
        autoFilling.value = true
        viewModelScope.launch {
            runCatching { c.lookup.lookup(word) }.getOrNull()?.let { l ->
                val f = form.value
                form.value = f.copy(
                    translation = f.translation.ifBlank { l.translationAr },
                    definition = f.definition.ifBlank { l.definition },
                    phonetic = f.phonetic.ifBlank { l.phonetic },
                    examples = f.examples.ifBlank { l.examples.joinToString("\n") },
                )
            }
            autoFilling.value = false
        }
    }

    fun pronounce() = c.pronouncer.speak(form.value.word)

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (f.word.isBlank()) return
        viewModelScope.launch {
            val base = original.value ?: VocabWordEntity(word = f.word)
            c.vocab.saveManual(
                base.copy(
                    word = f.word, translationAr = f.translation.trim(), definition = f.definition.trim(),
                    phonetic = f.phonetic.trim(), examples = f.examples.trim(), notes = f.notes.trim(), isLearned = f.learned,
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = original.value?.id ?: return
        viewModelScope.launch { c.vocab.delete(id); onDone() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditScreen(wordId: Long, onBack: () -> Unit) {
    val vm = com.englishreader.app.ui.components.appViewModel(key = "word-$wordId") { WordEditViewModel(it, wordId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val original by vm.original.collectAsStateWithLifecycle()
    val autoFilling by vm.autoFilling.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var showError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (wordId == 0L) R.string.new_word_title else R.string.edit_word_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (form.word.isNotBlank()) IconButton(onClick = vm::pronounce) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.word_pronounce)) }
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ltr = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                val rtl = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
                OutlinedTextField(
                    value = form.word, onValueChange = { vm.update(form.copy(word = it)); showError = false },
                    label = { Text(stringResource(R.string.field_word)) }, singleLine = true, textStyle = ltr,
                    isError = showError, supportingText = { if (showError) Text(stringResource(R.string.word_required)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = vm::autoFill, enabled = form.word.isNotBlank() && !autoFilling) {
                    if (autoFilling) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoFixHigh, null)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.auto_fill))
                }
                OutlinedTextField(form.translation, { vm.update(form.copy(translation = it)) }, label = { Text(stringResource(R.string.field_translation)) }, textStyle = rtl, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.phonetic, { vm.update(form.copy(phonetic = it)) }, label = { Text(stringResource(R.string.field_phonetic)) }, singleLine = true, textStyle = ltr, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.definition, { vm.update(form.copy(definition = it)) }, label = { Text(stringResource(R.string.field_definition)) }, minLines = 2, textStyle = ltr, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.examples, { vm.update(form.copy(examples = it)) }, label = { Text(stringResource(R.string.field_examples)) }, minLines = 2, textStyle = ltr, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.notes, { vm.update(form.copy(notes = it)) }, label = { Text(stringResource(R.string.field_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_learned), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = form.learned, onCheckedChange = { vm.update(form.copy(learned = it)) })
                }
                original?.let { o ->
                    Text(
                        stringResource(R.string.dict_added_on, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.dateAdded))) +
                            (o.sourceBookTitle?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { if (form.word.isBlank()) showError = true else vm.save(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dict_delete_title)) },
            text = { Text(stringResource(R.string.dict_delete_msg, form.word)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
