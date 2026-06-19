package com.utopiaxc.tts2.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.utopiaxc.tts2.databinding.ItemHistoryBinding
import com.utopiaxc.tts2.storage.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateData(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.tvText.text = item.text
            binding.tvDate.text = dateFormat.format(Date(item.timestamp))
            
            val kb = item.fileSize.toDouble() / 1024.0
            binding.tvSize.text = String.format(Locale.getDefault(), "%.1f KB", kb)

            val voiceShortName = if (item.voiceId.contains("-")) item.voiceId.substringAfterLast("-").replace("Neural", "") else item.voiceId
            val ssmlStr = if (item.isSsml) "SSML: 开启" else "SSML: 关闭"
            binding.tvMetadata.text = "${item.engineName} | $voiceShortName | $ssmlStr"

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}
