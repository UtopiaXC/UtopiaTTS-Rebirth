package com.utopiaxc.tts2.engine.azure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object AzureConfig {
    private const val TAG = "AzureConfig"
    private const val PREF_KEY_AZURE_SUBSCRIPTION_KEY = "azure_subscription_key"
    private const val PREF_KEY_AZURE_REGION = "azure_region"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "utopia_tts_encrypted_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences", e)
            context.getSharedPreferences("utopia_tts_encrypted_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getSubscriptionKey(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(PREF_KEY_AZURE_SUBSCRIPTION_KEY, "") ?: ""
    }

    fun getRegion(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(PREF_KEY_AZURE_REGION, "") ?: ""
    }

    fun saveCredentials(context: Context, key: String, region: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putString(PREF_KEY_AZURE_SUBSCRIPTION_KEY, key.trim())
            .putString(PREF_KEY_AZURE_REGION, region.trim())
            .apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getSubscriptionKey(context).isNotEmpty() && getRegion(context).isNotEmpty()
    }
}
