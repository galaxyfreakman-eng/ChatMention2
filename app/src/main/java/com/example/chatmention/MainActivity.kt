package com.example.chatmention

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "1. Tippe unten auf den Button.\n" +
                "2. Wähle \"Chat Mention\" und schalte den Dienst ein.\n" +
                "3. Öffne YouTube, einen Live-Chat, und tippe auf einen Nick."
            textSize = 16f
        })
        layout.addView(Button(this).apply {
            text = "Bedienungshilfen öffnen"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        setContentView(layout)
    }
}
