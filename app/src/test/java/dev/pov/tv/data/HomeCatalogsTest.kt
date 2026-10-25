package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeCatalogsTest {
    @Test fun `six public shelves retain their requested order despite independent responses`() {
        val addresses = java.util.Collections.synchronizedList(mutableListOf<String>())
        val year = java.time.Year.now(java.time.ZoneOffset.UTC).value
        val api = CatalogApi { address ->
            addresses += address
            JSONObject("""{"metas":[{"id":"tt1234567","name":"Public example","year":"$year"}]}""")
        }
        val shelves = api.homeShelves()
        assertEquals(listOf("New Movies", "New Shows", "Featured Movies", "Featured Shows", "Popular Movies", "Popular Shows"), shelves.map { it.title })
        assertEquals(listOf("movie", "series", "movie", "series", "movie", "series"), shelves.map { it.type })
        assertEquals(6, addresses.size)
        assertTrue(addresses.all { it.startsWith("https://v3-cinemeta.strem.io/catalog/") })
        assertEquals(2, addresses.count { it.endsWith("/year/genre=$year.json") })
        assertEquals(2, addresses.count { it.endsWith("/imdbRating.json") })
        assertTrue(shelves.all { it.items.size == 1 && !it.unavailable })
    }
    @Test fun `one failed catalog cannot hide other shelves or change their order`() {
        val year = java.time.Year.now(java.time.ZoneOffset.UTC).value
        val api = CatalogApi { address ->
            if (address.endsWith("/movie/imdbRating.json")) error("Temporarily offline")
            JSONObject("""{"metas":[{"id":"tt1234567","name":"Public example","year":"$year"}]}""")
        }
        val shelves = api.homeShelves()
        assertEquals("Featured Movies", shelves[2].title)
        assertTrue(shelves[2].unavailable)
        assertTrue(shelves[2].items.isEmpty())
        assertEquals(5, shelves.count { it.items.isNotEmpty() })
    }
    @Test fun `New excludes titles with inconsistent or absent release years`() {
        val api = CatalogApi {
            JSONObject("""{"metas":[{"id":"tt1234567","name":"New","releaseInfo":"2026–"},
                {"id":"tt1234568","name":"Old","year":"2021"},{"id":"tt1234569","name":"Unknown"}]}""")
        }
        assertEquals(listOf("New"), api.newReleases("series", 2026).map { it.title })
    }
    @Test fun `Featured preserves provider order even for unrated titles`() {
        val api = CatalogApi { JSONObject("""{"metas":[{"id":"tt1234567","name":"Unrated"},
            {"id":"tt1234568","name":"Rated","imdbRating":"9.0"}]}""") }
        assertEquals(listOf("Unrated", "Rated"), api.featured("movie").map { it.title })
    }
}
