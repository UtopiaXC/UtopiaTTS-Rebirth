package com.utopiaxc.tts2.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.utopiaxc.tts2.R
import com.utopiaxc.tts2.databinding.FragmentVoiceConfigBinding
import com.utopiaxc.tts2.engine.TtsEngineManager
import com.utopiaxc.tts2.engine.TtsEngineType
import com.utopiaxc.tts2.engine.callback.TtsSynthesisCallback
import com.utopiaxc.tts2.engine.callback.VoiceListCallback
import com.utopiaxc.tts2.engine.model.VoiceInfo
import com.utopiaxc.tts2.storage.PreferenceManager
import com.utopiaxc.tts2.util.AudioPlayerHelper
import com.utopiaxc.tts2.util.NetworkUtils
import java.util.Locale

class VoiceConfigFragment : Fragment() {
    private var _binding: FragmentVoiceConfigBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var ttsEngineManager: TtsEngineManager
    private lateinit var audioPlayerHelper: AudioPlayerHelper

    private var allVoices: List<VoiceInfo> = emptyList()
    private var filteredVoices: List<VoiceInfo> = emptyList()
    private var selectedVoice: VoiceInfo? = null

    private var isPlayingPreview = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferenceManager = PreferenceManager.getInstance(requireContext())
        ttsEngineManager = TtsEngineManager.getInstance(requireContext())
        audioPlayerHelper = AudioPlayerHelper(requireContext())

        setupEngineSpinner()
        setupSliders()
        setupPreviewButton()
        
        loadVoiceList()
    }

    private fun setupEngineSpinner() {
        val engines = listOf("Microsoft Edge WS", "Microsoft Azure SDK")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, engines)
        binding.autoEngine.setAdapter(adapter)

        val savedEngine = preferenceManager.engineType
        binding.autoEngine.setText(engines[savedEngine.ordinal], false)

        binding.autoEngine.setOnItemClickListener { _, _, position, _ ->
            val selectedType = TtsEngineType.values()[position]
            if (preferenceManager.engineType != selectedType) {
                preferenceManager.engineType = selectedType
                loadVoiceList()
            }
        }
    }

    private fun loadVoiceList() {
        binding.tvStatus.text = getString(R.string.status_loading_voices)
        
        if (!NetworkUtils.isNetworkAvailable(requireContext()) && preferenceManager.engineType == TtsEngineType.EDGE_WS) {
            binding.tvStatus.text = getString(R.string.network_unavailable)
            Toast.makeText(requireContext(), R.string.network_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val engineType = preferenceManager.engineType
        ttsEngineManager.getVoiceList(engineType, object : VoiceListCallback {
            override fun onSuccess(voices: List<VoiceInfo>) {
                activity?.runOnUiThread {
                    allVoices = voices
                    setupLanguageSpinner()
                    binding.tvStatus.text = getString(R.string.status_idle)
                }
            }

            override fun onError(error: Throwable) {
                activity?.runOnUiThread {
                    binding.tvStatus.text = getString(R.string.synthesis_failed, error.message)
                    Toast.makeText(requireContext(), error.message ?: "Failed to load voices", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setupLanguageSpinner() {
        val locales = allVoices.map { it.locale }.distinct().sorted()
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, locales)
        binding.autoLanguage.setAdapter(adapter)

        val savedVoiceId = preferenceManager.selectedVoiceId
        val savedVoice = allVoices.find { it.id == savedVoiceId }
        val savedLocale = savedVoice?.locale ?: "zh-CN"
        
        val selectIndex = locales.indexOf(savedLocale)
        if (selectIndex >= 0) {
            binding.autoLanguage.setText(locales[selectIndex], false)
            filterVoicesByLocale(locales[selectIndex])
        } else if (locales.isNotEmpty()) {
            binding.autoLanguage.setText(locales[0], false)
            filterVoicesByLocale(locales[0])
        }

        binding.autoLanguage.setOnItemClickListener { _, _, position, _ ->
            val selectedLocale = locales[position]
            filterVoicesByLocale(selectedLocale)
        }
    }

    private fun filterVoicesByLocale(localeStr: String) {
        filteredVoices = allVoices.filter { it.locale == localeStr }
        
        val voiceNames = filteredVoices.map { 
            val genderLocal = if (it.gender.equals("female", true)) getString(R.string.gender_female) else getString(R.string.gender_male)
            "${it.displayName} ($genderLocal)"
        }
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, voiceNames)
        binding.autoVoice.setAdapter(adapter)

        val savedVoiceId = preferenceManager.selectedVoiceId
        val savedVoiceIndex = filteredVoices.indexOfFirst { it.id == savedVoiceId }
        if (savedVoiceIndex >= 0) {
            binding.autoVoice.setText(voiceNames[savedVoiceIndex], false)
            selectedVoice = filteredVoices[savedVoiceIndex]
        } else if (filteredVoices.isNotEmpty()) {
            binding.autoVoice.setText(voiceNames[0], false)
            selectedVoice = filteredVoices[0]
            preferenceManager.selectedVoiceId = selectedVoice!!.id
        }

        binding.autoVoice.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < filteredVoices.size) {
                selectedVoice = filteredVoices[position]
                preferenceManager.selectedVoiceId = selectedVoice!!.id
            }
        }
    }

    private fun setupSliders() {
        binding.sliderSpeed.value = preferenceManager.speed
        updateSpeedLabel(preferenceManager.speed)
        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            preferenceManager.speed = value
            updateSpeedLabel(value)
        }

        binding.sliderPitch.value = preferenceManager.pitch
        updatePitchLabel(preferenceManager.pitch)
        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            preferenceManager.pitch = value
            updatePitchLabel(value)
        }

        binding.sliderVolume.value = preferenceManager.volume
        updateVolumeLabel(preferenceManager.volume)
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            preferenceManager.volume = value
            updateVolumeLabel(value)
        }
    }

    private fun updateSpeedLabel(value: Float) {
        binding.tvSpeedLabel.text = getString(R.string.speed_rate, String.format(Locale.US, "%.1fx", value))
    }

    private fun updatePitchLabel(value: Float) {
        binding.tvPitchLabel.text = getString(R.string.voice_pitch, String.format(Locale.US, "%.1fx", value))
    }

    private fun updateVolumeLabel(value: Float) {
        val percent = Math.round(value * 100)
        binding.tvVolumeLabel.text = getString(R.string.voice_volume, "$percent%")
    }

    private fun setupPreviewButton() {
        binding.btnPreview.setOnClickListener {
            if (isPlayingPreview) {
                stopPlayback()
            } else {
                startPreviewSynthesis()
            }
        }
    }

    private fun startPreviewSynthesis() {
        val voice = selectedVoice
        if (voice == null) {
            Toast.makeText(requireContext(), "No voice selected", Toast.LENGTH_SHORT).show()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(requireContext()) && voice.engineType == TtsEngineType.EDGE_WS) {
            Toast.makeText(requireContext(), R.string.network_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        isPlayingPreview = true
        binding.btnPreview.text = getString(R.string.btn_stop)
        binding.tvStatus.text = getString(R.string.status_synthesizing)
        
        audioPlayerHelper.startAccumulating()

        ttsEngineManager.getCurrentEngine().synthesize(
            text = getString(R.string.preview_default_text),
            voice = voice,
            speed = preferenceManager.speed,
            pitch = preferenceManager.pitch,
            volume = preferenceManager.volume,
            callback = object : TtsSynthesisCallback {
                override fun onStart() {
                    activity?.runOnUiThread {
                        binding.tvStatus.text = getString(R.string.btn_playing)
                    }
                }

                override fun onAudioAvailable(audioData: ByteArray) {
                    audioPlayerHelper.appendBytes(audioData)
                }

                override fun onDone() {
                    activity?.runOnUiThread {
                        audioPlayerHelper.finishAccumulatingAndPlay(
                            onComplete = {
                                activity?.runOnUiThread { stopPlayback() }
                            },
                            onError = { error ->
                                activity?.runOnUiThread {
                                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                                    stopPlayback()
                                }
                            }
                        )
                    }
                }

                override fun onError(error: String) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        stopPlayback()
                    }
                }
            }
        )
    }

    private fun stopPlayback() {
        isPlayingPreview = false
        audioPlayerHelper.stop()
        ttsEngineManager.getCurrentEngine().stop()
        binding.btnPreview.text = getString(R.string.btn_preview)
        binding.tvStatus.text = getString(R.string.status_idle)
    }

    override fun onPause() {
        super.onPause()
        if (isPlayingPreview) {
            stopPlayback()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
