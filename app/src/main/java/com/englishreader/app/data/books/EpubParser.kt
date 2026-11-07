package com.englishreader.app.data.books

import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

data class EpubContent(
    val title: String?,
    val author: String?,
    val description: String?,
    val paragraphs: List<String>,
    val coverBytes: ByteArray?,
)

/** Reads EPUB 2/3 files: container.xml → OPF → spine order → XHTML chapters. */
object EpubParser {

    fun parse(file: File): EpubContent = ZipFile(file).use { zip ->
        fun read(path: String): String? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }
        fun readBytes(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }

        val container = read("META-INF/container.xml") ?: error("Not a valid EPUB (missing container.xml)")
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: error("Not a valid EPUB (no rootfile)")
        val opf = read(opfPath) ?: error("Not a valid EPUB (missing ${opfPath})")
        val baseDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val title = tagText(opf, "dc:title")
        val author = tagText(opf, "dc:creator")
        val description = tagText(opf, "dc:description")?.let { TextProcessing.htmlToText(it).trim() }

        // manifest: id -> (href, media-type, properties)
        data class Item(val href: String, val type: String, val props: String)
        val items = HashMap<String, Item>()
        Regex("<(?:opf:)?item\\s[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).forEach { m ->
            val tag = m.value
            val id = attr(tag, "id") ?: return@forEach
            val href = attr(tag, "href") ?: return@forEach
            items[id] = Item(URLDecoder.decode(href, "UTF-8"), attr(tag, "media-type").orEmpty(), attr(tag, "properties").orEmpty())
        }
        val spine = Regex("<(?:opf:)?itemref\\s[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
            .mapNotNull { attr(it.value, "idref") }.toList()

        val paragraphs = ArrayList<String>()
        val order = spine.ifEmpty { items.filterValues { it.type.contains("html") }.keys.toList() }
        for (id in order) {
            val item = items[id] ?: continue
            if (!item.type.contains("html") && !item.href.endsWith("html", true) && !item.href.endsWith("htm", true)) continue
            val html = read(resolve(baseDir, item.href)) ?: continue
            paragraphs += TextProcessing.toParagraphs(TextProcessing.htmlToText(html))
        }

        val coverId = Regex("<meta\\s[^>]*name=\"cover\"[^>]*>", RegexOption.IGNORE_CASE).find(opf)?.let { attr(it.value, "content") }
        val coverItem = coverId?.let { items[it] } ?: items.values.firstOrNull { it.props.contains("cover-image") }
            ?: items.values.firstOrNull { it.type.startsWith("image/") && it.href.contains("cover", true) }
        val cover = coverItem?.takeIf { it.type.startsWith("image/") }?.let { readBytes(resolve(baseDir, it.href)) }

        EpubContent(title, author, description, paragraphs, cover)
    }

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        for (p in (base + href.substringBefore('#')).split('/')) {
            if (p.isEmpty() || p == ".") continue
            if (p == "..") {
                if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            } else {
                parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    private fun attr(tag: String, name: String): String? =
        Regex("(?:^|\\s)$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)

    private fun tagText(xml: String, tag: String): String? =
        Regex("<$tag(\\s[^>]*)?>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(xml)
            ?.groupValues?.get(2)?.let { TextProcessing.decodeEntities(it.replace(Regex("<[^>]+>"), "")).trim() }
            ?.takeIf { it.isNotEmpty() }
}
