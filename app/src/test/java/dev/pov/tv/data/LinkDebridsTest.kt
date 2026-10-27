package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LinkDebridsTest {
    private val hash = "a".repeat(40)
    private val movie = PlaybackIdentity("tt1234567", "movie", "Movie")
    private val source = SourceItem("Movie.2024.1080p.mkv", "Torrentio", hash)

    /** Answers by URL prefix and records every request. */
    private class Fake(val answers: Map<String, String>) : LinkTransport {
        val calls = mutableListOf<String>()
        private fun answer(method: String, url: String): String {
            calls += "$method $url"
            return answers.entries.firstOrNull { (prefix, _) -> "$method $url".startsWith(prefix) }?.value ?: "{}"
        }
        override fun get(url: String, headers: Map<String, String>) = answer("GET", url)
        override fun delete(url: String, headers: Map<String, String>) = answer("DELETE", url)
        override fun form(method: String, url: String, headers: Map<String, String>, fields: List<Pair<String, String>>) = answer(method, url)
        override fun json(method: String, url: String, headers: Map<String, String>, body: JSONObject) = answer(method, url)
    }

    private fun api(fake: Fake) = LinkDebrids({ "key-12345678" }, { _, _ -> }, fake, now = { 0L }, pause = {})

    @Test fun `real-debrid plays a cached torrent and keeps it`() {
        val fake = Fake(mapOf(
            "POST https://api.real-debrid.com/rest/1.0/torrents/addMagnet" to """{"id":"T1"}""",
            "GET https://api.real-debrid.com/rest/1.0/torrents/info/T1" to
                """{"status":"downloaded","files":[{"id":1,"path":"/Movie.2024.1080p.mkv","bytes":5,"selected":1}],"links":["https://real-debrid.com/d/X"]}""",
            "POST https://api.real-debrid.com/rest/1.0/unrestrict/link" to """{"download":"https://cdn.real-debrid.com/file.mkv"}"""))
        val prepared = api(fake).prepare("realdebrid", source, movie)!!
        assertEquals(listOf("Movie.2024.1080p.mkv"), prepared.files.map { it.name })
        assertEquals("https://cdn.real-debrid.com/file.mkv", api(fake).streamUrl(prepared, prepared.files.single()))
        assertTrue(fake.calls.none { it.startsWith("DELETE") })
    }

    @Test fun `real-debrid removes a torrent that is not cached`() {
        val fake = Fake(mapOf(
            "POST https://api.real-debrid.com/rest/1.0/torrents/addMagnet" to """{"id":"T2"}""",
            "GET https://api.real-debrid.com/rest/1.0/torrents/info/T2" to """{"status":"downloading","files":[],"links":[]}"""))
        assertNull(api(fake).prepare("realdebrid", source, movie))
        assertTrue("DELETE https://api.real-debrid.com/rest/1.0/torrents/delete/T2" in fake.calls)
    }

    @Test fun `alldebrid uses the ready flag and deletes uncached magnets`() {
        val cached = Fake(mapOf(
            "POST https://api.alldebrid.com/v4/magnet/upload" to """{"status":"success","data":{"magnets":[{"id":7,"ready":true}]}}""",
            "POST https://api.alldebrid.com/v4/magnet/files" to
                """{"status":"success","data":{"magnets":[{"files":[{"n":"Movie","e":[{"n":"Movie.2024.1080p.mkv","s":9,"l":"https://alldebrid.com/f/Y"}]}]}]}}"""))
        assertEquals("https://alldebrid.com/f/Y", api(cached).prepare("alldebrid", source, movie)!!.files.single().link)
        val uncached = Fake(mapOf("POST https://api.alldebrid.com/v4/magnet/upload" to
            """{"status":"success","data":{"magnets":[{"id":8,"ready":false}]}}"""))
        assertNull(api(uncached).prepare("alldebrid", source, movie))
        assertTrue(uncached.calls.any { it.startsWith("GET https://api.alldebrid.com/v4/magnet/delete") && it.endsWith("id=8") })
    }

    @Test fun `premiumize answers the cache up front`() {
        val other = "b".repeat(40)
        val fake = Fake(mapOf("GET https://www.premiumize.me/api/cache/check" to """{"status":"success","response":[false,true]}"""))
        assertEquals(setOf(other), api(fake).cached("premiumize", listOf(hash, other)))
        assertEquals(emptySet<String>(), api(fake).cached("realdebrid", listOf(hash)))
    }

    @Test fun `debrid-link removes a seedbox entry that is still downloading`() {
        val fake = Fake(mapOf("POST https://debrid-link.com/api/v2/seedbox/add" to
            """{"success":true,"value":{"id":"S1","downloadPercent":12,"files":[{"name":"Movie.2024.1080p.mkv","downloadUrl":""}]}}"""))
        assertNull(api(fake).prepare("debridlink", source, movie))
        assertTrue("DELETE https://debrid-link.com/api/v2/seedbox/S1/remove" in fake.calls)
    }

    @Test fun `every service has a name, a credential and a place in the priority list`() {
        LinkDebrids.IDS.forEach { id ->
            assertTrue(DebridServices.name(id).isNotBlank())
            assertEquals("$id.key", DebridServices.credential(id))
        }
        assertEquals(listOf("torbox", "offcloud") + LinkDebrids.IDS, DebridServices.parsePriority("torbox,offcloud", ""))
    }
}
