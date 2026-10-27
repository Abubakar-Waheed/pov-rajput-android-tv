package dev.pov.tv

import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAheadPreferencesTest {
    private val mb = 1024L * 1024

    @Test fun `the buffer stays within half the heap`() {
        assertEquals((384 * mb / 2).toInt(), ReadAheadPreferences.budgetBytes(384 * mb, ReadAhead.MAXIMUM))
        assertEquals(300_000_000, ReadAheadPreferences.budgetBytes(2048 * mb, ReadAhead.LONG))
    }

    @Test fun `a small heap never gets less than the player's normal video buffer`() {
        assertEquals(DefaultLoadControl.DEFAULT_VIDEO_BUFFER_SIZE, ReadAheadPreferences.budgetBytes(192 * mb, ReadAhead.LONG))
    }
}
