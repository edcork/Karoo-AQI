package com.example.karoo.aqi

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "AQI Monitor Installed.\n\nAdd /Downloads/aqi_config.json, then add the AQI data field in Karoo."
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        setContentView(textView)
    }
}