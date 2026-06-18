package com.utopiaxc.tts2.engine.edge

import android.util.Log
import java.security.MessageDigest
import java.util.Locale

object EdgeDrm {
    private const val TAG = "EdgeDrm"
    
    @Volatile
    var clockSkewSeconds: Long = 0L

    fun generateSecMsGecHeader(): String {
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val adjustedTimeSeconds = clientTimeSeconds + clockSkewSeconds
        val windowsEpochSeconds = adjustedTimeSeconds + EdgeConstants.WINDOWS_EPOCH_OFFSET
        
        val roundedSeconds = windowsEpochSeconds - (windowsEpochSeconds % 300)
        val ticks = roundedSeconds * 10_000_000L
        
        val hashInput = ticks.toString() + EdgeConstants.TRUSTED_CLIENT_TOKEN
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(hashInput.toByteArray(Charsets.UTF_8))
        
        val token = hashBytes.joinToString("") {
            String.format(Locale.US, "%02X", it)
        }
        
        Log.d(TAG, "generateSecMsGecHeader: clientTime=$clientTimeSeconds, clockSkew=$clockSkewSeconds, adjustedTime=$adjustedTimeSeconds, ticks=$ticks, token=$token")
        
        return token
    }

    fun generateMuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") {
            String.format(Locale.US, "%02X", it)
        }
    }
}
