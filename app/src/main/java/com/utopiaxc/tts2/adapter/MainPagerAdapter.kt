package com.utopiaxc.tts2.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.utopiaxc.tts2.fragment.SettingsFragment
import com.utopiaxc.tts2.fragment.TextToSpeechFragment
import com.utopiaxc.tts2.fragment.VoiceConfigFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> VoiceConfigFragment()
            1 -> TextToSpeechFragment()
            2 -> SettingsFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
