package com.korimbekef.reading.ui

import kotlin.random.Random

/** A text in two forms: addressed to a boy (m) or to a girl (f). */
data class G(val m: String, val f: String) {
    constructor(both: String) : this(both, both)

    fun of(girl: Boolean): String = if (girl) f else m
}

/**
 * Child-facing texts. They carry nikud so that children who are just learning to read can read
 * the buttons too, and so the speech engine pronounces them correctly. Messages praise effort,
 * never claim the app "heard" the child (it does not listen), and never scold.
 */
object T {
    // ---- Home ----
    fun hello(name: String) = if (name.isBlank()) "שָׁלוֹם!" else "שָׁלוֹם, $name!"
    const val GREET_MORNING = "בֹּקֶר טוֹב"
    const val GREET_NOON = "צָהֳרַיִם טוֹבִים"
    const val GREET_EVENING = "עֶרֶב טוֹב"
    const val GREET_NIGHT = "לַיְלָה טוֹב"
    const val WHAT_TODAY = "מָה נִקְרָא הַיּוֹם?"
    val START_STORY = G("בּוֹא נִקְרָא סִפּוּר!", "בּוֹאִי נִקְרָא סִפּוּר!")
    const val CONTINUE_LAST = "הֶמְשֵׁךְ מֵהַמָּקוֹם הָאַחֲרוֹן"
    const val MY_LIBRARY = "הַסִּפְרִיָּה שֶׁלִּי"
    const val MY_GAMES = "הַמִּשְׂחָקִים שֶׁלִּי"
    const val MY_WORDS = "הַמִּלִּים שֶׁלִּי"
    const val MY_TREASURE = "הָאוֹצָר שֶׁלִּי"
    const val PARENTS = "הוֹרִים"
    const val STARS = "כּוֹכָבִים"
    const val MEDALS = "מְדַלְיוֹת"
    const val DAYS_IN_ROW = "יָמִים בָּרֶצֶף"
    const val TODAY = "הַיּוֹם"
    fun minutesOf(done: Int, goal: Int) = "$done מִתּוֹךְ $goal דַּקּוֹת"
    const val GOAL_REACHED = "הִגַּעְנוּ לַיַּעַד הַיּוֹמִי! 🎉"
    const val NEXT_STORY = "הַסִּפּוּר הַבָּא"

    // ---- Library ----
    const val LIBRARY = "הַסִּפְרִיָּה"
    const val LEVEL = "רָמָה"
    fun linesCount(n: Int) = "$n שׁוּרוֹת"
    const val NEW = "חָדָשׁ!"
    const val LOCKED = "הָרָמָה הַזֹּאת עוֹד נְעוּלָה 🔒"
    const val LOCKED_SUB = "הַהוֹרִים יְכוֹלִים לִפְתֹּחַ אוֹתָהּ."
    const val FROM_PARENTS = "מֵהַהוֹרִים"
    const val NO_STORIES = "אֵין כָּאן סִפּוּרִים עֲדַיִן"

    // ---- Reading ----
    const val LISTEN = "לְהַקְשִׁיב"
    const val PAUSE = "הַפְסָקָה"
    const val RESUME = "לְהַמְשִׁיךְ"
    const val AGAIN = "עוֹד פַּעַם"
    const val NEXT_LINE = "הַשּׁוּרָה הַבָּאָה"
    const val PREV_LINE = "הַשּׁוּרָה הַקּוֹדֶמֶת"
    const val READ_ALL = "כָּל הַסִּפּוּר"
    const val DONE_READING = "סִיַּמְתִּי לִקְרֹא"
    const val LISTEN_AGAIN = "לִשְׁמֹעַ שׁוּב"
    const val READ_TOGETHER = "לִקְרֹא יַחַד לְאַט"
    const val NIKUD = "נִקּוּד"
    const val MODE_TOGETHER = "קְרִיאָה בְּיַחַד"
    const val MODE_ALONE = "קְרִיאָה לְבַד"
    const val ALONE_HINT = "לִגְעַת בְּמִלָּה כְּדֵי לִשְׁמֹעַ אוֹתָהּ"
    const val TAP_LINE_HINT = "לִגְעַת בְּשׁוּרָה כְּדֵי לִשְׁמֹעַ אוֹתָהּ"
    const val MARK_READ = "קָרָאתִי"
    fun lineOf(i: Int, n: Int) = "שׁוּרָה $i מִתּוֹךְ $n"
    const val FONT_SIZE = "גֹּדֶל הָאוֹתִיּוֹת"
    const val SPEED = "מְהִירוּת הַקְּרִיאָה"
    val SPEED_NAMES = listOf("אִטִּי מְאוֹד", "אִטִּי", "רָגִיל", "מָהִיר")
    const val READ_SETTINGS = "הַגְדָּרוֹת קְרִיאָה"
    const val OK = "אִישׁוּר"

    val TURN_PROMPTS = listOf(
        G("עַכְשָׁיו תּוֹרְךָ! קְרָא אֶת הַשּׁוּרָה בְּקוֹל.", "עַכְשָׁיו תּוֹרֵךְ! קִרְאִי אֶת הַשּׁוּרָה בְּקוֹל."),
        G("תּוֹרְךָ לִקְרֹא! לְאַט לְאַט, אֵין מָה לְמַהֵר.", "תּוֹרֵךְ לִקְרֹא! לְאַט לְאַט, אֵין מָה לְמַהֵר."),
        G("עַכְשָׁיו אַתָּה! אֶצְבַּע מִתַּחַת לַמִּלִּים, וְקָדִימָה.", "עַכְשָׁיו אַתְּ! אֶצְבַּע מִתַּחַת לַמִּלִּים, וְקָדִימָה."),
        G("נַסֵּה לִקְרֹא אֶת הַשּׁוּרָה לְבַד.", "נַסִּי לִקְרֹא אֶת הַשּׁוּרָה לְבַד."),
    )

    val PRAISE = listOf(
        G("אֵיזֶה יֹפִי! הִצְלַחְתָּ לִקְרֹא אֶת הַשּׁוּרָה!", "אֵיזֶה יֹפִי! הִצְלַחְתְּ לִקְרֹא אֶת הַשּׁוּרָה!"),
        G("כָּל הַכָּבוֹד! מַמְשִׁיכִים לַשּׁוּרָה הַבָּאָה?"),
        G("אַתָּה אַלּוּף! עוֹד שׁוּרָה!", "אַתְּ אַלּוּפָה! עוֹד שׁוּרָה!"),
        G("מְצֻיָּן! קָרָאתָ בְּעַצְמְךָ.", "מְצֻיָּן! קָרָאת בְּעַצְמֵךְ."),
        G("אֵיזוֹ הַתְמָדָה! כָּל הַכָּבוֹד."),
        G("יֹפִי שֶׁל מַאֲמָץ!"),
    )

    val TOGETHER_PROMPT = G("בּוֹא נְנַסֶּה שׁוּב בְּיַחַד. לְאַט לְאַט.", "בּוֹאִי נְנַסֶּה שׁוּב בְּיַחַד. לְאַט לְאַט.")
    val READ_ALL_DONE = G("עַכְשָׁיו קְרָא אַתָּה אֶת כָּל הַסִּפּוּר!", "עַכְשָׁיו קִרְאִי אַתְּ אֶת כָּל הַסִּפּוּר!")
    const val READ_ALL_DONE_BUTTON = "קָרָאתִי אֶת כָּל הַסִּפּוּר!"
    val COMPLETED = G("כָּל הַכָּבוֹד! סִיַּמְתָּ אֶת הַסִּפּוּר!", "כָּל הַכָּבוֹד! סִיַּמְתְּ אֶת הַסִּפּוּר!")
    val COMPLETED_SUB = G("קָרָאתָ אֶת כָּל הַשּׁוּרוֹת בְּעַצְמְךָ", "קָרָאת אֶת כָּל הַשּׁוּרוֹת בְּעַצְמֵךְ")
    const val TO_NEXT_STORY = "לַסִּפּוּר הַבָּא"
    const val READ_AGAIN = "לִקְרֹא שׁוּב"
    const val TO_LIBRARY = "לַסִּפְרִיָּה"
    const val NEW_MEDAL = "מְדַלְיָה חֲדָשָׁה!"
    const val OWL_GIFT = "יַנְשׁוּפִי קִבֵּל מַתָּנָה חֲדָשָׁה!"

    // ---- Word sheet ----
    const val HEAR_WORD = "לִשְׁמֹעַ"
    const val ADD_TO_MY_WORDS = "לְהוֹסִיף לַמִּלִּים שֶׁלִּי"
    const val IN_MY_WORDS = "בָּרְשִׁימָה שֶׁלִּי"
    const val REMOVE_FROM_LIST = "לְהָסִיר מֵהָרְשִׁימָה"
    const val WORD_SAVED = "הַמִּלָּה נִשְׁמְרָה!"

    // ---- Games ----
    const val GAME_WORD_PICTURE = "מִלָּה וּתְמוּנָה"
    const val GAME_PICTURE_WORD = "תְּמוּנָה וּמִלָּה"
    const val GAME_FIRST_LETTER = "הָאוֹת הָרִאשׁוֹנָה"
    const val GAME_MISSING = "מָה חָסֵר?"
    const val GAME_MEMORY = "מִשְׂחַק זִכָּרוֹן"
    const val GAME_LISTEN = "שׁוֹמְעִים וּבוֹחֲרִים"
    const val Q_WORD_PICTURE = "אֵיזוֹ תְּמוּנָה מַתְאִימָה לַמִּלָּה?"
    const val Q_PICTURE_WORD = "אֵיזוֹ מִלָּה מַתְאִימָה לַתְּמוּנָה?"
    const val Q_FIRST_LETTER = "בְּאֵיזוֹ אוֹת מַתְחִילָה הַמִּלָּה?"
    const val Q_MISSING = "אֵיזוֹ מִלָּה חֲסֵרָה?"
    const val Q_LISTEN = "אֵיזוֹ מִלָּה שָׁמַעְנוּ?"
    const val Q_MEMORY = "מְחַפְּשִׂים זוּגוֹת: מִלָּה וּתְמוּנָה"
    val CORRECT = listOf("נָכוֹן!", "מְצֻיָּן!", "יֹפִי!", "בְּדִיּוּק!")
    const val TRY_AGAIN = "כִּמְעַט! נְנַסֶּה שׁוּב?"
    const val GAME_DONE = "סִיַּמְנוּ אֶת הַמִּשְׂחָק!"
    const val PLAY_AGAIN = "לְשַׂחֵק שׁוּב"
    const val BACK = "חֲזָרָה"
    const val NEED_MORE_WORDS = "צְרִיכִים לְפָחוֹת שָׁלֹשׁ מִלִּים בָּרְשִׁימָה"

    // ---- My words ----
    const val MY_WORDS_EMPTY = "עוֹד אֵין מִלִּים בָּרְשִׁימָה."
    const val MY_WORDS_HOW = "אֶפְשָׁר לִגְעַת בְּמִלָּה בַּסִּפּוּר לְאֹרֶךְ זְמַן וְלִשְׁמֹר אוֹתָהּ."
    const val PRACTICE_MY_WORDS = "לְתַרְגֵּל אֶת הַמִּלִּים"

    // ---- Treasure ----
    fun starsToNextGift(n: Int) = "עוֹד $n כּוֹכָבִים לַמַּתָּנָה הַבָּאָה"
    const val ALL_GIFTS = "כָּל הַמַּתָּנוֹת נִפְתְּחוּ! 👑"
    const val MY_MEDALS = "הַמְּדַלְיוֹת שֶׁלִּי"

    // ---- Speech problems ----
    const val VOLUME_OFF = "הַקּוֹל כָּבוּי. כְּדַאי לְהַגְבִּיר אֶת הַקּוֹל 🔊"
    const val NO_VOICE_CHILD = "אֵין קוֹל עִבְרִי בַּמַּכְשִׁיר. אֶפְשָׁר לְבַקֵּשׁ עֶזְרָה מֵהַהוֹרִים."

    fun <T> pick(list: List<T>, random: Random = Random.Default): T = list[random.nextInt(list.size)]
}
