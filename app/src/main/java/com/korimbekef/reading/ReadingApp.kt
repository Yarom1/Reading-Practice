package com.korimbekef.reading

import android.app.Application
import com.korimbekef.reading.speech.SpeechController
import com.korimbekef.reading.speech.TtsSpeechController

open class ReadingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this) { createSpeechController(it) }
        // Start the speech engine early so the first line is read without delay.
        container.speech
    }

    /** Tests replace the real engine with a scripted fake. */
    protected open fun createSpeechController(container: AppContainer): SpeechController =
        TtsSpeechController(this, container.settings, container.scope)
}
