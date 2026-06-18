package com.utopiaxc.tts2.engine.callback

import com.utopiaxc.tts2.engine.model.VoiceInfo

interface VoiceListCallback {
    fun onSuccess(voices: List<VoiceInfo>)
    fun onError(error: Throwable)
}
