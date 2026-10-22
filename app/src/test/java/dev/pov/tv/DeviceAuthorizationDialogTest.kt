package dev.pov.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.pov.tv.data.ApiException
import dev.pov.tv.data.DeviceAuth
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class DeviceAuthorizationDialogTest {
    private lateinit var activity: Activity
    private lateinit var worker: ManualExecutor
    private var flow: DeviceAuthorizationDialog? = null
    private var connected = 0

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        worker = ManualExecutor()
    }

    @After fun cleanup() { flow?.dismiss(); activity.finish(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `connect gives immediate visible requesting feedback before network work`() {
        show()
        assertTrue(dialog().isShowing)
        assertTrue(labels().contains("Requesting a Trakt activation code"))
        assertEquals(1, worker.size)
        assertEquals(View.GONE, dialog().getButton(DialogInterface.BUTTON_POSITIVE).visibility)
    }

    @Test fun `successful code response displays the address code and expiry`() {
        show(); work()
        assertTrue(labels().contains("https://trakt.tv/activate"))
        assertTrue(labels().contains("TEST-CODE"))
        assertTrue(labels().contains("Waiting for approval"))
        assertTrue(labels().contains("remaining"))
        assertEquals(0, connected)
    }

    @Test fun `missing app registration is a visible error with retry and setup action`() {
        show(start = { throw ApiException(0, "Trakt is not configured. Add the Client ID from your registered Trakt application in Settings.") })
        work()
        assertTrue(dialog().isShowing)
        assertTrue(labels().contains("Trakt could not connect"))
        assertTrue(labels().contains("registered Trakt application"))
        assertEquals(View.VISIBLE, dialog().getButton(DialogInterface.BUTTON_POSITIVE).visibility)
        assertEquals(View.VISIBLE, dialog().getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
    }

    @Test fun `retry requests a new code in the same visible dialog`() {
        var starts = 0
        show(start = { if (++starts == 1) throw ApiException(0, "Trakt could not be reached. Retry.") else auth() })
        work()
        val original = dialog()
        original.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        assertSame(original, dialog())
        assertTrue(labels().contains("Requesting a Trakt activation code"))
        work()
        assertEquals(2, starts)
        assertTrue(labels().contains("TEST-CODE"))
    }

    @Test fun `cancel before queued start prevents the request and dialog resurrection`() {
        var starts = 0
        show(start = { starts++; auth() })
        flow!!.dismiss(); work()
        assertEquals(0, starts)
        assertFalse(dialog().isShowing)
        assertEquals(0, connected)
    }

    @Test fun `cancel during a request discards its later response`() {
        show(start = { flow!!.dismiss(); auth() })
        work()
        assertFalse(dialog().isShowing)
        assertEquals(0, connected)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(0, worker.size)
    }

    @Test fun `expired activation code stops polling and gives an actionable retry`() {
        show(start = { auth().copy(expiresSeconds = 2, intervalSeconds = 5) })
        work()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(labels().contains("expired"))
        assertEquals(View.VISIBLE, dialog().getButton(DialogInterface.BUTTON_POSITIVE).visibility)
        assertEquals(0, worker.size)
    }

    @Test fun `approval completes exactly once and closes the dialog`() {
        show(start = { auth().copy(intervalSeconds = 1) }, poll = { true })
        work()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        work()
        assertEquals(1, connected)
        assertFalse(dialog().isShowing)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(90))
        assertEquals(0, worker.size)
        assertEquals(1, connected)
    }

    @Test fun `a poll failure stays visible and hides the unusable code`() {
        show(start = { auth().copy(intervalSeconds = 1) }, poll = { throw ApiException(403, "Trakt rejected this app Client ID. Set the correct Client ID and retry.") })
        work()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); work()
        assertTrue(dialog().isShowing)
        assertTrue(labels().contains("Trakt rejected this app Client ID"))
        assertFalse(labels().contains("TEST-CODE"))
        assertEquals(View.VISIBLE, dialog().getButton(DialogInterface.BUTTON_POSITIVE).visibility)
    }

    @Test fun `unexpected exception does not expose arbitrary request details`() {
        show(start = { throw RuntimeException("https://example.invalid/?token=private-value") }); work()
        assertTrue(labels().contains("Check your connection"))
        assertFalse(labels().contains("private-value"))
        assertFalse(labels().contains("example.invalid"))
    }

    private fun show(start: () -> DeviceAuth = { auth() }, poll: (DeviceAuth) -> Boolean = { false }) {
        flow = DeviceAuthorizationDialog(activity, "Trakt", worker, start, poll, { connected++ }, configure = {}).also { it.show() }
    }
    private fun work() { worker.next(); shadowOf(Looper.getMainLooper()).idle() }
    private fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
    private fun labels(): String {
        fun collect(view: View): List<String> {
            if (view.visibility != View.VISIBLE) return emptyList()
            return when (view) {
                is TextView -> listOf(view.text.toString())
                is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
                else -> emptyList()
            }
        }
        return collect(dialog().window!!.decorView).joinToString("\n")
    }
    private fun auth() = DeviceAuth("synthetic-device", "TEST-CODE", "https://trakt.tv/activate", 5, 60)
    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val size get() = tasks.size
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun next() { tasks.removeFirst().run() }
    }
}
