package com.englishreader.app.ui.dictionary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.export.ExportFormat
import com.englishreader.app.data.export.VocabExporter
import com.englishreader.app.ui.components.UiMessage
import com.englishreader.app.ui.components.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder { NEWEST, OLDEST, ALPHA }
enum class LearnFilter { ALL, LEARNING, LEARNED }

data class DictionaryQuery(val text: String = "", val sort: SortOrder = SortOrder.NEWEST, val filter: LearnFilter = LearnFilter.ALL)

object DictionaryFilter {
    /** Pure function so it can be unit tested. */
    fun apply(words: List<VocabWordEntity>, q: DictionaryQuery): List<VocabWordEntity> {
        val t = q.text.trim()
        val filtered = words.filter { w ->
            (t.isEmpty() || w.word.contains(t, true) || w.translationAr.contains(t) || w.definition.contains(t, true) || w.notes.contains(t, true)) &&
                when (q.filter) {
                    LearnFilter.ALL -> true
                    LearnFilter.LEARNING -> !w.isLearned
                    LearnFilter.LEARNED -> w.isLearned
                }
        }
        return when (q.sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.OLDEST -> filtered.sortedBy { it.dateAdded }
            SortOrder.ALPHA -> filtered.sortedBy { it.word.lowercase() }
        }
    }
}

class DictionaryViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow(DictionaryQuery())
    private val all = c.vocab.observeAll()
    val totalCount: StateFlow<Int> = c.vocab.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val words: StateFlow<List<VocabWordEntity>> = combine(all, query) { w, q -> DictionaryFilter.apply(w, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun setText(t: String) { query.value = query.value.copy(text = t) }
    fun setSort(s: SortOrder) { query.value = query.value.copy(sort = s) }
    fun setFilter(f: LearnFilter) { query.value = query.value.copy(filter = f) }
    fun toggleLearned(w: VocabWordEntity) { viewModelScope.launch { c.vocab.setLearned(w.id, !w.isLearned) } }
    fun delete(w: VocabWordEntity) { viewModelScope.launch { c.vocab.delete(w.id) } }
    fun pronounce(w: VocabWordEntity) = c.pronouncer.speak(w.word)

    /** Exports the words currently shown (respecting search/filter) — or all if none shown. */
    fun export(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            try {
                val list = words.value.ifEmpty { c.vocab.allAlphabetical() }
                withContext(Dispatchers.IO) {
                    c.appContext.contentResolver.openOutputStream(uri, "wt")?.use { VocabExporter.export(list, format, it) }
                        ?: error("Cannot write file")
                }
                _messages.emit(UiMessage(R.string.dict_export_done, list.size))
            } catch (e: Exception) {
                _messages.emit(UiMessage(R.string.dict_export_failed, e.readable()))
            }
        }
    }
}
