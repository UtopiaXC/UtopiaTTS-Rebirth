package com.utopiaxc.tts2.engine.edge

import com.utopiaxc.tts2.engine.ITtsEngine
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo

class EdgeWsTtsEngine : ITtsEngine {
    private val wsClient = EdgeWsClient()

    override fun getType(): TtsEngineType = TtsEngineType.EDGE_WS

    override fun getVoiceList(callback: VoiceListCallback) {
        EdgeVoiceListApi.fetchVoiceList(callback)
    }

    override fun synthesize(
        text: String,
        voice: VoiceInfo,
        speed: Float,
        pitch: Float,
        volume: Float,
        outputFormat: String,
        callback: TtsSynthesisCallback
    ) {
        wsClient.synthesize(text, voice.name, speed, pitch, volume, outputFormat, callback)
    }

    override fun stop() {
        wsClient.stop()
    }

    override fun release() {
        wsClient.stop()
    }

    override fun isAvailable(): Boolean {
        return true
    }
}
