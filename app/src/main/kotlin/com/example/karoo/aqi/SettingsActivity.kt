package com.example.karoo.aqi

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val apiKeyInput = findViewById<EditText>(R.id.input_api_key)
        val btnSave = findViewById<Button>(R.id.btn_save)

        btnSave.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.length == 32) {
                saveConfig(apiKey)
            } else {
                Toast.makeText(this, "API Key must be 32 characters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveConfig(apiKey: String) {
        try {
            // Build the JSON structure expected by ConfigManager
            val json = JSONObject().apply {
                put("api_key", apiKey)
                put("standard", "US")
                put("display_mode", "AQI")
                put("alert_threshold", 100)
                put("alert_enabled", true)
            }

            // Write it directly to the public Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "aqi_config.json")
            file.writeText(json.toString(4)) // 4 spaces for readable formatting

            Toast.makeText(this, "Settings Saved! You can now use the data field.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}