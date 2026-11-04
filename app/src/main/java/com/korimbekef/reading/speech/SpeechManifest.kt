package com.korimbekef.reading.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a word starts and ends inside a recording, in milliseconds of audio. */
data class WordTime(val startMs: Int, val endMs: Int)

/** One recorded sentence (or single word) of the app's voice. */
data class Recording(val file: String, val durationMs: Int, val words: List<WordTime>) {
    /**
     * Index of the word heard at [positionMs]: the last word that has started (a little early,
     * [leadMs], so the highlight appears together with the sound). -1 before the first word.
     */
    fun wordAt(positionMs: Int, leadMs: Int = 60): Int {
        var ans = -1
        for (i in words.indices) {
            if (words[i].startMs <= positionMs + leadMs) ans = i else break
        }
        return ans
    }
}

/** The list of recordings shipped in assets/speech, keyed by their text (see [SpeechCatalog.key]). */
class SpeechManifest(val voice: String, private val items: Map<String, Recording>) {
    val size: Int get() = items.size

    fun find(text: String): Recording? = items[SpeechCatalog.key(text)]

    @Serializable
    private data class ItemJson(val f: String, val d: Int, val w: List<List<Int>> = emptyList())

    @Serializable
    private data class ManifestJson(val version: Int = 1, val voice: String = "", val items: Map<String, ItemJson> = emptyMap())

    companion object {
        val EMPTY = SpeechManifest("", emptyMap())
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): SpeechManifest {
            val m = json.decodeFromString(ManifestJson.serializer(), text)
            val items = m.items.mapValues { (_, v) ->
                Recording(v.f, v.d, v.w.map { WordTime(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }) })
            }.mapKeys { SpeechCatalog.key(it.key) }
            return SpeechManifest(m.voice, items)
        }
    }
}
