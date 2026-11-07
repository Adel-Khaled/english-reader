package com.englishreader.app.data.net

import com.englishreader.app.data.catalog.CatalogBook
import com.englishreader.app.data.db.Difficulty
import org.json.JSONObject
import java.net.URLEncoder

/** Searches the full Project Gutenberg collection through the free Gutendex API. */
object GutendexApi {
    suspend fun search(query: String): List<CatalogBook> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(Http.getString("https://gutendex.com/books/?search=$q&languages=en", 20_000))
        val results = json.optJSONArray("results") ?: return emptyList()
        val list = ArrayList<CatalogBook>()
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            if (o.optBoolean("copyright", false)) continue // only public-domain works
            val id = o.optInt("id")
            val formats = o.optJSONObject("formats") ?: JSONObject()
            val hasText = formats.keys().asSequence().any { it.startsWith("text/plain") }
            if (!hasText) continue
            val authors = o.optJSONArray("authors")
            val author = if (authors != null && authors.length() > 0)
                formatAuthor(authors.getJSONObject(0).optString("name")) else "Unknown"
            val shelves = o.optJSONArray("bookshelves")
            val subjects = o.optJSONArray("subjects")
            val category = shelves?.optString(0)?.removePrefix("Browsing: ")?.takeIf { it.isNotBlank() }
                ?: subjects?.optString(0)?.substringBefore(" -- ")?.takeIf { it.isNotBlank() }
                ?: "Literature"
            val summaries = o.optJSONArray("summaries")
            val summary = summaries?.optString(0).orEmpty()
            list += CatalogBook(
                gutenbergId = id,
                title = o.optString("title").substringBefore(";").trim(),
                author = author,
                category = category,
                difficulty = Difficulty.UNKNOWN,
                description = summary,
            )
        }
        return list
    }

    /** "Austen, Jane" -> "Jane Austen" */
    fun formatAuthor(name: String): String {
        val parts = name.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts.drop(1).joinToString(" ") + " " + parts[0] else name
    }
}
