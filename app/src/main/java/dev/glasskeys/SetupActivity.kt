package dev.glasskeys
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SetupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 160, 60, 60) }
        col.addView(TextView(this).apply { text = "Glass Keys"; textSize = 32f })
        col.addView(Button(this).apply { text = "1. Enable keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        col.addView(Button(this).apply { text = "2. Switch to Glass Keys"
            setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        col.addView(EditText(this).apply { hint = "Try typing here" })
        setContentView(col)
    }
}
