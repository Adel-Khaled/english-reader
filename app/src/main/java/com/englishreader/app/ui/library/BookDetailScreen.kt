package com.englishreader.app.ui.library

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.englishreader.app.AppContainer
import com.englishreader.app.R
import com.englishreader.app.data.catalog.Catalog
import com.englishreader.app.data.catalog.CatalogBook
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.BookSource
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.ui.components.BookCover
import com.englishreader.app.ui.components.DifficultyBadge
import com.englishreader.app.ui.components.appViewModel
import com.englishreader.app.ui.components.readable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Running : DownloadState
    data class Failed(val message: String) : DownloadState
}

class BookDetailViewModel(private val c: AppContainer, val bookId: String) : ViewModel() {
    val catalogBook: CatalogBook? = Catalog.find(bookId) ?: c.remoteBooks[bookId]
    val owned: StateFlow<BookEntity?> = c.books.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val download = MutableStateFlow<DownloadState>(DownloadState.Idle)

    fun downloadThen(onReady: (String) -> Unit) {
        val book = catalogBook ?: return
        download.value = DownloadState.Running
        viewModelScope.launch {
            try {
                val source = if (Catalog.find(book.id) != null) BookSource.CATALOG else BookSource.GUTENBERG
                val entity = c.books.downloadCatalogBook(book, source)
                download.value = DownloadState.Idle
                onReady(entity.id)
            } catch (e: Exception) {
                download.value = DownloadState.Failed(e.readable())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(bookId: String, onBack: () -> Unit, onRead: (String) -> Unit) {
    val vm = appViewModel(key = "detail-$bookId") { BookDetailViewModel(it, bookId) }
    val owned by vm.owned.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val catalog = vm.catalogBook

    val title = owned?.title ?: catalog?.title
    val author = owned?.author ?: catalog?.author.orEmpty()
    val description = owned?.description?.takeIf { it.isNotBlank() } ?: catalog?.description.orEmpty()
    val cover = owned?.coverUrl ?: catalog?.coverUrl
    val difficulty = owned?.difficulty?.takeIf { it != Difficulty.UNKNOWN } ?: catalog?.difficulty ?: Difficulty.UNKNOWN
    val category = owned?.category ?: catalog?.category.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (title == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.book_not_found)) }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(title, cover, width = 160.dp)
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (author.isNotBlank()) Text(stringResource(R.string.book_by, author), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DifficultyBadge(difficulty)
                    if (category.isNotBlank()) Text(category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val ownedBook = owned
                when {
                    ownedBook != null -> Button(onClick = { onRead(ownedBook.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null); Spacer(Modifier.width(8.dp))
                        Text(if (ownedBook.progressPercent > 0) stringResource(R.string.book_continue, ownedBook.progressPercent) else stringResource(R.string.book_read))
                    }
                    download is DownloadState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.book_downloading))
                    }
                    else -> Button(onClick = { vm.downloadThen(onRead) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.book_download_read))
                    }
                }
                (download as? DownloadState.Failed)?.let {
                    Text(stringResource(R.string.book_download_failed, it.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }

                Text(
                    description.ifBlank { stringResource(R.string.book_no_description) },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (catalog != null) Text(stringResource(R.string.book_public_domain), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
