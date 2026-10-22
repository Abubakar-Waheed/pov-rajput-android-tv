package dev.pov.tv

import android.app.Activity
import android.os.Looper
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerOptionSheetTest {
    @Test fun `unavailable track remains focusable without selecting it or closing the explanation`() {
        val owner = Robolectric.buildActivity(Activity::class.java).setup().visible()
        var unsupportedRequests = 0
        var supportedRequests = 0
        val dialog = PlayerOptionSheet.create(owner.get(), "Audio", "2 tracks · 1 unavailable", listOf(
            PlayerSheetChoice("Japanese · Unavailable: unsupported codec", selectable = false) { unsupportedRequests++ },
            PlayerSheetChoice("English · AAC") { supportedRequests++ }
        ), 0xFF00D5ED.toInt())
        try {
            dialog.show()
            shadowOf(Looper.getMainLooper()).idle()
            val unavailable = dialog.window!!.decorView.findViewWithTag<Button>("pov_sheet_choice_0")
            assertTrue(unavailable.isFocusable)
            assertTrue(unavailable.requestFocus())
            unavailable.performClick()
            assertEquals(0, unsupportedRequests)
            assertTrue(dialog.isShowing)
            dialog.window!!.decorView.findViewWithTag<Button>("pov_sheet_choice_1").performClick()
            assertEquals(1, supportedRequests)
            assertFalse(dialog.isShowing)
        } finally { dialog.dismiss(); owner.pause().stop().destroy() }
    }
}
