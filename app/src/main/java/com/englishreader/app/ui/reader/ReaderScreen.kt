package com.englishreader.app.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.R
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.data.prefs.ReaderTheme
import com.englishreader.app.ui.components.appViewModel
import com.englishreader.app.ui.theme.ReaderBodyStyle
import com.englishreader.app.ui.theme.ReaderColors
import com.englishreader.app.ui.theme.readerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val wordRegex = Regex("[A-Za-z][A-Za-z'’-]*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "reader-$bookId") { ReaderViewModel(it, bookId) }
    val content by vm.content.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved by vm.savedWords.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val colors = readerColors(settings.readerTheme)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); vm.onPause() }
    }

    val title = when (val c = content) {
        is ReaderContent.Text -> c.book.title
        is ReaderContent.PdfPages -> c.book.title
        else -> ""
    }
    var percent by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.TextFields, stringResource(R.string.reader_settings)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background, titleContentColor = colors.text,
                    navigationIconContentColor = colors.text, actionIconContentColor = colors.text,
                ),
            )
        },
        bottomBar = {
            if (content is ReaderContent.Text || content is ReaderContent.PdfPages) {
                Row(
                    Modifier.fillMaxWidth().background(colors.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.weight(1f), color = colors.saved, trackColor = colors.text.copy(alpha = 0.12f))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.reader_progress, percent), color = colors.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(colors.background), contentAlignment = Alignment.TopCenter) {
            when (val c = content) {
                ReaderContent.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.saved)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.reader_preparing), color = colors.text)
                }
                is ReaderContent.Error -> Text(
                    stringResource(R.string.reader_open_failed, c.message), color = colors.text,
                    modifier = Modifier.padding(32.dp), textAlign = TextAlign.Center,
                )
                is ReaderContent.Text -> TextReader(
                    paragraphs = c.paragraphs,
                    initialIndex = c.book.lastParagraph,
                    initialOffset = c.book.lastScrollOffset,
                    settings = settings,
                    colors = colors,
                    savedWords = saved,
                    selection = selection,
                    onTap = vm::onTap,
                    onProgress = { i, o -> vm.saveProgress(i, o, c.paragraphs.size) },
                    onPercent = { percent = it },
                )
                is ReaderContent.PdfPages -> PdfPagesReader(
                    file = c.file,
                    initialPage = c.book.lastPage,
                    onPage = { page, total ->
                        vm.savePage(page, total)
                        percent = if (total <= 1) 100 else ((page + 1) * 100 / total)
                    },
                    textColor = colors.text,
                )
            }
        }
    }

    if (selection != null) {
        val lookup by vm.lookup.collectAsStateWithLifecycle()
        val sentenceTr by vm.sentenceTranslation.collectAsStateWithLifecycle()
        val sentenceLoading by vm.sentenceLoading.collectAsStateWithLifecycle()
        val sel = selection!!
        WordSheet(
            selection = sel,
            lookup = lookup,
            isSaved = sel.word in saved,
            sentenceTranslation = sentenceTr,
            sentenceLoading = sentenceLoading,
            onDismiss = vm::clearSelection,
            onSave = vm::saveSelected,
            onPronounce = vm::pronounce,
            onTranslateSentence = vm::translateSentence,
        )
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ReaderSettingsPanel(settings, vm::setFontSize, vm::setLineSpacing, vm::setReaderTheme)
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun TextReader(
    paragraphs: List<String>,
    initialIndex: Int,
    initialOffset: Int,
    settings: AppSettings,
    colors: ReaderColors,
    savedWords: Set<String>,
    selection: WordSelection?,
    onTap: (Int, String, Int) -> Unit,
    onProgress: (Int, Int) -> Unit,
    onPercent: (Int) -> Unit,
) {
    val listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnPercent by rememberUpdatedState(onPercent)
    val percent by remember(paragraphs) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex
            if (paragraphs.size <= 1) 100 else ((lastVisible + 1) * 100 / paragraphs.size).coerceIn(0, 100)
        }
    }
    LaunchedEffect(percent) { currentOnPercent(percent) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(700)
            .collect { (i, o) -> currentOnProgress(i, o) }
    }
    DisposableEffect(Unit) {
        onDispose { currentOnProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    val style = ReaderBodyStyle.copy(
        fontSize = settings.readerFontSize.sp,
        lineHeight = settings.readerLineSpacing.em,
        color = colors.text,
        textDirection = TextDirection.Ltr,
        textAlign = TextAlign.Left,
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        itemsIndexed(paragraphs) { index, text ->
            TappableParagraph(
                text = text,
                style = style,
                colors = colors,
                savedWords = savedWords,
                selectedRange = selection?.takeIf { it.paragraph == index }?.range,
                onTap = { offset -> onTap(index, text, offset) },
            )
        }
    }
}

@Composable
private fun TappableParagraph(
    text: String,
    style: TextStyle,
    colors: ReaderColors,
    savedWords: Set<String>,
    selectedRange: IntRange?,
    onTap: (Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap by rememberUpdatedState(onTap)
    val annotated: AnnotatedString = remember(text, savedWords, selectedRange, colors) {
        buildAnnotatedString {
            append(text)
            if (savedWords.isNotEmpty()) {
                wordRegex.findAll(text).forEach { m ->
                    val w = m.value.lowercase().replace('’', '\'').removeSuffix("'s").trim('\'', '-')
                    if (w in savedWords) addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline, color = colors.saved),
                        m.range.first, m.range.last + 1,
                    )
                }
            }
            if (selectedRange != null && selectedRange.last < text.length) {
                addStyle(SpanStyle(background = colors.highlight), selectedRange.first, selectedRange.last + 1)
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(text) {
                detectTapGestures { pos -> layout?.let { tap(it.getOffsetForPosition(pos)) } }
            },
    )
}

@Composable
private fun ReaderSettingsPanel(
    settings: AppSettings,
    onFont: (Float) -> Unit,
    onSpacing: (Float) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.reader_font_size) + " · ${settings.readerFontSize.toInt()}", style = MaterialTheme.typography.labelLarge)
        Slider(value = settings.readerFontSize, onValueChange = onFont, valueRange = 12f..34f, steps = 21)
        Text(stringResource(R.string.reader_line_spacing) + " · ${"%.1f".format(settings.readerLineSpacing)}", style = MaterialTheme.typography.labelLarge)
        Slider(value = settings.readerLineSpacing, onValueChange = onSpacing, valueRange = 1.2f..2.2f, steps = 9)
        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReaderTheme.LIGHT to R.string.reader_theme_light,
                ReaderTheme.SEPIA to R.string.reader_theme_sepia,
                ReaderTheme.DARK to R.string.reader_theme_dark,
            ).forEach { (t, label) ->
                FilterChip(selected = settings.readerTheme == t, onClick = { onTheme(t) }, label = { Text(stringResource(label)) })
            }
        }
        val preview = readerColors(settings.readerTheme)
        Text(
            "The quick brown fox jumps over the lazy dog.",
            style = ReaderBodyStyle.copy(fontSize = settings.readerFontSize.sp, lineHeight = settings.readerLineSpacing.em, color = preview.text, textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth().background(preview.background, MaterialTheme.shapes.medium).padding(16.dp),
        )
    }
}

/** Fallback for scanned PDFs: render each page as an image. */
@Composable
private fun PdfPagesReader(file: File, initialPage: Int, onPage: (Int, Int) -> Unit, textColor: androidx.compose.ui.graphics.Color) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    val mutex = remember { Mutex() }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        Text(stringResource(R.string.reader_open_failed, file.name), color = textColor, modifier = Modifier.padding(32.dp))
        return
    }
    val count = renderer.pageCount
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (count - 1).coerceAtLeast(0)))
    val currentOnPage by rememberUpdatedState(onPage)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { currentOnPage(it, count) }
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.reader_scanned_pdf), color = textColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.coerceIn(400, 1400)
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
                items(count) { i ->
                    val bitmap by produceState<Bitmap?>(initialValue = null, i, widthPx) {
                        value = withContext(Dispatchers.IO) {
                            mutex.withLock {
                                runCatching {
                                    renderer.openPage(i).use { page ->
                                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        bmp
                                    }
                                }.getOrNull()
                            }
                        }
                    }
                    val b = bitmap
                    if (b != null) {
                        Image(
                            b.asImageBitmap(), stringResource(R.string.reader_page, i + 1, count),
                            modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
