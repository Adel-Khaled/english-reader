package com.englishreader.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishreader.app.data.catalog.Catalog
import com.englishreader.app.data.db.BookFormat
import com.englishreader.app.data.db.BookSource
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.export.ExportFormat
import com.englishreader.app.data.export.VocabExporter
import com.englishreader.app.data.net.GutendexApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs on a real Android device/emulator: file import, PDF, network services, export. */
@RunWith(AndroidJUnit4::class)
class RepositoryDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val c = container()
    private fun dir() = File(ctx.cacheDir, "test-input").apply { mkdirs() }

    @Test fun importTxt() = runBlocking {
        val f = File(dir(), "My Story.txt")
        f.writeText("Chapter 1\n\nThe little boat sailed\nacross the calm sea.\n\nThe end.")
        val book = c.books.importFromUri(Uri.fromFile(f))
        assertEquals("My Story", book.title)
        assertEquals(BookFormat.TXT, book.format)
        assertEquals(BookSource.IMPORTED, book.source)
        val p = c.books.loadParagraphs(book)
        assertEquals(listOf("Chapter 1", "The little boat sailed across the calm sea.", "The end."), p)
        c.books.saveProgress(book.id, 1, 30, p.size)
        val reloaded = c.books.get(book.id)!!
        assertEquals(1, reloaded.lastParagraph)
        assertEquals(66, reloaded.progressPercent)
        c.books.delete(book.id)
        assertEquals(null, c.books.get(book.id))
    }

    @Test fun importEpub() = runBlocking {
        val f = File(dir(), "sample.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""")
            put("content.opf", """<package><metadata><dc:title>Epub Title</dc:title><dc:creator>Some Author</dc:creator></metadata>
                <manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/></spine></package>""")
            put("a.xhtml", "<html><body><p>Reading EPUB files works on Android.</p></body></html>")
        }
        val book = c.books.importFromUri(Uri.fromFile(f))
        assertEquals("Epub Title", book.title)
        assertEquals("Some Author", book.author)
        assertEquals(listOf("Reading EPUB files works on Android."), c.books.loadParagraphs(book))
        c.books.delete(book.id)
    }

    @Test fun importPdfWithTextLayer() = runBlocking {
        val f = File(dir(), "lesson.pdf")
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(400, 600, 1).create())
        val paint = Paint().apply { textSize = 14f }
        page.canvas.drawText("Learning English with real books", 30f, 60f, paint)
        page.canvas.drawText("is enjoyable and effective.", 30f, 80f, paint)
        doc.finishPage(page)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        val book = c.books.importFromUri(Uri.fromFile(f))
        assertEquals(BookFormat.PDF, book.format)
        assertFalse("PDF text should be extracted", book.isImageOnlyPdf)
        val text = c.books.loadParagraphs(book).joinToString(" ")
        assertTrue(text, text.contains("Learning English"))
        c.books.delete(book.id)
    }

    @Test fun downloadPublicDomainBook() = runBlocking {
        val entry = Catalog.find("pg7256")!!
        val book = c.books.downloadCatalogBook(entry, BookSource.CATALOG)
        val p = c.books.loadParagraphs(book)
        assertTrue("paragraphs=${p.size}", p.size > 10)
        assertFalse(p.joinToString(" ").contains("PROJECT GUTENBERG LICENSE"))
    }

    @Test fun searchOnlineLibrary() = runBlocking {
        val results = GutendexApi.search("sherlock holmes")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.author.contains("Doyle") })
    }

    @Test fun lookupWordOnline() = runBlocking {
        val l = c.lookup.lookup("curious")
        android.util.Log.i("LookupTest", "curious -> ar='${l.translationAr}' src=${l.translationSource} def='${l.definition}' ipa='${l.phonetic}'")
        assertTrue(l.definition.isNotBlank())
        assertTrue(l.phonetic.isNotBlank())
        // Cached now: second lookup must not need the network.
        val again = c.lookup.lookup("curious")
        assertEquals(l.definition, again.definition)
    }

    @Test fun offlineTranslationPack() = runBlocking {
        assertTrue("model download", c.offlineTranslator.download())
        val words = listOf("curious", "lighthouse", "fragile", "struggling", "harbor")
        for (w in words) {
            val ar = c.offlineTranslator.translate(w)
            android.util.Log.i("LookupTest", "offline $w -> $ar")
            assertTrue(w, !ar.isNullOrBlank())
        }
        val l = c.lookup.lookup("brave")
        android.util.Log.i("LookupTest", "combined brave -> ${l.translationAr} (${l.translationSource}) ipa=${l.phonetic}")
        Unit
    }

    @Test fun exportPdfOnDevice() {
        val words = listOf(
            VocabWordEntity(word = "brave", translationAr = "شجاع", definition = "Ready to face danger.", phonetic = "/breɪv/", examples = "She was very brave."),
            VocabWordEntity(word = "harbor", translationAr = "ميناء", definition = "A sheltered place for ships.", examples = "The boat reached the harbor."),
        )
        val out = ByteArrayOutputStream()
        VocabExporter.export(words, ExportFormat.PDF, out)
        val bytes = out.toByteArray()
        assertTrue(String(bytes, 0, 4) == "%PDF")
        // Keep a copy so CI can attach it for visual inspection.
        val f = File(ctx.getExternalFilesDir(null), "dictionary-export.pdf")
        f.writeBytes(bytes)
        shell("mkdir -p /data/local/tmp/screens")
        shell("cp ${f.absolutePath} /data/local/tmp/screens/dictionary-export.pdf")
    }
}
