package dev.pov.tv

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackDebridTest {
    @Test fun `new direct playback clears the previous provider logo and accessibility label`() {
        val context = Robolectric.buildActivity(Activity::class.java).get()
        val root = FrameLayout(context)
        val badge = FrameLayout(context).apply { id = R.id.pov_debrid_badge }
        val logo = ImageView(context).apply { id = R.id.pov_debrid_logo }
        val name = TextView(context).apply { id = R.id.pov_debrid_name }
        badge.addView(logo); badge.addView(name); root.addView(badge)
        for ((id, label) in listOf("offcloud" to "Offcloud", "torbox" to "TorBox", "realdebrid" to "Real-Debrid",
                "alldebrid" to "AllDebrid", "premiumize" to "Premiumize", "debridlink" to "Debrid-Link")) {
            PlaybackDebrid.bind(root, id)
            assertEquals(View.VISIBLE, badge.visibility)
            assertEquals(label, name.text.toString())
            assertNotNull(logo.drawable)
        }
        for (unknown in listOf(null, "other", "", "TorBox")) {
            PlaybackDebrid.bind(root, unknown)
            assertEquals(View.GONE, badge.visibility)
            assertEquals("", name.text.toString())
            assertNull(logo.drawable)
            assertNull(logo.contentDescription)
        }
    }
}
