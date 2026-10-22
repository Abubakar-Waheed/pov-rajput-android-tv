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

    @Test fun `Trakt resume identity uses show IMDb ID and exact episode numbers`() {
        val items = JSONArray("""[
            {"type":"episode","progress":36.5,"show":{"title":"A Show","ids":{"imdb":"tt1234567"}},"episode":{"season":2,"number":7,"ids":{"imdb":"tt7654321"}}},
            {"type":"movie","progress":55,"movie":{"title":"A Movie","ids":{"imdb":"tt1111111"}}},
            {"type":"movie","progress":100,"movie":{"title":"Finished","ids":{"imdb":"tt2222222"}}},
            {"type":"episode","progress":10,"show":{"title":"No identity","ids":{}},"episode":{"season":1,"number":1}}
        ]""")
        val results = TraktApi(store).parsePlayback(items)
        assertEquals(2, results.size)
        val identity = results[0].first
        assertEquals("tt1234567", identity.imdbId)
        assertEquals("series", identity.type)
        assertEquals(2, identity.season)
        assertEquals(7, identity.episode)
        assertEquals(36.5, results[0].second, 0.001)
        assertEquals("movie", results[1].first.type)
    }

    @Test fun `Trakt lists skip entries that cannot safely match an IMDb catalog title`() {
        val api = TraktApi(store)
        assertNull(api.parseMedia(JSONObject("""{"show":{"title":"Unmatched","ids":{"tmdb":42}}}""")))
        assertNull(api.parseMedia(JSONObject("""{"person":{"name":"Not a video","ids":{"imdb":"nm1234567"}}}""")))
        val parsed = api.parseMedia(JSONObject("""{"show":{"title":"Example","year":2020,"runtime":44,"rating":7.25,"overview":null,"genres":["drama"],"ids":{"imdb":"tt1234567"}}}"""))!!
        assertEquals("series", parsed.type)
        assertEquals("", parsed.description)
        assertEquals("2020", parsed.year)
        assertEquals(listOf("drama"), parsed.genres)
        assertEquals("44", parsed.runtime)
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

    @Test fun `show watchlist adds the show root without adding seasons or episodes`() {
        val body = TraktApi(store).watchlistBody(PlaybackIdentity("tt1234567", "series", "A Show"))
        assertFalse(body.has("movies"))
        val show = body.getJSONArray("shows").getJSONObject(0)
        assertEquals("tt1234567", show.getJSONObject("ids").getString("imdb"))
        assertFalse(show.has("seasons"))
        assertFalse(show.has("episodes"))
    }

    @Test fun `next episode follows server progress instead of assuming last watched plus one`() {
        val api = TraktApi(store)
        val show = JSONObject("""{"title":"A Show","ids":{"imdb":"tt1234567"}}""")
        val progress = JSONObject("""{"last_episode":{"season":1,"number":9},"next_episode":{"season":2,"number":1,"ids":{"imdb":"tt7654321"}}}""")
        val next = api.parseNextEpisode(show, progress)!!
        assertEquals("tt1234567", next.imdbId)
        assertEquals(2, next.season)
        assertEquals(1, next.episode)
        assertNull(api.parseNextEpisode(show, JSONObject("""{"next_episode":null}""")))
    }
}
