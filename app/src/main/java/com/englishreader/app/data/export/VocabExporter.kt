package com.englishreader.app.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.db.exampleList
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val mime: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    CSV("text/csv", "csv"),
    TXT("text/plain", "txt"),
}

object VocabExporter {
    private val dateFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun fileName(format: ExportFormat): String =
        "my-dictionary-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.${format.extension}"

    fun export(words: List<VocabWordEntity>, format: ExportFormat, out: OutputStream) {
        when (format) {
            ExportFormat.CSV -> out.write(toCsv(words).toByteArray(Charsets.UTF_8))
            ExportFormat.TXT -> out.write(toTxt(words).toByteArray(Charsets.UTF_8))
            ExportFormat.PDF -> writePdf(words, out)
        }
        out.flush()
    }

    fun csvEscape(value: String): String {
        val needs = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val v = value.replace("\"", "\"\"")
        return if (needs) "\"$v\"" else v
    }

    /** UTF-8 with BOM so Excel shows Arabic correctly. */
    fun toCsv(words: List<VocabWordEntity>): String = buildString {
        append('﻿')
        append("Word,Arabic,Pronunciation,Part of speech,Definition,Examples,Notes,Learned,Date added\r\n")
        for (w in words) {
            append(listOf(
                w.word, w.translationAr, w.phonetic, w.partOfSpeech, w.definition,
                w.exampleList.joinToString(" | "), w.notes, if (w.isLearned) "yes" else "no",
                dateFmt.format(Date(w.dateAdded)),
            ).joinToString(",") { csvEscape(it) })
            append("\r\n")
        }
    }

    fun toTxt(words: List<VocabWordEntity>): String = buildString {
        append("MY DICTIONARY — ${words.size} words\n")
        append("Exported ${dateFmt.format(Date())}\n")
        append("=".repeat(40)).append("\n\n")
        words.forEachIndexed { i, w ->
            append("${i + 1}. ${w.word}")
            if (w.phonetic.isNotBlank()) append("  ${w.phonetic}")
            append("\n")
            if (w.translationAr.isNotBlank()) append("   Arabic: ‏${w.translationAr}‎\n")
            if (w.definition.isNotBlank()) append("   Definition: ${w.definition.replace("\n", "\n               ")}\n")
            w.exampleList.forEach { append("   • $it\n") }
            if (w.notes.isNotBlank()) append("   Notes: ${w.notes}\n")
            append("   Added: ${dateFmt.format(Date(w.dateAdded))}${if (w.isLearned) "  ✓ learned" else ""}\n\n")
        }
    }

    private fun writePdf(words: List<VocabWordEntity>, out: OutputStream) {
        val pageW = 595; val pageH = 842; val margin = 44f
        val width = (pageW - margin * 2).toInt()
        val doc = PdfDocument()
        val accent = Color.rgb(31, 95, 91)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = accent }
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = accent }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(40, 40, 40) }
        val examplePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(90, 90, 90); typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) }
        val linePaint = Paint().apply { color = Color.rgb(225, 225, 220); strokeWidth = 0.8f }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.GRAY }

        fun layout(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(2f, 1f)
                .build()

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            page?.let { p ->
                p.canvas.drawText("English Reader · My Dictionary · ${pageNo}", margin, pageH - 24f, footerPaint)
                doc.finishPage(p)
            }
        }
        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }

        newPage()
        val header = layout("My Dictionary", titlePaint)
        val sub = layout("${words.size} words · exported ${dateFmt.format(Date())}", bodyPaint)
        page!!.canvas.apply {
            save(); translate(margin, y); header.draw(this); restore()
            y += header.height + 4
            save(); translate(margin, y); sub.draw(this); restore()
            y += sub.height + 14
        }

        for (w in words) {
            val blocks = ArrayList<StaticLayout>()
            blocks += layout(w.word + if (w.phonetic.isNotBlank()) "   ${w.phonetic}" else "", wordPaint)
            if (w.translationAr.isNotBlank()) blocks += layout(w.translationAr, arabicPaint, Layout.Alignment.ALIGN_NORMAL)
            if (w.definition.isNotBlank()) blocks += layout(w.definition, bodyPaint)
            w.exampleList.take(3).forEach { blocks += layout("• $it", examplePaint) }
            if (w.notes.isNotBlank()) blocks += layout("Notes: ${w.notes}", bodyPaint)
            val blockHeight = blocks.sumOf { it.height + 3 } + 14
            if (y + blockHeight > pageH - margin - 10 && y > margin + 1) newPage()
            for (b in blocks) {
                if (y + b.height > pageH - margin - 10) { newPage() }
                val c = page!!.canvas
                c.save(); c.translate(margin, y); b.draw(c); c.restore()
                y += b.height + 3
            }
            y += 6
            page!!.canvas.drawLine(margin, y, pageW - margin, y, linePaint)
            y += 8
        }
        finishPage()
        doc.writeTo(out)
        doc.close()
    }
}
