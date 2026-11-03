package com.korimbekef.reading.data.content

import android.content.Context
import com.korimbekef.reading.core.ContentParser
import com.korimbekef.reading.core.Dictionary
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.core.PassageKind
import com.korimbekef.reading.core.VocabWord
import com.korimbekef.reading.data.db.CustomPassageDao
import com.korimbekef.reading.data.db.CustomPassageEntity
import com.korimbekef.reading.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Built-in graded passages (assets/content) plus passages the parent added. */
class ContentRepository(
    private val context: Context,
    private val customDao: CustomPassageDao,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private var builtInCache: List<Passage>? = null
    private var vocabCache: List<VocabWord>? = null
    private var dictionaryCache: Dictionary? = null

    suspend fun builtIn(): List<Passage> = mutex.withLock {
        builtInCache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open("content/passages.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            ContentParser.parsePassages(text)
        }.also { builtInCache = it }
    }

    suspend fun vocabulary(): List<VocabWord> = mutex.withLock {
        vocabCache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open("content/vocabulary.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            ContentParser.parseVocabulary(text)
        }.also { vocabCache = it }
    }

    suspend fun dictionary(): Dictionary {
        dictionaryCache?.let { return it }
        return Dictionary(vocabulary()).also { dictionaryCache = it }
    }

    /** Every passage, including hidden ones (used by the parent zone). */
    val allPassages: Flow<List<Passage>> =
        combine(flow { emit(builtIn()) }.flowOn(Dispatchers.IO), customDao.observeAll()) { builtIn, custom ->
            builtIn + custom.map { it.toPassage() }
        }

    /** Passages the child can see. */
    val visiblePassages: Flow<List<Passage>> =
        combine(allPassages, settings.settings) { list, s -> list.filter { it.id !in s.hiddenPassages } }

    suspend fun passage(id: String): Passage? = allPassages.first().firstOrNull { it.id == id }

    suspend fun addCustom(title: String, level: Int, emoji: String, text: String): Long =
        customDao.insert(CustomPassageEntity(title = title.trim(), level = level, emoji = emoji, text = text.trim()))

    suspend fun deleteCustom(passageId: String) {
        customIdOf(passageId)?.let { customDao.delete(it) }
    }

    companion object {
        const val CUSTOM_PREFIX = "custom-"

        fun customIdOf(passageId: String): Long? =
            if (passageId.startsWith(CUSTOM_PREFIX)) passageId.removePrefix(CUSTOM_PREFIX).toLongOrNull() else null

        fun CustomPassageEntity.toPassage(): Passage = Passage(
            id = "$CUSTOM_PREFIX$id",
            level = level,
            title = DualText.ofUserText(title),
            emoji = emoji,
            colorIndex = (id % 6).toInt(),
            kind = PassageKind.STORY,
            lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { DualText.ofUserText(it) },
            isCustom = true,
        )
    }
}
