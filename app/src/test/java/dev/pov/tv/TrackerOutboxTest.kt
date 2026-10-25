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
        val trakt = PlaybackStore(app); val wetrakr = PlaybackStore(app, "wetrakr.pending")
        val identity = PlaybackIdentity("tt1234567", "movie", "Example")
        val original = trakt.queue(identity, 25.0, "pause")
        val first = wetrakr.queue(identity, 42.0, "pause", "fixture-account", "2026-10-25T00:00:00Z")
        val latest = wetrakr.queue(identity, 50.0, "pause", "fixture-account", "2026-10-25T00:05:00Z")
        wetrakr.acknowledge(identity, first)
        assertEquals(latest, wetrakr.pending().single().revision)
        assertEquals("fixture-account", wetrakr.pending().single().accountRevision)
        assertEquals("2026-10-25T00:05:00Z", wetrakr.pending().single().timestamp)
        wetrakr.clearPending(); assertTrue(wetrakr.pending().isEmpty())
        assertEquals(original, trakt.pending().single().revision)
    }
}
