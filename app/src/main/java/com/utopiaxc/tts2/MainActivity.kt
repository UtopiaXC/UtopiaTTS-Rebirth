package com.utopiaxc.tts2

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.utopiaxc.tts2.activities.innerTts.HistoryActivity
import com.utopiaxc.tts2.adapter.MainPagerAdapter
import com.utopiaxc.tts2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    var onImportFileClickListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 3

        binding.viewPager.isUserInputEnabled = false

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_voice -> {
                    binding.viewPager.setCurrentItem(0, false)
                    true
                }
                R.id.nav_synthesis -> {
                    binding.viewPager.setCurrentItem(1, false)
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.setCurrentItem(2, false)
                    true
                }
                else -> false
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                
                // Show action buttons in header only for synthesis tab (position 1)
                if (position == 1) {
                    binding.layoutActionButtons.visibility = View.VISIBLE
                } else {
                    binding.layoutActionButtons.visibility = View.GONE
                }
            }
        })

        binding.btnImportFile.setOnClickListener {
            onImportFileClickListener?.invoke()
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
}
