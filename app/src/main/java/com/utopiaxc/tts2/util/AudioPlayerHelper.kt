package com.utopiaxc.tts2.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioPlayerHelper(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayerHelper"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var fos: FileOutputStream? = null

    fun startAccumulating() {
        stop()
        try {
            tempFile = File.createTempFile("tts_preview", ".mp3", context.cacheDir)
            tempFile?.deleteOnExit()
            fos = FileOutputStream(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temp file", e)
        }
    }

    fun appendBytes(bytes: ByteArray) {
        try {
            fos?.write(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bytes to temp file", e)
        }
    }

    fun finishAccumulatingAndPlay(onComplete: () -> Unit, onError: (String) -> Unit) {
        try {
            fos?.close()
            fos = null

            val file = tempFile ?: throw Exception("No audio data accumulated")
            if (file.length() == 0L) throw Exception("Audio file is empty")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setOnCompletionListener {
                    onComplete()
                    this@AudioPlayerHelper.stop()
                }
                setOnErrorListener { _, what, extra ->
                    onError("MediaPlayer error: what=$what, extra=$extra")
                    this@AudioPlayerHelper.stop()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            onError(e.message ?: "Playback failed")
            stop()
        }
    }

    fun playRawBytes(bytes: ByteArray, onComplete: () -> Unit, onError: (String) -> Unit) {
        try {
            startAccumulating()
            appendBytes(bytes)
            finishAccumulatingAndPlay(onComplete, onError)
        } catch (e: Exception) {
            onError(e.message ?: "Failed to play raw bytes")
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun stop() {
        try {
            fos?.close()
        } catch (e: Exception) {}
        fos = null

        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null

        tempFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        tempFile = null
    }
}
