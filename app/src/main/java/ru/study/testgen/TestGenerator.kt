package ru.study.testgen

import kotlin.math.abs
import kotlin.random.Random

/** Один вопрос теста. */
data class Question(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val source: String
)

/**
 * Делает тест из обычного текста без интернета и без нейросетей.
 * Ищет в тексте три вещи:
 *  1. определения вида «Термин — это ...»,
 *  2. числа и даты (их прячем, а в ответы кладём похожие числа),
 *  3. длинные слова (прячем слово, а неправильные ответы берём из других предложений).
 */
object TestGenerator {

    private val splitter = Regex("(?<=[.!?])\\s+|\\n+")
    private val marker = Regex("^(?:[-•*]|\\d+[.)])\\s+")
    private val defDash = Regex("^(.{2,60}?)\\s+[—–-]\\s+(?:это\\s+)?(.{12,})$", RegexOption.IGNORE_CASE)
    private val defEto = Regex("^(.{2,60}?)\\s+это\\s+(.{12,})$", RegexOption.IGNORE_CASE)
    private val wordRe = Regex("\\p{L}{6,}")
    private val numRe = Regex("(?<![\\d.,])\\d{2,4}(?!\\d)")

    private val stopWords = setOf(
        "который", "которые", "которых", "которой", "которого", "которая", "котором",
        "является", "являются", "поэтому", "потому", "также", "между", "после", "через",
        "только", "может", "можно", "этого", "этому", "когда", "другие", "других",
        "всегда", "иногда", "очень", "более", "каждый", "каждая", "таким", "образом",
        "обычно", "чтобы", "будет", "были", "этой", "этих", "своей", "своих"
    )

    private class Sentence(val text: String, val term: String?, val definition: String?)

    fun generate(source: String, count: Int): List<Question> {
        val rnd = Random(System.nanoTime())

        val sentences = source.split(splitter)
            .map { marker.replace(it.trim(), "").trim().trimEnd('.', '!', '?', ';', ':') }
            .filter { it.length in 20..300 }
            .distinct()
        if (sentences.isEmpty()) return emptyList()

        val parsed = sentences.map { parse(it) }
        val defs = parsed.filter { it.term != null }
        val pool = sentences.flatMap { keywords(it) }.distinctBy { it.lowercase() }

        val result = mutableListOf<Question>()
        for (s in parsed) {
            val q = buildDefinition(s, defs, rnd) ?: buildNumber(s, rnd) ?: buildBlank(s, pool, rnd)
            if (q != null) result.add(q)
        }
        return result.shuffled(rnd).take(count)
    }

    private fun parse(s: String): Sentence {
        val m = defDash.find(s) ?: defEto.find(s)
        if (m != null) {
            val term = m.groupValues[1].trim()
            val def = m.groupValues[2].trim()
            if (term.split(" ").size <= 5 && !term.contains(',')) {
                return Sentence(s, term, def)
            }
        }
        return Sentence(s, null, null)
    }

    private fun keywords(s: String): List<String> =
        wordRe.findAll(s).map { it.value }.filter { it.lowercase() !in stopWords }.toList()

    private fun shorten(text: String, max: Int = 90): String {
        if (text.length <= max) return text
        val cut = text.substring(0, max)
        val space = cut.lastIndexOf(' ')
        return (if (space > 30) cut.substring(0, space) else cut) + "…"
    }

    private fun cap(text: String): String =
        if (text.isEmpty()) text else text.substring(0, 1).uppercase() + text.substring(1)

    private fun buildDefinition(s: Sentence, defs: List<Sentence>, rnd: Random): Question? {
        val term = s.term ?: return null
        val definition = s.definition ?: return null
        val others = defs.filter { it !== s && it.term != null && it.definition != null }
        if (others.size < 2) return null
        val picked = others.shuffled(rnd).take(3)

        return if (rnd.nextBoolean()) {
            val correct = cap(shorten(definition))
            val options = (picked.map { cap(shorten(it.definition ?: "")) } + correct)
                .distinct().shuffled(rnd)
            if (options.size < 2) null
            else Question("Что такое «$term»?", options, options.indexOf(correct), s.text + ".")
        } else {
            val options = (picked.map { it.term ?: "" } + term).distinct().shuffled(rnd)
            if (options.size < 2) null
            else Question(
                "Как называется понятие:\n«" + cap(shorten(definition, 140)) + "»?",
                options, options.indexOf(term), s.text + "."
            )
        }
    }

    private fun buildNumber(s: Sentence, rnd: Random): Question? {
        val m = numRe.find(s.text) ?: return null
        val n = m.value.toInt()
        val deltas = if (n in 1000..2100) listOf(1, 5, 10, 12, 25, 50, 100) else listOf(1, 2, 3, 5, 10, 20)
        val wrong = deltas.flatMap { listOf(n + it, n - it) }
            .filter { it > 0 && it != n }
            .distinct().shuffled(rnd).take(3)
        if (wrong.size < 2) return null

        val options = (wrong + n).map { it.toString() }.shuffled(rnd)
        val hidden = s.text.replaceRange(m.range, "_____")
        return Question(
            "Вставь пропущенное число:\n«$hidden»",
            options, options.indexOf(n.toString()), s.text + "."
        )
    }

    private fun buildBlank(s: Sentence, pool: List<String>, rnd: Random): Question? {
        val words = keywords(s.text)
        if (words.isEmpty()) return null
        val target = words.sortedByDescending { it.length }.take(2).random(rnd)

        val wrong = pool
            .filter { !s.text.contains(it, ignoreCase = true) }
            .sortedBy { abs(it.length - target.length) }
            .take(8).shuffled(rnd).take(3)
        if (wrong.size < 2) return null

        val options = (wrong + target).shuffled(rnd)
        val hidden = s.text.replaceFirst(target, "_____")
        return Question(
            "Вставь пропущенное слово:\n«$hidden»",
            options, options.indexOf(target), s.text + "."
        )
    }
}
