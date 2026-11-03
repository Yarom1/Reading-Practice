package com.korimbekef.reading.core

import kotlin.random.Random

enum class GameType {
    /** Read a word, pick the matching picture. */
    WORD_TO_PICTURE,

    /** See a picture, pick the matching written word. */
    PICTURE_TO_WORD,

    /** Hear / see a picture, pick the first letter of the word. */
    FIRST_LETTER,

    /** Hear a word, pick it from written options. */
    LISTEN_AND_CHOOSE,

    /** Complete the missing word in a sentence from the stories. */
    MISSING_WORD,

    /** Classic memory game: match word cards with picture cards. */
    MEMORY,
}

data class QuizOption(
    val text: DualText? = null,
    val emoji: String? = null,
    val letter: Char? = null,
)

data class QuizQuestion(
    val type: GameType,
    /** Word / sentence shown as the question (for MISSING_WORD the blank is [blankWordIndex]). */
    val promptText: DualText? = null,
    val promptEmoji: String? = null,
    /** What the speaker button says (and what is read aloud after a correct answer). */
    val speakText: String? = null,
    val blankWordIndex: Int = -1,
    val options: List<QuizOption>,
    val correctIndex: Int,
)

data class MemoryCard(val id: Int, val pairId: Int, val word: DualText?, val emoji: String?)

class GameGenerator(
    private val vocabulary: List<VocabWord>,
    private val random: Random = Random.Default,
) {
    private val letters = "אבגדהוזחטיכלמנסעפצקרשת".toList()

    fun questions(type: GameType, count: Int, pool: List<VocabWord> = vocabulary, passages: List<Passage> = emptyList()): List<QuizQuestion> =
        when (type) {
            GameType.WORD_TO_PICTURE -> pick(pool, count).map { wordToPicture(it, pool) }
            GameType.PICTURE_TO_WORD -> pick(pool, count).map { pictureToWord(it, pool) }
            GameType.FIRST_LETTER -> pick(pool.filter { it.firstLetter != null }, count).map { firstLetter(it) }
            GameType.LISTEN_AND_CHOOSE -> pick(pool, count).map { listenAndChoose(it, pool) }
            GameType.MISSING_WORD -> missingWord(passages, count)
            GameType.MEMORY -> emptyList()
        }

    fun memoryCards(pairs: Int, pool: List<VocabWord> = vocabulary): List<MemoryCard> {
        val chosen = pick(pool.distinctBy { it.emoji }, pairs)
        val cards = ArrayList<MemoryCard>()
        chosen.forEachIndexed { i, w ->
            cards += MemoryCard(id = i * 2, pairId = i, word = w.word, emoji = null)
            cards += MemoryCard(id = i * 2 + 1, pairId = i, word = null, emoji = w.emoji)
        }
        return cards.shuffled(random)
    }

    private fun <T> pick(pool: List<T>, count: Int): List<T> = pool.shuffled(random).take(count)

    /** Distractors that look similar (same first letter or similar length) make the child really read. */
    private fun similarWords(target: VocabWord, pool: List<VocabWord>, n: Int): List<VocabWord> {
        val others = pool.filter { it.plainLetters != target.plainLetters && it.emoji != target.emoji }
        val sameStart = others.filter { it.firstLetter == target.firstLetter }.shuffled(random)
        val similarLength = others.filter { kotlin.math.abs(it.plainLetters.length - target.plainLetters.length) <= 1 }.shuffled(random)
        val result = LinkedHashSet<VocabWord>()
        for (w in sameStart) if (result.size < n) result += w
        for (w in similarLength) if (result.size < n) result += w
        for (w in others.shuffled(random)) if (result.size < n) result += w
        return result.toList()
    }

    private fun randomOthers(target: VocabWord, pool: List<VocabWord>, n: Int): List<VocabWord> =
        pool.filter { it.emoji != target.emoji && it.plainLetters != target.plainLetters }
            .distinctBy { it.emoji }
            .shuffled(random)
            .take(n)

    private fun <T> withCorrect(correct: T, distractors: List<T>): Pair<List<T>, Int> {
        val all = (distractors + correct).shuffled(random)
        return all to all.indexOf(correct)
    }

    private fun wordToPicture(target: VocabWord, pool: List<VocabWord>): QuizQuestion {
        val (options, idx) = withCorrect(target, randomOthers(target, pool, 2))
        return QuizQuestion(
            type = GameType.WORD_TO_PICTURE,
            promptText = target.word,
            speakText = target.word.nikud,
            options = options.map { QuizOption(emoji = it.emoji) },
            correctIndex = idx,
        )
    }

    private fun pictureToWord(target: VocabWord, pool: List<VocabWord>): QuizQuestion {
        val (options, idx) = withCorrect(target, similarWords(target, pool, 2))
        return QuizQuestion(
            type = GameType.PICTURE_TO_WORD,
            promptEmoji = target.emoji,
            speakText = target.word.nikud,
            options = options.map { QuizOption(text = it.word) },
            correctIndex = idx,
        )
    }

    private fun firstLetter(target: VocabWord): QuizQuestion {
        val correct = target.firstLetter ?: 'א'
        val distractors = letters.filter { it != correct }.shuffled(random).take(2)
        val (options, idx) = withCorrect(correct, distractors)
        return QuizQuestion(
            type = GameType.FIRST_LETTER,
            promptEmoji = target.emoji,
            promptText = target.word,
            speakText = target.word.nikud,
            options = options.map { QuizOption(letter = it) },
            correctIndex = idx,
        )
    }

    private fun listenAndChoose(target: VocabWord, pool: List<VocabWord>): QuizQuestion {
        val (options, idx) = withCorrect(target, similarWords(target, pool, 2))
        return QuizQuestion(
            type = GameType.LISTEN_AND_CHOOSE,
            speakText = target.word.nikud,
            promptEmoji = null,
            options = options.map { QuizOption(text = it.word) },
            correctIndex = idx,
        )
    }

    private fun missingWord(passages: List<Passage>, count: Int): List<QuizQuestion> {
        fun words(line: DualText) = (0 until line.wordCount).map { i -> DualText(line.nikudTokens.speechWord(i), line.plainTokens.speechWord(i)) }
        fun key(w: DualText) = HebrewText.lettersOnly(w.plain)
        val candidates = passages.flatMap { p -> p.lines.map { p to it } }
            .filter { (_, line) -> line.wordCount in 3..9 }
            .shuffled(random)
        val result = ArrayList<QuizQuestion>()
        for ((passage, line) in candidates) {
            if (result.size >= count) break
            val lineWords = words(line)
            // blank a content word (3+ letters) when possible, otherwise any 2+ letter word
            val blankable = lineWords.indices.filter { key(lineWords[it]).length >= 3 }
                .ifEmpty { lineWords.indices.filter { key(lineWords[it]).length >= 2 } }
            if (blankable.isEmpty()) continue
            val blank = blankable.random(random)
            val answer = lineWords[blank]
            // Distractors come from other passages, so they rarely fit this sentence as well.
            val ownWords = passage.lines.flatMap { words(it) }.map { key(it) }.toSet()
            val distractors = passages.asSequence()
                .filter { it.id != passage.id }
                .flatMap { it.lines.asSequence() }
                .flatMap { words(it).asSequence() }
                .filter { key(it).length >= 2 && key(it) !in ownWords }
                .distinctBy { key(it) }
                .toList()
                .shuffled(random)
                .take(2)
            if (distractors.size < 2) continue
            val (options, idx) = withCorrect(answer, distractors)
            result += QuizQuestion(
                type = GameType.MISSING_WORD,
                promptText = line,
                speakText = line.nikud,
                blankWordIndex = blank,
                options = options.map { QuizOption(text = it) },
                correctIndex = idx,
            )
        }
        return result
    }
}
