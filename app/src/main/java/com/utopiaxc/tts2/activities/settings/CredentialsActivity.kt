package com.utopiaxc.tts2.activities.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.utopiaxc.tts2.R
import com.utopiaxc.tts2.databinding.ActivityCredentialsBinding
import com.utopiaxc.tts2.engine.azure.AzureConfig

class CredentialsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityCredentialsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val savedKey = AzureConfig.getSubscriptionKey(this)
        val savedRegion = AzureConfig.getRegion(this)
        binding.etAzureKey.setText(savedKey)
        binding.etAzureRegion.setText(savedRegion)

        binding.btnSaveAzureConfig.setOnClickListener {
            val key = binding.etAzureKey.text?.toString()?.trim() ?: ""
            val region = binding.etAzureRegion.text?.toString()?.trim() ?: ""
            
            if (key.isEmpty() || region.isEmpty()) {
                Toast.makeText(this, R.string.azure_config_empty, Toast.LENGTH_SHORT).show()
            } else {
                AzureConfig.saveCredentials(this, key, region)
                Toast.makeText(this, R.string.azure_config_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
