package com.englishreader.app

import com.englishreader.app.data.books.TextProcessing
import com.englishreader.app.data.db.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessingTest {

    @Test fun stripsGutenbergHeaderAndFooter() {
        val raw = """
            The Project Gutenberg eBook of Test
            licence text
            *** START OF THE PROJECT GUTENBERG EBOOK TEST ***
            Chapter One

            It was a fine day.
            *** END OF THE PROJECT GUTENBERG EBOOK TEST ***
            more licence
        """.trimIndent()
        val body = TextProcessing.stripGutenbergBoilerplate(raw)
        assertTrue(body.contains("Chapter One"))
        assertTrue(!body.contains("licence"))
        assertTrue(!body.contains("START OF"))
    }

    @Test fun joinsHardWrappedLinesIntoParagraphs() {
        val text = "It was the best of times,\nit was the worst of times.\n\n\nSecond paragraph\r\nhere."
        val p = TextProcessing.toParagraphs(text)
        assertEquals(listOf("It was the best of times, it was the worst of times.", "Second paragraph here."), p)
    }

    @Test fun splitsVeryLongParagraphsAtSentences() {
        val long = (1..400).joinToString(" ") { "This is sentence number $it." }
        val parts = TextProcessing.splitLong(long, 500)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 500 })
        assertEquals(long.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test fun findsWordUnderTap() {
        val t = "Hello, wonderful world! It's mother-in-law's day."
        fun wordAt(i: Int) = TextProcessing.wordRangeAt(t, i)?.let { t.substring(it.first, it.last + 1) }
        assertEquals("Hello", wordAt(0))
        assertEquals("Hello", wordAt(5)) // just after the word (the comma)
        assertEquals("wonderful", wordAt(10))
        assertEquals("It's", wordAt(25))
        assertEquals("mother-in-law's", wordAt(32))
        assertNull(TextProcessing.wordRangeAt(t, 6)) // space after comma
    }

    @Test fun normalizesWords() {
        assertEquals("dog", TextProcessing.normalizeWord("Dog's"))
        assertEquals("dog", TextProcessing.normalizeWord("Dog’s"))
        assertEquals("it", TextProcessing.normalizeWord("'It'"))
        assertEquals("well-known", TextProcessing.normalizeWord("Well-known"))
    }

    @Test fun extractsSentenceAroundWord() {
        val t = "First sentence here. The curious girl opened the door! Last one?"
        val r = TextProcessing.wordRangeAt(t, t.indexOf("curious"))!!
        assertEquals("The curious girl opened the door!", TextProcessing.sentenceAround(t, r))
    }

    @Test fun convertsHtmlToText() {
        val html = "<html><head><title>x</title><style>p{}</style></head><body><h1>Title</h1><p>One &amp; two&nbsp;three</p><p>Line<br/>break &#8212; dash</p></body></html>"
        val paragraphs = TextProcessing.toParagraphs(TextProcessing.htmlToText(html))
        assertEquals(listOf("Title", "One & two three", "Line break — dash"), paragraphs)
    }

    @Test fun rebuildsPdfParagraphs() {
        val lines = listOf(
            "This is a long line of text that fills the whole width of the page",
            "and continues here with another long line that also fills the page",
            "until it ends.",
            "A new paragraph starts here and it is also quite a long line of text",
            "with an exam-",
            "ple of hyphenation at the end.",
        )
        val p = TextProcessing.pdfLinesToParagraphs(lines)
        assertEquals(2, p.size)
        assertTrue(p[0].endsWith("until it ends."))
        assertTrue(p[1].contains("example of hyphenation"))
    }

    @Test fun estimatesDifficulty() {
        val easy = "The cat sat on the mat. The dog ran to the cat. They play all day. It is fun. ".repeat(20)
        val hard = ("Notwithstanding the considerable administrative complications, the institutional representatives " +
            "deliberately postponed the implementation of comprehensive organisational restructuring initiatives. ").repeat(10)
        assertEquals(Difficulty.BEGINNER, TextProcessing.estimateDifficulty(easy))
        assertEquals(Difficulty.ADVANCED, TextProcessing.estimateDifficulty(hard))
        assertEquals(Difficulty.UNKNOWN, TextProcessing.estimateDifficulty("Too short."))
    }
}
