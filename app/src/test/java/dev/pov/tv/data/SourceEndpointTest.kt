package dev.pov.tv.data

import org.junit.Assert.*
import org.junit.Test

class SourceEndpointTest {
    private val cloud = object : SourceTorBox {
        override fun cached(hashes: List<String>): Set<String> = error("No service request expected")
        override fun addMagnet(hash: String): Long = error("No service request expected")
        override fun folder(kind: String, id: Long): CloudFolder = error("No service request expected")
        override fun streamUrl(kind: String, folderId: Long, fileId: Long): String = error("No service request expected")
    }
    @Test fun `unconfigured provider fails with setup instructions before any network request`() {
        val resolver = SourceResolver({ "" }, cloud, { error("No provider request expected") })
        try { resolver.sources(PlaybackIdentity("tt1234567", "movie", "Example")); fail("Setup required") }
        catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("Settings")) }
    }
}
