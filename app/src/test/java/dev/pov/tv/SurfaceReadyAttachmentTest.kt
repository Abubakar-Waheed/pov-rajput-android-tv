package dev.pov.tv

import org.junit.Assert.*
import org.junit.Test

class SurfaceReadyAttachmentTest {
    private class Target {
        var attached = true
        var shown = true
        var validSurface = false
        val ready get() = attached && shown && validSurface
    }
    private class Harness {
        var session: Any? = Any()
        var outgoing: Target? = Target().apply { validSurface = true }
        val switches = mutableListOf<Pair<Target?, Target>>()
        val callbacks = mutableListOf<() -> Unit>()
        var registrations = 0
        var removals = 0
        val gate = SurfaceReadyAttachment<Any, Target>(
            currentSession = { session }, isReady = { it.ready },
            observeReadiness = { _, ready ->
                registrations++
                callbacks += ready
                ({ removals++ })
            },
            attach = { _, target -> switches += outgoing to target; outgoing = target },
        )
    }

    @Test fun `incoming target is not switched until its surface is valid`() {
        val h = Harness(); val outgoing = h.outgoing; val incoming = Target()
        h.gate.request(h.session!!, incoming)
        assertSame(outgoing, h.outgoing)
        assertTrue(h.switches.isEmpty())
        h.callbacks.single().invoke()
        assertTrue(h.switches.isEmpty())
        incoming.validSurface = true; h.callbacks.single().invoke()
        assertEquals(listOf(outgoing to incoming), h.switches)
        assertSame(incoming, h.outgoing)
        assertEquals(1, h.removals)
    }

    @Test fun `already visible valid surface switches immediately without registering observers`() {
        val h = Harness(); val incoming = Target().apply { validSurface = true }
        h.gate.request(h.session!!, incoming)
        assertSame(incoming, h.outgoing)
        assertEquals(1, h.switches.size)
        assertEquals(0, h.registrations)
    }

    @Test fun `repeated requests for same pending target use one observer and one switch`() {
        val h = Harness(); val incoming = Target()
        repeat(12) { h.gate.request(h.session!!, incoming) }
        assertEquals(1, h.registrations)
        incoming.validSurface = true
        repeat(12) { h.callbacks.single().invoke() }
        assertEquals(1, h.switches.size)
        assertEquals(1, h.removals)
    }

    @Test fun `replaced pending target ignores its stale queued surface callback`() {
        val h = Harness(); val old = Target(); val next = Target()
        h.gate.request(h.session!!, old); val stale = h.callbacks.last()
        h.gate.request(h.session!!, next)
        old.validSurface = true; stale()
        assertTrue(h.switches.isEmpty())
        next.validSurface = true; h.callbacks.last().invoke()
        assertSame(next, h.outgoing)
        assertEquals(2, h.registrations)
        assertEquals(2, h.removals)
    }

    @Test fun `old session callback never attaches a view to a replacement player`() {
        val h = Harness(); val incoming = Target()
        h.gate.request(h.session!!, incoming)
        h.session = Any(); incoming.validSurface = true
        h.callbacks.single().invoke()
        assertTrue(h.switches.isEmpty())
        assertEquals(1, h.removals)
    }

    @Test fun `detach cancels its pending target but leaves other target requests intact`() {
        val h = Harness(); val incoming = Target(); val unrelated = Target()
        h.gate.request(h.session!!, incoming)
        h.gate.cancel(unrelated)
        assertEquals(0, h.removals)
        h.gate.cancel(incoming)
        incoming.validSurface = true; h.callbacks.single().invoke()
        assertTrue(h.switches.isEmpty())
        assertEquals(1, h.removals)
    }

    @Test fun `stop cancels pending observer and stale callback cannot resurrect playback`() {
        val h = Harness(); val incoming = Target()
        h.gate.request(h.session!!, incoming)
        h.session = null; h.gate.cancelAll(); h.gate.cancelAll()
        incoming.validSurface = true; h.callbacks.single().invoke()
        assertTrue(h.switches.isEmpty())
        assertEquals(1, h.removals)
    }

    @Test fun `surface creation still waits for attached and shown view`() {
        val h = Harness(); val incoming = Target().apply { attached = false; shown = false; validSurface = true }
        h.gate.request(h.session!!, incoming)
        h.callbacks.single().invoke(); assertTrue(h.switches.isEmpty())
        incoming.attached = true
        h.callbacks.single().invoke(); assertTrue(h.switches.isEmpty())
        incoming.shown = true
        h.callbacks.single().invoke(); assertSame(incoming, h.outgoing)
    }

    @Test fun `immediate target replaces pending target without stale attachment`() {
        val h = Harness(); val pending = Target(); val immediate = Target().apply { validSurface = true }
        h.gate.request(h.session!!, pending); val stale = h.callbacks.single()
        h.gate.request(h.session!!, immediate)
        pending.validSurface = true; stale()
        assertEquals(1, h.switches.size)
        assertSame(immediate, h.outgoing)
        assertEquals(1, h.removals)
    }

    @Test fun `synchronous readiness during registration closes observer and attaches once`() {
        val session = Any(); val target = Target()
        var switches = 0; var removals = 0
        val gate = SurfaceReadyAttachment<Any, Target>({ session }, { it.ready }, { view, ready ->
            view.validSurface = true; ready()
            ({ removals++ })
        }, { _, _ -> switches++ })
        gate.request(session, target)
        assertEquals(1, switches)
        assertEquals(1, removals)
    }
}
