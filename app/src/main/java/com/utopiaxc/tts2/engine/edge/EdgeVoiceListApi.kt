package com.utopiaxc.tts2.engine.edge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import okhttp3.*
import java.io.IOException

object EdgeVoiceListApi {
    private val client = EdgeWsClient.client
    private val gson = Gson()

    fun fetchVoiceList(callback: VoiceListCallback) {
        val request = Request.Builder()
            .url(EdgeConstants.VOICE_LIST_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val dateStr = response.header("Date")
                if (!dateStr.isNullOrEmpty()) {
                    try {
                        val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                        sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
                        val serverDate = sdf.parse(dateStr)
                        if (serverDate != null) {
                            val serverTimeSeconds = serverDate.time / 1000L
                            val clientTimeSeconds = System.currentTimeMillis() / 1000L
                            val skew = serverTimeSeconds - clientTimeSeconds
                            EdgeDrm.clockSkewSeconds = skew
                        }
                    } catch (e: Exception) {}
                }

                if (!response.isSuccessful) {
                    callback.onError(IOException("Unexpected HTTP code: $response"))
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        callback.onError(IOException("Empty response body"))
                        return
                    }

                    val listType = object : TypeToken<List<EdgeVoice>>() {}.type
                    val edgeVoices: List<EdgeVoice> = gson.fromJson(responseBody, listType)
                    
                    val voices = edgeVoices.map { it.toVoiceInfo() }
                    callback.onSuccess(voices)
                } catch (e: Exception) {
                    callback.onError(e)
                }
            }
        })
    }
}

private data class EdgeVoice(
    @com.google.gson.annotations.SerializedName("Name") val name: String,
    @com.google.gson.annotations.SerializedName("ShortName") val shortName: String,
    @com.google.gson.annotations.SerializedName("Gender") val gender: String,
    @com.google.gson.annotations.SerializedName("Locale") val locale: String,
    @com.google.gson.annotations.SerializedName("FriendlyName") val friendlyName: String
) {
    fun toVoiceInfo(): VoiceInfo {
        val cleanName = if (friendlyName.contains("Online")) {
            friendlyName.substringBefore("Online").replace("Microsoft", "").trim() + " (Neural)"
        } else {
            shortName
        }
        return VoiceInfo(
            id = shortName,
            name = shortName,
            displayName = cleanName,
            locale = locale,
            gender = gender,
            engineType = com.utopiaxc.tts2.engine.TtsEngineType.EDGE_WS
        )
    }
}
