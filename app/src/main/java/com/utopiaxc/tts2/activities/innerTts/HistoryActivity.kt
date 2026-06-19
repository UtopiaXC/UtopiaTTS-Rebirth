package com.utopiaxc.tts2.activities.innerTts

import android.content.ContentValues
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.utopiaxc.tts2.R
import com.utopiaxc.tts2.adapter.HistoryAdapter
import com.utopiaxc.tts2.databinding.ActivityHistoryBinding
import com.utopiaxc.tts2.databinding.DialogHistoryDetailBinding
import com.utopiaxc.tts2.storage.HistoryItem
import com.utopiaxc.tts2.storage.HistoryManager
import java.io.File

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyManager: HistoryManager
    private lateinit var adapter: HistoryAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var playingItem: HistoryItem? = null
    private var dialogPlayButton: com.google.android.material.button.MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyManager = HistoryManager.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(
            items = emptyList(),
            onItemClick = { item -> showDetailDialog(item) },
            onDeleteClick = { item -> confirmDelete(item) }
        )
        binding.recyclerViewHistory.adapter = adapter
    }

    private fun loadHistory() {
        val list = historyManager.getHistory().sortedByDescending { it.timestamp }
        adapter.updateData(list)
        if (list.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerViewHistory.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerViewHistory.visibility = View.VISIBLE
        }
    }

    private fun confirmDelete(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("确定要删除这条合成记录及其音频文件吗？")
            .setPositiveButton("删除") { _, _ ->
                if (playingItem?.id == item.id) {
                    stopPlaying()
                }
                historyManager.deleteRecord(item)
                loadHistory()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDetailDialog(item: HistoryItem) {
        val dialogBinding = DialogHistoryDetailBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvDialogText.text = item.text
        
        dialogBinding.tvDialogEngine.text = "引擎: ${item.engineName}"
        dialogBinding.tvDialogLocale.text = "语言: ${item.locale}"
        
        val voiceShortName = if (item.voiceId.contains("-")) item.voiceId.substringAfterLast("-").replace("Neural", "") else item.voiceId
        dialogBinding.tvDialogVoice.text = "音色: $voiceShortName"
        dialogBinding.tvDialogSsml.text = "SSML: ${if (item.isSsml) "启用" else "禁用"}"
        dialogBinding.tvDialogSpeed.text = "语速: ${item.speed}x"
        
        val volPercent = Math.round(item.volume * 100)
        dialogBinding.tvDialogParams.text = "音量: ${volPercent}% | 音调: ${item.pitch}x"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        if (playingItem?.id == item.id && mediaPlayer?.isPlaying == true) {
            dialogBinding.btnDialogPlay.text = "暂停"
            dialogBinding.btnDialogPlay.setIconResource(android.R.drawable.ic_media_pause)
            dialogPlayButton = dialogBinding.btnDialogPlay
        } else {
            dialogBinding.btnDialogPlay.text = "播放"
            dialogBinding.btnDialogPlay.setIconResource(android.R.drawable.ic_media_play)
        }

        dialogBinding.btnDialogPlay.setOnClickListener {
            handlePlayPause(item, dialogBinding.btnDialogPlay)
        }

        dialogBinding.btnDialogExport.setOnClickListener {
            exportAudio(item)
        }

        dialogBinding.btnDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            stopPlaying()
        }

        dialog.show()
    }

    private fun handlePlayPause(item: HistoryItem, playButton: com.google.android.material.button.MaterialButton) {
        val audioFile = File(historyManager.audioDir, item.audioFileName)
        if (!audioFile.exists()) {
            Toast.makeText(this, "音频文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        if (playingItem?.id == item.id) {
            val player = mediaPlayer
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                    playButton.text = "播放"
                    playButton.setIconResource(android.R.drawable.ic_media_play)
                } else {
                    player.start()
                    playButton.text = "暂停"
                    playButton.setIconResource(android.R.drawable.ic_media_pause)
                }
            } else {
                startPlaying(item, audioFile, playButton)
            }
        } else {
            stopPlaying()
            startPlaying(item, audioFile, playButton)
        }
    }

    private fun startPlaying(item: HistoryItem, file: File, playButton: com.google.android.material.button.MaterialButton) {
        playingItem = item
        dialogPlayButton = playButton

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@HistoryActivity, Uri.fromFile(file))
                setOnCompletionListener {
                    playButton.text = "播放"
                    playButton.setIconResource(android.R.drawable.ic_media_play)
                    stopPlaying()
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@HistoryActivity, "播放错误: what=$what, extra=$extra", Toast.LENGTH_SHORT).show()
                    playButton.text = "播放"
                    playButton.setIconResource(android.R.drawable.ic_media_play)
                    stopPlaying()
                    true
                }
                prepare()
                start()
            }
            playButton.text = "暂停"
            playButton.setIconResource(android.R.drawable.ic_media_pause)
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放音频: ${e.message}", Toast.LENGTH_SHORT).show()
            stopPlaying()
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null
        dialogPlayButton?.let {
            it.text = "播放"
            it.setIconResource(android.R.drawable.ic_media_play)
        }
        dialogPlayButton = null
        playingItem = null
    }

    private fun exportAudio(item: HistoryItem) {
        val srcFile = File(historyManager.audioDir, item.audioFileName)
        if (!srcFile.exists()) {
            Toast.makeText(this, "音频文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "UtopiaTTS_Export_${System.currentTimeMillis()}.mp3"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UtopiaTTS")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        srcFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(this, "导出成功，已保存至 Downloads/UtopiaTTS/$fileName", Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("无法在 MediaStore 中创建条目")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val utopiaDir = File(downloadsDir, "UtopiaTTS")
                if (!utopiaDir.exists()) {
                    utopiaDir.mkdirs()
                }
                val destFile = File(utopiaDir, fileName)
                srcFile.inputStream().use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(this, "导出成功: ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPlaying()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlaying()
    }
}
