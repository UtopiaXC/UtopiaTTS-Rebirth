package com.utopiaxc.tts2.engine.azure

import android.content.Context
import android.util.Log
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat
import com.microsoft.cognitiveservices.speech.SynthesisVoicesResult
import com.microsoft.cognitiveservices.speech.SynthesisVoiceGender
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails
import com.utopiaxc.tts2.engine.ITtsEngine
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AzureSdkTtsEngine(private val context: Context) : ITtsEngine {
    companion object {
        private const val TAG = "AzureSdkTtsEngine"
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile
    private var activeSynthesizer: SpeechSynthesizer? = null

    override fun getType(): TtsEngineType = TtsEngineType.AZURE_SDK

    override fun getVoiceList(callback: VoiceListCallback) {
        val key = AzureConfig.getSubscriptionKey(context)
        val region = AzureConfig.getRegion(context)

        if (key.isEmpty() || region.isEmpty()) {
            callback.onError(Exception("Azure SDK is not configured yet. Please configure it in Settings -> About."))
            return
        }

        executor.execute {
            var config: SpeechConfig? = null
            var synthesizer: SpeechSynthesizer? = null
            var voicesResult: SynthesisVoicesResult? = null
            try {
                config = SpeechConfig.fromSubscription(key, region)
                synthesizer = SpeechSynthesizer(config, null)
                voicesResult = synthesizer.getVoicesAsync().get()

                if (voicesResult.reason == ResultReason.VoicesListRetrieved) {
                    val voices = voicesResult.voices.map { voice ->
                        val genderStr = when (voice.gender) {
                            SynthesisVoiceGender.Female -> "Female"
                            SynthesisVoiceGender.Male -> "Male"
                            else -> "Female"
                        }
                        VoiceInfo(
                            id = voice.shortName,
                            name = voice.shortName,
                            displayName = voice.localName + " (" + voice.voiceType.name + ")",
                            locale = voice.locale,
                            gender = genderStr,
                            engineType = TtsEngineType.AZURE_SDK
                        )
                    }
                    callback.onSuccess(voices)
                } else {
                    callback.onError(IOException("Failed to retrieve Azure voices: ${voicesResult.reason}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Azure voices", e)
                callback.onError(e)
            } finally {
                voicesResult?.close()
                synthesizer?.close()
                config?.close()
            }
        }
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
        val key = AzureConfig.getSubscriptionKey(context)
        val region = AzureConfig.getRegion(context)

        if (key.isEmpty() || region.isEmpty()) {
            callback.onError("Azure SDK is not configured yet. Please configure it in Settings -> About.")
            return
        }

        executor.execute {
            var config: SpeechConfig? = null
            var synthesizer: SpeechSynthesizer? = null
            var result: SpeechSynthesisResult? = null
            try {
                config = SpeechConfig.fromSubscription(key, region)
                val format = if (outputFormat.contains("mp3", ignoreCase = true)) {
                    SpeechSynthesisOutputFormat.Audio24Khz48KBitRateMonoMp3
                } else {
                    SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm
                }
                config.setSpeechSynthesisOutputFormat(format)

                synthesizer = SpeechSynthesizer(config, null)
                activeSynthesizer = synthesizer

                callback.onStart()

                val ratePercent = Math.round((speed - 1.0f) * 100)
                val rateStr = (if (ratePercent >= 0) "+" else "") + ratePercent.toString() + "%"

                val pitchPercent = Math.round((pitch - 1.0f) * 100)
                val pitchStr = (if (pitchPercent >= 0) "+" else "") + pitchPercent.toString() + "%"

                val volumePercent = Math.round(volume * 100)
                val volumeStr = volumePercent.toString() + "%"

                val escapedText = escapeXml(text)

                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${voice.locale}'>" +
                        "<voice name='${voice.name}'>" +
                        "<prosody pitch='$pitchStr' rate='$rateStr' volume='$volumeStr'>" +
                        escapedText +
                        "</prosody>" +
                        "</voice>" +
                        "</speak>"

                Log.d(TAG, "Synthesizing with Azure SDK: $ssml")
                val currentResult = synthesizer.SpeakSsmlAsync(ssml).get()
                result = currentResult

                if (currentResult.reason == ResultReason.SynthesizingAudioCompleted) {
                    val audioData = currentResult.audioData
                    if (audioData != null && audioData.isNotEmpty()) {
                        callback.onAudioAvailable(audioData)
                    }
                    callback.onDone()
                } else {
                    val details = SpeechSynthesisCancellationDetails.fromResult(currentResult)
                    val errorMsg = "Synthesis cancelled: ${currentResult.reason}, error details: ${details.errorDetails}"
                    Log.e(TAG, errorMsg)
                    callback.onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Azure synthesis error", e)
                callback.onError(e.message ?: "Unknown Azure SDK error")
            } finally {
                result?.close()
                synthesizer?.close()
                config?.close()
                activeSynthesizer = null
            }
        }
    }

    override fun stop() {
        try {
            activeSynthesizer?.StopSpeakingAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Azure synthesizer", e)
        }
    }

    override fun release() {
        try {
            activeSynthesizer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Azure synthesizer", e)
        } finally {
            activeSynthesizer = null
            executor.shutdown()
        }
    }

    override fun isAvailable(): Boolean {
        return AzureConfig.isConfigured(context)
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
