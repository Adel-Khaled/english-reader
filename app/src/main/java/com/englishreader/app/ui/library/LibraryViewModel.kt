package com.englishreader.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.catalog.Catalog
import com.englishreader.app.data.catalog.CatalogBook
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.net.GutendexApi
import com.englishreader.app.ui.components.UiMessage
import com.englishreader.app.ui.components.readable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Loading : OnlineSearch
    data class Results(val books: List<CatalogBook>) : OnlineSearch
    data class Error(val message: String) : OnlineSearch
}

data class LibraryFilters(val query: String = "", val category: String? = null, val difficulty: Difficulty? = null)

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val filters = MutableStateFlow(LibraryFilters())
    val online = MutableStateFlow<OnlineSearch>(OnlineSearch.Idle)
    val importing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    val categories = Catalog.categories

    val myBooks: StateFlow<List<BookEntity>> = combine(c.books.observeMyBooks(), filters) { books, f ->
        books.filter { b ->
            matches(f.query, b.title, b.author, b.category) &&
                (f.difficulty == null || b.difficulty == f.difficulty)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ownedIds: StateFlow<Set<String>> = combine(c.books.observeMyBooks(), filters) { b, _ -> b.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val featured: StateFlow<List<CatalogBook>> = combine(filters, online) { f, _ ->
        Catalog.books.filter { b ->
            matches(f.query, b.title, b.author, b.category) &&
                (f.category == null || b.category == f.category) &&
                (f.difficulty == null || b.difficulty == f.difficulty)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Catalog.books)

    private fun matches(q: String, vararg fields: String): Boolean {
        val query = q.trim()
        if (query.isEmpty()) return true
        return fields.any { it.contains(query, ignoreCase = true) }
    }

    fun setQuery(q: String) { filters.value = filters.value.copy(query = q); if (q.isBlank()) online.value = OnlineSearch.Idle }
    fun setCategory(cat: String?) { filters.value = filters.value.copy(category = cat) }
    fun setDifficulty(d: Difficulty?) { filters.value = filters.value.copy(difficulty = d) }

    fun searchOnline() {
        val q = filters.value.query.trim()
        if (q.isEmpty()) return
        online.value = OnlineSearch.Loading
        viewModelScope.launch {
            online.value = try {
                val results = GutendexApi.search(q)
                results.forEach { c.remoteBooks[it.id] = it }
                OnlineSearch.Results(results)
            } catch (e: Exception) {
                OnlineSearch.Error(e.readable())
            }
        }
    }

    fun import(uri: Uri) {
        importing.value = true
        viewModelScope.launch {
            try {
                c.books.importFromUri(uri)
                _messages.emit(UiMessage(R.string.library_import_done))
            } catch (e: Throwable) {
                _messages.emit(UiMessage(R.string.library_import_failed, e.readable()))
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(id: String) { viewModelScope.launch { c.books.delete(id) } }
}
