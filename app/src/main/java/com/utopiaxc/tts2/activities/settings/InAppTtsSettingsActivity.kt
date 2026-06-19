package com.utopiaxc.tts2.activities.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.utopiaxc.tts2.R
import com.utopiaxc.tts2.databinding.ActivityInAppTtsSettingsBinding
import com.utopiaxc.tts2.storage.HistoryManager
import com.utopiaxc.tts2.storage.PreferenceManager

class InAppTtsSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInAppTtsSettingsBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var historyManager: HistoryManager

    private val countOptions = arrayOf("100条", "200条", "500条", "无上限", "自定义")
    private val countValues = arrayOf(100, 200, 500, 0, -1) // -1 means custom

    private val sizeOptions = arrayOf("100MB", "200MB", "500MB", "无上限", "自定义")
    private val sizeValues = arrayOf(100, 200, 500, 0, -1) // -1 means custom

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInAppTtsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager.getInstance(this)
        historyManager = HistoryManager.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupHistoryCountLimit()
        setupCacheSizeLimit()
        updateCurrentCacheInfo()

        binding.btnClearCache.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要清空所有的历史记录与缓存音频吗？该操作不可恢复。")
                .setPositiveButton("确定") { _, _ ->
                    historyManager.clearAll()
                    updateCurrentCacheInfo()
                    Toast.makeText(this, "清理完毕", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun setupHistoryCountLimit() {
        val countAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countOptions)
        binding.spinnerHistoryCount.setAdapter(countAdapter)

        val currentCount = preferenceManager.maxHistoryCount
        when (currentCount) {
            100 -> {
                binding.spinnerHistoryCount.setText("100条", false)
                binding.layoutCustomCount.visibility = View.GONE
            }
            200 -> {
                binding.spinnerHistoryCount.setText("200条", false)
                binding.layoutCustomCount.visibility = View.GONE
            }
            500 -> {
                binding.spinnerHistoryCount.setText("500条", false)
                binding.layoutCustomCount.visibility = View.GONE
            }
            0 -> {
                binding.spinnerHistoryCount.setText("无上限", false)
                binding.layoutCustomCount.visibility = View.GONE
            }
            else -> {
                binding.spinnerHistoryCount.setText("自定义", false)
                binding.layoutCustomCount.visibility = View.VISIBLE
                binding.etCustomCount.setText(currentCount.toString())
            }
        }

        binding.spinnerHistoryCount.setOnItemClickListener { _, _, position, _ ->
            val value = countValues[position]
            if (value >= 0) {
                binding.layoutCustomCount.visibility = View.GONE
                preferenceManager.maxHistoryCount = value
                historyManager.prune()
                updateCurrentCacheInfo()
            } else {
                binding.layoutCustomCount.visibility = View.VISIBLE
                val customVal = binding.etCustomCount.text.toString().toIntOrNull() ?: 100
                preferenceManager.maxHistoryCount = customVal
                historyManager.prune()
                updateCurrentCacheInfo()
            }
        }

        binding.etCustomCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toIntOrNull()
                if (input != null && input > 0) {
                    preferenceManager.maxHistoryCount = input
                    historyManager.prune()
                    updateCurrentCacheInfo()
                }
            }
        })
    }

    private fun setupCacheSizeLimit() {
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizeOptions)
        binding.spinnerCacheSize.setAdapter(sizeAdapter)

        val currentSize = preferenceManager.maxCacheSizeMb
        when (currentSize) {
            100 -> {
                binding.spinnerCacheSize.setText("100MB", false)
                binding.layoutCustomSize.visibility = View.GONE
            }
            200 -> {
                binding.spinnerCacheSize.setText("200MB", false)
                binding.layoutCustomSize.visibility = View.GONE
            }
            500 -> {
                binding.spinnerCacheSize.setText("500MB", false)
                binding.layoutCustomSize.visibility = View.GONE
            }
            0 -> {
                binding.spinnerCacheSize.setText("无上限", false)
                binding.layoutCustomSize.visibility = View.GONE
            }
            else -> {
                binding.spinnerCacheSize.setText("自定义", false)
                binding.layoutCustomSize.visibility = View.VISIBLE
                binding.etCustomSize.setText(currentSize.toString())
            }
        }

        binding.spinnerCacheSize.setOnItemClickListener { _, _, position, _ ->
            val value = sizeValues[position]
            if (value >= 0) {
                binding.layoutCustomSize.visibility = View.GONE
                preferenceManager.maxCacheSizeMb = value
                historyManager.prune()
                updateCurrentCacheInfo()
            } else {
                binding.layoutCustomSize.visibility = View.VISIBLE
                val customVal = binding.etCustomSize.text.toString().toIntOrNull() ?: 100
                preferenceManager.maxCacheSizeMb = customVal
                historyManager.prune()
                updateCurrentCacheInfo()
            }
        }

        binding.etCustomSize.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toIntOrNull()
                if (input != null && input > 0) {
                    preferenceManager.maxCacheSizeMb = input
                    historyManager.prune()
                    updateCurrentCacheInfo()
                }
            }
        })
    }

    private fun updateCurrentCacheInfo() {
        binding.tvCurrentCache.text = "当前已用缓存: ${historyManager.getFormattedCacheSize()}"
    }
}
