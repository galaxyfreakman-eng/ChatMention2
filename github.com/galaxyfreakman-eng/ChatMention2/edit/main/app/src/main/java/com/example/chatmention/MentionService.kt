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
import android.widget.Toast

class MentionService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatMention"
        private const val DEBUG_TOAST = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val source = event.source ?: return

        val raw = collectText(source)
        Log.d(TAG, "Klick: raw='$raw' class=${source.className} id=${source.viewIdResourceName}")
        if (raw.isNullOrBlank()) return

        val nick = extractNick(raw) ?: return
        val input = findChatInput(rootInActiveWindow) ?: run {
            Log.d(TAG, "Kein Eingabefeld gefunden")
            return
        }
        if (DEBUG_TOAST) Toast.makeText(this, "@$nick", Toast.LENGTH_SHORT).show()
        insertMention(input, nick)
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

    private fun findChatInput(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isEditable && n.className?.contains("EditText") == true) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun insertMention(input: AccessibilityNodeInfo, nick: String) {
        val existing = if (input.isShowingHintText) "" else input.text?.toString().orEmpty()
        val prefix = if (existing.isNotEmpty() && !existing.last().isWhitespace()) " " else ""
        val toInsert = "$prefix@$nick "

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mention", toInsert))

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, existing.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
        }
        input.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)

        Handler(Looper.getMainLooper()).postDelayed({
            input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }, 80)
    }

    override fun onInterrupt() {}
}
