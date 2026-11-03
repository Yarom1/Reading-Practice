package com.korimbekef.reading.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One DataStore per process (created by the app container). */
fun createSettingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { context.applicationContext.preferencesDataStoreFile("settings") })

/** How word highlighting is kept in sync with the voice. */
enum class SyncMode {
    /** Use the engine's word-position events when the engine supports them, otherwise word by word. */
    AUTO,

    /** Always use the engine's word-position events (one utterance per line). */
    ENGINE,

    /** Always speak word by word; each word is highlighted when its audio starts. */
    WORD_BY_WORD,
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val activeChildId: Long = -1,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val enginePackage: String? = null,
    val voiceName: String? = null,
    val pitch: Float = 1.0f,
    val syncMode: SyncMode = SyncMode.AUTO,
    val speakNikud: Boolean = true,
    val soundEffects: Boolean = true,
    val voicePrompts: Boolean = true,
    /** voice name -> "m" / "f", as tagged by the parent (engines rarely report gender). */
    val voiceGenders: Map<String, String> = emptyMap(),
    /** "engine|voice" -> result of the word-sync capability check. */
    val rangeSupport: Map<String, String> = emptyMap(),
    val hiddenPassages: Set<String> = emptySet(),
) {
    val hasPin: Boolean get() = !pinHash.isNullOrEmpty() && !pinSalt.isNullOrEmpty()
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val activeChild = longPreferencesKey("active_child")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val engine = stringPreferencesKey("tts_engine")
        val voice = stringPreferencesKey("tts_voice")
        val pitch = floatPreferencesKey("tts_pitch")
        val syncMode = stringPreferencesKey("sync_mode")
        val speakNikud = booleanPreferencesKey("speak_nikud")
        val soundEffects = booleanPreferencesKey("sound_effects")
        val voicePrompts = booleanPreferencesKey("voice_prompts")
        val voiceGenders = stringSetPreferencesKey("voice_genders")
        val rangeSupport = stringSetPreferencesKey("range_support")
        val hidden = stringSetPreferencesKey("hidden_passages")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            activeChildId = p[Keys.activeChild] ?: -1,
            pinHash = p[Keys.pinHash],
            pinSalt = p[Keys.pinSalt],
            enginePackage = p[Keys.engine]?.takeIf { it.isNotBlank() },
            voiceName = p[Keys.voice]?.takeIf { it.isNotBlank() },
            pitch = p[Keys.pitch] ?: 1.0f,
            syncMode = p[Keys.syncMode]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.AUTO,
            speakNikud = p[Keys.speakNikud] ?: true,
            soundEffects = p[Keys.soundEffects] ?: true,
            voicePrompts = p[Keys.voicePrompts] ?: true,
            voiceGenders = decodeMap(p[Keys.voiceGenders]),
            rangeSupport = decodeMap(p[Keys.rangeSupport]),
            hiddenPassages = p[Keys.hidden] ?: emptySet(),
        )
    }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.onboardingDone] = done }
    suspend fun setActiveChild(id: Long) = edit { it[Keys.activeChild] = id }
    suspend fun setPin(hash: String, salt: String) = edit {
        it[Keys.pinHash] = hash
        it[Keys.pinSalt] = salt
    }

    suspend fun setEngine(pkg: String?) = edit {
        if (pkg == null) it.remove(Keys.engine) else it[Keys.engine] = pkg
        it.remove(Keys.voice)
    }

    suspend fun setVoice(name: String?) = edit { if (name == null) it.remove(Keys.voice) else it[Keys.voice] = name }
    suspend fun setPitch(pitch: Float) = edit { it[Keys.pitch] = pitch }
    suspend fun setSyncMode(mode: SyncMode) = edit { it[Keys.syncMode] = mode.name }
    suspend fun setSpeakNikud(value: Boolean) = edit { it[Keys.speakNikud] = value }
    suspend fun setSoundEffects(value: Boolean) = edit { it[Keys.soundEffects] = value }
    suspend fun setVoicePrompts(value: Boolean) = edit { it[Keys.voicePrompts] = value }

    suspend fun setVoiceGender(voice: String, gender: String?) = edit { p ->
        val map = decodeMap(p[Keys.voiceGenders]).toMutableMap()
        if (gender == null) map.remove(voice) else map[voice] = gender
        p[Keys.voiceGenders] = encodeMap(map)
    }

    suspend fun setRangeSupport(key: String, value: String?) = edit { p ->
        val map = decodeMap(p[Keys.rangeSupport]).toMutableMap()
        if (value == null) map.remove(key) else map[key] = value
        p[Keys.rangeSupport] = encodeMap(map)
    }

    suspend fun clearRangeSupport() = edit { it.remove(Keys.rangeSupport) }

    suspend fun setPassageHidden(id: String, hidden: Boolean) = edit { p ->
        val set = (p[Keys.hidden] ?: emptySet()).toMutableSet()
        if (hidden) set += id else set -= id
        p[Keys.hidden] = set
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private companion object {
        const val SEP = "\u0001"

        fun decodeMap(set: Set<String>?): Map<String, String> =
            set.orEmpty().mapNotNull { entry ->
                val i = entry.indexOf(SEP)
                if (i <= 0) null else entry.substring(0, i) to entry.substring(i + SEP.length)
            }.toMap()

        fun encodeMap(map: Map<String, String>): Set<String> = map.map { (k, v) -> "$k$SEP$v" }.toSet()
    }
}
