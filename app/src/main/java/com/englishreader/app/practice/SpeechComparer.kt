package com.englishreader.app.practice

import kotlin.math.max
import kotlin.math.min

/** Word-level alignment between the target text and what the speech recogniser heard. */
object SpeechComparer {
    private val numbers = mapOf(
        "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four", "5" to "five",
        "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine", "10" to "ten",
    )

    fun tokens(text: String): List<String> =
        text.lowercase().replace('’', '\'').split(Regex("[^a-z0-9']+"))
            .map { it.trim('\'') }.filter { it.isNotEmpty() }.map { numbers[it] ?: it }

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val d = levenshtein(a, b)
        return 1.0 - d.toDouble() / max(a.length, b.length)
    }

    fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    fun compare(target: String, heard: String): SpeechScore {
        val e = tokens(target)
        val h = tokens(heard)
        if (e.isEmpty()) return SpeechScore(emptyList(), 0)
        // DP alignment: substitution cost depends on how similar the words sound/look.
        val n = e.size; val m = h.size
        val cost = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) cost[i][0] = i.toDouble()
        for (j in 0..m) cost[0][j] = j.toDouble()
        for (i in 1..n) for (j in 1..m) {
            val sim = similarity(e[i - 1], h[j - 1])
            val sub = when { sim == 1.0 -> 0.0; sim >= 0.7 -> 0.5; else -> 1.0 }
            cost[i][j] = minOf(cost[i - 1][j - 1] + sub, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
        }
        val out = ArrayList<WordMatch>()
        var i = n; var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val sim = similarity(e[i - 1], h[j - 1])
                val sub = when { sim == 1.0 -> 0.0; sim >= 0.7 -> 0.5; else -> 1.0 }
                if (cost[i][j] == cost[i - 1][j - 1] + sub) {
                    val status = when (sub) { 0.0 -> MatchStatus.CORRECT; 0.5 -> MatchStatus.CLOSE; else -> MatchStatus.WRONG }
                    out += WordMatch(e[i - 1], h[j - 1], status); i--; j--; continue
                }
            }
            if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) { out += WordMatch(e[i - 1], null, MatchStatus.MISSING); i--; continue }
            out += WordMatch(null, h[j - 1], MatchStatus.EXTRA); j--
        }
        out.reverse()
        val points = out.sumOf { when (it.status) { MatchStatus.CORRECT -> 1.0; MatchStatus.CLOSE -> 0.5; else -> 0.0 } }
        val extras = out.count { it.status == MatchStatus.EXTRA }
        val score = ((points - extras * 0.25).coerceAtLeast(0.0) / n * 100).toInt().coerceIn(0, 100)
        return SpeechScore(out, score)
    }

    /** Picks the recogniser alternative that best matches the target (recognisers return several guesses). */
    fun best(target: String, alternatives: List<String>): Pair<String, SpeechScore>? =
        alternatives.map { it to compare(target, it) }.maxByOrNull { it.second.score }
}
