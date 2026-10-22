package dev.pov.tv.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnimeEpisodeCatalogTest {
    private fun fixture(name: String): JSONObject = javaClass.getResourceAsStream("/anime/$name.json")!!.bufferedReader().use {
        JSONObject(it.readText())
    }
    private val mappings get() = fixture("attack-on-titan-mappings")
    private fun title(id: String): AnimeTitle {
        val meta = mappings.getJSONObject(id).getJSONObject("meta")
        return AnimeTitle(id, meta.getString("name"), meta.getString("type"), subtype = meta.getString("animeType"))
    }
    private fun canonical(): MediaItemData {
        val data = fixture("attack-on-titan-canonical")
        val episodes = data.getJSONArray("episodes")
        return MediaItemData(data.getString("imdbId"), data.getString("type"), data.getString("title"),
            episodes = (0 until episodes.length()).map { index ->
                val episode = episodes.getJSONObject(index)
                EpisodeData(episode.getInt("season"), episode.getInt("episode"), "Canonical episode", episode.optString("released"))
            })
    }
    private fun details(id: String) = AnimeEpisodeCatalogParser.parse(mappings.getJSONObject(id), title(id), canonical())

    @Test fun `Attack on Titan season one contains only its 25 season one episodes`() {
        val result = details("7442")
        val episodes = result.groups.single().episodes
        assertEquals(25, episodes.size)
        assertTrue(episodes.all { it.identity.season == 1 })
        assertEquals((1..25).toList(), episodes.map { it.identity.episode })
        assertEquals((1..25).toList(), episodes.map { it.catalogEpisode })
        assertEquals((1..25).toList(), episodes.map { it.absoluteEpisode })
        assertEquals(25, result.media.episodes.size)
        assertEquals(0, result.missingMappings)
    }

    @Test fun `season two starts at its episode one and preserves canonical season two and overall 26`() {
        val result = details("8671")
        val choices = result.groups.single().episodes
        assertEquals(12, choices.size)
        assertEquals(1, choices.first().catalogEpisode)
        assertEquals(26, choices.first().absoluteEpisode)
        assertEquals(PlaybackIdentity("tt2560140", "series", "Attack on Titan", 2, 1), choices.first().identity)
        assertEquals(12, choices.last().identity.episode)
        assertTrue(result.media.title.contains("Season 2"))
    }

    @Test fun `season three second cour uses local episode one canonical S3E13 overall 50`() {
        val result = details("41982")
        val choices = result.groups.single().episodes
        assertEquals(10, choices.size)
        assertEquals((1..10).toList(), choices.map { it.catalogEpisode })
        assertEquals((13..22).toList(), choices.map { it.identity.episode })
        assertTrue(choices.all { it.identity.season == 3 })
        assertEquals((50..59).toList(), choices.map { it.absoluteEpisode })
    }

    @Test fun `final season second part uses canonical S4E17 through E28 without earlier episodes`() {
        val choices = details("44240").groups.single().episodes
        assertEquals((17..28).toList(), choices.map { it.identity.episode })
        assertEquals((76..87).toList(), choices.map { it.absoluteEpisode })
        assertTrue(choices.all { it.identity.season == 4 })
        assertEquals(12, choices.size)
    }

    @Test fun `the two final chapter presentations retain S4E29 and S4E30 instead of film identities`() {
        val first = details("46038")
        val second = details("47132")
        assertEquals("special", first.anime.category)
        assertEquals("Special presentation · Season 4", first.groups.single().title)
        assertEquals(29, first.groups.single().episodes.single().identity.episode)
        assertEquals(30, second.groups.single().episodes.single().identity.episode)
        assertEquals(88, first.groups.single().episodes.single().absoluteEpisode)
        assertEquals(89, second.groups.single().episodes.single().absoluteEpisode)
        assertEquals("series", first.media.type)
        assertEquals("tt2560140", second.media.imdbId)
    }

    @Test fun `OVA episode order remains noncontiguous canonical specials with no invented absolute numbering`() {
        for ((id, expected) in mapOf("7694" to listOf(7, 12, 13), "8674" to listOf(15, 17), "13718" to listOf(20, 22, 23))) {
            val result = details(id)
            assertEquals("special", result.anime.category)
            assertEquals("Specials and OVAs", result.groups.single().title)
            val episodes = result.groups.single().episodes
            assertEquals(expected, episodes.map { it.identity.episode })
            assertTrue(episodes.all { it.identity.season == 0 && it.absoluteEpisode == null })
        }
    }

    @Test fun `anime compilation movies stay films and never receive a TV episode selector`() {
        val anime = title("8672")
        val result = AnimeEpisodeCatalogParser.parse(mappings.getJSONObject("8672"), anime,
            MediaItemData("tt3646944", "movie", "Attack on Titan: Crimson Bow and Arrow"))
        assertEquals("movie", result.anime.category)
        assertTrue(result.groups.isEmpty())
        assertTrue(result.media.episodes.isEmpty())
        assertEquals("tt3646944", result.media.imdbId)
    }

    @Test fun `canonical episode missing from metadata cannot be silently replaced with a full show list`() {
        val selected = title("8671")
        val fewerEpisodes = canonical().copy(episodes = canonical().episodes.filterNot { it.season == 2 && it.episode == 1 })
        val result = AnimeEpisodeCatalogParser.parse(mappings.getJSONObject("8671"), selected, fewerEpisodes)
        assertEquals(1, result.missingMappings)
        assertEquals(11, result.media.episodes.size)
        assertEquals(2, result.groups.single().episodes.first().catalogEpisode)
        assertTrue(result.notice.contains("Only verified episodes"))
    }

    @Test fun `missing per-video IMDb season does not infer numbering from catalog fields or array position`() {
        val payload = mappings.getJSONObject("8671")
        payload.getJSONObject("meta").getJSONArray("videos").getJSONObject(0).remove("imdbSeason")
        val result = AnimeEpisodeCatalogParser.parse(payload, title("8671"), canonical())
        assertEquals(1, result.missingMappings)
        assertEquals(11, result.groups.single().episodes.size)
        assertEquals(2, result.groups.single().episodes.first().catalogEpisode)
    }

    @Test fun `conflicting local episode mappings and duplicate canonical episodes are excluded`() {
        val payload = mappings.getJSONObject("8671")
        val videos = payload.getJSONObject("meta").getJSONArray("videos")
        videos.put(JSONObject(videos.getJSONObject(0).toString()).put("imdbEpisode", 12))
        val result = AnimeEpisodeCatalogParser.parse(payload, title("8671"), canonical())
        assertTrue(result.groups.single().episodes.none { it.catalogEpisode == 1 })
        assertEquals(2, result.missingMappings)
        val secondPayload = mappings.getJSONObject("8671")
        secondPayload.getJSONObject("meta").getJSONArray("videos").getJSONObject(1).put("imdbEpisode", 1)
        val sameCanonical = AnimeEpisodeCatalogParser.parse(secondPayload, title("8671"), canonical())
        assertTrue(sameCanonical.groups.single().episodes.none { it.identity.episode == 1 })
        assertEquals(2, sameCanonical.missingMappings)
    }

    @Test fun `missing mappings keep overview but show no substituted episodes and a useful message`() {
        val payload = mappings.getJSONObject("46038")
        payload.getJSONObject("meta").getJSONArray("videos").getJSONObject(0).put("imdbEpisode", 999)
        val result = AnimeEpisodeCatalogParser.parse(payload, title("46038"), canonical())
        assertTrue(result.groups.isEmpty())
        assertTrue(result.media.episodes.isEmpty())
        assertEquals(1, result.missingMappings)
        assertTrue(result.notice.contains("could not be verified"))
    }

    @Test fun `new details integration fetches selected entry and validates root before applying scope`() {
        val payload = mappings.getJSONObject("8671")
        var requests = 0
        val api = AnimeCatalogApi(publicJson = { address ->
            requests++
            assertEquals("https://anime-kitsu.strem.fun/meta/series/kitsu:8671.json", address)
            payload
        }, lookupId = { _, _, _ -> error("Root-only lookup must not replace per-episode mapping") }, canonicalDetails = { stub ->
            assertEquals("tt2560140", stub.imdbId)
            canonical()
        })
        assertEquals(12, api.details(title("8671")).media.episodes.size)
        assertEquals(1, requests)
        val mismatch = AnimeCatalogApi(publicJson = { payload }, lookupId = { _, _, _ -> null },
            canonicalDetails = { canonical().copy(imdbId = "tt1234567") })
        assertThrows(IllegalStateException::class.java) { mismatch.details(title("8671")) }
    }

    private fun record(id: String, subtype: String) = JSONObject().put("id", id).put("type", "anime")
        .put("attributes", JSONObject().put("canonicalTitle", "Example $id").put("subtype", subtype))

    @Test fun `series movies and specials are mutually separate public browse filters`() {
        val response = JSONObject().put("data", JSONArray().put(record("1", "TV")).put(record("2", "ONA"))
            .put(record("3", "movie")).put(record("4", "OVA")).put(record("5", "special")))
        val requests = mutableListOf<String>()
        val api = AnimeCatalogApi(publicJson = { requests += it; response }, lookupId = { _, _, _ -> null })
        assertEquals(listOf("1", "2"), api.popular().items.map { it.kitsuId })
        assertEquals(listOf("3"), api.popular("movie").items.map { it.kitsuId })
        assertEquals(listOf("4", "5"), api.popular("special").items.map { it.kitsuId })
        assertTrue(requests[0].contains("filter%5Bsubtype%5D=TV%2CONA"))
        assertTrue(requests[2].contains("filter%5Bsubtype%5D=OVA%2Cspecial"))
        assertEquals(5, api.popular("all").items.size)
    }

    @Test fun `mismatched anime subtype rejects film and special metadata crossing a series selection`() {
        val payload = mappings.getJSONObject("8671")
        val parser = AnimeCatalogApi(publicJson = { error("Unexpected request") }, lookupId = { _, _, _ -> null })
        payload.getJSONObject("meta").put("animeType", "movie")
        assertThrows(ApiException::class.java) { parser.parseImdbMapping(payload, title("8671")) }
    }

    @Test fun `explicit related chain orders main story seasons and final chapters apart from films and OVAs`() {
        val fixtures = fixture("attack-on-titan-relations")
        var calls = 0
        val api = AnimeCatalogApi(publicJson = { address ->
            calls++
            val id = address.substringAfter("/anime/").substringBefore('/')
            fixtures.getJSONObject(id)
        }, lookupId = { _, _, _ -> null })
        val selected = AnimeTitle("7442", "Attack on Titan", "series", "2013", subtype = "TV", startDate = "2013-04-07")
        val result = api.related(selected)
        assertEquals(listOf("7442", "8671", "13569", "41982", "42422", "44240", "46038", "47132"), result.seasons.map { it.kitsuId })
        assertTrue(result.movies.any { it.kitsuId == "8672" })
        assertTrue(result.movies.any { it.kitsuId == "49129" })
        assertTrue(result.movies.all { it.type == "movie" && it.category == "movie" })
        assertTrue(result.specials.any { it.kitsuId == "7694" })
        assertTrue(result.specials.none { it.kitsuId in setOf("46038", "47132") })
        assertTrue(result.otherSeries.any { it.kitsuId == "11315" })
        assertTrue(result.seasons.none { it.kitsuId == "11315" })
        assertFalse(result.truncated)
        assertEquals(8, calls)
        api.related(selected)
        assertEquals(8, calls)
    }

    @Test fun `large explicit sequel chains are bounded and labeled incomplete`() {
        var requests = 0
        val api = AnimeCatalogApi(publicJson = { address ->
            requests++
            val id = address.substringAfter("/anime/").substringBefore('/').toInt()
            val destination = (id + 1).toString()
            JSONObject().put("data", JSONArray().put(JSONObject().put("type", "mediaRelationships").put("id", id.toString())
                .put("attributes", JSONObject().put("role", "sequel"))
                .put("relationships", JSONObject().put("destination", JSONObject().put("data",
                    JSONObject().put("type", "anime").put("id", destination))))))
                .put("included", JSONArray().put(record(destination, "TV")))
        }, lookupId = { _, _, _ -> null })
        val result = api.related(AnimeTitle("1", "First part", "series", subtype = "TV"))
        assertEquals(12, requests)
        assertTrue(result.truncated)
        assertTrue(result.notice.contains("limited"))
        assertTrue(result.movies.isEmpty())
    }

    @Test fun `selected OVA is an extra and cannot become a TV season without explicit story links`() {
        val api = AnimeCatalogApi(publicJson = { JSONObject().put("data", JSONArray()) }, lookupId = { _, _, _ -> null })
        val result = api.related(AnimeTitle("8674", "Attack on Titan: No Regrets", "series", subtype = "OVA"))
        assertTrue(result.seasons.isEmpty())
        assertEquals(listOf("8674"), result.specials.map { it.kitsuId })
        assertTrue(result.movies.isEmpty())
    }

    @Test fun `root-only presentation ID cannot identify a row of a multi-episode season`() {
        val payload = mappings.getJSONObject("8671")
        payload.getJSONObject("meta").getJSONArray("videos").getJSONObject(0).put("id", "kitsu:8671")
        val result = AnimeEpisodeCatalogParser.parse(payload, title("8671"), canonical())
        assertEquals(1, result.missingMappings)
        assertEquals(11, result.groups.single().episodes.size)
        assertEquals(2, result.groups.single().episodes.first().catalogEpisode)
    }

    @Test fun `root-only presentation ID still requires explicit local episode one and canonical identity`() {
        val payload = mappings.getJSONObject("46038")
        val video = payload.getJSONObject("meta").getJSONArray("videos").getJSONObject(0)
        video.put("episode", 2)
        val wrongLocalNumber = AnimeEpisodeCatalogParser.parse(payload, title("46038"), canonical())
        assertTrue(wrongLocalNumber.groups.isEmpty())
        video.put("episode", 1).put("imdb_id", "tt1234567")
        val wrongRoot = AnimeEpisodeCatalogParser.parse(payload, title("46038"), canonical())
        assertTrue(wrongRoot.groups.isEmpty())
    }
}
