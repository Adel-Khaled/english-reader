package com.englishreader.app.practice

import com.englishreader.app.data.net.Http
import org.json.JSONObject

/** Free grammar & spelling checker (https://languagetool.org public API, no key needed). */
object LanguageToolApi {
    suspend fun check(text: String): List<GrammarIssue> {
        val body = Http.postForm(
            "https://api.languagetool.org/v2/check",
            mapOf("text" to text, "language" to "en-US", "enabledOnly" to "false"),
            20_000,
        )
        return parse(body)
    }

    fun parse(body: String): List<GrammarIssue> {
        val matches = JSONObject(body).optJSONArray("matches") ?: return emptyList()
        val out = ArrayList<GrammarIssue>()
        for (i in 0 until matches.length()) {
            val m = matches.getJSONObject(i)
            val reps = m.optJSONArray("replacements")
            val replacements = (0 until (reps?.length() ?: 0)).map { reps!!.getJSONObject(it).optString("value") }.filter { it.isNotEmpty() }.take(3)
            val rule = m.optJSONObject("rule")
            out += GrammarIssue(
                message = m.optString("message"),
                offset = m.optInt("offset"),
                length = m.optInt("length"),
                replacements = replacements,
                category = rule?.optJSONObject("category")?.optString("name").orEmpty(),
            )
        }
        return out
    }

    /** Applies the first suggestion of every issue to build a corrected sentence. */
    fun applyFixes(text: String, issues: List<GrammarIssue>): String {
        var result = text
        for (issue in issues.sortedByDescending { it.offset }) {
            val rep = issue.replacements.firstOrNull() ?: continue
            if (issue.offset < 0 || issue.offset + issue.length > result.length) continue
            result = result.replaceRange(issue.offset, issue.offset + issue.length, rep)
        }
        return result
    }
}
