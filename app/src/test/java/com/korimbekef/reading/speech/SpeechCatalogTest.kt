package com.korimbekef.reading.speech

import com.korimbekef.reading.core.ContentParser
import com.korimbekef.reading.core.LineTokens
import com.korimbekef.reading.ui.T
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The recorded voice covers everything the app says with a fixed text.
 * With SPEECH_EXPORT=<file> the catalog is written out for tools/voice/build_speech.py.
 */
class SpeechCatalogTest {

    private fun file(rel: String): File = listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.exists() } ?: File(rel)

    private val passages by lazy { ContentParser.parsePassages(file("src/main/assets/content/passages.json").readText()) }
    private val vocabulary by lazy { ContentParser.parseVocabulary(file("src/main/assets/content/vocabulary.json").readText()) }
    private val entries by lazy { SpeechCatalog.entries(passages, vocabulary) }

    @Test
    fun catalogContainsMessagesLinesAndWords() {
        val kinds = entries.groupingBy { it.kind }.eachCount()
        assertTrue(kinds.toString(), (kinds[SpeechCatalog.KIND_LINE] ?: 0) >= 250)
        assertTrue(kinds.toString(), (kinds[SpeechCatalog.KIND_WORD] ?: 0) >= 500)
        assertTrue(kinds.toString(), (kinds[SpeechCatalog.KIND_VOCAB] ?: 0) >= 60)
        T.SPOKEN.forEach { msg -> assertTrue(msg, entries.any { it.text == SpeechCatalog.key(msg) }) }
        assertEquals(entries.size, entries.map { it.text }.toSet().size)
    }

    @Test
    fun exportForRecording() {
        val out = System.getenv("SPEECH_EXPORT") ?: return
        val json = buildJsonArray {
            entries.forEach { e ->
                val tokens = LineTokens.of(e.text)
                add(
                    buildJsonObject {
                        put("text", e.text)
                        put("kind", e.kind)
                        put("tokens", JsonArray((0 until tokens.wordCount).map { JsonPrimitive(tokens.word(it)) }))
                        put("cores", JsonArray((0 until tokens.wordCount).map { JsonPrimitive(tokens.speechWord(it)) }))
                    },
                )
            }
        }
        File(out).apply { parentFile?.mkdirs() }.writeText(json.toString())
    }

    @Test
    fun everyEntryHasARecordingWithMatchingWords() {
        val manifestFile = file("src/main/assets/speech/manifest.json")
        if (!manifestFile.exists()) return // recordings not generated yet
        val manifest = SpeechManifest.parse(manifestFile.readText())
        val missing = entries.filter { manifest.find(it.text) == null }
        assertTrue("${missing.size} without recording, e.g. ${missing.take(5).map { it.text }}", missing.isEmpty())
        val assets = manifestFile.parentFile
        for (e in entries) {
            val r = manifest.find(e.text)!!
            assertEquals("word timings of \"${e.text}\"", LineTokens.of(e.text).wordCount, r.words.size)
            assertTrue("audio file ${r.file}", File(assets, r.file).isFile)
            assertTrue("duration of \"${e.text}\"", r.durationMs > 0 && r.words.all { it.endMs <= r.durationMs + 50 })
        }
    }

    @Test
    fun wordAtFollowsTheTimeline() {
        val r = Recording("x.mp3", 2000, listOf(WordTime(100, 500), WordTime(600, 1000), WordTime(1100, 1900)))
        assertEquals(-1, r.wordAt(0))
        assertEquals(0, r.wordAt(50))
        assertEquals(0, r.wordAt(500))
        assertEquals(1, r.wordAt(560))
        assertEquals(2, r.wordAt(1950))
    }

    @Test
    fun manifestParsesAndNormalizesKeys() {
        val m = SpeechManifest.parse("""{"version":1,"voice":"noa","items":{"שָׁלוֹם  לְכֻלָּם":{"f":"a.mp3","d":1200,"w":[[80,500],[560,1150]]}}}""")
        val r = m.find("שָׁלוֹם לְכֻלָּם")
        assertEquals("a.mp3", r?.file)
        assertEquals(2, r?.words?.size)
        assertEquals("noa", m.voice)
    }
}
