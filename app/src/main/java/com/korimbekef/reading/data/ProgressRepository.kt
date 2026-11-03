package com.korimbekef.reading.data

import androidx.room.withTransaction
import com.korimbekef.reading.core.ChildStats
import com.korimbekef.reading.core.MedalDef
import com.korimbekef.reading.core.Medals
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.core.Rewards
import com.korimbekef.reading.core.Streaks
import com.korimbekef.reading.data.db.AppDatabase
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.DailyActivityEntity
import com.korimbekef.reading.data.db.LinePracticeEntity
import com.korimbekef.reading.data.db.MedalEntity
import com.korimbekef.reading.data.db.PassageProgressEntity
import com.korimbekef.reading.data.db.PracticeWordEntity
import com.korimbekef.reading.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class ChildSummary(
    val totalStars: Int = 0,
    val todaySeconds: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalSeconds: Int = 0,
    val linesRead: Int = 0,
    val linesHeard: Int = 0,
    val wordsHeard: Int = 0,
    val wordsRead: Int = 0,
    val passagesCompleted: Int = 0,
    val gamesPlayed: Int = 0,
    val medals: List<MedalEntity> = emptyList(),
    val days: List<DailyActivityEntity> = emptyList(),
    val progress: Map<String, PassageProgressEntity> = emptyMap(),
)

data class CompletionResult(
    val starsAdded: Int,
    val timesCompleted: Int,
    val newMedals: List<MedalDef>,
)

/**
 * All progress bookkeeping. Note the deliberate distinction between what the app read aloud
 * ("heard") and what the child reported reading by themselves ("read") — nothing here is a
 * reading-accuracy measurement.
 */
class ProgressRepository(private val db: AppDatabase) {
    private val sessions = db.sessionDao()
    private val lines = db.linePracticeDao()
    private val progress = db.passageProgressDao()
    private val activity = db.dailyActivityDao()
    private val medals = db.medalDao()
    private val words = db.practiceWordDao()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun observeSummary(childId: Long): Flow<ChildSummary> =
        combine(
            activity.observeForChild(childId),
            medals.observeForChild(childId),
            progress.observeForChild(childId),
        ) { days, medalList, progressList ->
            val today = today()
            val practiceDays = days.filter { it.isPracticeDay }.map { it.day }.toSet()
            ChildSummary(
                totalStars = days.sumOf { it.stars },
                todaySeconds = days.firstOrNull { it.day == today }?.seconds ?: 0,
                currentStreak = Streaks.current(practiceDays, today),
                bestStreak = Streaks.best(practiceDays),
                totalSeconds = days.sumOf { it.seconds },
                linesRead = days.sumOf { it.linesRead },
                linesHeard = days.sumOf { it.linesHeard },
                wordsHeard = days.sumOf { it.wordsHeard },
                wordsRead = days.sumOf { it.wordsRead },
                passagesCompleted = progressList.count { it.timesCompleted > 0 },
                gamesPlayed = days.sumOf { it.gamesPlayed },
                medals = medalList,
                days = days,
                progress = progressList.associateBy { it.passageId },
            )
        }

    fun observeLatestUnfinished(childId: Long) = sessions.observeLatestUnfinished(childId)

    fun observeRecentSessions(childId: Long, limit: Int = 30) = sessions.observeRecent(childId, limit)

    fun observeProgress(childId: Long) = progress.observeForChild(childId)

    /** Continues an unfinished session of this passage, or starts a new one. */
    suspend fun openSession(childId: Long, passageId: String, forceNew: Boolean): SessionEntity {
        val now = System.currentTimeMillis()
        val existing = if (forceNew) null else sessions.latestUnfinishedFor(childId, passageId)
        val p = progress.get(childId, passageId) ?: PassageProgressEntity(childId, passageId)
        progress.upsert(p.copy(lastOpenedAt = now))
        if (existing != null) return existing
        val s = SessionEntity(childId = childId, passageId = passageId, startedAt = now, updatedAt = now)
        return s.copy(id = sessions.insert(s))
    }

    suspend fun saveCurrentLine(session: SessionEntity, line: Int): SessionEntity {
        val updated = session.copy(currentLine = line, updatedAt = System.currentTimeMillis())
        sessions.update(updated)
        return updated
    }

    suspend fun recordListen(session: SessionEntity, lineIndex: Int, wordCount: Int): SessionEntity =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val current = sessions.get(session.id) ?: session
            val heard = current.heardSet
            val firstTime = lineIndex !in heard
            val updated = current.copy(
                linesHeard = SessionEntity.joinSet(heard + lineIndex),
                listenCount = current.listenCount + 1,
                wordsHeard = current.wordsHeard + wordCount,
                currentLine = lineIndex,
                updatedAt = now,
            )
            sessions.update(updated)
            val lp = lines.get(session.id, lineIndex)
                ?: LinePracticeEntity(session.id, lineIndex, session.childId, session.passageId, practicedAt = now)
            lines.upsert(lp.copy(listenedCount = lp.listenedCount + 1, practicedAt = now))
            bumpDay(session.childId) {
                it.copy(
                    linesHeard = it.linesHeard + if (firstTime) 1 else 0,
                    wordsHeard = it.wordsHeard + wordCount,
                )
            }
            updated
        }

    /** The child pressed "I finished reading" for a line. Returns the updated session and stars added. */
    suspend fun recordLineRead(session: SessionEntity, lineIndex: Int, wordCount: Int, gamification: Boolean): Pair<SessionEntity, Int> =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val current = sessions.get(session.id) ?: session
            val read = current.readSet
            if (lineIndex in read) return@withTransaction current to 0
            val stars = if (gamification) Rewards.STARS_PER_LINE_READ else 0
            val updated = current.copy(
                linesRead = SessionEntity.joinSet(read + lineIndex),
                starsEarned = current.starsEarned + stars,
                currentLine = lineIndex,
                updatedAt = now,
            )
            sessions.update(updated)
            val lp = lines.get(session.id, lineIndex)
                ?: LinePracticeEntity(session.id, lineIndex, session.childId, session.passageId, practicedAt = now)
            lines.upsert(lp.copy(markedAsRead = true, practicedAt = now))
            bumpDay(session.childId) {
                it.copy(linesRead = it.linesRead + 1, wordsRead = it.wordsRead + wordCount, stars = it.stars + stars)
            }
            updated to stars
        }

    /** Marks all remaining lines as read (used after "read the whole story by yourself"). */
    suspend fun recordAllLinesRead(session: SessionEntity, passage: Passage, gamification: Boolean): Pair<SessionEntity, Int> {
        var s = session
        var total = 0
        for (i in passage.lines.indices) {
            if (i !in s.readSet) {
                val (u, stars) = recordLineRead(s, i, passage.lines[i].wordCount, gamification)
                s = u
                total += stars
            }
        }
        return s to total
    }

    suspend fun addTime(childId: Long, sessionId: Long?, seconds: Int) {
        if (seconds <= 0) return
        db.withTransaction {
            if (sessionId != null) {
                sessions.get(sessionId)?.let {
                    sessions.update(it.copy(durationSeconds = it.durationSeconds + seconds, updatedAt = System.currentTimeMillis()))
                }
            }
            bumpDay(childId) { it.copy(seconds = it.seconds + seconds) }
        }
    }

    suspend fun completeSession(session: SessionEntity, child: ChildEntity): CompletionResult {
        val result = db.withTransaction {
            val now = System.currentTimeMillis()
            val current = sessions.get(session.id) ?: session
            if (current.completedAt != null) {
                val p = progress.get(child.id, session.passageId)
                return@withTransaction CompletionResult(0, p?.timesCompleted ?: 0, emptyList())
            }
            val stars = if (child.gamification) Rewards.STARS_PER_PASSAGE else 0
            sessions.update(current.copy(completedAt = now, updatedAt = now, starsEarned = current.starsEarned + stars))
            val p = progress.get(child.id, session.passageId) ?: PassageProgressEntity(child.id, session.passageId)
            val times = p.timesCompleted + 1
            progress.upsert(p.copy(timesCompleted = times, lastCompletedAt = now, lastOpenedAt = now))
            bumpDay(child.id) { it.copy(passagesCompleted = it.passagesCompleted + 1, stars = it.stars + stars) }
            CompletionResult(stars, times, emptyList())
        }
        return result.copy(newMedals = awardMedals(child))
    }

    suspend fun recordGame(child: ChildEntity): Pair<Int, List<MedalDef>> {
        val stars = if (child.gamification) Rewards.STARS_PER_GAME_ROUND else 0
        db.withTransaction {
            bumpDay(child.id) { it.copy(gamesPlayed = it.gamesPlayed + 1, stars = it.stars + stars) }
        }
        return stars to awardMedals(child)
    }

    /** Checks all medal rules and stores newly earned medals. */
    suspend fun awardMedals(child: ChildEntity): List<MedalDef> {
        if (!child.gamification) return emptyList()
        val days = activity.allForChild(child.id)
        val practiceDays = days.filter { it.isPracticeDay }.map { it.day }.toSet()
        val progressList = progress.allForChild(child.id)
        val stats = ChildStats(
            linesRead = days.sumOf { it.linesRead },
            passagesCompleted = progressList.count { it.timesCompleted > 0 },
            bestStreak = Streaks.best(practiceDays),
            maxCompletionsOfOnePassage = progressList.maxOfOrNull { it.timesCompleted } ?: 0,
            gamesPlayed = days.sumOf { it.gamesPlayed },
            level = child.level,
            goalDays = days.count { it.seconds >= child.dailyGoalMinutes * 60 },
            savedWords = words.count(child.id),
        )
        val have = medals.codes(child.id).toSet()
        val now = System.currentTimeMillis()
        val earned = Medals.all.filter { it.code !in have && it.earned(stats) }
        earned.forEach { medals.insert(MedalEntity(child.id, it.code, now)) }
        return earned
    }

    // ---- personal word list ----

    fun observeWords(childId: Long) = words.observeForChild(childId)

    suspend fun addWord(childId: Long, word: String, plain: String, byParent: Boolean): Boolean {
        val id = words.insert(PracticeWordEntity(childId = childId, word = word, plain = plain, addedByParent = byParent))
        return id > 0
    }

    suspend fun removeWord(id: Long) = words.delete(id)

    suspend fun removeWordByPlain(childId: Long, plain: String) = words.deleteByPlain(childId, plain)

    // ---- reset ----

    suspend fun resetChild(childId: Long) {
        db.withTransaction {
            sessions.deleteForChild(childId)
            lines.deleteForChild(childId)
            progress.deleteForChild(childId)
            activity.deleteForChild(childId)
            medals.deleteForChild(childId)
            words.deleteForChild(childId)
        }
    }

    private suspend fun bumpDay(childId: Long, change: (DailyActivityEntity) -> DailyActivityEntity) {
        val day = today()
        val current = activity.get(childId, day) ?: DailyActivityEntity(childId = childId, day = day)
        activity.upsert(change(current))
    }
}
