package dev.pov.tv.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceParsersTest {
    private val store get() = SecureStore(RuntimeEnvironment.getApplication())

    @Test fun `TorBox readiness requires completed content still present on server`() {
        val api = TorBoxApi(store)
        val partial = JSONObject("""{"id":41,"name":"Partial","download_finished":false,"download_present":true,"files":[{"id":3,"short_name":"video.mkv","size":9000}]}""")
        assertFalse(api.parseFolder(partial, "torrents").ready)
        partial.put("download_finished", true).put("download_present", false)
        assertFalse(api.parseFolder(partial, "torrents").ready)
        partial.put("download_present", true)
        val folder = api.parseFolder(partial, "torrents")
        assertTrue(folder.ready)
        assertEquals(3L, folder.files.single().id)
        assertEquals("video.mkv", folder.files.single().name)
        assertEquals(9000L, folder.files.single().size)
    }

    @Test fun `TorBox file IDs are preserved and are never replaced with array indexes`() {
        val folder = TorBoxApi(store).parseFolder(JSONObject("""{"id":5,"files":[{"id":9,"name":"first.mkv"},{"id":22,"name":"second.mkv"},{"name":"missing ID.mkv"}]}"""), "usenet")
        assertEquals(listOf(9L, 22L), folder.files.map { it.id })
        assertEquals("usenet", folder.kind)
    }

    @Test fun `insecure service address fails before a connection and never exposes credential text`() {
        try {
            Net.raw("http://example.com/request?token=do-not-expose")
            fail("Expected secure-address rejection")
        } catch (error: ApiException) {
            assertFalse(error.message.orEmpty().contains("do-not-expose"))
            assertFalse(error.message.orEmpty().contains("example.com"))
            assertEquals(0, error.code)
        }
    }

    @Test fun `playback identity survives persistence without losing episode selection`() {
        val identity = PlaybackIdentity("tt1234567", "series", "Title", 0, 4)
        assertEquals(identity, PlaybackIdentity.fromJson(JSONObject(identity.toJson().toString())))
    }

}
