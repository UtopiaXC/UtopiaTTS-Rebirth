package com.utopiaxc.tts2.engine.callback

interface TtsSynthesisCallback {
    fun onStart()
    fun onAudioAvailable(audioData: ByteArray)
    fun onDone()
    fun onError(error: String)
}
