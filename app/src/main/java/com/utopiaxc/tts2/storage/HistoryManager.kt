package com.utopiaxc.tts2.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

data class HistoryItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val audioFileName: String,
    val fileSize: Long,
    val voiceId: String,
    val speed: Float,
    val pitch: Float,
    val volume: Float,
    val isSsml: Boolean,
    val engineName: String = "Microsoft TTS (Free)",
    val locale: String = "zh-CN"
)

class HistoryManager private constructor(private val context: Context) {
    companion object {
        private const val HISTORY_FILE_NAME = "tts_history.json"
        
        @Volatile
        private var INSTANCE: HistoryManager? = null
        
        fun getInstance(context: Context): HistoryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = HistoryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val gson = Gson()
    private val preferenceManager = PreferenceManager.getInstance(context)
    
    private val historyFile: File
        get() = File(context.filesDir, HISTORY_FILE_NAME)
        
    val audioDir: File
        get() = File(context.filesDir, "history_audio").apply { if (!exists()) mkdirs() }
        
    @Synchronized
    fun getHistory(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val json = historyFile.readText()
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    @Synchronized
    private fun saveHistory(list: List<HistoryItem>) {
        try {
            val json = gson.toJson(list)
            historyFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    @Synchronized
    fun addRecord(
        text: String,
        audioData: ByteArray,
        voiceId: String,
        speed: Float,
        pitch: Float,
        volume: Float,
        isSsml: Boolean,
        engineName: String,
        locale: String
    ): HistoryItem? {
        try {
            val id = UUID.randomUUID().toString()
            val fileName = "audio_$id.mp3"
            val file = File(audioDir, fileName)
            file.writeBytes(audioData)
            
            val item = HistoryItem(
                id = id,
                text = text,
                timestamp = System.currentTimeMillis(),
                audioFileName = fileName,
                fileSize = file.length(),
                voiceId = voiceId,
                speed = Math.round(speed * 10f) / 10f,
                pitch = Math.round(pitch * 10f) / 10f,
                volume = Math.round(volume * 100f) / 100f,
                isSsml = isSsml,
                engineName = engineName,
                locale = locale
            )
            
            val list = getHistory().toMutableList()
            list.add(item)
            
            // Prune
            pruneHistory(list)
            
            saveHistory(list)
            return item
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    @Synchronized
    fun deleteRecord(item: HistoryItem) {
        val file = File(audioDir, item.audioFileName)
        if (file.exists()) {
            file.delete()
        }
        val list = getHistory().filter { it.id != item.id }
        saveHistory(list)
    }
    
    @Synchronized
    fun clearAll() {
        val list = getHistory()
        for (item in list) {
            val file = File(audioDir, item.audioFileName)
            if (file.exists()) file.delete()
        }
        saveHistory(emptyList())
    }

    @Synchronized
    fun prune() {
        val list = getHistory().toMutableList()
        pruneHistory(list)
        saveHistory(list)
    }
    
    private fun pruneHistory(list: MutableList<HistoryItem>) {
        val maxCount = preferenceManager.maxHistoryCount
        val maxCacheMb = preferenceManager.maxCacheSizeMb
        
        val maxCountLimit = if (maxCount > 0) maxCount else Int.MAX_VALUE
        val maxCacheLimit = if (maxCacheMb > 0) maxCacheMb.toLong() * 1024 * 1024 else Long.MAX_VALUE
        
        // Sort by timestamp ascending (oldest first)
        list.sortBy { it.timestamp }
        
        while (list.isNotEmpty() && (list.size > maxCountLimit || calculateTotalSize(list) > maxCacheLimit)) {
            val oldest = list.removeAt(0)
            val file = File(audioDir, oldest.audioFileName)
            if (file.exists()) file.delete()
        }
    }
    
    private fun calculateTotalSize(list: List<HistoryItem>): Long {
        return list.sumOf { it.fileSize }
    }
    
    fun getFormattedCacheSize(): String {
        val bytes = calculateTotalSize(getHistory())
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", mb)
    }
}
