package com.example.karoo.aqi

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import org.json.JSONObject
import java.io.File

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val apiKeyInput = findViewById<EditText>(R.id.input_api_key)
        val standardSpinner = findViewById<Spinner>(R.id.spinner_standard)
        val displayModeSpinner = findViewById<Spinner>(R.id.spinner_display_mode)
        val alertSwitch = findViewById<Switch>(R.id.switch_alert)
        val thresholdInput = findViewById<EditText>(R.id.input_threshold)
        val btnSave = findViewById<Button>(R.id.btn_save)

        // Populate Standard Spinner
        val standards = arrayOf("US", "UK", "WHO", "EU", "AU")
        standardSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, standards)

        // Populate Display Mode Spinner
        val displayModes = arrayOf("AQI", "PM25", "PM10", "O3", "NO2", "SO2", "CO")
        displayModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayModes)

        btnSave.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.length != 32) {
                Toast.makeText(this, "API Key must be 32 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val standard = standardSpinner.selectedItem.toString()
            val displayMode = displayModeSpinner.selectedItem.toString()
            val alertEnabled = alertSwitch.isChecked
            val thresholdStr = thresholdInput.text.toString()
            val threshold = thresholdStr.toIntOrNull() ?: 100

            saveConfig(apiKey, standard, displayMode, alertEnabled, threshold)
        }
    }

    private fun saveConfig(apiKey: String, standard: String, displayMode: String, alertEnabled: Boolean, threshold: Int) {
        try {
            val json = JSONObject().apply {
                put("api_key", apiKey)
                put("standard", standard)
                put("display_mode", displayMode)
                put("alert_threshold", threshold)
                put("alert_enabled", alertEnabled)
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "aqi_config.json")
            file.writeText(json.toString(4))

            Toast.makeText(this, "Settings Saved! You can now add the data field.", Toast.LENGTH_LONG).show()

            // Close the activity upon success
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}