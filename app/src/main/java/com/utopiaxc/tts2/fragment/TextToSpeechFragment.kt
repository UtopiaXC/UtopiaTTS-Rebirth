package com.utopiaxc.tts2.fragment

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.utopiaxc.tts2.R
import com.utopiaxc.tts2.databinding.FragmentTextToSpeechBinding
import com.utopiaxc.tts2.engine.TtsEngineManager
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import com.utopiaxc.tts2.storage.PreferenceManager
import com.utopiaxc.tts2.util.AudioPlayerHelper
import com.utopiaxc.tts2.util.NetworkUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class TextToSpeechFragment : Fragment() {
    private var _binding: FragmentTextToSpeechBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var ttsEngineManager: TtsEngineManager
    private lateinit var audioPlayerHelper: AudioPlayerHelper

    private var allVoices: List<VoiceInfo> = emptyList()
    private var isSynthesizing = false
    private var isPlaying = false
    private var synthesizedBytes: ByteArray? = null

    private val audioOutputStream = ByteArrayOutputStream()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextToSpeechBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferenceManager = PreferenceManager.getInstance(requireContext())
        ttsEngineManager = TtsEngineManager.getInstance(requireContext())
        audioPlayerHelper = AudioPlayerHelper(requireContext())

        setupButtons()
        loadVoiceList()
    }

    private fun loadVoiceList() {
        val engineType = preferenceManager.engineType
        ttsEngineManager.getVoiceList(engineType, object : VoiceListCallback {
            override fun onSuccess(voices: List<VoiceInfo>) {
                activity?.runOnUiThread {
                    allVoices = voices
                }
            }

            override fun onError(error: Throwable) {}
        })
    }

    private fun setupButtons() {
        binding.btnSynthesize.setOnClickListener {
            if (isSynthesizing) {
                stopSynthesis()
            } else {
                startSynthesis()
            }
        }

        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                audioPlayerHelper.pause()
                isPlaying = false
                binding.btnPlayPause.text = getString(R.string.btn_play)
            } else {
                val bytes = synthesizedBytes
                if (bytes != null) {
                    if (audioPlayerHelper.isPlaying()) {
                        audioPlayerHelper.resume()
                        isPlaying = true
                        binding.btnPlayPause.text = getString(R.string.btn_pause)
                    } else {
                        isPlaying = true
                        binding.btnPlayPause.text = getString(R.string.btn_pause)
                        audioPlayerHelper.playRawBytes(
                            bytes,
                            onComplete = {
                                activity?.runOnUiThread {
                                    isPlaying = false
                                    binding.btnPlayPause.text = getString(R.string.btn_play)
                                }
                            },
                            onError = { error ->
                                activity?.runOnUiThread {
                                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                                    isPlaying = false
                                    binding.btnPlayPause.text = getString(R.string.btn_play)
                                }
                            }
                        )
                    }
                }
            }
        }

        binding.btnStop.setOnClickListener {
            audioPlayerHelper.stop()
            isPlaying = false
            binding.btnPlayPause.text = getString(R.string.btn_play)
        }

        binding.btnSave.setOnClickListener {
            saveSynthesizedAudio()
        }
    }

    private fun startSynthesis() {
        val text = binding.etInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter text to synthesize", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedVoiceId = preferenceManager.selectedVoiceId
        val voice = allVoices.find { it.id == selectedVoiceId } ?: VoiceInfo(
            id = selectedVoiceId.ifEmpty { "zh-CN-XiaoxiaoNeural" },
            name = selectedVoiceId.ifEmpty { "zh-CN-XiaoxiaoNeural" },
            displayName = if (selectedVoiceId.contains("-")) selectedVoiceId.substringAfterLast("-").replace("Neural", "") else "Xiaoxiao",
            locale = if (selectedVoiceId.contains("-")) {
                val parts = selectedVoiceId.split("-")
                if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "zh-CN"
            } else "zh-CN",
            gender = "Female",
            engineType = preferenceManager.engineType
        )

        if (!NetworkUtils.isNetworkAvailable(requireContext()) && voice.engineType == TtsEngineType.EDGE_WS) {
            Toast.makeText(requireContext(), R.string.network_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        isSynthesizing = true
        synthesizedBytes = null
        audioOutputStream.reset()

        binding.btnSynthesize.text = getString(R.string.btn_stop)
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusInfo.text = getString(R.string.status_synthesizing)
        binding.layoutPlayerControls.visibility = View.GONE

        ttsEngineManager.getCurrentEngine().synthesize(
            text = text,
            voice = voice,
            speed = preferenceManager.speed,
            pitch = preferenceManager.pitch,
            volume = preferenceManager.volume,
            callback = object : TtsSynthesisCallback {
                override fun onStart() {
                    activity?.runOnUiThread {
                        binding.tvStatusInfo.text = "Streaming audio data..."
                    }
                }

                override fun onAudioAvailable(audioData: ByteArray) {
                    audioOutputStream.write(audioData)
                }

                override fun onDone() {
                    activity?.runOnUiThread {
                        synthesizedBytes = audioOutputStream.toByteArray()
                        isSynthesizing = false
                        binding.btnSynthesize.text = getString(R.string.btn_synthesize)
                        binding.cardStatus.visibility = View.GONE
                        binding.layoutPlayerControls.visibility = View.VISIBLE
                        binding.btnPlayPause.text = getString(R.string.btn_play)
                        Toast.makeText(requireContext(), R.string.synthesis_success, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(error: String) {
                    activity?.runOnUiThread {
                        isSynthesizing = false
                        binding.btnSynthesize.text = getString(R.string.btn_synthesize)
                        binding.cardStatus.visibility = View.GONE
                        Toast.makeText(requireContext(), getString(R.string.synthesis_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun stopSynthesis() {
        ttsEngineManager.getCurrentEngine().stop()
        isSynthesizing = false
        binding.btnSynthesize.text = getString(R.string.btn_synthesize)
        binding.cardStatus.visibility = View.GONE
    }

    private fun saveSynthesizedAudio() {
        val bytes = synthesizedBytes
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(requireContext(), "No synthesized audio to save", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "UtopiaTTS_${System.currentTimeMillis()}.mp3"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UtopiaTTS")
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                    }
                    Toast.makeText(requireContext(), getString(R.string.save_success, "Downloads/UtopiaTTS/$fileName"), Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("Failed to insert MediaStore row")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val utopiaDir = File(downloadsDir, "UtopiaTTS")
                if (!utopiaDir.exists()) {
                    utopiaDir.mkdirs()
                }
                val destFile = File(utopiaDir, fileName)
                FileOutputStream(destFile).use { fos ->
                    fos.write(bytes)
                }
                Toast.makeText(requireContext(), getString(R.string.save_success, destFile.absolutePath), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.save_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioPlayerHelper.stop()
        _binding = null
    }
}
