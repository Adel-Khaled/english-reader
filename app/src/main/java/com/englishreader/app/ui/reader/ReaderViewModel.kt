package com.englishreader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.data.books.TextProcessing
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.data.prefs.ReaderTheme
import com.englishreader.app.data.vocab.TranslationSource
import com.englishreader.app.data.vocab.WordLookup
import com.englishreader.app.ui.components.readable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ReaderContent {
    data object Loading : ReaderContent
    data class Text(val book: BookEntity, val paragraphs: List<String>) : ReaderContent
    data class PdfPages(val book: BookEntity, val file: File) : ReaderContent
    data class Error(val message: String) : ReaderContent
}

data class WordSelection(val paragraph: Int, val range: IntRange, val word: String, val sentence: String)

sealed interface LookupState {
    data object Loading : LookupState
    data class Loaded(val lookup: WordLookup) : LookupState
}

class ReaderViewModel(private val c: AppContainer, private val bookId: String) : ViewModel() {
    val content = MutableStateFlow<ReaderContent>(ReaderContent.Loading)
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val savedWords: StateFlow<Set<String>> = c.vocab.observeSavedWords().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val selection = MutableStateFlow<WordSelection?>(null)
    val lookup = MutableStateFlow<LookupState>(LookupState.Loading)
    val sentenceTranslation = MutableStateFlow<String?>(null)
    val sentenceLoading = MutableStateFlow(false)
    private var lookupJob: Job? = null
    private var sessionStart = 0L

    init {
        viewModelScope.launch {
            try {
                val book = c.books.get(bookId) ?: error("Book not found")
                content.value = if (book.isImageOnlyPdf && book.originalPath != null) {
                    ReaderContent.PdfPages(book, File(book.originalPath))
                } else {
                    val paragraphs = c.books.loadParagraphs(book)
                    if (paragraphs.isEmpty()) error("This book has no readable text")
                    ReaderContent.Text(book, paragraphs)
                }
                // Mark as opened so it shows under "Continue reading".
                if (book.isImageOnlyPdf) c.books.savePageProgress(book.id, book.lastPage, book.pageCount)
                else c.books.saveProgress(book.id, book.lastParagraph, book.lastScrollOffset, book.totalParagraphs)
            } catch (e: Exception) {
                content.value = ReaderContent.Error(e.readable())
            }
        }
    }

    fun onTap(paragraphIndex: Int, text: String, offset: Int) {
        val range = TextProcessing.wordRangeAt(text, offset) ?: return
        val raw = text.substring(range.first, range.last + 1)
        val word = TextProcessing.normalizeWord(raw)
        if (word.isEmpty() || word.none { it.isLetter() }) return
        selection.value = WordSelection(paragraphIndex, range, word, TextProcessing.sentenceAround(text, range))
        sentenceTranslation.value = null
        lookup.value = LookupState.Loading
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            c.stats.recordLookup()
            val result = try {
                c.lookup.lookup(word)
            } catch (e: Exception) {
                WordLookup(word, "", TranslationSource.NONE, "", "", "", null, emptyList(), offline = true)
            }
            if (selection.value?.word == word) lookup.value = LookupState.Loaded(result)
        }
    }

    fun clearSelection() {
        lookupJob?.cancel()
        selection.value = null
        sentenceTranslation.value = null
    }

    fun saveSelected() {
        val sel = selection.value ?: return
        val l = (lookup.value as? LookupState.Loaded)?.lookup
            ?: WordLookup(sel.word, "", TranslationSource.NONE, "", "", "", null, emptyList(), offline = true)
        val book = (content.value as? ReaderContent.Text)?.book
        viewModelScope.launch { c.vocab.saveFromLookup(l, book?.id, book?.title, sel.sentence) }
    }

    fun pronounce(word: String, audioUrl: String?) = c.pronouncer.speak(word, audioUrl)

    fun translateSentence() {
        val sel = selection.value ?: return
        sentenceLoading.value = true
        viewModelScope.launch {
            sentenceTranslation.value = c.lookup.translateSentence(sel.sentence) ?: ""
            sentenceLoading.value = false
        }
    }

    fun saveProgress(paragraph: Int, offset: Int, total: Int) {
        c.appScopeLaunch { c.books.saveProgress(bookId, paragraph, offset, total) }
    }

    fun savePage(page: Int, total: Int) {
        c.appScopeLaunch { c.books.savePageProgress(bookId, page, total) }
    }

    fun setFontSize(v: Float) { viewModelScope.launch { c.settings.setFontSize(v) } }
    fun setLineSpacing(v: Float) { viewModelScope.launch { c.settings.setLineSpacing(v) } }
    fun setReaderTheme(t: ReaderTheme) { viewModelScope.launch { c.settings.setReaderTheme(t) } }

    fun onResume() { sessionStart = System.currentTimeMillis() }
    fun onPause() {
        if (sessionStart == 0L) return
        val seconds = (System.currentTimeMillis() - sessionStart) / 1000
        sessionStart = 0L
        // Cap a single session at 3 hours to ignore a device left open.
        c.appScopeLaunch { c.stats.addReadingSeconds(seconds.coerceAtMost(3 * 3600)) }
    }

    override fun onCleared() { onPause() }
}
