package dev.pov.tv

import android.os.Looper
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackHoldGestureTest {
    private fun back(action: Int, down: Long, at: Long, repeat: Int = 0) = KeyEvent(down, at, action, KeyEvent.KEYCODE_BACK, repeat)

    @Test fun `a remote without key repeats opens on release after one second`() {
        var opened = 0
        val gesture = BackHoldGesture { opened++ }
        assertFalse(gesture.intercept(back(KeyEvent.ACTION_DOWN, 1_000, 1_000)))
        assertTrue(gesture.intercept(back(KeyEvent.ACTION_UP, 1_000, 3_100)))
        assertEquals(1, opened)
    }

    @Test fun `holding opens after one second even before release and swallows the release`() {
        var opened = 0
        val gesture = BackHoldGesture { opened++ }
        assertFalse(gesture.intercept(back(KeyEvent.ACTION_DOWN, 1_000, 1_000)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_050))
        assertEquals(1, opened)
        assertTrue(gesture.intercept(back(KeyEvent.ACTION_UP, 1_000, 5_000)))
        assertEquals(1, opened)
    }

    @Test fun `key repeats open once and a short press stays an ordinary Back`() {
        var opened = 0
        val gesture = BackHoldGesture { opened++ }
        gesture.intercept(back(KeyEvent.ACTION_DOWN, 1_000, 1_000))
        assertTrue(gesture.intercept(back(KeyEvent.ACTION_DOWN, 1_000, 1_500, repeat = 10)))
        assertTrue(gesture.intercept(back(KeyEvent.ACTION_DOWN, 1_000, 3_200, repeat = 40)))
        assertTrue(gesture.intercept(back(KeyEvent.ACTION_UP, 1_000, 3_300)))
        assertEquals(1, opened)
        assertFalse(gesture.intercept(back(KeyEvent.ACTION_DOWN, 9_000, 9_000)))
        assertFalse(gesture.intercept(back(KeyEvent.ACTION_UP, 9_000, 9_300)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))
        assertEquals(1, opened)
    }
}
