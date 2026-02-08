package com.example.chat

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

import android.widget.EditText
import android.widget.RadioGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etOpenAIKey = findViewById<EditText>(R.id.et_openai_key)
        val etGeminiKey = findViewById<EditText>(R.id.et_gemini_key)
        val spGeminiModel = findViewById<Spinner>(R.id.sp_gemini_model)
        val btnRefreshModels = findViewById<Button>(R.id.btn_refresh_models)
        val rgProvider = findViewById<RadioGroup>(R.id.rg_provider)

        // Load saved prefs
        etOpenAIKey.setText(SettingsManager.getApiKey(this, "openai"))
        etGeminiKey.setText(SettingsManager.getApiKey(this, "gemini"))
        
        // Initial Spinner population (Saved model + Default)
        val savedModel = SettingsManager.getGeminiModel(this)
        val defaultModels = mutableListOf(savedModel)
        if (savedModel != "gemini-2.0-flash") defaultModels.add("gemini-2.0-flash")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, defaultModels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spGeminiModel.adapter = adapter
        
        val currentProvider = SettingsManager.getProvider(this)
        if (currentProvider == "gemini") {
            rgProvider.check(R.id.rb_gemini)
        } else {
            rgProvider.check(R.id.rb_openai)
        }
        
        btnRefreshModels.setOnClickListener {
            val key = etGeminiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter Gemini API Key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Fetching models...", Toast.LENGTH_SHORT).show()
            
            GeminiModelFetcher.fetchModels(key) { models ->
                runOnUiThread {
                    if (models.isNotEmpty()) {
                        val modelNames = models.map { it.name }
                        val newAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
                        newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spGeminiModel.adapter = newAdapter
                        
                        // Try to re-select preserved model
                        val pos = modelNames.indexOf(SettingsManager.getGeminiModel(this))
                        if (pos >= 0) spGeminiModel.setSelection(pos)
                        
                        Toast.makeText(this, "Models refreshed!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to fetch models", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            SettingsManager.setApiKey(this, "openai", etOpenAIKey.text.toString().trim())
            SettingsManager.setApiKey(this, "gemini", etGeminiKey.text.toString().trim())
            
            val selectedModel = spGeminiModel.selectedItem?.toString() ?: "gemini-2.0-flash"
            SettingsManager.setGeminiModel(this, selectedModel)
            
            val selectedProvider = if (rgProvider.checkedRadioButtonId == R.id.rb_gemini) "gemini" else "openai"
            SettingsManager.setProvider(this, selectedProvider)
            
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_start_sim).setOnClickListener {
            val intent = Intent(this, MessagingService::class.java)
            intent.action = "com.example.chat.SIMULATE_MESSAGE"
            startService(intent)
        }
    }
}
