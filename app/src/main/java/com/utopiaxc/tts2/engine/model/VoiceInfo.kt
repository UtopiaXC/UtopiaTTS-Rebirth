package com.utopiaxc.tts2.engine.model

import com.utopiaxc.tts2.engine.TtsEngineType
import java.io.Serializable

data class VoiceInfo(
    val id: String,
    val name: String,
    val displayName: String,
    val locale: String,
    val gender: String,
    val engineType: TtsEngineType
) : Serializable
