package dev.glasskeys
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** Liquid-glass keyboard: translucent frosted keys, specular highlights, magnified key bubble, drag-space cursor. */
class GlassKeyboardView(ctx: Context, private val send: (String) -> Unit) : View(ctx) {
    enum class Mode { LETTERS, NUMBERS, SYMBOLS, EMOJI }
    var mode = Mode.LETTERS; set(v) { field = v; requestLayout(); invalidate() }
    var shift = 0; set(v) { field = v; invalidate() }          // 0 off, 1 once, 2 caps lock
    var returnLabel = "return"; set(v) { field = v; invalidate() }
    var suggestions: List<String> = emptyList(); set(v) { field = v; invalidate() }

    private class K(val label: String, val r: RectF, val weight: Float = 1f)
    private var keys = listOf<K>(); private var pressed: K? = null
    private val dp = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val h = Handler(Looper.getMainLooper()); private var repeating = false
    private var downX = 0f; private var spaceDrag = false; private var lastX = 0f; private var lastShiftTap = 0L

    private val letters = listOf("qwertyuiop", "asdfghjkl", "⇧zxcvbnm⌫")
    private val nums = listOf("1234567890", "-/:;()$&@\"", "#+=.,?!'⌫")
    private val syms = listOf("[]{}#%^*+=", "_\\|~<>€£¥•", "123.,?!'⌫")
    private val emoji = "😀😁😂🤣😊😍😘😎🤔😴😭😡👍👎🙏👏💪🔥✨❤️💔🎉🎂🌹🌞🌙⭐🍕🍔🍟🍺☕🚗✈️📱💻🎵📷🏆💯✅❌🙈🙉🙊🐶🐱🐼🦄🌈🍀🎁🥳🤩😇🤗😉🙃".let { s -> Regex("\\X").findAll(s).map { it.value }.toList() }

    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private fun rows() = when (mode) { Mode.LETTERS -> letters; Mode.NUMBERS -> nums; Mode.SYMBOLS -> syms; Mode.EMOJI -> emptyList() }

    override fun onMeasure(w: Int, hs: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (292 * dp).toInt())

    private fun layoutKeys() {
        val out = ArrayList<K>(); val W = width.toFloat(); val gap = 6 * dp; val top = 44 * dp
        val rowH = (height - top - 8 * dp - gap * 3) / 4
        fun row(y: Float, items: List<Pair<String, Float>>, sidePad: Float = 0f) {
            val total = items.sumOf { it.second.toDouble() }.toFloat()
            val avail = W - 2 * gap - 2 * sidePad - gap * (items.size - 1); var x = gap + sidePad
            for ((l, wt) in items) { val kw = avail * wt / total; out.add(K(l, RectF(x, y, x + kw, y + rowH), wt)); x += kw + gap }
        }
        if (mode == Mode.EMOJI) {
            val cols = 8; val cw = (W - 2 * gap - gap * (cols - 1)) / cols
            emoji.take(cols * 3).forEachIndexed { i, e -> val x = gap + (i % cols) * (cw + gap); val y = top + (i / cols) * (rowH + gap)
                out.add(K(e, RectF(x, y, x + cw, y + rowH))) }
        } else rows().forEachIndexed { i, r ->
            val y = top + i * (rowH + gap)
            val items = r.map { c -> val s = c.toString(); s to (if (s == "⇧" || s == "⌫") 1.5f else 1f) }
            row(y, items, if (i == 1 && mode == Mode.LETTERS) W * 0.045f else 0f)
        }
        val by = top + 3 * (rowH + gap)
        row(by, listOf((if (mode == Mode.LETTERS) "123" else "ABC") to 1.3f, (if (mode == Mode.EMOJI) "⌫" else "😀") to 1f,
            "space" to 4.2f, (if (mode == Mode.NUMBERS) "#+=" else if (mode == Mode.SYMBOLS) "123" else "◀▶") to 1f, returnLabel to 1.7f))
        keys = out
    }
    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) { layoutKeys() }
    override fun onLayout(c: Boolean, l: Int, t: Int, r: Int, b: Int) { super.onLayout(c, l, t, r, b); layoutKeys() }

    override fun onDraw(c: Canvas) {
        if (keys.isEmpty()) layoutKeys()
        val d = dark; val W = width.toFloat(); val H = height.toFloat()
        // Panel: frosted glass slab with specular top edge
        p.shader = LinearGradient(0f, 0f, 0f, H, if (d) intArrayOf(0x66202330, 0xAA0E1018.toInt()) else intArrayOf(0x88F4F6FF.toInt(), 0xCCDCE0EE.toInt()), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL; val panel = RectF(0f, 0f, W, H + 40 * dp); c.drawRoundRect(panel, 26 * dp, 26 * dp, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dp; p.color = if (d) 0x55FFFFFF else 0xCCFFFFFF.toInt()
        c.drawRoundRect(RectF(0.6f * dp, 0.6f * dp, W - 0.6f * dp, H + 40 * dp), 26 * dp, 26 * dp, p)
        // Suggestion capsules
        txt.textSize = 15 * dp; txt.typeface = Typeface.DEFAULT; val sw = (W - 4 * 8 * dp) / 3
        suggestions.take(3).forEachIndexed { i, s ->
            val r = RectF(8 * dp + i * (sw + 8 * dp), 8 * dp, 8 * dp + i * (sw + 8 * dp) + sw, 38 * dp)
            p.style = Paint.Style.FILL; p.color = if (d) 0x22FFFFFF else 0x55FFFFFF; c.drawRoundRect(r, 15 * dp, 15 * dp, p)
            txt.color = if (d) Color.WHITE else 0xFF1C1C1E.toInt(); c.drawText(s, r.centerX(), r.centerY() + 5 * dp, txt)
        }
        for (k in keys) drawKey(c, k, k === pressed, d)
        pressed?.let { k -> if (k.label.length <= 2 && k.label.none { it == '⇧' || it == '⌫' }) { // magnified bubble
            val b = RectF(k.r.centerX() - 30 * dp, k.r.top - 62 * dp, k.r.centerX() + 30 * dp, k.r.top - 4 * dp)
            if (b.left < 2 * dp) b.offset(2 * dp - b.left, 0f); if (b.right > W - 2 * dp) b.offset(W - 2 * dp - b.right, 0f)
            p.style = Paint.Style.FILL; p.color = if (d) 0xF03A3D4A.toInt() else 0xF8FFFFFF.toInt(); c.drawRoundRect(b, 16 * dp, 16 * dp, p)
            txt.textSize = 30 * dp; txt.color = if (d) Color.WHITE else Color.BLACK; c.drawText(disp(k.label), b.centerX(), b.centerY() + 11 * dp, txt) } }
    }
    private fun disp(l: String) = if (l.length == 1 && l[0].isLetter() && shift > 0) l.uppercase() else l

    private fun drawKey(c: Canvas, k: K, down: Boolean, d: Boolean) {
        val special = k.label in listOf("⇧", "⌫", "123", "ABC", "#+=", "◀▶", "😀") || k.label == returnLabel
        val r = RectF(k.r); val rad = 10 * dp
        val base = when { k.label == returnLabel -> if (down) 0xFF0A6CE0.toInt() else 0xCC0A84FF.toInt()
            k.label == "⇧" && shift > 0 -> if (d) 0xEEFFFFFF.toInt() else 0xFFFFFFFF.toInt()
            special -> if (d) 0x40FFFFFF else 0x66B4BCD0
            else -> if (d) 0x55FFFFFF else 0xB3FFFFFF.toInt() }
        p.style = Paint.Style.FILL; p.shader = null; p.setShadowLayer(3 * dp, 0f, 1.5f * dp, if (d) 0x66000000 else 0x33000000); p.color = base
        c.drawRoundRect(r, rad, rad, p); p.clearShadowLayer()
        // liquid sheen: bright upper half fading out
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(if (d) 0x40FFFFFF else 0x99FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p); p.shader = null
        if (down) { p.color = if (d) 0x30FFFFFF else 0x30000000; c.drawRoundRect(r, rad, rad, p) }
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.9f * dp; p.color = if (d) 0x44FFFFFF else 0xAAFFFFFF.toInt(); c.drawRoundRect(r, rad, rad, p)
        txt.color = if (k.label == returnLabel) Color.WHITE else if (k.label == "⇧" && shift > 0) Color.BLACK else if (d) Color.WHITE else 0xFF1C1C1E.toInt()
        val big = mode == Mode.EMOJI && k.label.length > 1 || (mode == Mode.EMOJI && !k.label[0].isLetter() && k.label != "⌫" && k.label != "ABC" && k.label != "space" && k.label != returnLabel && k.label != "◀▶")
        txt.textSize = (if (big) 24 else if (special || k.label == "space") 15 else 22) * dp
        val t = when (k.label) { "⇧" -> if (shift == 2) "⇪" else "⇧"; "space" -> ""; else -> disp(k.label) }
        c.drawText(t, r.centerX(), r.centerY() + txt.textSize * 0.35f, txt)
    }

    private fun hit(x: Float, y: Float) = keys.minByOrNull { val dx = maxOf(it.r.left - x, 0f, x - it.r.right); val dy = maxOf(it.r.top - y, 0f, y - it.r.bottom); dx * dx + dy * dy }
    private val repeat = object : Runnable { override fun run() { send("⌫"); h.postDelayed(this, 45) } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.y < 44 * dp) { val i = (e.x / (width / 3f)).toInt().coerceIn(0, 2); suggestions.getOrNull(i)?.let { send("sug:$it"); performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }; return true }
                pressed = hit(e.x, e.y); downX = e.x; lastX = e.x; spaceDrag = false; repeating = false
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (pressed?.label == "⌫") { repeating = true; send("⌫"); h.postDelayed(repeat, 380) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val k = pressed ?: return true
                if (k.label == "space" && Math.abs(e.x - downX) > 14 * dp) { spaceDrag = true
                    while (e.x - lastX > 12 * dp) { send("▶"); lastX += 12 * dp }; while (lastX - e.x > 12 * dp) { send("◀"); lastX -= 12 * dp } }
                else if (k.label != "⌫" && !spaceDrag) { val n = hit(e.x, e.y); if (n !== k) { pressed = n; invalidate() } }
            }
            MotionEvent.ACTION_UP -> { h.removeCallbacks(repeat); val k = pressed; pressed = null
                if (k != null && !repeating && !spaceDrag) fire(k, e.x); invalidate() }
            MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(repeat); pressed = null; invalidate() }
        }
        return true
    }
    private fun fire(k: K, x: Float) {
        when (k.label) {
            "⇧" -> { val n = System.currentTimeMillis(); shift = if (n - lastShiftTap < 350) 2 else if (shift == 0) 1 else 0; lastShiftTap = n }
            "123" -> mode = Mode.NUMBERS
            "ABC" -> mode = Mode.LETTERS
            "#+=" -> mode = Mode.SYMBOLS
            "😀" -> mode = Mode.EMOJI
            "◀▶" -> send(if (x < k.r.centerX()) "◀" else "▶")
            else -> send(if (k.label.length == 1 && k.label[0].isLetter() && shift > 0) k.label.uppercase() else k.label)
        }
    }
}
