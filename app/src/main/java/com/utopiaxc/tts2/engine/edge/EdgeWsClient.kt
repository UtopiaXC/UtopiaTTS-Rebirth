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
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .build()
    }

    private enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private class PendingRequest(
        val text: String,
        val voiceName: String,
        val locale: String,
        val speed: Float,
        val pitch: Float,
        val volume: Float,
        val outputFormat: String,
        val isSsml: Boolean,
        val callback: TtsSynthesisCallback
    )

    private var activeWebSocket: WebSocket? = null
    private var connectionState = ConnectionState.DISCONNECTED
    private var activeCallback: TtsSynthesisCallback? = null
    private var pendingRequest: PendingRequest? = null
    private var isStopped = false
    private var idleTimer: Timer? = null

    fun synthesize(
        text: String,
        voiceName: String,
        locale: String,
        speed: Float,
        pitch: Float,
        volume: Float,
        outputFormat: String,
        isSsml: Boolean,
        callback: TtsSynthesisCallback
    ) {
        synchronized(this) {
            cancelIdleTimeout()
            isStopped = false

            activeCallback?.let {
                Log.w(TAG, "Another synthesis was active. Cancelling it.")
                it.onError("Cancelled by new synthesis request")
            }
            activeCallback = null

            val req = PendingRequest(text, voiceName, locale, speed, pitch, volume, outputFormat, isSsml, callback)

            when (connectionState) {
                ConnectionState.CONNECTED -> {
                    val ws = activeWebSocket
                    if (ws != null) {
                        activeCallback = callback
                        sendConfigAndSsml(ws, req)
                    } else {
                        connectionState = ConnectionState.CONNECTING
                        pendingRequest = req
                        connectWebSocket()
                    }
                }
                ConnectionState.CONNECTING -> {
                    pendingRequest = req
                }
                ConnectionState.DISCONNECTED -> {
                    connectionState = ConnectionState.CONNECTING
                    pendingRequest = req
                    connectWebSocket()
                }
            }
        }
    }

    private fun connectWebSocket() {
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
                synchronized(this@EdgeWsClient) {
                    if (isStopped) {
                        webSocket.close(1000, "Stopped by user")
                        connectionState = ConnectionState.DISCONNECTED
                        activeWebSocket = null
                        return
                    }
                    connectionState = ConnectionState.CONNECTED
                    val req = pendingRequest
                    if (req != null) {
                        activeCallback = req.callback
                        sendConfigAndSsml(webSocket, req)
                        pendingRequest = null
                    } else {
                        startIdleTimeout()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
//                Log.d(TAG, "onMessage (text):\n$text")
                val cb = synchronized(this@EdgeWsClient) {
                    if (isStopped) return
                    activeCallback
                }
                
                if (text.contains("Path:turn.start")) {
                    cb?.onStart()
                } else if (text.contains("Path:turn.end")) {
                    cb?.onDone()
                    synchronized(this@EdgeWsClient) {
                        if (activeCallback === cb) {
                            activeCallback = null
                        }
                        startIdleTimeout()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
//                Log.d(TAG, "onMessage (bytes): size=${bytes.size}")
                val cb = synchronized(this@EdgeWsClient) {
                    if (isStopped) return
                    activeCallback
                }
                if (cb == null) return
                
                try {
                    val rawBytes = bytes.toByteArray()
                    if (rawBytes.size < 2) return
                    
                    val headerLength = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
                    if (rawBytes.size < 2 + headerLength) return

                    val headerString = String(rawBytes, 2, headerLength, Charsets.UTF_8)
//                    Log.d(TAG, "onMessage (bytes) header: $headerString")
                    if (headerString.contains("Path:audio")) {
                        val audioData = ByteArray(rawBytes.size - 2 - headerLength)
                        System.arraycopy(rawBytes, 2 + headerLength, audioData, 0, audioData.size)
                        
                        if (audioData.isNotEmpty()) {
                            cb.onAudioAvailable(audioData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing binary message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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
                
                val (cb, pendingCb) = synchronized(this@EdgeWsClient) {
                    val currentCb = activeCallback
                    val currentPendingCb = pendingRequest?.callback
                    activeCallback = null
                    pendingRequest = null
                    activeWebSocket = null
                    connectionState = ConnectionState.DISCONNECTED
                    cancelIdleTimeout()
                    Pair(currentCb, currentPendingCb)
                }
                
                cb?.onError(errorLog)
                pendingCb?.onError(errorLog)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: code=$code, reason=$reason")
                val cb = synchronized(this@EdgeWsClient) {
                    if (activeWebSocket === webSocket) {
                        activeWebSocket = null
                        connectionState = ConnectionState.DISCONNECTED
                    }
                    val c = activeCallback
                    activeCallback = null
                    c
                }
                cb?.onError("WebSocket closed: code=$code, reason=$reason")
                try {
                    webSocket.close(1000, null)
                } catch (e: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                val cb = synchronized(this@EdgeWsClient) {
                    if (activeWebSocket === webSocket) {
                        activeWebSocket = null
                        connectionState = ConnectionState.DISCONNECTED
                    }
                    val c = activeCallback
                    activeCallback = null
                    c
                }
                cb?.onError("WebSocket closed: code=$code, reason=$reason")
            }
        })
    }

    private fun sanitizeSsml(ssml: String, voiceName: String, locale: String): String {
        var result = ssml.trim()
        val speakIndex = result.indexOf("<speak", ignoreCase = true)
        if (speakIndex != -1) {
            val tagEndIndex = result.indexOf(">", speakIndex)
            if (tagEndIndex != -1) {
                val tagContent = result.substring(speakIndex, tagEndIndex)
                if (!tagContent.contains("xml:lang", ignoreCase = true)) {
                    val restOfSsml = result.substring(tagEndIndex + 1)
                    result = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" + restOfSsml
                }
            }
        }
        val voiceRegex = Regex("(?i)<voice([^>]*)>")
        result = voiceRegex.replace(result) { matchResult ->
            val attrs = matchResult.groupValues[1]
            if (!attrs.contains("name=", ignoreCase = true)) {
                "<voice$attrs name=\"$voiceName\">"
            } else {
                matchResult.value
            }
        }
        return result
    }

    private fun sendConfigAndSsml(webSocket: WebSocket, req: PendingRequest) {
        val dateStr = getFormattedDate()
        val configMsg = "X-Timestamp:$dateStr\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"${req.outputFormat}\"}}}}"
        
        Log.d(TAG, "Sending configMsg:\n$configMsg")
        val sentConfig = webSocket.send(configMsg)
        Log.d(TAG, "configMsg sent success: $sentConfig")

        val requestId = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.US)

        val ssmlBody = if (req.isSsml) {
            sanitizeSsml(req.text, req.voiceName, req.locale)
        } else {
            val escapedText = escapeXml(req.text)
            val ratePercent = Math.round((req.speed - 1.0f) * 100)
            val rateStr = (if (ratePercent >= 0) "+" else "") + ratePercent.toString() + "%"
            
            val pitchPercent = Math.round((req.pitch - 1.0f) * 100)
            val pitchStr = (if (pitchPercent >= 0) "+" else "") + pitchPercent.toString() + "%"
            
            val volumePercent = Math.round(req.volume * 100)
            val volumeStr = volumePercent.toString() + "%"

            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${req.locale}'>" +
                    "<voice name='${req.voiceName}'>" +
                    "<prosody pitch='$pitchStr' rate='$rateStr' volume='$volumeStr'>" +
                    escapedText +
                    "</prosody>" +
                    "</voice>" +
                    "</speak>"
        }

        val ssmlMsg = "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:${dateStr}Z\r\n" +
                "Path:ssml\r\n\r\n" +
                ssmlBody

        Log.d(TAG, "Sending ssmlMsg:\n$ssmlMsg")
        val sentSsml = webSocket.send(ssmlMsg)
        Log.d(TAG, "ssmlMsg sent success: $sentSsml")
    }

    fun stop() {
        synchronized(this) {
            isStopped = true
            cancelIdleTimeout()
            activeWebSocket?.let {
                it.close(1000, "Stopped")
                activeWebSocket = null
            }
            connectionState = ConnectionState.DISCONNECTED
            activeCallback = null
            pendingRequest = null
        }
    }

    private fun startIdleTimeout() {
        cancelIdleTimeout()
        idleTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "Idle timeout reached. Closing WebSocket connection.")
                    closeConnection()
                }
            }, 20000) // 20 seconds
        }
    }

    private fun cancelIdleTimeout() {
        idleTimer?.cancel()
        idleTimer = null
    }

    private fun closeConnection() {
        synchronized(this) {
            activeWebSocket?.close(1000, "Idle timeout or stop")
            activeWebSocket = null
            connectionState = ConnectionState.DISCONNECTED
            activeCallback = null
            pendingRequest = null
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
