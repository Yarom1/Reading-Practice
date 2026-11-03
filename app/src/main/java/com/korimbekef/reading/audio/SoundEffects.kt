package com.korimbekef.reading.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.korimbekef.reading.R
import com.korimbekef.reading.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Sfx { STAR, CORRECT, FANFARE, SOFT, POP }

/** Short, gentle sound effects (generated tones, no recordings). Can be turned off by the parent. */
class SoundEffects(context: Context, settings: SettingsRepository, scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private var enabled = true
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }.getOrNull()

    private val ids: Map<Sfx, Int> = pool?.let { p ->
        mapOf(
            Sfx.STAR to p.load(appContext, R.raw.sfx_star, 1),
            Sfx.CORRECT to p.load(appContext, R.raw.sfx_correct, 1),
            Sfx.FANFARE to p.load(appContext, R.raw.sfx_fanfare, 1),
            Sfx.SOFT to p.load(appContext, R.raw.sfx_soft, 1),
            Sfx.POP to p.load(appContext, R.raw.sfx_pop, 1),
        )
    } ?: emptyMap()

    init {
        scope.launch { settings.settings.collect { enabled = it.soundEffects } }
    }

    fun play(sfx: Sfx, volume: Float = 0.7f) {
        if (!enabled) return
        val p = pool ?: return
        val id = ids[sfx] ?: return
        runCatching { p.play(id, volume, volume, 1, 0, 1f) }
    }

    /** True when the media volume is muted, so we can tell the child to turn the sound up. */
    fun isMediaMuted(): Boolean {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 }.getOrDefault(false)
    }
}
