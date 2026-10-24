package dev.pov.tv

import org.junit.Assert.*
import org.junit.Test

class DpadSeekPreviewTest {
    @Test fun `movie length never changes the one second step and release commits once`() {
        val seek = DpadSeekPreview()
        assertTrue(seek.begin(600_000, 7_200_000))
        assertEquals(601_000, seek.step(1))
        repeat(9) { seek.step(1) }
        assertTrue(seek.active)
        assertEquals(10, seek.offsetSeconds)
        assertEquals(610_000L, seek.finish())
        assertFalse(seek.active)
        assertNull(seek.finish())
    }
    @Test fun `reverse direction and cancellation preserve the original position`() {
        val seek = DpadSeekPreview()
        seek.begin(90_000, 600_000)
        repeat(5) { seek.step(1) }; repeat(8) { seek.step(-1) }
        assertEquals(87_000, seek.position)
        assertEquals(-3, seek.offsetSeconds)
        assertNull(seek.finish(cancelled = true))
        seek.begin(90_000, 600_000)
        assertEquals(89_000, seek.step(-1))
    }
    @Test fun `unknown duration and both boundaries are safe without overflowing`() {
        val seek = DpadSeekPreview()
        assertFalse(seek.begin(0, -1))
        assertFalse(seek.begin(0, 0))
        seek.begin(400, 2200)
        assertEquals(0, seek.step(-1))
        repeat(5) { seek.step(1) }
        assertEquals(2200, seek.position)
        seek.finish(); seek.begin(Long.MAX_VALUE - 500, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, seek.step(1))
    }
    @Test fun `continuous seeks accelerate after one five and fifteen minutes and remain bounded`() {
        val seek = DpadSeekPreview()
        seek.begin(0, 7_200_000)
        repeat(60) { seek.step(1) }
        assertEquals(60_000, seek.position)
        assertEquals(65_000, seek.step(1))
        repeat(47) { seek.step(1) }
        assertEquals(300_000, seek.position)
        assertEquals(310_000, seek.step(1))
        repeat(59) { seek.step(1) }
        assertEquals(900_000, seek.position)
        assertEquals(930_000, seek.step(1))
        repeat(1000) { seek.step(1) }
        assertEquals(7_200_000, seek.position)
        assertEquals(7_200_000L, seek.finish())
    }
    @Test fun `reversing or starting a new hold restores precise one second steps`() {
        val seek = DpadSeekPreview()
        seek.begin(2_000_000, 7_200_000)
        repeat(60) { seek.step(-1) }
        assertEquals(1_935_000, seek.step(-1))
        assertEquals(1_936_000, seek.step(1))
        seek.finish()
        seek.begin(1_936_000, 7_200_000)
        assertEquals(1_937_000, seek.step(1))
    }

    @Test fun `timer always includes seconds and a long movie includes hours`() {
        assertEquals("00:00", DpadSeekPreview.time(0))
        assertEquals("01:01", DpadSeekPreview.time(61_999))
        assertEquals("1:02:03", DpadSeekPreview.time(3_723_000))
    }
}
