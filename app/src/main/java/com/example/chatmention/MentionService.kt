package com.example.chatmention

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

class MentionService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatMention"
        private const val PENDING_MS = 60_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingNick: String? = null
    private var pendingTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event)
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> handleFocus(event)
        }
    }

    private fun keyboardVisible(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun handleClick(event: AccessibilityEvent) {
        val source = event.source ?: return

        // Antippen des Chat-Feldes selbst: wartenden Nick einfügen
        if (source.isEditable || source.className?.contains("EditText") == true) {
            if (pendingNick != null) schedulePaste()
            return
        }

        val raw = collectText(source)
        Log.d(TAG, "Klick: raw='$raw' class=${source.className}")
        if (raw.isNullOrBlank()) return
        val nick = extractNick(raw) ?: return
        val text = "@$nick "

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mention", text))

        pendingNick = nick
        pendingTime = System.currentTimeMillis()

        if (keyboardVisible()) {
            // Tastatur offen = echtes Feld offen: sofort einfügen
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            schedulePaste()
        } else {
            // erst warten, bis du das Chat-Feld antippst
            Toast.makeText(this, "$text bereit - jetzt Chat-Feld antippen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFocus(event: AccessibilityEvent) {
        val src = event.source ?: return
        if (!src.isEditable) return
        if (pendingNick == null) return
        schedulePaste()
    }

    /** Fügt in drei Versuchen ein und prüft jedes Mal, ob @nick im Feld steht. */
    private fun schedulePaste() {
        val nick = pendingNick ?: return
        if (System.currentTimeMillis() - pendingTime > PENDING_MS) {
            pendingNick = null
            return
        }
        for (delay in longArrayOf(400, 900, 1500)) {
            handler.postDelayed({
                val current = pendingNick ?: return@postDelayed
                val target = rootInActiveWindow
                    ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?.takeIf { it.isEditable } ?: return@postDelayed
                val existing = if (target.isShowingHintText) "" else target.text?.toString().orEmpty()
                if (existing.contains("@$current")) {
                    pendingNick = null
                    return@postDelayed
                }
                Log.d(TAG, "Füge @$current ein (Feld: '$existing')")
                pasteInto(target, existing)
            }, delay)
        }
    }

    private fun pasteInto(target: AccessibilityNodeInfo, existing: String) {
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, existing.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
        }
        target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun collectText(node: AccessibilityNodeInfo): String? {
        fun own(n: AccessibilityNodeInfo): String? =
            (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }

        own(node)?.let { return it }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { c -> own(c)?.let { return it } }
        }
        var p = node.parent
        var depth = 0
        while (p != null && depth < 3) {
            own(p)?.let { return it }
            p = p.parent; depth++
        }
        return null
    }

    private fun extractNick(raw: String): String? {
        var s = raw.trim().lineSequence().first().trim()
        val sep = listOf(": ", ", ", " – ", " - ").map { s.indexOf(it) }.filter { it > 0 }.minOrNull()
        s = if (sep != null) s.substring(0, sep) else s.split(" ").first()
        s = s.removePrefix("@").trim()
        return s.takeIf { it.isNotEmpty() && it.length <= 40 }
    }

    override fun onInterrupt() {}
}
