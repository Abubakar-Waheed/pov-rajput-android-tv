package dev.pov.tv

import org.junit.Assert.*
import org.junit.Test

class PlaybackForegroundGateTest {
    @Test fun `fullscreen to browser handoff cancels a background pause`() {
        var pending: Runnable? = null
        var pauses = 0
        var resumes = 0
        val gate = PlaybackForegroundGate({ pending = it }, { if (pending === it) pending = null }, { pauses++ }, { resumes++ })
        val fullscreen = Any()
        val browser = Any()
        gate.started(fullscreen)
        gate.stopped(fullscreen)
        val oldCallback = pending!!
        gate.started(browser)
        assertNull(pending)
        oldCallback.run() // Even a stale scheduled callback cannot pause a visible app.
        assertEquals(0, pauses)
        assertEquals(0, resumes)
    }

    @Test fun `leaving every app host pauses once and returning resumes once`() {
        var pending: Runnable? = null
        var pauses = 0
        var resumes = 0
        val gate = PlaybackForegroundGate({ pending = it }, { if (pending === it) pending = null }, { pauses++ }, { resumes++ })
        val main = Any()
        val video = Any()
        gate.started(main); gate.started(video)
        gate.stopped(main)
        assertNull(pending)
        gate.stopped(video)
        pending!!.run()
        assertEquals(1, pauses)
        gate.started(main); gate.started(main)
        assertEquals(1, resumes)
    }

    @Test fun `duplicate stop or destroy cannot undercount a still visible host`() {
        var scheduled = 0
        val gate = PlaybackForegroundGate({ scheduled++ }, {}, {}, {})
        val main = Any()
        val video = Any()
        gate.started(main); gate.started(video)
        gate.stopped(main); gate.stopped(main)
        assertEquals(0, scheduled)
        gate.stopped(video); gate.stopped(video)
        assertEquals(1, scheduled)
    }
}

