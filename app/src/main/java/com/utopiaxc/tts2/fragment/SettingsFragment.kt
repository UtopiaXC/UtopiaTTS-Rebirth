package com.utopiaxc.tts2.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.utopiaxc.tts2.activities.settings.AboutActivity
import com.utopiaxc.tts2.activities.settings.CredentialsActivity
import com.utopiaxc.tts2.activities.settings.InAppTtsSettingsActivity
import com.utopiaxc.tts2.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.itemCredentials.setOnClickListener {
            val intent = Intent(requireContext(), CredentialsActivity::class.java)
            startActivity(intent)
        }

        binding.itemInAppTtsSettings.setOnClickListener {
            val intent = Intent(requireContext(), InAppTtsSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.itemAbout.setOnClickListener {
            val intent = Intent(requireContext(), AboutActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
