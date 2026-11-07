package com.englishreader.app.practice

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.net.Http
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional AI helper using the learner's own free Google Gemini API key
 * (created at aistudio.google.com). Used only when a key is set in Settings;
 * the app falls back to on-device generation otherwise or on any error.
 */
object GeminiClient {
    private const val MODEL = "gemini-flash-latest"

    suspend fun generateJson(apiKey: String, prompt: String): JSONObject {
        val req = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8))
        val body = Http.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey",
            req.toString(), "application/json; charset=UTF-8", 60_000,
        )
        return JSONObject(extractText(body))
    }

    fun extractText(response: String): String {
        val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        return text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun cefr(level: Difficulty) = when (level) {
        Difficulty.BEGINNER -> "A2 (simple sentences, common words)"
        Difficulty.ADVANCED -> "C1 (rich vocabulary, complex sentences)"
        else -> "B1 (clear everyday English)"
    }

    suspend fun story(apiKey: String, words: List<VocabWordEntity>, level: Difficulty): Story {
        val list = words.joinToString("\n") { "- ${it.word}" + if (it.translationAr.isNotBlank()) " (Arabic: ${it.translationAr})" else "" }
        val prompt = """
            You are an English teacher for Arabic-speaking learners. Write an original short story at CEFR level ${cefr(level)}
            that naturally uses EVERY one of these words (use each word at least once, in a natural way):
            $list
            Length: ${if (level == Difficulty.BEGINNER) "100-150" else if (level == Difficulty.ADVANCED) "220-300" else "150-220"} words, 2-4 paragraphs.
            Then write 7 multiple-choice questions with exactly 4 options each:
            3 comprehension questions about the story, 2 vocabulary questions about the target words, 2 fill-in-the-blank questions
            (a sentence from the story with the target word replaced by "_____").
            Return ONLY JSON in this exact shape:
            {"title": "...", "paragraphs": ["...", "..."], "questions": [{"type": "comprehension|vocabulary|fill_blank",
            "question": "...", "options": ["a","b","c","d"], "answer_index": 0, "explanation": "short explanation"}]}
        """.trimIndent()
        return parseStory(generateJson(apiKey, prompt), words.map { it.word })
    }

    fun parseStory(json: JSONObject, targets: List<String>): Story {
        val paragraphs = json.getJSONArray("paragraphs").let { a -> (0 until a.length()).map { a.getString(it).trim() } }.filter { it.isNotEmpty() }
        require(paragraphs.isNotEmpty()) { "empty story" }
        val qa = json.optJSONArray("questions") ?: JSONArray()
        val questions = (0 until qa.length()).mapNotNull { i ->
            val q = qa.getJSONObject(i)
            val opts = q.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val idx = q.optInt("answer_index", -1)
            if (opts.size < 2 || idx !in opts.indices) return@mapNotNull null
            val type = when (q.optString("type").lowercase()) {
                "vocabulary" -> QuestionType.VOCABULARY
                "fill_blank", "fill-blank", "fill in the blank" -> QuestionType.FILL_BLANK
                else -> QuestionType.COMPREHENSION
            }
            Question(type, q.getString("question"), opts, idx, q.optString("explanation"))
        }
        return Story(json.optString("title", "Your Story"), paragraphs, targets, questions, ContentSource.AI)
    }

    suspend fun reviewSentence(apiKey: String, word: String, sentence: String): Triple<String?, String?, List<String>> {
        val prompt = """
            An Arabic-speaking English learner wrote this sentence to practise the word "$word":
            "$sentence"
            Check grammar, word choice and whether "$word" is used correctly.
            Return ONLY JSON: {"corrected": "corrected sentence (same as input if already correct)",
            "explanation": "1-3 short sentences explaining any mistakes in simple English, then the same explanation in Arabic",
            "alternatives": ["a more natural version", "another natural example using $word"]}
        """.trimIndent()
        val j = generateJson(apiKey, prompt)
        val alts = j.optJSONArray("alternatives")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return Triple(j.optString("corrected").takeIf { it.isNotBlank() }, j.optString("explanation").takeIf { it.isNotBlank() }, alts)
    }
}
