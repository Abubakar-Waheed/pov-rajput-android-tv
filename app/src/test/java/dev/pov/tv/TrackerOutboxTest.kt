package dev.pov.tv

import android.content.Context
import dev.pov.tv.data.PlaybackIdentity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackerOutboxTest {
    @Test fun `one provider acknowledgment or disconnect never removes the other providers updates`() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
        val legacy = PlaybackStore(app); val wetrakr = PlaybackStore(app, "wetrakr.pending")
        val identity = PlaybackIdentity("tt1234567", "movie", "Example")
        val original = legacy.queue(identity, 25.0, "pause")
        val first = wetrakr.queue(identity, 42.0, "pause", "fixture-account", "2026-10-25T00:00:00Z")
        val latest = wetrakr.queue(identity, 50.0, "pause", "fixture-account", "2026-10-25T00:05:00Z")
        wetrakr.acknowledge(identity, first)
        assertEquals(latest, wetrakr.pending().single().revision)
        assertEquals("fixture-account", wetrakr.pending().single().accountRevision)
        assertEquals("2026-10-25T00:05:00Z", wetrakr.pending().single().timestamp)
        wetrakr.clearPending(); assertTrue(wetrakr.pending().isEmpty())
        assertEquals(original, legacy.pending().single().revision)
    }

    @Test fun `removing one title clears only its retry and preserves the scrobbling epoch on others`() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
        val store = PlaybackStore(app, "wetrakr.pending")
        val first = PlaybackIdentity("tt1234567", "movie", "Example")
        val second = PlaybackIdentity("tt7654321", "series", "Example Show", 1, 2)
        store.queue(first, 30.0, "pause", "fixture-account", scrobbleRevision = "fixture-epoch")
        store.queue(second, 40.0, "pause", "fixture-account", scrobbleRevision = "fixture-epoch")
        store.discard(first)
        val pending = PlaybackStore(app, "wetrakr.pending").pending().single()
        assertEquals(second, pending.identity)
        assertEquals("fixture-epoch", pending.scrobbleRevision)
    }
}
