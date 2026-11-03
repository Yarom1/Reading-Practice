package com.korimbekef.reading

import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.PinHasher
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.speech.SpeakRequest
import com.korimbekef.reading.ui.T
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end flows on Robolectric with a scripted speech engine. Each test also saves
 * screenshots (build/screenshots) that CI publishes for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val app: TestApp get() = RuntimeEnvironment.getApplication() as TestApp
    private val fake: FakeSpeechController get() = TestApp.fake

    @Before
    fun setUp() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 8, 0)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    // ------------------------------------------------------------------ helpers

    private fun seed(name: String = "דני", girl: Boolean = false, level: Int = 1) = runBlocking {
        val c = app.container
        c.children.create(
            ChildEntity(
                name = name,
                gender = if (girl) ChildEntity.GENDER_GIRL else ChildEntity.GENDER_BOY,
                level = level,
                maxLevel = level,
                fontStep = 1,
            ),
        )
        val salt = PinHasher.newSalt()
        c.settings.setPin(PinHasher.hash("1234", salt), salt)
        c.settings.setOnboardingDone(true)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun shot(name: String) {
        var activity: android.app.Activity? = null
        scenario?.onActivity { activity = it }
        Screenshots.capture(rule, name, activity)
    }

    private fun content(raw: String): String = DualText.parse(raw).nikud

    private fun waitText(text: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun node(text: String): SemanticsNodeInteraction {
        waitText(text)
        return rule.onAllNodesWithText(text, substring = true).onFirst()
    }

    private fun click(text: String) {
        node(text).performClick()
        rule.waitForIdle()
    }

    private fun clickDescription(description: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasContentDescription(description)).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun exists(text: String): Boolean =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        runCatching { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText(text, substring = true)) }
        rule.waitForIdle()
    }

    private fun openStory(title: String) {
        click(T.MY_LIBRARY)
        scrollTo(content(title))
        click(content(title))
        waitText(T.LISTEN)
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun onboardingCreatesProfileAndShowsHome() {
        launch()
        waitText("בואו נתחיל")
        shot("01_onboarding_welcome")
        click("בואו נתחיל")
        node("שם הילד או הילדה").performTextInput("נועה")
        click("בת")
        shot("02_onboarding_child")
        click("המשך")
        waitText("מאיזו רמה להתחיל?")
        shot("03_onboarding_level")
        click("המשך")
        waitText("בדיקת קול")
        shot("04_onboarding_voice")
        click("המשך")
        waitText("בחרו קוד הורים")
        listOf("1", "2", "3", "4").forEach { click(it) }
        waitText("הקישו שוב את הקוד")
        listOf("1", "2", "3", "4").forEach { click(it) }
        // a girl is addressed in the feminine form
        waitText(T.START_STORY.f)
        shot("05_home_girl")
        val child = runBlocking { app.container.children.active() }
        assertEquals("נועה", child?.name)
        assertTrue(child?.isGirl == true)
    }

    @Test
    fun readingLineHighlightsWordsAndWaitsForChild() {
        seed()
        launch()
        waitText(T.START_STORY.m)
        shot("10_home")
        click(T.MY_LIBRARY)
        waitText(content("אַבָּא בָּא"))
        shot("11_library")
        click(content("אַבָּא בָּא"))
        waitText(T.LISTEN)
        shot("12_reading")

        // 1) tapping a line reads it
        click(content("אִמָּא בָּאָה."))
        val first = fake.requests.last().second
        assertEquals(1, first.startLine)
        assertEquals(SpeakRequest.TAG_LINE, first.tag)

        // 2) the highlight follows the engine's word events
        fake.emitWord(1, 1)
        rule.waitForIdle()
        shot("13_reading_highlight")

        // 3) tapping another line while speaking starts the new line (the engine flushes the old one)
        click(content("סַבָּא וְסַבְתָּא בָּאִים."))
        val second = fake.requests.last().second
        assertEquals(2, second.startLine)
        fake.emitWord(2, 0)
        rule.waitForIdle()

        // 4) after the line ends the child is invited to read; nothing moves on by itself
        val requestsBefore = fake.requests.size
        fake.finish()
        waitText(T.DONE_READING)
        shot("14_reading_your_turn")
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        assertEquals("no automatic reading of the next line", requestsBefore, fake.requests.size)

        // 5) "I finished reading" marks the line and praises effort
        click(T.DONE_READING)
        rule.waitUntil(10_000) { runBlocking { app.container.progress.observeSummary(app.container.children.active()!!.id).first().linesRead } == 1 }
        shot("15_reading_after_done")

        // 6) listen again replays the focused line from its first word
        clickDescription(T.AGAIN)
        val replay = fake.requests.last().second
        assertEquals(0, replay.startWord)

        // 7) switching nikud off shows the plain text
        click(T.NIKUD)
        waitText("סבא וסבתא באים.")
        shot("16_reading_no_nikud")
    }

    @Test
    fun finishingAStoryCelebratesAndProgressIsSaved() {
        seed()
        launch()
        waitText(T.START_STORY.m)
        openStory("הַשֶּׁמֶשׁ")
        val lines = listOf("הִנֵּה הַשֶּׁמֶשׁ.", "הַשֶּׁמֶשׁ חַמָּה.", "{הַשָּׁמַיִם|השמיים} {כְּחֻלִּים|כחולים}.", "נֵצֵא לְשַׂחֵק בַּחוּץ!")
        lines.forEach { raw ->
            click(content(raw))
            fake.finish()
            click(T.DONE_READING)
        }
        waitText(T.COMPLETED.m)
        shot("20_story_completed")
        val summary = runBlocking { app.container.progress.observeSummary(app.container.children.active()!!.id).first() }
        assertEquals(4, summary.linesRead)
        assertEquals(1, summary.passagesCompleted)
        assertEquals(4 + 3, summary.totalStars)

        // progress survives closing and reopening the app
        scenario?.close()
        launch()
        waitText(T.START_STORY.m)
        waitText("7")
        shot("21_home_after_story")
    }

    @Test
    fun aloneModeAndWordCard() {
        seed(level = 2)
        launch()
        waitText(T.START_STORY.m)
        openStory("{הַצִּפּוֹר|הציפור} הַקְּטַנָּה")
        clickDescription(T.READ_SETTINGS)
        waitText(T.FONT_SIZE)
        shot("30_reading_settings")
        click(T.MODE_ALONE)
        rule.waitForIdle()
        click(T.OK)
        shot("31_reading_alone_mode")
        node(content("{הַצִּפּוֹר|הציפור} שָׁרָה שִׁיר.")).performTouchInput { longClick(center) }
        waitText(T.ADD_TO_MY_WORDS)
        shot("32_word_card")
        click(T.ADD_TO_MY_WORDS)
        waitText(T.IN_MY_WORDS)
    }

    @Test
    fun parentZoneIsProtectedByPin() {
        seed()
        launch()
        waitText(T.START_STORY.m)
        clickDescription(T.PARENTS)
        waitText("הקישו את קוד ההורים")
        listOf("0", "0", "0", "0").forEach { click(it) }
        waitText("קוד שגוי")
        shot("40_parent_pin_wrong")
        listOf("1", "2", "3", "4").forEach { click(it) }
        waitText("דוח התקדמות")
        shot("41_parent_home")
        click("דוח התקדמות")
        waitText("היסטוריית תרגול")
        shot("42_parent_report")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("קול והקראה")
        waitText("בחירת קול")
        shot("43_parent_voice")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("הגדרות הילד/ה")
        waitText("רמת קריאה")
        shot("44_parent_child_settings")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("קטעי קריאה")
        waitText("הוספת קטע קריאה משלכם")
        shot("45_parent_content")
    }

    @Test
    fun gamesTreasureAndWords() {
        seed()
        launch()
        waitText(T.START_STORY.m)
        click(T.MY_GAMES)
        waitText(T.GAME_MEMORY)
        shot("50_games")
        click(T.GAME_WORD_PICTURE)
        waitText(T.Q_WORD_PICTURE)
        shot("51_game_word_picture")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click(T.GAME_MISSING)
        waitText(T.Q_MISSING)
        shot("52_game_missing_word")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click(T.GAME_MEMORY)
        waitText(T.Q_MEMORY)
        shot("53_game_memory")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click(T.MY_TREASURE)
        waitText(T.MY_MEDALS)
        shot("54_treasure")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click(T.MY_WORDS)
        waitText(T.MY_WORDS_EMPTY)
        shot("55_my_words")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletLayout() {
        seed(level = 3)
        launch()
        waitText(T.START_STORY.m)
        shot("60_tablet_home")
        openStory("הַטִּיּוּל שֶׁל דָּנִי")
        click(content("דָּנִי יָצָא {לְטַיֵּל|לטייל} {בַּגִּנָּה|בגינה}."))
        fake.emitWord(0, 2)
        rule.waitForIdle()
        shot("61_tablet_reading")
        assertTrue(exists(T.PAUSE))
    }
}
