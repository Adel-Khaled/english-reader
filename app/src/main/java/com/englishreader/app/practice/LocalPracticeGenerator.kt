package com.englishreader.app.practice

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.VocabWordEntity
import com.englishreader.app.data.db.exampleList
import kotlin.random.Random

/**
 * Builds personalised practice on the device, with no internet and no AI:
 * a short story that uses the learner's real example sentences (so the English is always natural),
 * plus comprehension, vocabulary and fill-in-the-blank questions.
 */
class LocalPracticeGenerator(private val random: Random = Random.Default) {

    private data class Person(val name: String, val he: String, val him: String, val his: String)

    private val people = listOf(
        Person("Sara", "she", "her", "her"), Person("Omar", "he", "him", "his"), Person("Lina", "she", "her", "her"),
        Person("Adam", "he", "him", "his"), Person("Mona", "she", "her", "her"), Person("Karim", "he", "him", "his"),
        Person("Nour", "she", "her", "her"), Person("Youssef", "he", "him", "his"),
    )
    private val places = listOf("library", "park", "museum", "market", "beach", "café", "old bookshop", "train station")
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val times = listOf("morning", "afternoon", "evening")
    private val endings = listOf(
        "Read the new words again", "Called a friend", "Bought a new phone", "Cooked dinner", "Went swimming", "Watched a film",
    )

    /** Used as wrong options when the learner has saved only a few words. */
    val fillerWords: List<Pair<String, String>> = listOf(
        "window" to "نافذة", "happy" to "سعيد", "quickly" to "بسرعة", "garden" to "حديقة", "travel" to "يسافر",
        "bright" to "مشرق", "kitchen" to "مطبخ", "answer" to "إجابة", "quiet" to "هادئ", "mountain" to "جبل",
    )

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Shortest/longest natural example that really contains the word. */
    fun exampleFor(w: VocabWordEntity, level: Difficulty = Difficulty.INTERMEDIATE): String? {
        val candidates = (w.exampleList + listOfNotNull(w.sourceSentence))
            .map { it.trim().trim('“', '”', '"').trim() }
            .filter { it.length in 12..260 && WordForms.contains(it, w.word) }
            .distinct()
        if (candidates.isEmpty()) return null
        return when (level) {
            Difficulty.BEGINNER -> candidates.minByOrNull { it.length }
            Difficulty.ADVANCED -> candidates.maxByOrNull { it.length }
            else -> candidates.sortedBy { it.length }[candidates.size / 2]
        }?.let { s -> if (s.last() in ".!?…\"”") s else "$s." }
    }

    fun shortDefinition(w: VocabWordEntity): String {
        val first = w.definition.lines().firstOrNull { it.isNotBlank() }.orEmpty()
            .replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("^\\([a-z ]+\\)\\s*"), "").trim()
        val cut = if (first.length > 110) first.take(110).substringBeforeLast(' ') + "…" else first
        return cut.replaceFirstChar { it.lowercase() }.trimEnd('.')
    }

    fun story(words: List<VocabWordEntity>, level: Difficulty, allWords: List<VocabWordEntity>): Story {
        val p = people.random(random)
        val place = places.random(random)
        val day = days.random(random)
        val time = times.random(random)
        val name = p.name

        val intro = buildString {
            append("One $day $time, $name went to the $place to practise English.")
            when (level) {
                Difficulty.BEGINNER -> append(" ${cap(p.he)} was very happy.")
                Difficulty.ADVANCED -> append(" ${cap(p.he)} had decided to collect real sentences from everyday life, because ${p.he} believed that words are remembered best when they are met in context.")
                else -> append(" ${cap(p.he)} brought a small notebook, because ${p.he} wanted to learn some new words.")
            }
        }
        val frames = listOf(
            "$name opened a book and read: “%s”",
            "On the wall there was a poster that said: “%s”",
            "A friendly teacher gave ${p.him} an example: “%s”",
            "Later, $name heard someone say: “%s”",
            "$name wrote a sentence in ${p.his} notebook: “%s”",
            "A friend sent ${p.him} a message: “%s”",
            "Near the door, $name saw a sign: “%s”",
        ).shuffled(random)
        val middle = words.mapIndexed { i, w ->
            val ex = exampleFor(w, level)
            if (ex != null) frames[i % frames.size].format(ex)
            else {
                val def = shortDefinition(w)
                if (def.isNotBlank()) "$name looked up the word “${w.word}”. It means $def." else "$name learned the word “${w.word}”."
            }
        }
        val closing = "Before going home, $name read the new words again: ${words.joinToString(", ") { it.word }}. ${cap(p.he)} felt proud of ${p.his} progress."
        val paragraphs = listOf(intro) + middle.chunked(2).map { it.joinToString(" ") } + closing

        val questions = ArrayList<Question>()
        // Comprehension (facts of the story)
        questions += mcq(QuestionType.COMPREHENSION, "Where did $name go to practise English?", "the ${place}".let(::cap),
            places.filter { it != place }.shuffled(random).take(3).map { cap("the $it") }, "The story says: “$name went to the $place”.")
        questions += mcq(QuestionType.COMPREHENSION, "When did $name go there?", "On $day $time",
            (days.filter { it != day }.shuffled(random).take(3)).map { "On $it ${times.random(random)}" }, "The first sentence: “One $day $time…”.")
        questions += mcq(QuestionType.COMPREHENSION, "What did $name do before going home?", endings[0],
            endings.drop(1).shuffled(random).take(3), "“Before going home, $name read the new words again.”")

        // Vocabulary: meaning of each target word
        val translationPool = (allWords + words).filter { it.translationAr.isNotBlank() }.distinctBy { it.word }
        for (w in words.take(4)) {
            if (w.translationAr.isNotBlank()) {
                val wrong = (translationPool.filter { it.word != w.word }.map { firstMeaning(it.translationAr) } +
                    fillerWords.map { it.second }).distinct().filter { it != firstMeaning(w.translationAr) }.shuffled(random).take(3)
                questions += mcq(QuestionType.VOCABULARY, "What does “${w.word}” mean?", firstMeaning(w.translationAr), wrong,
                    "${w.word} = ${w.translationAr}" + if (w.definition.isNotBlank()) " — ${shortDefinition(w)}" else "", w.word)
            } else if (w.definition.isNotBlank()) {
                val wrong = (allWords.filter { it.word != w.word && it.definition.isNotBlank() }.map { shortDefinition(it) })
                    .shuffled(random).take(3)
                if (wrong.size >= 2) questions += mcq(QuestionType.VOCABULARY, "What does “${w.word}” mean?", shortDefinition(w), wrong, "", w.word)
            }
        }
        // Fill in the blank from the story's own sentences
        for (b in blanks(words, allWords, level).take(3)) {
            questions += Question(QuestionType.FILL_BLANK, "Complete the sentence:\n“${b.sentenceWithBlank}”", b.options,
                b.options.indexOf(b.word), "“${b.fullSentence}”", b.word)
        }
        val title = listOf("A Day at the ${cap(place)}", "$name and the New Words", "A $day to Remember").random(random)
        return Story(title, paragraphs, words.map { it.word }, questions.shuffled(random).take(8), ContentSource.LOCAL)
    }

    private fun firstMeaning(ar: String) = ar.split('،', ',').first().trim()

    private fun mcq(type: QuestionType, prompt: String, correct: String, wrong: List<String>, explanation: String, word: String? = null): Question {
        val options = (wrong.take(3) + correct).distinct().shuffled(random)
        return Question(type, prompt, options, options.indexOf(correct), explanation, word)
    }

    fun blanks(words: List<VocabWordEntity>, allWords: List<VocabWordEntity>, level: Difficulty = Difficulty.INTERMEDIATE): List<BlankItem> {
        val pool = (allWords.map { it.word } + fillerWords.map { it.first }).distinct()
        return words.mapNotNull { w ->
            val sentence = exampleFor(w, level) ?: return@mapNotNull null
            val range = WordForms.find(sentence, w.word) ?: return@mapNotNull null
            val blanked = sentence.replaceRange(range, "_____")
            val wrong = pool.filter { it != w.word && !it.contains(w.word) && !w.word.contains(it) }.shuffled(random).take(3)
            val options = (wrong + w.word).shuffled(random)
            BlankItem(w.id, w.word, blanked, sentence, options, w.translationAr, shortDefinition(w))
        }
    }
}
