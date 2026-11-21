package com.example.chatmention

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        layout.addView(TextView(this).apply {
            text = "Einrichtung (nur einmal, in dieser Reihenfolge)\n\n" +
                "Schritt 1: Tippe auf \"1. Bedienungshilfen öffnen\", wähle YLivechat " +
                "(unter Heruntergeladene Apps) und versuche den Dienst einzuschalten. " +
                "Es kommt die Meldung \"App wurde der Zugriff verweigert\". Tippe auf OK.\n\n" +
                "Schritt 2: Tippe auf \"2. App-Info öffnen\". Tippe dort oben rechts auf " +
                "More (die drei Punkte) und wähle \"Eingeschränkte Einstellungen zulassen\" " +
                "(Allow restricted settings). Bestätige.\n\n" +
                "Schritt 3: Tippe auf \"3. Bedienungshilfen öffnen\", wähle YLivechat " +
                "und schalte den Dienst jetzt ein.\n\n" +
                "Benutzung\n\n" +
                "Tippe im YouTube-Live-Chat auf einen Nick. Es erscheint ein roter Knopf mit dem Namen. " +
                "Tippe ins Chat-Feld, bis die Tastatur offen ist, dann auf den roten Knopf. " +
                "Langes Drücken auf den Knopf löscht den gespeicherten Namen."
            textSize = 16f
        })

        layout.addView(Button(this).apply {
            text = "1. Bedienungshilfen öffnen"
            setOnClickListener { openAccessibility() }
        })
        layout.addView(Button(this).apply {
            text = "2. App-Info öffnen"
            setOnClickListener { openAppInfo() }
        })
        layout.addView(Button(this).apply {
            text = "3. Bedienungshilfen öffnen"
            setOnClickListener { openAccessibility() }
        })

        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
