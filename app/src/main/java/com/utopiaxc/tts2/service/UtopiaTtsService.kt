package com.utopiaxc.tts2.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import com.utopiaxc.tts2.engine.TtsEngineManager
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import com.utopiaxc.tts2.storage.PreferenceManager

class UtopiaTtsService : TextToSpeechService() {
    companion object {
        private const val TAG = "UtopiaTtsService"
        private const val DEFAULT_LOCALE = "zh-CN"
        
        private val STATIC_FALLBACK_VOICES = listOf(
            VoiceInfo("zh-CN-XiaoxiaoNeural", "zh-CN-XiaoxiaoNeural", "Xiaoxiao (Neural)", "zh-CN", "Female", TtsEngineType.EDGE_WS),
            VoiceInfo("zh-CN-YunxiNeural", "zh-CN-YunxiNeural", "Yunxi (Neural)", "zh-CN", "Male", TtsEngineType.EDGE_WS),
            VoiceInfo("en-US-EmmaMultilingualNeural", "en-US-EmmaMultilingualNeural", "Emma (Neural)", "en-US", "Female", TtsEngineType.EDGE_WS)
        )
    }

    private lateinit var ttsEngineManager: TtsEngineManager
    private lateinit var preferenceManager: PreferenceManager
    private var allVoices: List<VoiceInfo> = emptyList()
    @Volatile
    private var activeQueue: java.util.concurrent.LinkedBlockingQueue<SynthesisEvent>? = null

    override fun onCreate() {
        super.onCreate()
        ttsEngineManager = TtsEngineManager.getInstance(this)
        preferenceManager = PreferenceManager.getInstance(this)
        loadVoiceListAsync()
    }

    private fun loadVoiceListAsync() {
        val engineType = preferenceManager.engineType
        ttsEngineManager.getVoiceList(engineType, object : VoiceListCallback {
            override fun onSuccess(voices: List<VoiceInfo>) {
                allVoices = voices
                Log.d(TAG, "Successfully loaded ${voices.size} voices from current engine")
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Failed to load voices from current engine", error)
            }
        })
    }

    private fun getAvailableVoices(): List<VoiceInfo> {
        return if (allVoices.isEmpty()) STATIC_FALLBACK_VOICES else allVoices
    }

    private fun getLocaleFromIso3(lang: String?, country: String?): java.util.Locale? {
        if (lang.isNullOrEmpty()) return null
        val targetLang = lang.lowercase(java.util.Locale.US)
        val targetCountry = country?.lowercase(java.util.Locale.US) ?: ""
        
        val iso2Lang = when (targetLang) {
            "zho", "chi" -> "zh"
            "eng" -> "en"
            "jpn" -> "ja"
            "kor" -> "ko"
            "fra" -> "fr"
            "deu" -> "de"
            "rus" -> "ru"
            "spa" -> "es"
            "ita" -> "it"
            else -> {
                var found: String? = null
                for (locale in java.util.Locale.getAvailableLocales()) {
                    try {
                        if (locale.isO3Language.lowercase(java.util.Locale.US) == targetLang) {
                            found = locale.language
                            break
                        }
                    } catch (e: Exception) {}
                }
                found ?: targetLang
            }
        }
        
        val iso2Country = when (targetCountry) {
            "chn" -> "CN"
            "usa" -> "US"
            "jpn" -> "JP"
            "kor" -> "KR"
            "fra" -> "FR"
            "deu" -> "DE"
            "rus" -> "RU"
            "esp" -> "ES"
            "ita" -> "IT"
            else -> {
                var found: String? = null
                if (targetCountry.isNotEmpty()) {
                    for (locale in java.util.Locale.getAvailableLocales()) {
                        try {
                            if (locale.isO3Country.lowercase(java.util.Locale.US) == targetCountry) {
                                found = locale.country
                                break
                            }
                        } catch (e: Exception) {}
                    }
                }
                found ?: targetCountry
            }
        }
        
        return if (iso2Country.isEmpty()) {
            java.util.Locale(iso2Lang)
        } else {
            java.util.Locale(iso2Lang, iso2Country)
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED
        val matchedLocale = getLocaleFromIso3(lang, country) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        
        val voices = getAvailableVoices()
        val searchPrefix = if (matchedLocale.country.isEmpty()) {
            matchedLocale.language
        } else {
            "${matchedLocale.language}-${matchedLocale.country}"
        }
        
        val matches = voices.any { it.locale.startsWith(searchPrefix, ignoreCase = true) }
        return if (matches) {
            if (matchedLocale.country.isEmpty()) {
                TextToSpeech.LANG_AVAILABLE
            } else {
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            }
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        val voices = getAvailableVoices()
        val selectedVoice = getSelectedVoice(voices) ?: getFallbackVoice(voices)
        val localeStr = selectedVoice?.locale ?: DEFAULT_LOCALE
        val parts = localeStr.split("-")
        
        val locale = if (parts.size >= 2) {
            java.util.Locale(parts[0], parts[1])
        } else {
            java.util.Locale(parts[0])
        }
        
        val iso3Lang = try {
            locale.isO3Language
        } catch (e: Exception) {
            "zho"
        }
        val iso3Country = try {
            locale.isO3Country
        } catch (e: Exception) {
            "CHN"
        }
        
        return arrayOf(iso3Lang, iso3Country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onGetVoices(): List<android.speech.tts.Voice> {
        val voices = getAvailableVoices()
        return voices.map { voiceInfo ->
            val parts = voiceInfo.locale.split("-")
            val locale = if (parts.size >= 2) java.util.Locale(parts[0], parts[1]) else java.util.Locale(parts[0])
            val features = HashSet<String>()
            features.add(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)
            
            android.speech.tts.Voice(
                voiceInfo.id,
                locale,
                android.speech.tts.Voice.QUALITY_VERY_HIGH,
                android.speech.tts.Voice.LATENCY_NORMAL,
                true,
                features
            )
        }
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val matchedLocale = getLocaleFromIso3(lang, country)
        val searchPrefix = if (matchedLocale != null) {
            if (matchedLocale.country.isEmpty()) matchedLocale.language else "${matchedLocale.language}-${matchedLocale.country}"
        } else {
            "zh-CN"
        }
        val voices = getAvailableVoices()
        val matchedVoice = findVoiceForLocale(voices, searchPrefix) ?: getFallbackVoice(voices)
        return matchedVoice?.id ?: ""
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        val voices = getAvailableVoices()
        return if (voices.any { it.id == voiceName }) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }
    }

    override fun onStop() {
        ttsEngineManager.getCurrentEngine().stop()
        activeQueue?.offer(SynthesisEvent.Done)
        activeQueue = null
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text = request.charSequenceText?.toString() ?: ""
        if (text.isBlank()) {
            callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val speed = request.speechRate.toFloat() / 100f
        val pitch = request.pitch.toFloat() / 100f
        val volume = preferenceManager.volume

        val reqLanguage = request.language
        val reqCountry = request.country

        val reqVoiceName = try {
            request.voiceName
        } catch (e: NoSuchMethodError) {
            null
        }

        val voices = getAvailableVoices()
        
        var voice = if (!reqVoiceName.isNullOrEmpty()) {
            voices.find { it.id == reqVoiceName }
        } else {
            null
        }

        if (voice == null && !reqLanguage.isNullOrEmpty()) {
            val matchedLocale = getLocaleFromIso3(reqLanguage, reqCountry)
            if (matchedLocale != null) {
                val localeCode = if (matchedLocale.country.isEmpty()) {
                    matchedLocale.language
                } else {
                    "${matchedLocale.language}-${matchedLocale.country}"
                }
                voice = findVoiceForLocale(voices, localeCode)
            }
        }

        if (voice == null) {
            voice = getSelectedVoice(voices)
        }

        if (voice == null) {
            voice = getFallbackVoice(voices)
        }

        if (voice == null) {
            Log.e(TAG, "No suitable voice found for synthesis")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        Log.d(TAG, "Synthesizing text: \"$text\" using voice: ${voice.name}, speed: $speed, pitch: $pitch")

        val queue = java.util.concurrent.LinkedBlockingQueue<SynthesisEvent>()
        activeQueue = queue
        val outputFormat = "raw-24khz-16bit-mono-pcm"

        ttsEngineManager.getCurrentEngine().synthesize(
            text = text,
            voice = voice,
            speed = speed,
            pitch = pitch,
            volume = volume,
            outputFormat = outputFormat,
            callback = object : TtsSynthesisCallback {
                override fun onStart() {
                    queue.offer(SynthesisEvent.Start)
                }

                override fun onAudioAvailable(audioData: ByteArray) {
                    queue.offer(SynthesisEvent.Audio(audioData))
                }

                override fun onDone() {
                    queue.offer(SynthesisEvent.Done)
                }

                override fun onError(error: String) {
                    queue.offer(SynthesisEvent.Error(error))
                }
            }
        )

        var isStarted = false
        var running = true
        while (running) {
            val event = try {
                queue.poll(30, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Synthesis loop interrupted", e)
                if (isStarted) callback.error()
                break
            }

            if (event == null) {
                Log.e(TAG, "Synthesis timed out waiting for events")
                if (isStarted) callback.error()
                break
            }

            when (event) {
                is SynthesisEvent.Start -> {
                    if (!isStarted) {
                        val startResult = callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
                        if (startResult != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "SynthesisCallback.start() returned error: $startResult")
                            ttsEngineManager.getCurrentEngine().stop()
                            running = false
                        } else {
                            isStarted = true
                        }
                    }
                }
                is SynthesisEvent.Audio -> {
                    if (!isStarted) {
                        val startResult = callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
                        if (startResult != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "SynthesisCallback.start() returned error: $startResult")
                            ttsEngineManager.getCurrentEngine().stop()
                            running = false
                            continue
                        }
                        isStarted = true
                    }
                    val audioData = event.data
                    val maxBufferSize = callback.maxBufferSize
                    var offset = 0
                    while (offset < audioData.size) {
                        val length = Math.min(audioData.size - offset, maxBufferSize)
                        val writeResult = callback.audioAvailable(audioData, offset, length)
                        if (writeResult != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "SynthesisCallback.audioAvailable() returned error: $writeResult")
                            ttsEngineManager.getCurrentEngine().stop()
                            running = false
                            break
                        }
                        offset += length
                    }
                }
                is SynthesisEvent.Done -> {
                    if (isStarted) callback.done()
                    running = false
                }
                is SynthesisEvent.Error -> {
                    Log.e(TAG, "Synthesis error: ${event.message}")
                    if (isStarted) callback.error()
                    running = false
                }
            }
        }
        activeQueue = null
    }

    private fun getSelectedVoice(voices: List<VoiceInfo>): VoiceInfo? {
        val id = preferenceManager.selectedVoiceId
        return voices.find { it.id == id }
    }

    private fun findVoiceForLocale(voices: List<VoiceInfo>, localeStr: String): VoiceInfo? {
        var matched = voices.find { it.locale.equals(localeStr, ignoreCase = true) }
        if (matched == null) {
            matched = voices.find { it.locale.startsWith(localeStr, ignoreCase = true) }
        }
        return matched
    }

    private fun getFallbackVoice(voices: List<VoiceInfo>): VoiceInfo? {
        if (voices.isNotEmpty()) {
            return voices.find { it.locale.startsWith("zh", ignoreCase = true) }
                ?: voices.find { it.locale.startsWith("en", ignoreCase = true) }
                ?: voices[0]
        }
        return null
    }
}

private sealed class SynthesisEvent {
    object Start : SynthesisEvent()
    class Audio(val data: ByteArray) : SynthesisEvent()
    object Done : SynthesisEvent()
    class Error(val message: String) : SynthesisEvent()
}
