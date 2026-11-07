package com.englishreader.app

import com.englishreader.app.data.books.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun parsesSpineInOrderWithMetadataAndCover() {
        val file = tmp.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(name: String, content: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(content); z.closeEntry() }
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", """<?xml version="1.0"?><container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            put("OEBPS/content.opf", """
                <package><metadata><dc:title>My Test Book</dc:title><dc:creator>Jane Writer</dc:creator><meta name="cover" content="img"/></metadata>
                <manifest>
                  <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                  <item id="img" href="images/cover.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>
            """.trimIndent().toByteArray())
            put("OEBPS/text/ch1.xhtml", "<html><body><h2>Chapter 1</h2><p>First paragraph.</p></body></html>".toByteArray())
            put("OEBPS/text/ch2.xhtml", "<html><body><p>Second chapter text.</p></body></html>".toByteArray())
            put("OEBPS/images/cover.jpg", byteArrayOf(1, 2, 3))
        }
        val epub = EpubParser.parse(file)
        assertEquals("My Test Book", epub.title)
        assertEquals("Jane Writer", epub.author)
        assertEquals(listOf("Chapter 1", "First paragraph.", "Second chapter text."), epub.paragraphs)
        assertNotNull(epub.coverBytes)
        assertEquals(3, epub.coverBytes!!.size)
    }
}
