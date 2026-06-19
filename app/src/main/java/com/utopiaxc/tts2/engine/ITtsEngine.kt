package com.utopiaxc.tts2.engine

import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo

interface ITtsEngine {
    fun getType(): TtsEngineType
    fun getVoiceList(callback: VoiceListCallback)
    fun synthesize(
        text: String,
        voice: VoiceInfo,
        speed: Float,
        pitch: Float,
        volume: Float,
        outputFormat: String = "audio-24khz-48kbitrate-mono-mp3",
        isSsml: Boolean = false,
        callback: TtsSynthesisCallback
    )
    fun stop()
    fun release()
    fun isAvailable(): Boolean
}
