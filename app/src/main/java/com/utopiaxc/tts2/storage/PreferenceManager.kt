package com.utopiaxc.tts2.storage

import android.content.Context
import android.content.SharedPreferences
import com.utopiaxc.tts2.engine.TtsEngineType

class PreferenceManager private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "utopia_tts_settings"
        private const val KEY_ENGINE_TYPE = "key_engine_type"
        private const val KEY_SELECTED_VOICE = "key_selected_voice"
        private const val KEY_SPEED = "key_speed"
        private const val KEY_PITCH = "key_pitch"
        private const val KEY_VOLUME = "key_volume"

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
            sharedPreferences.edit().putString(KEY_ENGINE_TYPE, value.name).apply()
        }

    var selectedVoiceId: String
        get() = sharedPreferences.getString(KEY_SELECTED_VOICE, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(KEY_SELECTED_VOICE, value).apply()
        }

    var speed: Float
        get() = sharedPreferences.getFloat(KEY_SPEED, 1.0f)
        set(value) {
            sharedPreferences.edit().putFloat(KEY_SPEED, value).apply()
        }

    var pitch: Float
        get() = sharedPreferences.getFloat(KEY_PITCH, 1.0f)
        set(value) {
            sharedPreferences.edit().putFloat(KEY_PITCH, value).apply()
        }

    var volume: Float
        get() = sharedPreferences.getFloat(KEY_VOLUME, 1.0f)
        set(value) {
            sharedPreferences.edit().putFloat(KEY_VOLUME, value).apply()
        }
}
