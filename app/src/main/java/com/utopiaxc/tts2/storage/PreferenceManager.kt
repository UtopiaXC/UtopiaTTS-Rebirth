package com.utopiaxc.tts2.storage

import android.content.Context
import android.content.SharedPreferences
import com.utopiaxc.tts2.engine.TtsEngineType
import androidx.core.content.edit

class PreferenceManager private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "utopia_tts_settings"
        private const val KEY_ENGINE_TYPE = "key_engine_type"
        private const val KEY_SELECTED_VOICE = "key_selected_voice"
        private const val KEY_SPEED = "key_speed"
        private const val KEY_PITCH = "key_pitch"
        private const val KEY_VOLUME = "key_volume"
        private const val KEY_MAX_HISTORY_COUNT = "key_max_history_count"
        private const val KEY_MAX_CACHE_SIZE_MB = "key_max_cache_size_mb"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferenceManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var engineType: TtsEngineType
        get() {
            val name = sharedPreferences.getString(KEY_ENGINE_TYPE, TtsEngineType.EDGE_WS.name)
            return try {
                TtsEngineType.valueOf(name ?: TtsEngineType.EDGE_WS.name)
            } catch (e: Exception) {
                TtsEngineType.EDGE_WS
            }
        }
        set(value) {
            sharedPreferences.edit { putString(KEY_ENGINE_TYPE, value.name) }
        }

    var selectedVoiceId: String
        get() = sharedPreferences.getString(KEY_SELECTED_VOICE, "") ?: ""
        set(value) {
            sharedPreferences.edit { putString(KEY_SELECTED_VOICE, value) }
        }

    var speed: Float
        get() = sharedPreferences.getFloat(KEY_SPEED, 1.0f)
        set(value) {
            val rounded = Math.round(value * 10f) / 10f
            sharedPreferences.edit { putFloat(KEY_SPEED, rounded) }
        }

    var pitch: Float
        get() = sharedPreferences.getFloat(KEY_PITCH, 1.0f)
        set(value) {
            val rounded = Math.round(value * 10f) / 10f
            sharedPreferences.edit { putFloat(KEY_PITCH, rounded) }
        }

    var volume: Float
        get() = sharedPreferences.getFloat(KEY_VOLUME, 1.0f)
        set(value) {
            val rounded = Math.round(value * 100f) / 100f
            sharedPreferences.edit().putFloat(KEY_VOLUME, rounded).apply()
        }

    var maxHistoryCount: Int
        get() = sharedPreferences.getInt(KEY_MAX_HISTORY_COUNT, 100)
        set(value) {
            sharedPreferences.edit { putInt(KEY_MAX_HISTORY_COUNT, value) }
        }

    var maxCacheSizeMb: Int
        get() = sharedPreferences.getInt(KEY_MAX_CACHE_SIZE_MB, 100)
        set(value) {
            sharedPreferences.edit { putInt(KEY_MAX_CACHE_SIZE_MB, value) }
        }
}
