package com.example.chatmention

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class MentionService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatMention"
        // true = zeigt bei jedem Klick den erkannten Rohtext (zum Testen)
        private const val DEBUG_RAW = true
    }

    // Zusätze am Ende des Namens, die entfernt werden (Moderator, Bestätigung)
    private val badgeEnd = Regex(
        "[\\s,]+(moderator(in)?|bestätigung|bestätigt)\\s*$",
        RegexOption.IGNORE_CASE
    )

    private val handler = Handler(Looper.getMainLooper())
    private var pendingText: String? = null
    private var button: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Chat Mention aktiv", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val source = event.source ?: return
        if (source.isEditable || source.className?.contains("EditText") == true) return

        val raw = collectText(source)
        Log.d(TAG, "Klick: raw='$raw' class=${source.className}")
        if (DEBUG_RAW) {
            Toast.makeText(this, "Roh: ${raw?.take(60)}", Toast.LENGTH_SHORT).show()
        }
        if (raw.isNullOrBlank()) return
        val nick = extractNick(raw) ?: return

        // neuer Nick ersetzt den gespeicherten (die Zwischenablage wird nicht angefasst)
        pendingText = "@$nick "
        showButton("@$nick")
    }

    // ---------- schwebender Knopf ----------

    private fun showButton(label: String) {
        val existing = button
        if (existing != null) {
            existing.text = label
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val padH = (16 * dm.density).toInt()
        val padV = (10 * dm.density).toInt()

        val tv = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = (dm.widthPixels * 0.6).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(0xE6D32F2F.toInt())
            }
            elevation = 12f
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels / 2
            y = dm.heightPixels / 3
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (e.eventTime - e.downTime > 800) {
                            pendingText = null
                            hideButton()
                            Toast.makeText(this, "Gespeicherter Nick gelöscht", Toast.LENGTH_SHORT).show()
                        } else {
                            insertPending()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(tv, lp)
            button = tv
        } catch (t: Throwable) {
            Log.d(TAG, "Knopf konnte nicht angezeigt werden: $t")
            Toast.makeText(this, "Knopf Fehler: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideButton() {
        val b = button ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(b)
        } catch (_: Throwable) {}
        button = null
    }

    // ---------- Einfügen ins offene Feld (ohne Zwischenablage) ----------

    private fun findFocusedInput(): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }?.let { return it }
        for (w in windows) {
            val f = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (f != null && f.isEditable) return f
        }
        return null
    }

    private fun writeText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun setCursorToEnd(node: AccessibilityNodeInfo, length: Int) {
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
    }

    private fun currentText(node: AccessibilityNodeInfo): String =
        if (node.isShowingHintText) "" else node.text?.toString().orEmpty()

    private fun insertPending() {
        val text = pendingText ?: return
        val target = findFocusedInput()
        if (target == null) {
            Toast.makeText(this, "Kein offenes Eingabefeld - erst Chat-Feld antippen", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = currentText(target)
        val prefix = if (existing.isNotEmpty() && !existing.last().isWhitespace()) " " else ""
        writeText(target, existing + prefix + text)

        // Prüfen; falls der Name fehlt, ein zweiter Versuch
        handler.postDelayed({
            val now = findFocusedInput() ?: return@postDelayed
            val cur = currentText(now)
            if (!cur.contains(text.trim())) {
                val p = if (cur.isNotEmpty() && !cur.last().isWhitespace()) " " else ""
                writeText(now, cur + p + text)
            }
            handler.postDelayed({
                val fin = findFocusedInput() ?: return@postDelayed
                setCursorToEnd(fin, currentText(fin).length)
            }, 200)
        }, 300)
    }

    // ---------- Nick erkennen ----------

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
        val seps = listOf(": ", ", ", " – ", " - ", ". ", " • ", " · ")
        val sep = seps.map { s.indexOf(it) }.filter { it > 0 }.minOrNull()
        s = if (sep != null) s.substring(0, sep) else s.split(" ").first()
        // Moderator / Bestätigung am Ende entfernen (auch mehrfach, in jeder Reihenfolge)
        while (true) {
            val t = s.replace(badgeEnd, "").trim()
            if (t == s) break
            s = t
        }
        s = s.removePrefix("@").trim().trimEnd('.', ',', ':', ';', '!', '?', '•', '·')
        return s.takeIf { it.isNotEmpty() && it.length <= 40 }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideButton()
        super.onDestroy()
    }
}
