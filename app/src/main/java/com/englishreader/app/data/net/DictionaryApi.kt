package com.englishreader.app.data.net

import org.json.JSONArray
import java.net.URLEncoder

data class DictionaryResult(
    val phonetic: String,
    val audioUrl: String?,
    val partOfSpeech: String,
    val definition: String,
    val examples: List<String>,
)

/** Free Dictionary API (https://dictionaryapi.dev) — definitions, IPA, audio and examples. */
object DictionaryApi {
    /** Returns null when the word is not in the dictionary (HTTP 404). */
    suspend fun lookup(word: String): DictionaryResult? {
        val body = try {
            Http.getString("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8").replace("+", "%20"))
        } catch (e: HttpException) {
            if (e.code == 404) return null else throw e
        }
        return parse(body)
    }

    fun parse(body: String): DictionaryResult? {
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        var phonetic = ""
        var audio: String? = null
        val defs = ArrayList<Pair<String, String>>() // pos to definition
        val examples = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            if (phonetic.isEmpty()) phonetic = entry.optString("phonetic")
            entry.optJSONArray("phonetics")?.let { ph ->
                for (j in 0 until ph.length()) {
                    val p = ph.getJSONObject(j)
                    if (phonetic.isEmpty()) phonetic = p.optString("text")
                    val a = p.optString("audio")
                    if (audio == null && a.isNotBlank()) audio = if (a.startsWith("//")) "https:$a" else a
                }
            }
            entry.optJSONArray("meanings")?.let { meanings ->
                for (m in 0 until meanings.length()) {
                    val meaning = meanings.getJSONObject(m)
                    val pos = meaning.optString("partOfSpeech")
                    val d = meaning.optJSONArray("definitions") ?: continue
                    for (k in 0 until d.length()) {
                        val def = d.getJSONObject(k)
                        val text = def.optString("definition")
                        if (text.isNotBlank() && defs.size < 3) defs += pos to text
                        val ex = def.optString("example")
                        if (ex.isNotBlank() && examples.size < 4) examples += ex
                    }
                }
            }
        }
        if (defs.isEmpty()) return null
        val definition = if (defs.size == 1) defs[0].second
        else defs.mapIndexed { i, (pos, d) -> "${i + 1}. ${if (pos.isNotBlank()) "($pos) " else ""}$d" }.joinToString("\n")
        return DictionaryResult(
            phonetic = phonetic,
            audioUrl = audio,
            partOfSpeech = defs.map { it.first }.filter { it.isNotBlank() }.distinct().joinToString(", "),
            definition = definition,
            examples = examples.toList(),
        )
    }
}
