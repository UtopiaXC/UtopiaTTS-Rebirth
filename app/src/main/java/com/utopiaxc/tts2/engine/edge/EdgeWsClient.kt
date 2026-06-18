package com.utopiaxc.tts2.engine.edge

import android.util.Log
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import okhttp3.*
import okio.ByteString
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EdgeWsClient {
    companion object {
        private const val TAG = "EdgeWsClient"
        internal val client = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var activeWebSocket: WebSocket? = null
    private var isStopped = false

    fun synthesize(
        text: String,
        voiceName: String,
        speed: Float,
        pitch: Float,
        volume: Float,
        outputFormat: String,
        callback: TtsSynthesisCallback
    ) {
        isStopped = false
        val connectionId = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.US)
        val gecToken = EdgeDrm.generateSecMsGecHeader()
        
        val wssUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=${EdgeConstants.TRUSTED_CLIENT_TOKEN}" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$gecToken" +
                "&Sec-MS-GEC-Version=${EdgeConstants.SEC_MS_GEC_VERSION}"
        
        Log.d(TAG, "Connecting to WebSocket URL: $wssUrl")
        
        val request = Request.Builder()
            .url(wssUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiancpmjcobklbhgkeepaillocifjaf")
            .header("Cookie", "muid=${EdgeDrm.generateMuid()};")
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened successfully")
                if (isStopped) {
                    webSocket.close(1000, "Stopped by user")
                    return
                }
                
                val dateStr = getFormattedDate()
                val configMsg = "X-Timestamp:$dateStr\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"$outputFormat\"}}}}"
                
                Log.d(TAG, "Sending configMsg:\n$configMsg")
                val sentConfig = webSocket.send(configMsg)
                Log.d(TAG, "configMsg sent success: $sentConfig")

                val requestId = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.US)
                val escapedText = escapeXml(text)
                
                val ratePercent = Math.round((speed - 1.0f) * 100)
                val rateStr = (if (ratePercent >= 0) "+" else "") + ratePercent.toString() + "%"
                
                val pitchPercent = Math.round((pitch - 1.0f) * 100)
                val pitchStr = (if (pitchPercent >= 0) "+" else "") + pitchPercent.toString() + "%"
                
                val volumePercent = Math.round(volume * 100)
                val volumeStr = volumePercent.toString() + "%"

                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${dateStr}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                        "<voice name='$voiceName'>" +
                        "<prosody pitch='$pitchStr' rate='$rateStr' volume='$volumeStr'>" +
                        escapedText +
                        "</prosody>" +
                        "</voice>" +
                        "</speak>"

                Log.d(TAG, "Sending ssmlMsg:\n$ssmlMsg")
                val sentSsml = webSocket.send(ssmlMsg)
                Log.d(TAG, "ssmlMsg sent success: $sentSsml")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "onMessage (text):\n$text")
                if (isStopped) return
                
                if (text.contains("Path:turn.start")) {
                    callback.onStart()
                } else if (text.contains("Path:turn.end")) {
                    callback.onDone()
                    webSocket.close(1000, "Success")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "onMessage (bytes): size=${bytes.size}")
                if (isStopped) return
                
                try {
                    val rawBytes = bytes.toByteArray()
                    if (rawBytes.size < 2) return
                    
                    val headerLength = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
                    if (rawBytes.size < 2 + headerLength) return

                    val headerString = String(rawBytes, 2, headerLength, Charsets.UTF_8)
                    Log.d(TAG, "onMessage (bytes) header: $headerString")
                    if (headerString.contains("Path:audio")) {
                        val audioData = ByteArray(rawBytes.size - 2 - headerLength)
                        System.arraycopy(rawBytes, 2 + headerLength, audioData, 0, audioData.size)
                        
                        if (audioData.isNotEmpty()) {
                            callback.onAudioAvailable(audioData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing binary message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isStopped) return
                
                val builder = StringBuilder()
                builder.append("WebSocket failure: ").append(t.message).append("\n")
                if (response != null) {
                    builder.append("Response code: ").append(response.code).append("\n")
                    builder.append("Response message: ").append(response.message).append("\n")
                    builder.append("Response headers: ").append(response.headers).append("\n")
                    try {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            builder.append("Response body: ").append(body).append("\n")
                        }
                    } catch (e: Exception) {
                        builder.append("Failed to read response body: ").append(e.message).append("\n")
                    }
                }
                
                val errorLog = builder.toString()
                Log.e(TAG, errorLog, t)
                callback.onError(errorLog)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        })
    }

    fun stop() {
        isStopped = true
        activeWebSocket?.let {
            it.close(1000, "Stopped")
            activeWebSocket = null
        }
    }

    private fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
