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
    private const val ENCRYPTED_PREFS_NAME = "utopia_tts_encrypted_prefs"
    private const val FALLBACK_PREFS_NAME = "utopia_tts_encrypted_prefs_fallback"

    @Volatile
    private var encryptionAvailable: Boolean? = null

    /**
     * 检查加密存储是否可用。
     * 如果返回 false，Azure 凭证将以明文存储在普通 SharedPreferences 中。
     */
    fun isEncryptionAvailable(context: Context): Boolean {
        encryptionAvailable?.let { return it }
        return tryInitEncryptedPrefs(context)
    }

    private fun tryInitEncryptedPrefs(context: Context): Boolean {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encryptionAvailable = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences 初始化失败！Azure 凭证将以明文存储！", e)
            Log.w(TAG, "设备不支持加密存储或首次初始化异常，正在降级到普通 SharedPreferences。建议检查设备是否具备硬件加密支持。")
            encryptionAvailable = false
            false
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return if (tryInitEncryptedPrefs(context)) {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else {
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
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
