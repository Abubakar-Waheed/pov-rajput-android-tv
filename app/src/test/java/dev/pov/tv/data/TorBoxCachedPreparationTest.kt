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
class TorBoxCachedPreparationTest {
    private val hash = "a".repeat(40)
    private val source = SourceItem("Movie.mkv", "fixture", hash, cached = true)
    private val movie = PlaybackIdentity("tt1234567", "movie", "Movie")

    private class Credentials : TorBoxCredentials {
        var key = "fixture-secret"
        override fun get(key: String) = this.key
        override fun put(key: String, value: String) { this.key = value }
    }
    private inner class Harness {
        val credentials = Credentials()
        var existing = JSONArray()
        var hit = true
        var folderId = 7L
        var ready = true
        var files = JSONArray().put(JSONObject().put("id", 42).put("short_name", "Movie.mkv").put("size", 1000))
        var linkFails = false
        var cleanupFails = false
        var onLink: () -> Unit = {}
        var creates = 0
        var form = emptyMap<String, String>()
        val deleted = mutableListOf<Long>()
        val streamed = mutableListOf<Long>()
        val deletionHeaders = mutableListOf<String>()
        val api = TorBoxApi(credentials, { address, _, headers, body ->
            when {
                address.contains("/checkcached?") -> success(if (hit) JSONArray().put(JSONObject().put("hash", hash)) else JSONArray())
                address.contains("/mylist?id=") -> success(JSONObject().put("id", folderId).put("name", "Movie")
                    .put("download_finished", ready).put("files", files))
                address.contains("/mylist?") -> success(existing)
                address.contains("/requestdl?") -> {
                    val fileId = address.substringAfter("file_id=").substringBefore('&').toLong()
                    streamed += fileId; onLink()
                    if (linkFails) throw ApiException(429, "Try again later.")
                    success("https://files.example/$fileId.mkv")
                }
                address.endsWith("/controltorrent") -> {
                    assertEquals("delete", body!!.getString("operation"))
                    assertFalse(body.has("all"))
                    deleted += body.getLong("torrent_id")
                    deletionHeaders += headers.getValue("Authorization")
                    if (cleanupFails) throw ApiException(503, "Unavailable.")
                    success(JSONObject())
                }
                else -> error("Unexpected endpoint")
            }
        }, { 0L }, { _, fields, _ ->
            creates++; form = fields
            success(JSONObject().put("torrent_id", 7))
        })
    }

    @Test fun `new cached source resolves URLs and removes only its new entry`() {
        val h = Harness()
        val prepared = h.api.prepareCached(source, movie)
        assertEquals(1, h.creates)
        assertEquals("true", h.form["add_only_if_cached"])
        assertEquals("magnet:?xt=urn:btih:$hash", h.form["magnet"])
        assertEquals(listOf(7L), h.deleted)
        assertEquals("https://files.example/42.mkv", prepared.resolvedUrls[42L])
        assertTrue(h.api.ownsTag(prepared.accountTag!!))
    }

    @Test fun `existing hash resolves without creating renaming or deleting library item`() {
        val h = Harness()
        h.existing.put(JSONObject().put("id", 7).put("hash", hash))
        h.api.prepareCached(source, movie)
        assertEquals(0, h.creates)
        assertTrue(h.deleted.isEmpty())
    }

    @Test fun `creation returning a preexisting ID never deletes it`() {
        val h = Harness()
        h.existing.put(JSONObject().put("id", 7).put("hash", "b".repeat(40)))
        h.api.prepareCached(source, movie)
        assertEquals(1, h.creates)
        assertTrue(h.deleted.isEmpty())
    }

    @Test fun `cache miss cannot create an uncached download`() {
        val h = Harness(); h.hit = false
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(0, h.creates)
        assertTrue(h.deleted.isEmpty())
    }

    @Test fun `repeated library pages fail before creating or removing an entry`() {
        val h = Harness()
        repeat(1000) { h.existing.put(JSONObject().put("id", it).put("hash", "b".repeat(40))) }
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(0, h.creates)
        assertTrue(h.deleted.isEmpty())
    }

    @Test fun `failed link generation still removes the new cached entry`() {
        val h = Harness(); h.linkFails = true
        assertThrows(ApiException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(listOf(7L), h.deleted)
    }

    @Test fun `wrong folder response removes only the ID returned by creation`() {
        val h = Harness(); h.folderId = 99
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(listOf(7L), h.deleted)
        assertTrue(h.streamed.isEmpty())
    }

    @Test fun `cleanup failure retries and never returns a prepared URL`() {
        val h = Harness(); h.cleanupFails = true
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(listOf(7L, 7L), h.deleted)
    }

    @Test fun `account change rejects links and cleanup uses captured account`() {
        val h = Harness(); h.onLink = { h.credentials.key = "replacement-key" }
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie) }
        assertEquals(listOf("Bearer fixture-secret"), h.deletionHeaders)
    }

    @Test fun `prepared URLs become invalid after account replacement`() {
        val h = Harness(); val prepared = h.api.prepareCached(source, movie)
        h.credentials.key = "replacement-key"
        assertFalse(h.api.ownsTag(prepared.accountTag!!))
    }

    @Test fun `episode packs list matching versions and resolve only the selected version`() {
        val h = Harness()
        h.files = JSONArray().put(file(10, "Show.S01E01.mkv"))
            .put(file(21, "Show.S01E02.1080p.mkv")).put(file(42, "Show.S01E02.2160p.mkv"))
            .put(file(50, "sample.mkv"))
        val prepared = h.api.prepareCached(source, PlaybackIdentity("tt1234567", "series", "Show", 1, 2))
        assertTrue(h.streamed.isEmpty())
        assertTrue(prepared.resolvedUrls.isEmpty())
        assertEquals(listOf(21L, 42L), prepared.files.map { it.id })
        assertEquals(listOf(7L), h.deleted)
        val selected = h.api.prepareCached(source, prepared.identity, prepared.files.last())
        assertEquals(listOf(42L), h.streamed)
        assertEquals(setOf(42L), selected.resolvedUrls.keys)
        assertEquals(listOf(7L, 7L), h.deleted)
    }

    @Test fun `large packs never generate a link for every video`() {
        val h = Harness(); h.files = JSONArray()
        repeat(500) { h.files.put(file(it.toLong(), "Movie.$it.mkv")) }
        val prepared = h.api.prepareCached(source, movie)
        assertEquals(500, prepared.files.size)
        assertTrue(prepared.resolvedUrls.isEmpty())
        assertTrue(h.streamed.isEmpty())
        assertEquals(hash, prepared.sourceInfoHash)
        assertEquals(listOf(7L), h.deleted)
    }

    @Test fun `file selection matches name and size instead of reusing a deleted transfer file ID`() {
        val h = Harness(); h.files = JSONArray().put(file(90, "Movie.mkv")).put(file(100, "Other.mkv"))
        val prepared = h.api.prepareCached(source, movie, CloudFile(42, "Movie.mkv", 1000))
        assertEquals(listOf(90L), h.streamed)
        assertEquals(setOf(90L), prepared.resolvedUrls.keys)
    }

    @Test fun `ambiguous selected filenames cannot resolve another file by accident`() {
        val h = Harness(); h.files = JSONArray().put(file(90, "Movie.mkv")).put(file(100, "Movie.mkv"))
        assertThrows(IllegalStateException::class.java) { h.api.prepareCached(source, movie, CloudFile(42, "Movie.mkv", 1000)) }
        assertTrue(h.streamed.isEmpty())
        assertEquals(listOf(7L), h.deleted)
    }

    private fun file(id: Long, name: String) = JSONObject().put("id", id).put("short_name", name).put("size", 1000)
    private fun success(data: Any) = JSONObject().put("success", true).put("data", data)
}
