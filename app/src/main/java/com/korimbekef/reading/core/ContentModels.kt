package com.korimbekef.reading.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The same text with nikud and without nikud. Both versions always have the same number of
 * words, in the same order, so a highlighted word index is valid in either version.
 */
data class DualText(val nikud: String, val plain: String) {
    fun get(withNikud: Boolean): String = if (withNikud) nikud else plain

    val nikudTokens: LineTokens by lazy { LineTokens.of(nikud) }
    val plainTokens: LineTokens by lazy { LineTokens.of(plain) }

    fun tokens(withNikud: Boolean): LineTokens = if (withNikud) nikudTokens else plainTokens

    val wordCount: Int get() = nikudTokens.wordCount

    companion object {
        private val OVERRIDE = Regex("\\{([^{}|]+)\\|([^{}]+)\\}")

        /**
         * Parses authored content. Words whose plain (ktiv male) spelling differs from the
         * vocalized spelling are written as {סִפּוּר|סיפור}; everything else is derived by
         * removing the nikud. A full alternative may also be given after " || ".
         */
        fun parse(raw: String): DualText {
            val fullSplit = raw.indexOf("||")
            if (fullSplit >= 0) {
                val n = HebrewText.normalize(raw.substring(0, fullSplit))
                val p = HebrewText.normalize(raw.substring(fullSplit + 2))
                return DualText(n, p)
            }
            val nikud = StringBuilder()
            val plain = StringBuilder()
            var last = 0
            for (m in OVERRIDE.findAll(raw)) {
                val before = raw.substring(last, m.range.first)
                nikud.append(before)
                plain.append(HebrewText.stripNikud(before))
                nikud.append(m.groupValues[1])
                plain.append(m.groupValues[2])
                last = m.range.last + 1
            }
            val rest = raw.substring(last)
            nikud.append(rest)
            plain.append(HebrewText.stripNikud(rest))
            return DualText(HebrewText.normalize(nikud.toString()), HebrewText.normalize(plain.toString()))
        }

        /** Text typed by a parent: may or may not contain nikud. */
        fun ofUserText(raw: String): DualText {
            val n = HebrewText.normalize(raw)
            return DualText(n, HebrewText.stripNikud(n))
        }
    }
}

enum class PassageKind { STORY, POEM, INFO, WORDS }

data class Passage(
    val id: String,
    val level: Int,
    val title: DualText,
    val emoji: String,
    val colorIndex: Int,
    val kind: PassageKind,
    val lines: List<DualText>,
    val isCustom: Boolean = false,
) {
    val wordCount: Int by lazy { lines.sumOf { it.wordCount } }
    val lineCount: Int get() = lines.size
}

/** A picture word used by the games and the personal dictionary. */
data class VocabWord(
    val word: DualText,
    val emoji: String,
    val category: String,
    val description: DualText?,
) {
    val plainLetters: String by lazy { HebrewText.lettersOnly(word.plain) }
    val firstLetter: Char? by lazy { HebrewText.firstLetter(word.plain) }
}

data class LevelInfo(val number: Int, val name: DualText, val emoji: String, val description: String)

object Levels {
    val all: List<LevelInfo> = listOf(
        LevelInfo(1, DualText.parse("מַתְחִילִים"), "🌱", "אותיות, מילים קצרות ומשפטים פשוטים"),
        LevelInfo(2, DualText.parse("צְעָדִים רִאשׁוֹנִים"), "🐣", "משפטים קצרים ומילים נפוצות"),
        LevelInfo(3, DualText.parse("קוֹרְאִים לְבַד"), "🚲", "פסקאות קצרות וסיפורים פשוטים"),
        LevelInfo(4, DualText.parse("קוֹרְאִים שׁוֹטֵף"), "🚀", "סיפורים ארוכים ומשפטים מורכבים"),
        LevelInfo(5, DualText.parse("מִתְקַדְּמִים"), "🏆", "סיפורים, עובדות וקטעי מידע"),
    )

    const val MIN = 1
    const val MAX = 5

    fun info(level: Int): LevelInfo = all[(level.coerceIn(MIN, MAX)) - 1]
}

@Serializable
data class PassageJson(
    val id: String,
    val level: Int,
    val title: String,
    val emoji: String,
    val color: Int = 0,
    val kind: String = "story",
    val lines: List<String>,
)

@Serializable
data class VocabJson(
    val w: String,
    val e: String,
    val c: String = "",
    val d: String? = null,
)

object ContentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePassages(text: String): List<Passage> =
        json.decodeFromString<List<PassageJson>>(text).map { it.toPassage() }

    fun parseVocabulary(text: String): List<VocabWord> =
        json.decodeFromString<List<VocabJson>>(text).map {
            VocabWord(
                word = DualText.parse(it.w),
                emoji = it.e,
                category = it.c,
                description = it.d?.let { d -> DualText.parse(d) },
            )
        }

    private fun PassageJson.toPassage(): Passage = Passage(
        id = id,
        level = level,
        title = DualText.parse(title),
        emoji = emoji,
        colorIndex = color,
        kind = when (kind.lowercase()) {
            "poem" -> PassageKind.POEM
            "info" -> PassageKind.INFO
            "words" -> PassageKind.WORDS
            else -> PassageKind.STORY
        },
        lines = lines.filter { it.isNotBlank() }.map { DualText.parse(it) },
    )
}
