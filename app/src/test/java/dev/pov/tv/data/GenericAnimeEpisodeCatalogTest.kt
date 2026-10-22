package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Public metadata samples from three unrelated anime, using the same production details API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GenericAnimeEpisodeCatalogTest {
    private fun details(kitsuId: String): AnimeEpisodeCatalog {
        val fixtures = javaClass.getResourceAsStream("/anime/generic-anime-mappings.json")!!.bufferedReader().use {
            JSONObject(it.readText())
        }
        val entry = fixtures.getJSONObject(kitsuId)
        val anime = entry.getJSONObject("anime")
        val source = entry.getJSONObject("canonical")
        val episodes = source.getJSONArray("episodes")
        val canonical = MediaItemData(source.getString("imdbId"), source.getString("type"), source.getString("title"),
            episodes = (0 until episodes.length()).map { index ->
                val item = episodes.getJSONObject(index)
                EpisodeData(item.getInt("season"), item.getInt("episode"), "Canonical episode", item.optString("released"))
            })
        val api = AnimeCatalogApi(publicJson = { address ->
            assertEquals("https://anime-kitsu.strem.fun/meta/${anime.getString("type")}/kitsu:$kitsuId.json", address)
            entry.getJSONObject("mapping")
        }, lookupId = { _, _, _ -> error("No root-only fallback is needed for explicit episode metadata") },
            canonicalDetails = { stub ->
                assertEquals(canonical.imdbId, stub.imdbId)
                assertEquals(canonical.type, stub.type)
                canonical
            })
        return api.details(AnimeTitle(kitsuId, anime.getString("title"), anime.getString("type"),
            anime.getString("year"), subtype = anime.getString("subtype")))
    }

    @Test fun `Frieren selected first season contains exactly 28 verified episodes without later seasons`() {
        val result = details("46474")
        val choices = result.groups.single().episodes
        assertEquals(28, choices.size)
        assertEquals((1..28).toList(), choices.map { it.catalogEpisode })
        assertEquals((1..28).toList(), choices.map { it.absoluteEpisode })
        assertTrue(choices.all { it.identity.imdbId == "tt22248376" && it.identity.season == 1 })
        assertEquals((1..28).toList(), result.media.episodes.map { it.episode })
        assertEquals(0, result.missingMappings)
    }

    @Test fun `My Hero Academia season two preserves S2 identity and overall episodes 14 through 38`() {
        val result = details("12268")
        val choices = result.groups.single().episodes
        assertEquals(25, choices.size)
        assertEquals((1..25).toList(), choices.map { it.catalogEpisode })
        assertEquals((1..25).toList(), choices.map { it.identity.episode })
        assertEquals((14..38).toList(), choices.map { it.absoluteEpisode })
        assertTrue(choices.all { it.identity.imdbId == "tt5626028" && it.identity.season == 2 })
        assertEquals("My Hero Academia", choices.first().identity.title)
        assertEquals("My Hero Academia Season 2", result.media.title)
        assertEquals(0, result.missingMappings)
    }

    @Test fun `Your Name remains a film even when the anime metadata contains a single video numbered one`() {
        val result = details("11614")
        assertEquals("movie", result.anime.category)
        assertEquals("movie", result.media.type)
        assertEquals("tt5311514", result.media.imdbId)
        assertEquals("Your Name.", result.media.title)
        assertTrue(result.groups.isEmpty())
        assertTrue(result.media.episodes.isEmpty())
        assertEquals(0, result.missingMappings)
    }
}
