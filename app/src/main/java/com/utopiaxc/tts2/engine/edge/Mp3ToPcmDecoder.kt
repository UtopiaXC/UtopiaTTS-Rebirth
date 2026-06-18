package com.utopiaxc.tts2.engine.edge

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log

class Mp3ToPcmDecoder(private val onPcmAvailable: (ByteArray) -> Unit) {
    companion object {
        private const val TAG = "Mp3ToPcmDecoder"
        private const val TIMEOUT_US = 5000L
    }

    private var mediaCodec: MediaCodec? = null

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, 24000, 1)
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG)
            mediaCodec?.configure(format, null, null, 0)
            mediaCodec?.start()
            Log.d(TAG, "MediaCodec decoder for MP3 initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaCodec decoder", e)
        }
    }

    fun feedAndDecode(mp3Data: ByteArray) {
        val codec = mediaCodec ?: return
        var offset = 0
        while (offset < mp3Data.size) {
            val inputBufferIndex = try {
                codec.dequeueInputBuffer(TIMEOUT_US)
            } catch (e: Exception) {
                Log.e(TAG, "Error dequeuing input buffer", e)
                -1
            }
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val chunkSize = Math.min(inputBuffer.remaining(), mp3Data.size - offset)
                    inputBuffer.put(mp3Data, offset, chunkSize)
                    try {
                        codec.queueInputBuffer(inputBufferIndex, 0, chunkSize, 0, 0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error queuing input buffer", e)
                        break
                    }
                    offset += chunkSize
                } else {
                    break
                }
            } else {
                drainOutput()
            }
        }
        drainOutput()
    }

    private fun drainOutput() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            while (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(pcmData)
                    onPcmAvailable(pcmData)
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during draining output buffers", e)
        }
    }

    fun flushAndRelease() {
        try {
            val codec = mediaCodec ?: return
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainOutput()
            codec.stop()
            codec.release()
            Log.d(TAG, "MediaCodec decoder released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaCodec decoder", e)
        } finally {
            mediaCodec = null
        }
    }
}
