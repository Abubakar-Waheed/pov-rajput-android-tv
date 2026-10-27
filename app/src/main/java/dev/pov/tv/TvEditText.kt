package dev.pov.tv

import android.content.Context
import android.view.KeyEvent
import android.widget.EditText

/** A text field whose Back press closes the TV keyboard and hands focus to the dialog's actions. */
internal class TvEditText(context: Context) : EditText(context) {
    var onBack: (() -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        val handler = onBack
        if (keyCode == KeyEvent.KEYCODE_BACK && handler != null) {
            if (event.action == KeyEvent.ACTION_UP) handler()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }
}
