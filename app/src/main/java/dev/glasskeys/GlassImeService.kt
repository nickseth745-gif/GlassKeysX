package dev.glasskeys
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class GlassImeService : InputMethodService() {
    private lateinit var kb: GlassKeyboardView
    private val words = ("the and that have for not with you this but from they say her she will one all would there their what about " +
        "which when make can like time just know take people into year your good some could them see other than then now look only " +
        "come its over think also back after use two how our work first well way even new want because any these give day most us hello thanks please").split(" ")

    override fun onCreateInputView(): View {
        kb = GlassKeyboardView(this) { handle(it) }
        if (Build.VERSION.SDK_INT >= 31) runCatching { window.window?.setBackgroundBlurRadius(70) }
        return kb
    }
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        kb.returnLabel = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"
            EditorInfo.IME_ACTION_SEND -> "send"; EditorInfo.IME_ACTION_NEXT -> "next"; else -> "return" }
        kb.mode = GlassKeyboardView.Mode.LETTERS
        autoCap(); suggest()
    }
    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f); if (::kb.isInitialized) { autoCap(); suggest() }
    }
    private fun before() = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: ""
    private fun autoCap() {
        if (kb.shift == 2) return
        val t = before().trimEnd()
        kb.shift = if (t.isEmpty() || (before().endsWith(" ") && t.last() in ".!?")) 1 else 0
    }
    private fun suggest() {
        val w = before().takeLastWhile { it.isLetter() }.lowercase()
        kb.suggestions = if (w.isEmpty()) listOf("I", "The", "Hello") else words.filter { it.startsWith(w) && it != w }.take(3).ifEmpty { listOf(w) }
    }
    private fun key(code: Int) = currentInputConnection?.run {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }

    private fun handle(k: String) {
        val ic = currentInputConnection ?: return
        when (k) {
            "⌫" -> if (ic.getSelectedText(0).isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
            "return", "search", "go", "send", "next" -> {
                val a = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                if (a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(a) else key(KeyEvent.KEYCODE_ENTER)
            }
            "space" -> { val b = before()
                if (b.endsWith(" ") && !b.endsWith("  ") && b.trim().lastOrNull()?.isLetterOrDigit() == true) { ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1) }
                else ic.commitText(" ", 1) }
            "◀" -> key(KeyEvent.KEYCODE_DPAD_LEFT)
            "▶" -> key(KeyEvent.KEYCODE_DPAD_RIGHT)
            else -> if (k.startsWith("sug:")) {
                val w = before().takeLastWhile { it.isLetter() }; ic.deleteSurroundingText(w.length, 0)
                val s = k.removePrefix("sug:"); ic.commitText((if (kb.shift > 0) s.replaceFirstChar { it.uppercase() } else s) + " ", 1)
            } else { ic.commitText(k, 1); if (kb.shift == 1) kb.shift = 0 }
        }
        suggest()
    }
}
