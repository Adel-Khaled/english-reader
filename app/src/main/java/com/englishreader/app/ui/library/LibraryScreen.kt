package com.englishreader.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.R
import com.englishreader.app.data.catalog.CatalogBook
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.BookSource
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.ui.components.BookCover
import com.englishreader.app.ui.components.DifficultyBadge
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import com.englishreader.app.ui.components.labelRes

private val importMimeTypes = arrayOf(
    "application/pdf", "application/epub+zip", "text/plain", "text/html", "application/xhtml+xml", "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenDetail: (String) -> Unit, onRead: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val filters by vm.filters.collectAsStateWithLifecycle()
    val featured by vm.featured.collectAsStateWithLifecycle()
    val myBooks by vm.myBooks.collectAsStateWithLifecycle()
    val owned by vm.ownedIds.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.showSnackbar(if (m.arg != null) context.getString(m.res, m.arg) else context.getString(m.res))
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.import(uri); tab = 1 }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = { picker.launch(importMimeTypes) }) {
                        Icon(Icons.Outlined.FileUpload, stringResource(R.string.library_import))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(
                onClick = { picker.launch(importMimeTypes) },
                icon = { Icon(Icons.Outlined.FileUpload, null) },
                text = { Text(stringResource(R.string.library_import)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.library_discover)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.library_my_books)) })
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())

            OutlinedTextField(
                value = filters.query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) }
                },
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); if (tab == 0) vm.searchOnline() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filters.difficulty == null, onClick = { vm.setDifficulty(null) }, label = { Text(stringResource(R.string.all)) })
                listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.ADVANCED).forEach { d ->
                    FilterChip(
                        selected = filters.difficulty == d,
                        onClick = { vm.setDifficulty(if (filters.difficulty == d) null else d) },
                        label = { Text(stringResource(d.labelRes())) },
                    )
                }
            }
            if (tab == 0) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filters.category == null, onClick = { vm.setCategory(null) }, label = { Text(stringResource(R.string.category) + ": " + stringResource(R.string.all)) })
                    vm.categories.forEach { cat ->
                        FilterChip(selected = filters.category == cat, onClick = { vm.setCategory(if (filters.category == cat) null else cat) }, label = { Text(cat) })
                    }
                }
            }

            if (tab == 0) DiscoverGrid(featured, owned, online, filters.query, onOpenDetail, onSearchOnline = { focus.clearFocus(); vm.searchOnline() })
            else MyBooksList(myBooks, onRead, onDelete = vm::delete)
        }
    }
}

@Composable
private fun DiscoverGrid(
    featured: List<CatalogBook>,
    owned: Set<String>,
    online: OnlineSearch,
    query: String,
    onOpen: (String) -> Unit,
    onSearchOnline: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.library_featured), style = MaterialTheme.typography.titleMedium)
        }
        if (featured.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.library_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(featured, key = { it.id }) { b -> BookGridItem(b, b.id in owned) { onOpen(b.id) } }

        if (query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(8.dp))
                    when (online) {
                        OnlineSearch.Idle -> OutlinedButton(onClick = onSearchOnline) {
                            Icon(Icons.Outlined.Public, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.library_search_online))
                        }
                        OnlineSearch.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.loading))
                        }
                        is OnlineSearch.Error -> Column {
                            Text(stringResource(R.string.error_generic, online.message), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onSearchOnline) { Text(stringResource(R.string.retry)) }
                        }
                        is OnlineSearch.Results -> Text(stringResource(R.string.library_online_results), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (online is OnlineSearch.Results) {
                if (online.books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.library_no_results))
                }
                items(online.books, key = { "online-" + it.id }) { b -> BookGridItem(b, b.id in owned) { onOpen(b.id) } }
            }
        }
    }
}

@Composable
private fun BookGridItem(book: CatalogBook, owned: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            BookCover(book.title, book.coverUrl, width = 120.dp)
            if (owned) Icon(
                Icons.Filled.CheckCircle, stringResource(R.string.library_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        DifficultyBadge(book.difficulty)
    }
}

@Composable
private fun MyBooksList(books: List<BookEntity>, onRead: (String) -> Unit, onDelete: (String) -> Unit) {
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }
    if (books.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Outlined.LibraryBooks, stringResource(R.string.library_empty_my_books), Modifier.padding(top = 24.dp))
    } else {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books, key = { it.id }) { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { onRead(b.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(b.title, b.coverUrl, width = 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = buildString {
                            if (b.author.isNotBlank()) append(b.author).append(" · ")
                            append(
                                when (b.source) {
                                    BookSource.IMPORTED -> stringResource(R.string.library_imported) + " " + b.format.name
                                    else -> stringResource(R.string.library_downloaded)
                                }
                            )
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DifficultyBadge(b.difficulty)
                            Spacer(Modifier.width(8.dp))
                            LinearProgressIndicator(progress = { b.progressPercent / 100f }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reader_progress, b.progressPercent), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { pendingDelete = b }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                }
            }
        }
    }
    pendingDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_msg, b.title)) },
            confirmButton = { TextButton(onClick = { onDelete(b.id); pendingDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
