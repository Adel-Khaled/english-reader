package com.englishreader.app.practice

/** Finds a saved word (or a common inflection of it) inside a sentence. */
object WordForms {
    private fun pattern(word: String): Regex {
        val w = Regex.escape(word.lowercase())
        val alts = mutableListOf("$w(?:s|es|ed|d|ing|ly|er|est|'s)?")
        if (word.length > 3 && word.endsWith("e")) alts += Regex.escape(word.dropLast(1).lowercase()) + "(?:ing|ed|er|est)"
        if (word.length > 3 && word.endsWith("y")) alts += Regex.escape(word.dropLast(1).lowercase()) + "(?:ies|ied|ier|iest|ily)"
        if (word.length > 4 && word.endsWith("ing")) alts += Regex.escape(word.dropLast(3).lowercase()) + "(?:e|es|ed|s)?"
        // doubled consonant: stop -> stopped / stopping
        if (word.length >= 3 && word.last() !in "aeiouwxy") alts += w + Regex.escape(word.last().toString()) + "(?:ed|ing|er)"
        return Regex("(?<![A-Za-z])(?:" + alts.joinToString("|") + ")(?![A-Za-z])", RegexOption.IGNORE_CASE)
    }

    fun find(sentence: String, word: String): IntRange? = pattern(word).find(sentence)?.range

    fun contains(sentence: String, word: String): Boolean = find(sentence, word) != null

    /** Accepts the base word or an inflected form typed by the learner. */
    fun matchesAnswer(typed: String, word: String): Boolean {
        val t = typed.trim().trim('.', ',', '!', '?', '"', '\'').lowercase()
        if (t.isEmpty()) return false
        val r = pattern(word).find(t) ?: return false
        return r.range.first == 0 && r.range.last == t.length - 1
    }
}
