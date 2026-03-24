package com.example.karoo.aqi

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.widget.TextView

class SettingsActivity : Activity() {

    private val scope = MainScope() // Scope for managing coroutines

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val apiKeyInput = findViewById<EditText>(R.id.input_api_key)
        val standardSpinner = findViewById<Spinner>(R.id.spinner_standard)
        val displayModeSpinner = findViewById<Spinner>(R.id.spinner_display_mode)
        val alertSwitch = findViewById<Switch>(R.id.switch_alert)
        val thresholdInput = findViewById<EditText>(R.id.input_threshold)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val thresholdLabel = findViewById<TextView>(R.id.label_threshold) // Add this line

        val displayNamesToCodes = mapOf(
            "US (EPA)" to "US",
            "CN (MEP)" to "CN",
            "IN (CPCB)" to "IN",
            "UK (DAQI)" to "UK",
            "EU (CAQI)" to "EU",
            "AU (NEPM)" to "AU"
        )

        val codesToPollutants = mapOf(
            "US" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2", "CO"),
            "CN" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2", "CO"),
            "IN" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2", "CO"),
            "UK" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2"),
            "EU" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2", "CO"),
            "AU" to arrayOf("AQI", "PM2.5", "PM10", "O3", "NO2", "SO2", "CO")
        )

        val standardsList = displayNamesToCodes.keys.toTypedArray()
        standardSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, standardsList)

        standardSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedUiName = standardsList[position]
                val backendCode = displayNamesToCodes[selectedUiName] ?: "US"
                val availableModes = codesToPollutants[backendCode] ?: arrayOf("AQI")

                val currentMode = displayModeSpinner.selectedItem?.toString()

                val modeAdapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, availableModes)
                displayModeSpinner.adapter = modeAdapter

                val modePosition = availableModes.indexOf(currentMode)
                if (modePosition >= 0) {
                    displayModeSpinner.setSelection(modePosition)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Make the Threshold label react to the Display Mode spinner
        displayModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = parent?.getItemAtPosition(position).toString()
                if (selectedMode == "AQI") {
                    thresholdLabel.text = "Alert Threshold (AQI):"
                } else {
                    thresholdLabel.text = "Alert Threshold (µg/m³):"
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.length != 32) {
                Toast.makeText(this, "API Key must be 32 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedUiName = standardSpinner.selectedItem.toString()
            val standardCode = displayNamesToCodes[selectedUiName] ?: "US"
            val displayModeRaw = displayModeSpinner.selectedItem.toString()
            val displayMode = displayModeRaw.replace(".", "")
            val alertEnabled = alertSwitch.isChecked
            val thresholdStr = thresholdInput.text.toString()
            val threshold = thresholdStr.toIntOrNull() ?: 100

            // Update UI to show working state
            btnSave.isEnabled = false
            btnSave.text = "Validating..."

            // Launch background network check
            scope.launch {
                val isValid = validateApiKey(apiKey)
                if (isValid) {
                    saveConfig(apiKey, standardCode, displayMode, alertEnabled, threshold)
                } else {
                    Toast.makeText(this@SettingsActivity, "Invalid API Key. Connection unauthorized.", Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Save Configuration"
                }
            }
        }
    }

    private suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // Ping OWM with dummy coordinates just to test the 401 response
            val url = URL("https://api.openweathermap.org/data/2.5/air_pollution?lat=0&lon=0&appid=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.responseCode == 200
        }.getOrDefault(false)
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

            Toast.makeText(this, "Configuration Saved", Toast.LENGTH_LONG).show()
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
            findViewById<Button>(R.id.btn_save).apply {
                isEnabled = true
                text = "Save Configuration"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Clean up background threads when activity closes
    }
}