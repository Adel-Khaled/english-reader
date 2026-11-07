package com.englishreader.app.data.books

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

data class PdfContent(val title: String?, val author: String?, val paragraphs: List<String>, val pageCount: Int)

/** Extracts the text layer of a PDF so words can be tapped and translated. */
class PdfTextExtractor(private val context: Context) {
    @Volatile private var initialized = false

    fun extract(file: File): PdfContent {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        PDDocument.load(file).use { doc ->
            val pages = doc.numberOfPages
            val info = doc.documentInformation
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val paragraphs = ArrayList<String>()
            for (page in 1..pages) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(doc)
                paragraphs += TextProcessing.pdfLinesToParagraphs(text.lines())
            }
            return PdfContent(
                title = info?.title?.takeIf { it.isNotBlank() },
                author = info?.author?.takeIf { it.isNotBlank() },
                paragraphs = paragraphs,
                pageCount = pages,
            )
        }
    }
}
