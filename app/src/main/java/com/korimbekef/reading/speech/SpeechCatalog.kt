package com.korimbekef.reading.speech

import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.core.VocabWord
import com.korimbekef.reading.ui.T

/**
 * Everything the app says with a fixed text. The recorded voice is generated from exactly this
 * list (see tools/voice), and a test checks that every entry has a recording.
 */
object SpeechCatalog {
    data class Entry(val text: String, val kind: String)

    const val KIND_MESSAGE = "msg"
    const val KIND_LINE = "line"
    const val KIND_VOCAB = "vocab"
    const val KIND_WORD = "word"

    /** Lookup key of a recording: the text in canonical form (NFC, single spaces). */
    fun key(text: String): String = HebrewText.normalize(text)

    fun entries(passages: List<Passage>, vocabulary: List<VocabWord>): List<Entry> {
        val out = LinkedHashMap<String, Entry>()
        fun add(text: String, kind: String) {
            val k = key(text)
            if (k.isNotEmpty() && k !in out) out[k] = Entry(k, kind)
        }
        T.SPOKEN.forEach { add(it, KIND_MESSAGE) }
        val builtIn = passages.filter { !it.isCustom }
        builtIn.forEach { p -> p.lines.forEach { add(it.nikud, KIND_LINE) } }
        vocabulary.forEach { add(it.word.nikud, KIND_VOCAB) }
        // single words, for a tap on a word and for slow word-by-word reading
        builtIn.forEach { p ->
            p.lines.forEach { line ->
                val tokens = line.nikudTokens
                for (i in 0 until tokens.wordCount) add(tokens.speechWord(i), KIND_WORD)
            }
        }
        return out.values.toList()
    }
}
