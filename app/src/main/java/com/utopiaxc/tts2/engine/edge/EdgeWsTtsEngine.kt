package com.utopiaxc.tts2.engine.edge

import com.utopiaxc.tts2.engine.ITtsEngine
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo

class EdgeWsTtsEngine : ITtsEngine {
    private val wsClient = EdgeWsClient()
    @Volatile
    private var activeDecoder: Mp3ToPcmDecoder? = null

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
        isSsml: Boolean,
        callback: TtsSynthesisCallback
    ) {
        if (outputFormat == "raw-24khz-16bit-mono-pcm") {
            val decoder = Mp3ToPcmDecoder { pcmData ->
                callback.onAudioAvailable(pcmData)
            }
            activeDecoder = decoder
            wsClient.synthesize(
                text,
                voice.name,
                voice.locale,
                speed,
                pitch,
                volume,
                "audio-24khz-48kbitrate-mono-mp3",
                isSsml,
                object : TtsSynthesisCallback {
                    override fun onStart() {
                        callback.onStart()
                    }

                    override fun onAudioAvailable(audioData: ByteArray) {
                        decoder.feedAndDecode(audioData)
                    }

                    override fun onDone() {
                        decoder.flushAndRelease()
                        if (activeDecoder === decoder) {
                            activeDecoder = null
                        }
                        callback.onDone()
                    }

                    override fun onError(error: String) {
                        decoder.flushAndRelease()
                        if (activeDecoder === decoder) {
                            activeDecoder = null
                        }
                        callback.onError(error)
                    }
                }
            )
        } else {
            wsClient.synthesize(text, voice.name, voice.locale, speed, pitch, volume, outputFormat, isSsml, callback)
        }
    }

    override fun stop() {
        wsClient.stop()
        activeDecoder?.flushAndRelease()
        activeDecoder = null
    }

    override fun release() {
        wsClient.stop()
        activeDecoder?.flushAndRelease()
        activeDecoder = null
    }

    override fun isAvailable(): Boolean {
        return true
    }
}
