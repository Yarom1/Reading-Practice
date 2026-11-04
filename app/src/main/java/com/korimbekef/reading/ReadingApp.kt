package com.korimbekef.reading

import android.app.Application
import com.korimbekef.reading.speech.HybridSpeechController
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

    /**
     * The recorded voice for built-in texts, the device's text-to-speech for everything else.
     * Tests replace it with a scripted fake.
     */
    protected open fun createSpeechController(container: AppContainer): SpeechController =
        HybridSpeechController(
            this,
            container.settings,
            container.scope,
            device = TtsSpeechController(this, container.settings, container.scope),
        )
}
