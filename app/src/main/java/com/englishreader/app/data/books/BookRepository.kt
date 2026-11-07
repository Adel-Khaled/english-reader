package com.englishreader.app.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.englishreader.app.data.catalog.CatalogBook
import com.englishreader.app.data.db.BookDao
import com.englishreader.app.data.db.BookEntity
import com.englishreader.app.data.db.BookFormat
import com.englishreader.app.data.db.BookSource
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class UnsupportedFormatException(name: String) : Exception("Unsupported file type: $name")

class BookRepository(
    private val context: Context,
    private val dao: BookDao,
    private val pdfExtractor: PdfTextExtractor,
) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }

    fun observeMyBooks(): Flow<List<BookEntity>> = dao.observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = dao.observe(id)
    fun observeLastOpened(): Flow<BookEntity?> = dao.observeLastOpened()
    fun observeStarted(): Flow<List<BookEntity>> = dao.observeStarted()
    suspend fun get(id: String): BookEntity? = dao.get(id)

    private fun dirFor(id: String) = File(booksDir, id).apply { mkdirs() }

    private fun writeContent(id: String, paragraphs: List<String>): File {
        val file = File(dirFor(id), "content.txt")
        file.writeText(paragraphs.joinToString("\n\n"))
        return file
    }

    /** Loads the normalised paragraphs for the reader. */
    suspend fun loadParagraphs(book: BookEntity): List<String> = withContext(Dispatchers.IO) {
        val path = book.contentPath ?: return@withContext emptyList()
        val f = File(path)
        if (!f.exists()) return@withContext emptyList()
        f.readText().split("\n\n").filter { it.isNotBlank() }
    }

    /** Built-in short story so the reader can be tried immediately, even offline. */
    suspend fun ensureSampleBook() = withContext(Dispatchers.IO) {
        val id = "sample-lighthouse"
        if (dao.get(id) != null) return@withContext
        val text = context.assets.open("sample_story.txt").use { it.readBytes().toString(Charsets.UTF_8) }
        val paragraphs = TextProcessing.toParagraphs(text).drop(2) // drop title + subtitle lines
        val content = writeContent(id, paragraphs)
        dao.upsert(
            BookEntity(
                id = id,
                title = "The Lighthouse Keeper's Notebook",
                author = "English Reader",
                description = "A short original story for learners. Use it to try tap-to-translate.",
                category = "Short Stories",
                difficulty = Difficulty.BEGINNER,
                source = BookSource.SAMPLE,
                format = BookFormat.TXT,
                contentPath = content.absolutePath,
                totalParagraphs = paragraphs.size,
            )
        )
    }

    /** Downloads a public-domain book from Project Gutenberg as plain text. */
    suspend fun downloadCatalogBook(book: CatalogBook, source: BookSource): BookEntity = withContext(Dispatchers.IO) {
        val dir = dirFor(book.id)
        val raw = File(dir, "source.txt")
        try {
            Http.download(book.textUrl, raw)
        } catch (e: Exception) {
            Http.download(book.fallbackTextUrl, raw)
        }
        val text = TextProcessing.stripGutenbergBoilerplate(raw.readText())
        val paragraphs = TextProcessing.toParagraphs(text)
        if (paragraphs.isEmpty()) error("The downloaded book is empty")
        val content = writeContent(book.id, paragraphs)
        raw.delete()
        val existing = dao.get(book.id)
        val entity = BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            description = book.description,
            coverUrl = book.coverUrl,
            category = book.category,
            difficulty = if (book.difficulty != Difficulty.UNKNOWN) book.difficulty
                else TextProcessing.estimateDifficulty(paragraphs.take(200).joinToString(" ")),
            source = source,
            format = BookFormat.TXT,
            contentPath = content.absolutePath,
            totalParagraphs = paragraphs.size,
            lastParagraph = existing?.lastParagraph ?: 0,
            progressPercent = existing?.progressPercent ?: 0,
            lastOpenedAt = existing?.lastOpenedAt ?: 0,
        )
        dao.upsert(entity)
        entity
    }

    /** Copies a user file into private storage and converts it for the reader. */
    suspend fun importFromUri(uri: Uri): BookEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Imported book"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = resolver.getType(uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val format = when {
            ext == "pdf" || mime == "application/pdf" -> BookFormat.PDF
            ext == "epub" || mime == "application/epub+zip" -> BookFormat.EPUB
            ext in setOf("html", "htm", "xhtml") || mime.contains("html") -> BookFormat.HTML
            ext in setOf("txt", "text", "md") || mime.startsWith("text/") -> BookFormat.TXT
            else -> throw UnsupportedFormatException(name)
        }
        val id = "imp-" + UUID.randomUUID().toString().take(12)
        val dir = dirFor(id)
        val original = File(dir, "original." + format.name.lowercase())
        try {
            resolver.openInputStream(uri)?.use { input -> original.outputStream().use { input.copyTo(it) } }
                ?: error("Cannot read the selected file")

            val baseTitle = name.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "Imported book" }
            var title = baseTitle
            var author = ""
            var description = ""
            var coverPath: String? = null
            var imageOnly = false
            var pageCount = 0
            val paragraphs: List<String> = when (format) {
                BookFormat.TXT -> TextProcessing.toParagraphs(TextProcessing.stripGutenbergBoilerplate(readTextSmart(original)))
                BookFormat.HTML -> TextProcessing.toParagraphs(TextProcessing.htmlToText(readTextSmart(original)))
                BookFormat.EPUB -> {
                    val epub = EpubParser.parse(original)
                    epub.title?.let { title = it }
                    epub.author?.let { author = it }
                    epub.description?.let { description = it }
                    epub.coverBytes?.let { bytes ->
                        val f = File(dir, "cover.img"); f.writeBytes(bytes); coverPath = f.absolutePath
                    }
                    epub.paragraphs
                }
                BookFormat.PDF -> {
                    val pdf = pdfExtractor.extract(original)
                    pdf.title?.let { title = it }
                    pdf.author?.let { author = it }
                    pageCount = pdf.pageCount
                    val letters = pdf.paragraphs.sumOf { p -> p.count { it.isLetter() } }
                    if (letters < 150) imageOnly = true
                    pdf.paragraphs
                }
            }
            if (paragraphs.isEmpty() && !imageOnly) error("No readable text was found in this file")
            val content = if (paragraphs.isNotEmpty()) writeContent(id, paragraphs) else null
            val joined = paragraphs.take(200).joinToString(" ")
            val entity = BookEntity(
                id = id,
                title = title,
                author = author,
                description = description.ifEmpty { paragraphs.firstOrNull { it.length > 80 }?.take(220)?.plus("…").orEmpty() },
                coverUrl = coverPath?.let { "file://$it" },
                category = format.name,
                difficulty = TextProcessing.estimateDifficulty(joined),
                source = BookSource.IMPORTED,
                format = format,
                originalPath = original.absolutePath,
                contentPath = content?.absolutePath,
                totalParagraphs = paragraphs.size,
                isImageOnlyPdf = imageOnly,
                pageCount = pageCount,
            )
            dao.upsert(entity)
            entity
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Reads text as UTF-8, falling back to Windows-1252 for legacy files. */
    private fun readTextSmart(file: File): String {
        val bytes = file.readBytes()
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, charset("windows-1252")) else utf8.removePrefix("﻿")
    }

    suspend fun saveProgress(id: String, paragraph: Int, offset: Int, total: Int) {
        val percent = if (total <= 1) 100 else ((paragraph + 1) * 100 / total).coerceIn(0, 100)
        dao.updateProgress(id, paragraph, offset, percent, System.currentTimeMillis())
    }

    suspend fun savePageProgress(id: String, page: Int, total: Int) {
        val percent = if (total <= 1) 100 else ((page + 1) * 100 / total).coerceIn(0, 100)
        dao.updatePageProgress(id, page, percent, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(booksDir, id).deleteRecursively()
        dao.delete(id)
    }
}
