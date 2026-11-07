package com.englishreader.app.data.books

import com.englishreader.app.data.db.Difficulty

/** Pure text utilities (no Android dependencies) — fully unit tested. */
object TextProcessing {

    private const val MAX_PARAGRAPH = 2500

    /** Removes the Project Gutenberg licence header and footer if present. */
    fun stripGutenbergBoilerplate(text: String): String {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("*** START OF") || it.trimStart().startsWith("***START OF") }
        val end = lines.indexOfLast { it.trimStart().startsWith("*** END OF") || it.trimStart().startsWith("***END OF") }
        val from = if (start >= 0) start + 1 else 0
        val to = if (end > from) end else lines.size
        return lines.subList(from, to).joinToString("\n")
    }

    /**
     * Converts hard-wrapped plain text into paragraphs. Blank lines separate paragraphs;
     * single line breaks inside a paragraph are joined with spaces.
     */
    fun toParagraphs(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
        val result = ArrayList<String>()
        normalized.split(Regex("\n[ \t]*\n+")).forEach { block ->
            val joined = block.lines().joinToString(" ") { it.trim() }
                .replace(Regex("[ \t]+"), " ")
                .trim()
            if (joined.isNotEmpty() && joined.any { it.isLetterOrDigit() }) result += splitLong(joined)
        }
        return result
    }

    /** Splits very long paragraphs at sentence boundaries so the reader stays fast. */
    fun splitLong(paragraph: String, max: Int = MAX_PARAGRAPH): List<String> {
        if (paragraph.length <= max) return listOf(paragraph)
        val parts = ArrayList<String>()
        var rest = paragraph
        while (rest.length > max) {
            val window = rest.substring(0, max)
            var cut = maxOf(window.lastIndexOf(". "), window.lastIndexOf("? "), window.lastIndexOf("! "))
            cut = if (cut > max / 3) cut + 1 else window.lastIndexOf(' ').takeIf { it > 0 } ?: max
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }

    /**
     * Rebuilds paragraphs from PDF text lines (PDFs have no blank lines between paragraphs).
     * A paragraph ends when a line finishes a sentence and is noticeably shorter than a full line.
     */
    fun pdfLinesToParagraphs(lines: List<String>): List<String> {
        val clean = lines.map { it.trim() }
        val full = clean.map { it.length }.sorted().let { if (it.isEmpty()) 0 else it[(it.size * 0.8).toInt().coerceAtMost(it.size - 1)] }
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            val p = sb.toString().replace(Regex("\\s+"), " ").trim()
            if (p.isNotEmpty() && p.any { it.isLetter() }) out += splitLong(p)
            sb.setLength(0)
        }
        for (line in clean) {
            if (line.isEmpty()) { flush(); continue }
            if (sb.isNotEmpty()) {
                if (sb.endsWith("-") && sb.length > 1 && sb[sb.length - 2].isLetter() && line.first().isLowerCase()) {
                    sb.setLength(sb.length - 1) // de-hyphenate "exam-\nple"
                } else sb.append(' ')
            }
            sb.append(line)
            val endsSentence = line.last() in ".!?:\"”’)"
            if (endsSentence && full > 0 && line.length < full * 0.75) flush()
        }
        flush()
        return out
    }

    /** Very small HTML → text converter for EPUB chapters and HTML files. */
    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article)>"), "\n\n")
        s = s.replace(Regex("(?i)<(p|div|h[1-6]|li|blockquote|tr|section|article)(\\s[^>]*)?>"), "\n\n")
        s = s.replace(Regex("(?s)<[^>]+>"), "")
        s = decodeEntities(s)
        // Inside HTML, single newlines are just whitespace; keep only our block breaks.
        return s.split(Regex("\n[ \t]*\n")).joinToString("\n\n") { it.replace(Regex("\\s+"), " ").trim() }
    }

    fun decodeEntities(s: String): String {
        var r = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
            .replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…")
            .replace("&lsquo;", "‘").replace("&rsquo;", "’").replace("&ldquo;", "“").replace("&rdquo;", "”")
        r = Regex("&#(x?)([0-9a-fA-F]+);").replace(r) { m ->
            val code = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toIntOrNull(16) else m.groupValues[2].toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        return r.replace("&amp;", "&")
    }

    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == '-'

    /** Finds the word under [offset] in [text]; returns null for spaces/punctuation. */
    fun wordRangeAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        var o = offset.coerceIn(0, text.length - 1)
        if (!text[o].isLetter()) {
            // Tapping right after a word (offset at the boundary) should still select it.
            if (o > 0 && text[o - 1].isLetter()) o -= 1 else return null
        }
        var start = o
        var end = o
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length - 1 && isWordChar(text[end + 1])) end++
        // trim leading/trailing apostrophes and hyphens
        while (start <= end && !text[start].isLetter()) start++
        while (end >= start && !text[end].isLetter()) end--
        return if (start <= end) start..end else null
    }

    /** Returns the sentence containing [range], used as a real-life example. */
    fun sentenceAround(text: String, range: IntRange): String {
        val enders = ".!?"
        var s = range.first
        while (s > 0 && text[s - 1] !in enders) s--
        var e = range.last
        while (e < text.length - 1 && text[e] !in enders) e++
        val sentence = text.substring(s, (e + 1).coerceAtMost(text.length)).trim().trimStart('"', '“', '\'', '’', ' ')
        return if (sentence.length > 300) {
            val a = (range.first - s - 120).coerceAtLeast(0)
            "…" + sentence.substring(a, (a + 240).coerceAtMost(sentence.length)).trim() + "…"
        } else sentence
    }

    /** Lower-cases and removes possessives so "Dog's" and "dogs'" look up "dog"/"dogs". */
    fun normalizeWord(raw: String): String {
        var w = raw.trim().replace('’', '\'').lowercase()
        w = w.trim('\'', '-')
        if (w.endsWith("'s")) w = w.dropLast(2)
        return w.trim('\'', '-')
    }

    fun countSyllables(word: String): Int {
        val w = word.lowercase().filter { it.isLetter() }
        if (w.isEmpty()) return 0
        var count = 0
        var prevVowel = false
        for (c in w) {
            val v = c in "aeiouy"
            if (v && !prevVowel) count++
            prevVowel = v
        }
        if (w.endsWith("e") && !w.endsWith("le") && count > 1) count--
        return count.coerceAtLeast(1)
    }

    /** Flesch Reading Ease on a sample of the text. */
    fun fleschReadingEase(text: String): Double {
        val sample = text.take(30_000)
        val sentences = sample.split(Regex("[.!?]+")).count { it.any(Char::isLetter) }.coerceAtLeast(1)
        val words = Regex("[A-Za-z']+").findAll(sample).map { it.value }.toList()
        if (words.isEmpty()) return 0.0
        val syllables = words.sumOf { countSyllables(it) }
        return 206.835 - 1.015 * (words.size.toDouble() / sentences) - 84.6 * (syllables.toDouble() / words.size)
    }

    fun estimateDifficulty(text: String): Difficulty {
        if (Regex("[A-Za-z]+").findAll(text.take(30_000)).count() < 50) return Difficulty.UNKNOWN
        val score = fleschReadingEase(text)
        return when {
            score >= 75 -> Difficulty.BEGINNER
            score >= 55 -> Difficulty.INTERMEDIATE
            else -> Difficulty.ADVANCED
        }
    }
}
