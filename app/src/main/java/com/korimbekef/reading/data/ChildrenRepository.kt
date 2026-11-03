package com.korimbekef.reading.data

import com.korimbekef.reading.data.db.AppDatabase
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ChildrenRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val progress: ProgressRepository,
) {
    private val dao = db.childDao()

    val children: Flow<List<ChildEntity>> = dao.observeAll()

    /** The active child; falls back to the first profile if the stored id no longer exists. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChild: Flow<ChildEntity?> =
        combine(settings.settings, dao.observeAll()) { s, list ->
            list.firstOrNull { it.id == s.activeChildId } ?: list.firstOrNull()
        }

    suspend fun active(): ChildEntity? = activeChild.first()

    suspend fun get(id: Long): ChildEntity? = dao.get(id)

    suspend fun create(child: ChildEntity, makeActive: Boolean = true): Long {
        val id = dao.insert(child.copy(id = 0))
        if (makeActive) settings.setActiveChild(id)
        return id
    }

    suspend fun update(child: ChildEntity) {
        val old = dao.get(child.id)
        dao.update(child.copy(maxLevel = maxOf(child.maxLevel, child.level)))
        if (old != null && child.level > old.level) {
            progress.awardMedals(child)
        }
    }

    suspend fun setActive(id: Long) = settings.setActiveChild(id)

    suspend fun delete(id: Long) {
        progress.resetChild(id)
        dao.delete(id)
        val s = settings.current()
        if (s.activeChildId == id) {
            dao.first()?.let { settings.setActiveChild(it.id) }
        }
    }
}
