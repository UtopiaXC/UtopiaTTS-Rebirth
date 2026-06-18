package com.utopiaxc.tts2.engine

import android.content.Context
import com.utopiaxc.tts2.engine.azure.AzureSdkTtsEngine
import com.utopiaxc.tts2.engine.edge.EdgeWsTtsEngine
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import com.utopiaxc.tts2.storage.PreferenceManager

class TtsEngineManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: TtsEngineManager? = null

        fun getInstance(context: Context): TtsEngineManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TtsEngineManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val preferenceManager = PreferenceManager.getInstance(context)
    private val engines = mapOf<TtsEngineType, ITtsEngine>(
        TtsEngineType.EDGE_WS to EdgeWsTtsEngine(),
        TtsEngineType.AZURE_SDK to AzureSdkTtsEngine(context)
    )
    private val voiceCache = mutableMapOf<TtsEngineType, List<VoiceInfo>>()

    fun getCurrentEngine(): ITtsEngine {
        val type = preferenceManager.engineType
        return engines[type] ?: engines[TtsEngineType.EDGE_WS]!!
    }

    fun getEngine(type: TtsEngineType): ITtsEngine {
        return engines[type] ?: engines[TtsEngineType.EDGE_WS]!!
    }

    fun setEngineType(type: TtsEngineType) {
        preferenceManager.engineType = type
    }

    fun getVoiceList(type: TtsEngineType, callback: VoiceListCallback) {
        val cached = voiceCache[type]
        if (cached != null) {
            callback.onSuccess(cached)
            return
        }
        getEngine(type).getVoiceList(object : VoiceListCallback {
            override fun onSuccess(voices: List<VoiceInfo>) {
                voiceCache[type] = voices
                callback.onSuccess(voices)
            }
            override fun onError(error: Throwable) {
                callback.onError(error)
            }
        })
    }
}
