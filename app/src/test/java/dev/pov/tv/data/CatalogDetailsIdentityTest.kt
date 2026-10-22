package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CatalogDetailsIdentityTest {
    @Test fun `movie mapping cannot accidentally load a TV show with the same requested identity`() {
        val catalog = CatalogApi { JSONObject("""{"meta":{"id":"tt1234567","type":"series","name":"A TV show"}}""") }
        val error = assertThrows(IllegalStateException::class.java) {
            catalog.details(MediaItemData("tt1234567", "movie", "An anime movie"))
        }
        assertTrue(error.message.orEmpty().contains("different media type"))
    }

    @Test fun `anime stub loads canonical show and real season numbers instead of inventing season one`() {
        val catalog = CatalogApi { address ->
            assertTrue(address.endsWith("/meta/series/tt1234567.json"))
            JSONObject("""{"meta":{"id":"tt1234567","type":"series","name":"Canonical Show","videos":[{"season":3,"episode":7,"title":"Real episode"}]}}""")
        }
        val show = catalog.details(MediaItemData("tt1234567", "series", "Anime Cour Two"))
        assertEquals("Canonical Show", show.title)
        assertEquals(3, show.episodes.single().season)
        assertEquals(7, show.episodes.single().episode)
    }

    @Test fun `details with a different IMDb root remain rejected even with the same media type`() {
        val catalog = CatalogApi { JSONObject("""{"meta":{"id":"tt7654321","type":"series","name":"Wrong show"}}""") }
        assertThrows(IllegalStateException::class.java) {
            catalog.details(MediaItemData("tt1234567", "series", "Selected show"))
        }
    }

    @Test fun `missing optional metadata type preserves supported Cinemeta responses`() {
        val catalog = CatalogApi { JSONObject("""{"meta":{"id":"tt1234567","name":"A Movie"}}""") }
        assertEquals("A Movie", catalog.details(MediaItemData("tt1234567", "movie", "A Movie")).title)
    }
}
