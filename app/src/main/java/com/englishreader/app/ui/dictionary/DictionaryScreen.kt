package com.englishreader.app.ui.dictionary

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.R
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.export.ExportFormat
import com.englishreader.app.data.export.VocabExporter
import com.englishreader.app.ui.components.EmptyState
import com.englishreader.app.ui.components.appViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(onOpenWord: (Long) -> Unit, onReview: () -> Unit) {
    val vm = appViewModel { DictionaryViewModel(it) }
    val words by vm.words.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var exportMenu by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var pendingDelete by remember { mutableStateOf<VocabWordEntity?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { m -> snackbar.showSnackbar(if (m.arg != null) context.getString(m.res, m.arg) else context.getString(m.res)) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mime)) { uri -> uri?.let { vm.export(it, ExportFormat.PDF) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { uri -> uri?.let { vm.export(it, ExportFormat.CSV) } }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.TXT.mime)) { uri -> uri?.let { vm.export(it, ExportFormat.TXT) } }
    fun launchExport(f: ExportFormat) {
        pendingFormat = f
        val name = VocabExporter.fileName(f)
        when (f) { ExportFormat.PDF -> pdfLauncher.launch(name); ExportFormat.CSV -> csvLauncher.launch(name); ExportFormat.TXT -> txtLauncher.launch(name) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.nav_dictionary))
                        if (total > 0) Text(pluralStringResource(R.plurals.dict_count, total, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (total > 0) IconButton(onClick = onReview) { Icon(Icons.Outlined.Replay, stringResource(R.string.dict_review)) }
                    Box {
                        IconButton(onClick = { exportMenu = true }, enabled = total > 0) { Icon(Icons.Outlined.FileDownload, stringResource(R.string.dict_export)) }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.dict_export_pdf)) }, onClick = { exportMenu = false; launchExport(ExportFormat.PDF) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.dict_export_csv)) }, onClick = { exportMenu = false; launchExport(ExportFormat.CSV) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.dict_export_txt)) }, onClick = { exportMenu = false; launchExport(ExportFormat.TXT) })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenWord(0L) }) { Icon(Icons.Outlined.Add, stringResource(R.string.dict_add_word)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = q.text, onValueChange = vm::setText,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (q.text.isNotEmpty()) IconButton(onClick = { vm.setText("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } },
                    placeholder = { Text(stringResource(R.string.dict_search_hint)) },
                    singleLine = true, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(SortOrder.NEWEST to R.string.dict_sort_date, SortOrder.OLDEST to R.string.dict_sort_oldest, SortOrder.ALPHA to R.string.dict_sort_alpha).forEach { (s, l) ->
                        FilterChip(selected = q.sort == s, onClick = { vm.setSort(s) }, label = { Text(stringResource(l)) })
                    }
                    VerticalDivider(Modifier.height(24.dp))
                    listOf(LearnFilter.ALL to R.string.all, LearnFilter.LEARNING to R.string.dict_filter_learning, LearnFilter.LEARNED to R.string.dict_filter_learned).forEach { (f, l) ->
                        FilterChip(selected = q.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(l)) })
                    }
                }
                if (words.isEmpty()) {
                    EmptyState(Icons.Outlined.Translate, stringResource(if (total == 0) R.string.dict_empty else R.string.library_no_results))
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(words, key = { it.id }) { w ->
                            WordCard(
                                w,
                                onClick = { onOpenWord(w.id) },
                                onToggleLearned = { vm.toggleLearned(w) },
                                onPronounce = { vm.pronounce(w) },
                                onEdit = { onOpenWord(w.id) },
                                onDelete = { pendingDelete = w },
                            )
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dict_delete_title)) },
            text = { Text(stringResource(R.string.dict_delete_msg, w.word)) },
            confirmButton = { TextButton(onClick = { vm.delete(w); pendingDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WordCard(
    w: VocabWordEntity,
    onClick: () -> Unit,
    onToggleLearned: () -> Unit,
    onPronounce: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr))
                    if (w.phonetic.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(w.phonetic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (w.translationAr.isNotBlank()) Text(
                    w.translationAr,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(),
                )
                if (w.definition.isNotBlank()) Text(
                    w.definition, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.dict_added_on, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(w.dateAdded))) +
                        (w.sourceBookTitle?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleLearned) {
                    if (w.isLearned) Icon(Icons.Filled.CheckCircle, stringResource(R.string.dict_mark_learning), tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.CheckCircleOutline, stringResource(R.string.dict_mark_learned))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.word_pronounce)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null) }, onClick = { menu = false; onPronounce() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (w.isLearned) R.string.dict_mark_learning else R.string.dict_mark_learned)) },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircleOutline, null) }, onClick = { menu = false; onToggleLearned() },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
