package com.korimbekef.reading

import android.content.Context
import com.korimbekef.reading.audio.SoundEffects
import com.korimbekef.reading.data.ChildrenRepository
import com.korimbekef.reading.data.ProgressRepository
import com.korimbekef.reading.data.content.ContentRepository
import com.korimbekef.reading.data.db.AppDatabase
import com.korimbekef.reading.data.prefs.SettingsRepository
import com.korimbekef.reading.data.prefs.createSettingsDataStore
import com.korimbekef.reading.speech.SpeechController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container, created once by [ReadingApp]. */
class AppContainer(
    context: Context,
    speechFactory: (AppContainer) -> SpeechController,
) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(createSettingsDataStore(appContext)) }
    val content: ContentRepository by lazy { ContentRepository(appContext, db.customPassageDao(), settings) }
    val progress: ProgressRepository by lazy { ProgressRepository(db) }
    val children: ChildrenRepository by lazy { ChildrenRepository(db, settings, progress) }
    val speech: SpeechController by lazy { speechFactory(this) }
    val sounds: SoundEffects by lazy { SoundEffects(appContext, settings, scope) }
}
