package com.korimbekef.reading.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ------------------------------------------------------------------ Entities

@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "boy" or "girl" — only used to address the child with the right Hebrew forms. */
    val gender: String = GENDER_BOY,
    val avatar: String = "🦁",
    val level: Int = 1,
    /** Highest level the child may open (the parent unlocks levels). */
    val maxLevel: Int = 1,
    val withNikud: Boolean = true,
    /** 0..3, see [com.korimbekef.reading.ui.screens.reading.FontSteps]. */
    val fontStep: Int = 1,
    val speechRate: Float = 0.85f,
    val dailyGoalMinutes: Int = 10,
    val gamification: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isGirl: Boolean get() = gender == GENDER_GIRL

    companion object {
        const val GENDER_BOY = "boy"
        const val GENDER_GIRL = "girl"
    }
}

@Entity(tableName = "custom_passages")
data class CustomPassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val level: Int,
    val emoji: String,
    /** One reading line per text line; may include nikud. */
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sessions", indices = [Index("childId"), Index("passageId")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val passageId: String,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val durationSeconds: Int = 0,
    val currentLine: Int = 0,
    /** Comma separated indexes of lines the child marked "I read it" (self report). */
    val linesRead: String = "",
    /** Comma separated indexes of lines the app read aloud at least once. */
    val linesHeard: String = "",
    val listenCount: Int = 0,
    val wordsHeard: Int = 0,
    val starsEarned: Int = 0,
) {
    val readSet: Set<Int> get() = parseSet(linesRead)
    val heardSet: Set<Int> get() = parseSet(linesHeard)

    companion object {
        fun parseSet(s: String): Set<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        fun joinSet(set: Set<Int>): String = set.sorted().joinToString(",")
    }
}

@Entity(
    tableName = "line_practice",
    primaryKeys = ["sessionId", "lineIndex"],
    indices = [Index("childId")],
)
data class LinePracticeEntity(
    val sessionId: Long,
    val lineIndex: Int,
    val childId: Long,
    val passageId: String,
    val listenedCount: Int = 0,
    val markedAsRead: Boolean = false,
    val practicedAt: Long,
)

@Entity(tableName = "passage_progress", primaryKeys = ["childId", "passageId"])
data class PassageProgressEntity(
    val childId: Long,
    val passageId: String,
    val timesCompleted: Int = 0,
    val lastCompletedAt: Long? = null,
    val lastOpenedAt: Long = 0,
)

/** Per-day totals; [day] is the local epoch day. */
@Entity(tableName = "daily_activity", primaryKeys = ["childId", "day"])
data class DailyActivityEntity(
    val childId: Long,
    val day: Long,
    val seconds: Int = 0,
    val linesHeard: Int = 0,
    val linesRead: Int = 0,
    val wordsHeard: Int = 0,
    val wordsRead: Int = 0,
    val stars: Int = 0,
    val passagesCompleted: Int = 0,
    val gamesPlayed: Int = 0,
) {
    val isPracticeDay: Boolean get() = seconds >= 60 || linesRead > 0 || gamesPlayed > 0 || passagesCompleted > 0
}

@Entity(tableName = "medals", primaryKeys = ["childId", "code"])
data class MedalEntity(
    val childId: Long,
    val code: String,
    val earnedAt: Long,
)

@Entity(
    tableName = "practice_words",
    indices = [Index(value = ["childId", "plain"], unique = true)],
)
data class PracticeWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val word: String,
    val plain: String,
    val addedByParent: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

// ------------------------------------------------------------------ DAOs

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY id")
    fun observeAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE id = :id")
    fun observe(id: Long): Flow<ChildEntity?>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun get(id: Long): ChildEntity?

    @Query("SELECT * FROM children ORDER BY id LIMIT 1")
    suspend fun first(): ChildEntity?

    @Insert
    suspend fun insert(child: ChildEntity): Long

    @Update
    suspend fun update(child: ChildEntity)

    @Query("DELETE FROM children WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CustomPassageDao {
    @Query("SELECT * FROM custom_passages ORDER BY createdAt")
    fun observeAll(): Flow<List<CustomPassageEntity>>

    @Insert
    suspend fun insert(passage: CustomPassageEntity): Long

    @Query("DELETE FROM custom_passages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query(
        "SELECT * FROM sessions WHERE childId = :childId AND completedAt IS NULL " +
            "AND (linesRead != '' OR listenCount > 0) ORDER BY updatedAt DESC LIMIT 1",
    )
    fun observeLatestUnfinished(childId: Long): Flow<SessionEntity?>

    @Query(
        "SELECT * FROM sessions WHERE childId = :childId AND passageId = :passageId " +
            "AND completedAt IS NULL ORDER BY updatedAt DESC LIMIT 1",
    )
    suspend fun latestUnfinishedFor(childId: Long, passageId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE childId = :childId AND (listenCount > 0 OR linesRead != '' OR completedAt IS NOT NULL) ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(childId: Long, limit: Int): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

@Dao
interface LinePracticeDao {
    @Query("SELECT * FROM line_practice WHERE sessionId = :sessionId AND lineIndex = :lineIndex")
    suspend fun get(sessionId: Long, lineIndex: Int): LinePracticeEntity?

    @Upsert
    suspend fun upsert(entity: LinePracticeEntity)

    @Query("DELETE FROM line_practice WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

@Dao
interface PassageProgressDao {
    @Query("SELECT * FROM passage_progress WHERE childId = :childId")
    fun observeForChild(childId: Long): Flow<List<PassageProgressEntity>>

    @Query("SELECT * FROM passage_progress WHERE childId = :childId")
    suspend fun allForChild(childId: Long): List<PassageProgressEntity>

    @Query("SELECT * FROM passage_progress WHERE childId = :childId AND passageId = :passageId")
    suspend fun get(childId: Long, passageId: String): PassageProgressEntity?

    @Upsert
    suspend fun upsert(entity: PassageProgressEntity)

    @Query("DELETE FROM passage_progress WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE childId = :childId AND day = :day")
    suspend fun get(childId: Long, day: Long): DailyActivityEntity?

    @Upsert
    suspend fun upsert(entity: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE childId = :childId ORDER BY day")
    fun observeForChild(childId: Long): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activity WHERE childId = :childId ORDER BY day")
    suspend fun allForChild(childId: Long): List<DailyActivityEntity>

    @Query("DELETE FROM daily_activity WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

@Dao
interface MedalDao {
    @Query("SELECT * FROM medals WHERE childId = :childId ORDER BY earnedAt")
    fun observeForChild(childId: Long): Flow<List<MedalEntity>>

    @Query("SELECT code FROM medals WHERE childId = :childId")
    suspend fun codes(childId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(medal: MedalEntity)

    @Query("DELETE FROM medals WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

@Dao
interface PracticeWordDao {
    @Query("SELECT * FROM practice_words WHERE childId = :childId ORDER BY addedAt DESC")
    fun observeForChild(childId: Long): Flow<List<PracticeWordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: PracticeWordEntity): Long

    @Query("DELETE FROM practice_words WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM practice_words WHERE childId = :childId AND plain = :plain")
    suspend fun deleteByPlain(childId: Long, plain: String)

    @Query("SELECT COUNT(*) FROM practice_words WHERE childId = :childId")
    suspend fun count(childId: Long): Int

    @Query("DELETE FROM practice_words WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}

// ------------------------------------------------------------------ Database

@Database(
    entities = [
        ChildEntity::class,
        CustomPassageEntity::class,
        SessionEntity::class,
        LinePracticeEntity::class,
        PassageProgressEntity::class,
        DailyActivityEntity::class,
        MedalEntity::class,
        PracticeWordEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun customPassageDao(): CustomPassageDao
    abstract fun sessionDao(): SessionDao
    abstract fun linePracticeDao(): LinePracticeDao
    abstract fun passageProgressDao(): PassageProgressDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun medalDao(): MedalDao
    abstract fun practiceWordDao(): PracticeWordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "korim_bekef.db").build()
    }
}
