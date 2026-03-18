package com.example.karoo.customfield

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "Peanut Locator Installed.\n\nPlease open the Extensions menu on your Karoo to configure the data field."
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        setContentView(textView)
    }
}