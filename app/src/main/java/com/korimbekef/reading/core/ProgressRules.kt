package com.korimbekef.reading.core

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.min

/**
 * Reward rules. Everything here rewards effort and persistence: stars are only ever added,
 * never removed, and listening again is never penalised.
 */
object Rewards {
    const val STARS_PER_LINE_READ = 1
    const val STARS_PER_PASSAGE = 3
    const val STARS_PER_GAME_ROUND = 2

    /** 1-3 stars on a story card: one for every time it was completed (re-reading builds fluency). */
    fun passageStars(timesCompleted: Int): Int = timesCompleted.coerceIn(0, 3)
}

object Streaks {
    /** Current streak in days, ending today (or yesterday, if today has no practice yet). */
    fun current(practiceDays: Set<Long>, today: Long): Int {
        var day = if (today in practiceDays) today else today - 1
        var count = 0
        while (day in practiceDays) {
            count++
            day--
        }
        return count
    }

    fun best(practiceDays: Set<Long>): Int {
        if (practiceDays.isEmpty()) return 0
        val sorted = practiceDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}

/** What the companion owl wears, unlocked by collecting stars. */
enum class MascotStage(val minStars: Int, val title: String, val emoji: String) {
    BABY(0, "יַנְשׁוּפִי", "🦉"),
    GLASSES(25, "יַנְשׁוּפִי עִם מִשְׁקְפֵי קְרִיאָה", "👓"),
    SCARF(60, "יַנְשׁוּפִי עִם צָעִיף צִבְעוֹנִי", "🧣"),
    CAP(120, "יַנְשׁוּפִי עִם כּוֹבַע שֶׁל חֲכָמִים", "🎓"),
    CROWN(200, "יַנְשׁוּפִי מֶלֶךְ הַקְּרִיאָה", "👑");

    val next: MascotStage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forStars(stars: Int): MascotStage = entries.last { stars >= it.minStars }
    }
}

data class ChildStats(
    val linesRead: Int = 0,
    val passagesCompleted: Int = 0,
    val bestStreak: Int = 0,
    val maxCompletionsOfOnePassage: Int = 0,
    val gamesPlayed: Int = 0,
    val level: Int = 1,
    val goalDays: Int = 0,
    val savedWords: Int = 0,
)

data class MedalDef(
    val code: String,
    val emoji: String,
    val title: String,
    val hint: String,
    val earned: (ChildStats) -> Boolean,
)

object Medals {
    val all: List<MedalDef> = listOf(
        MedalDef("first_line", "🌟", "שׁוּרָה רִאשׁוֹנָה", "קְרִיאַת שׁוּרָה רִאשׁוֹנָה לְבַד") { it.linesRead >= 1 },
        MedalDef("first_story", "📖", "סִפּוּר רִאשׁוֹן", "סִיּוּם הַסִּפּוּר הָרִאשׁוֹן") { it.passagesCompleted >= 1 },
        MedalDef("stories_5", "📚", "חֲמִשָּׁה סִפּוּרִים", "סִיּוּם חֲמִשָּׁה סִפּוּרִים") { it.passagesCompleted >= 5 },
        MedalDef("stories_15", "🏅", "חֲמִשָּׁה עָשָׂר סִפּוּרִים", "סִיּוּם חֲמִשָּׁה עָשָׂר סִפּוּרִים") { it.passagesCompleted >= 15 },
        MedalDef("stories_30", "🏆", "שְׁלוֹשִׁים סִפּוּרִים", "סִיּוּם שְׁלוֹשִׁים סִפּוּרִים") { it.passagesCompleted >= 30 },
        MedalDef("lines_50", "✏️", "חֲמִשִּׁים שׁוּרוֹת", "קְרִיאַת חֲמִשִּׁים שׁוּרוֹת לְבַד") { it.linesRead >= 50 },
        MedalDef("lines_200", "🚀", "מָאתַיִם שׁוּרוֹת", "קְרִיאַת מָאתַיִם שׁוּרוֹת לְבַד") { it.linesRead >= 200 },
        MedalDef("streak_3", "🔥", "שְׁלוֹשָׁה יָמִים בָּרֶצֶף", "קְרִיאָה שְׁלוֹשָׁה יָמִים בָּרֶצֶף") { it.bestStreak >= 3 },
        MedalDef("streak_7", "🌈", "שָׁבוּעַ שָׁלֵם", "קְרִיאָה שִׁבְעָה יָמִים בָּרֶצֶף") { it.bestStreak >= 7 },
        MedalDef("streak_14", "💎", "שְׁבוּעַיִם בָּרֶצֶף", "קְרִיאָה אַרְבָּעָה עָשָׂר יָמִים בָּרֶצֶף") { it.bestStreak >= 14 },
        MedalDef("streak_30", "👑", "חֹדֶשׁ שֶׁל קְרִיאָה", "קְרִיאָה שְׁלוֹשִׁים יָמִים בָּרֶצֶף") { it.bestStreak >= 30 },
        MedalDef("reread", "🔁", "קְרִיאָה חוֹזֶרֶת", "לִקְרֹא אֶת אוֹתוֹ סִפּוּר שָׁלֹשׁ פְּעָמִים") { it.maxCompletionsOfOnePassage >= 3 },
        MedalDef("games_5", "🎮", "חֲמִשָּׁה מִשְׂחָקִים", "לְסַיֵּם חֲמִשָּׁה מִשְׂחָקִים") { it.gamesPlayed >= 5 },
        MedalDef("goal_day", "🎯", "יַעַד יוֹמִי", "לְהַגִּיעַ לַיַּעַד הַיּוֹמִי") { it.goalDays >= 1 },
        MedalDef("words_10", "🔤", "עֶשֶׂר מִלִּים שֶׁלִּי", "לִשְׁמֹר עֶשֶׂר מִלִּים בָּרְשִׁימָה") { it.savedWords >= 10 },
        MedalDef("level_up", "⬆️", "רָמָה חֲדָשָׁה", "לַעֲלוֹת לְרָמָה חֲדָשָׁה") { it.level >= 2 },
    )

    fun byCode(code: String): MedalDef? = all.firstOrNull { it.code == code }

    fun earnedCodes(stats: ChildStats): Set<String> = all.filter { it.earned(stats) }.map { it.code }.toSet()
}

object LevelAdvisor {
    /**
     * Suggests moving up a level once most stories of the current level were completed.
     * The suggestion is shown to the parent, who decides — the level never changes on its own.
     */
    fun recommendedLevel(level: Int, completedInLevel: Int, totalInLevel: Int): Int? {
        if (level >= Levels.MAX || totalInLevel <= 0) return null
        val needed = min(5, ceil(totalInLevel * 0.7).toInt()).coerceAtLeast(1)
        return if (completedInLevel >= needed) level + 1 else null
    }
}

object PinHasher {
    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        digest.update(pin.toByteArray())
        return digest.digest().toHex()
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** Finds a picture word for a word tapped in a story, ignoring common one-letter prefixes. */
class Dictionary(vocabulary: List<VocabWord>) {
    private val byPlain: Map<String, VocabWord> = vocabulary.associateBy { it.plainLetters }
    private val byStripped: Map<String, VocabWord> =
        vocabulary.associateBy { HebrewText.lettersOnly(HebrewText.stripNikud(it.word.nikud)) }

    fun find(nikudWord: String, plainWord: String): VocabWord? {
        val candidates = listOf(HebrewText.lettersOnly(plainWord), HebrewText.lettersOnly(HebrewText.stripNikud(nikudWord)))
        for (c in candidates) {
            lookup(c)?.let { return it }
        }
        for (c in candidates) {
            for (k in 1..2) {
                if (c.length - k >= 2 && c.take(k).all { it in PREFIXES }) {
                    lookup(c.drop(k))?.let { return it }
                }
            }
        }
        return null
    }

    private fun lookup(letters: String): VocabWord? = byPlain[letters] ?: byStripped[letters]

    private companion object {
        const val PREFIXES = "והבלמשכ"
    }
}
