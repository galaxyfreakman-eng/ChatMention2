package com.example.chatmention

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
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
        private const val DEBUG_RAW = false
        // true = nur das erste Wort als Nick (sprachunabhängig, aber Namen mit Leerzeichen werden gekürzt)
        private const val FIRST_WORD_ONLY = false

        // Rollen-Wörter, die nicht zum Nick gehören
        private val BADGE_WORDS = listOf(
            // Moderator
            "moderator", "moderatorin", "moderator:in", "modérateur", "modératrice",
            "moderador", "moderadora", "moderatore", "moderatrice", "moderatör",
            "модератор", "モデレーター", "관리자",
            // Besitzer
            "owner", "besitzer", "inhaber", "eigentümer", "propietario", "propriétaire",
            "proprietario", "proprietário", "sahibi", "владелец", "właściciel",
            "所有者", "소유자",
            // Mitglied
            "new member", "neues mitglied", "member", "mitglied", "nuevo miembro",
            "miembro", "nouveau membre", "membre", "novo membro", "nuovo membro",
            "membro", "üye", "участник", "członek",
            // Bestätigt / Verifiziert
            "verified", "verifiziert", "verifizierung", "bestätigung", "bestätigt",
            "verificado", "verificada", "vérifié", "verificato", "doğrulandı",
            "подтверждено", "zweryfikowany", "確認済み", "인증됨"
        )
    }

    private val badgeAlt = BADGE_WORDS.sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
    private val badgeEnd = Regex("[\\s,(\\[•·\\-]+($badgeAlt)[\\s,)\\]]*$", RegexOption.IGNORE_CASE)
    private val badgeStart = Regex("^[\\s(\\[]*($badgeAlt)[\\s,)\\]:]+", RegexOption.IGNORE_CASE)

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
        val text = "@$nick "

        // neuer Nick ersetzt den gespeicherten
        pendingText = text
        setClip(text)
        showButton("@$nick")
    }

    private fun setClip(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mention", text))
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

    // ---------- Einfügen ins offene Feld ----------

    private fun findFocusedInput(): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }?.let { return it }
        for (w in windows) {
            val f = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (f != null && f.isEditable) return f
        }
        return null
    }

    private fun insertPending() {
        val text = pendingText ?: return
        val target = findFocusedInput()
        if (target == null) {
            Toast.makeText(this, "Kein offenes Eingabefeld - erst Chat-Feld antippen", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = if (target.isShowingHintText) "" else target.text?.toString().orEmpty()
        val prefix = if (existing.isNotEmpty() && !existing.last().isWhitespace()) " " else ""

        setClip(prefix + text)
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, existing.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
        }
        target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        target.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        // Prüfen, ob es geklappt hat; sonst Text direkt setzen
        handler.postDelayed({
            val now = findFocusedInput()
            val cur = if (now == null || now.isShowingHintText) "" else now.text?.toString().orEmpty()
            if (now != null && !cur.contains(text.trim())) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        existing + prefix + text
                    )
                }
                now.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }, 400)
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

    private fun stripBadges(input: String): String {
        var s = input.trim()
        var changed = true
        while (changed) {
            changed = false
            val a = s.replace(badgeEnd, "").trim()
            val b = a.replace(badgeStart, "").trim()
            if (b != s) { s = b; changed = true }
        }
        return s
    }

    private fun extractNick(raw: String): String? {
        var s = raw.trim().lineSequence().first().trim()
        val seps = listOf(": ", ", ", " – ", " - ", ". ", " • ", " · ")
        val sep = seps.map { s.indexOf(it) }.filter { it > 0 }.minOrNull()
        s = if (sep != null) s.substring(0, sep) else s
        s = stripBadges(s)
        if (sep == null || FIRST_WORD_ONLY) s = s.split(" ").first()
        s = s.removePrefix("@").trim().trimEnd('.', ',', ':', ';', '!', '?', '•', '·')
        return s.takeIf { it.isNotEmpty() && it.length <= 40 }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideButton()
        super.onDestroy()
    }
}
