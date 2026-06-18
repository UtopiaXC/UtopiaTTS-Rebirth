package com.utopiaxc.tts2.engine.model

data class SynthesisResult(
    val audioData: ByteArray,
    val mimeType: String = "audio/mpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SynthesisResult

        if (!audioData.contentEquals(other.audioData)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
