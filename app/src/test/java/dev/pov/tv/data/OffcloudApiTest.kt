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
class OffcloudApiTest {
    private val hash = "a".repeat(40)
    private val movie = PlaybackIdentity("tt1234567", "movie", "Example")
    private val source get() = SourceItem("Movie.mkv", "fixture", hash)

    private class Account : OffcloudAccount {
        var token = "fixture-secret"
        var saved = JSONObject()
        override fun key() = token
        override fun transfers() = JSONObject(saved.toString())
        override fun save(key: String, transfers: JSONObject) { token = key; saved = transfers }
    }

    private class Harness {
        val account = Account()
        var time = 0L
        var state = "downloaded"
        var files = """["https://files.example/Movie.mkv"]"""
        var override: ((String) -> String?)? = null
        val calls = mutableListOf<String>()
        val fields = mutableListOf<JSONObject?>()
        val api = OffcloudApi(account, { path, _, input, method ->
            calls += "$method $path"; fields += input
            override?.invoke(path) ?: when (path) {
                "account/info" -> """{"user_id":"fixture-user","is_premium":true}"""
                "cache/info" -> """[{"cached":true}]"""
                "cloud" -> """{"requestId":"transfer1","status":"created"}"""
                "cloud/status" -> """{"status":{"status":"$state"}}"""
                "cloud/explore/transfer1" -> files
                else -> error("Unexpected endpoint")
            }
        }, { time }, { time += it * 1_000_000 })
    }

    @Test fun `cache checks use current URL array protocol without creating transfers`() {
        val h = Harness()
        assertEquals(setOf(hash), h.api.cached(listOf(hash)))
        assertEquals(listOf("POST cache/info"), h.calls)
        assertEquals("magnet:?xt=urn:btih:$hash", h.fields.single()!!.getJSONArray("urls").getString(0))
        assertFalse(h.fields.single()!!.getBoolean("includeFiles"))
    }

    @Test fun `selected source creates transfer and returns playable file`() {
        val h = Harness()
        val prepared = h.api.prepare(source, movie)
        assertEquals("Movie.mkv", prepared.files.single().name)
        assertEquals("https://files.example/Movie.mkv", prepared.files.single().url)
        assertEquals("magnet:?xt=urn:btih:$hash", h.fields.first()!!.getString("url"))
        assertEquals("transfer1", h.account.saved.getString(hash))
        assertTrue(h.api.owns(prepared))
    }

    @Test fun `pending transfer survives timeout and retry never adds it again`() {
        val h = Harness(); h.state = "downloading"
        assertThrows(IllegalStateException::class.java) { h.api.prepare(source, movie) }
        assertTrue(h.time >= 20_000_000_000L)
        h.state = "downloaded"
        h.api.prepare(source, movie)
        assertEquals(1, h.calls.count { it == "POST cloud" })
    }

    @Test fun `multiple files preserve exact URLs and exclude samples`() {
        val h = Harness()
        h.files = """["https://files.example/Film.1080p.mkv","https://files.example/Film.2160p.mkv","https://files.example/sample.mkv"]"""
        val prepared = h.api.prepare(source, movie)
        assertEquals(listOf(0L, 1L), prepared.files.map { it.id })
        assertEquals("https://files.example/Film.2160p.mkv", prepared.files[1].url)
    }

    @Test fun `episode selection excludes explicit mismatches`() {
        val h = Harness()
        h.files = """["https://files.example/Show.S01E01.mkv","https://files.example/Show.S01E02.mkv"]"""
        val prepared = h.api.prepare(source, PlaybackIdentity("tt1234567", "series", "Show", 1, 2))
        assertEquals("Show.S01E02.mkv", prepared.files.single().name)
    }

    @Test fun `unmarked episode pack is never silently selected`() {
        val h = Harness()
        h.files = """["https://files.example/part-one.mkv","https://files.example/part-two.mkv"]"""
        assertThrows(IllegalStateException::class.java) {
            h.api.prepare(source, PlaybackIdentity("tt1234567", "series", "Show", 1, 2))
        }
    }

    @Test fun `detailed responses and encoded filenames are supported`() {
        val detailed = OffcloudApi.parseFiles(JSONObject("""{"files":[{"name":"Film.mkv","size":1024,"url":"https://files.example/film"}]}"""))
        assertEquals(1024L, detailed.single().size)
        val simple = OffcloudApi.parseFiles(JSONArray("""["https://files.example/Film%20A+B.mkv"]"""))
        assertEquals("Film A+B.mkv", simple.single().name)
    }

    @Test fun `insecure and credential bearing file links are rejected`() {
        listOf("http://files.example/Film.mkv", "https://user:password@files.example/Film.mkv", "file:///Film.mkv").forEach {
            assertThrows(IllegalArgumentException::class.java) { OffcloudApi.parseFiles(JSONArray().put(it)) }
        }
    }

    @Test fun `API error and plan responses never expose server text`() {
        listOf("""{"error":"fixture-secret https://private.example"}""", """{"not_available":"cloud"}""").forEach { payload ->
            val h = Harness(); h.override = { payload }
            val error = assertThrows(IllegalStateException::class.java) { h.api.prepare(source, movie) }
            assertFalse(error.message.orEmpty().contains("fixture-secret"))
            assertFalse(error.message.orEmpty().contains("https://"))
        }
    }

    @Test fun `failed or unknown transfers never explore or play`() {
        listOf("error", "canceled", "unrecognized").forEach { state ->
            val h = Harness(); h.state = state
            assertThrows(IllegalStateException::class.java) { h.api.prepare(source, movie) }
            assertFalse(h.calls.any { it.contains("explore") })
        }
    }

    @Test fun `account change during request cannot persist old transfer`() {
        val h = Harness()
        h.override = { path -> if (path == "cloud") h.account.token = "replacement-secret"; null }
        assertThrows(IllegalStateException::class.java) { h.api.prepare(source, movie) }
        assertEquals(0, h.account.saved.length())
    }

    @Test fun `replacing or disconnecting account invalidates prepared links`() {
        val h = Harness(); val prepared = h.api.prepare(source, movie)
        h.api.setApiKey("another-fixture-key")
        assertFalse(h.api.owns(prepared))
        assertEquals(0, h.account.saved.length())
        h.api.disconnect()
        assertFalse(h.api.connected())
    }

    @Test fun `credential validation cannot restore disconnected account`() {
        val h = Harness()
        h.override = { path -> if (path == "account/info") h.api.disconnect(); null }
        assertThrows(IllegalStateException::class.java) { h.api.setApiKey("another-fixture-key") }
        assertFalse(h.api.connected())
    }

    @Test fun `resolver routes Offcloud without touching TorBox`() {
        val h = Harness()
        val torbox = object : SourceTorBox {
            override fun cached(hashes: List<String>): Set<String> = error("TorBox must not be called")
            override fun addMagnet(hash: String): Long = error("TorBox must not be called")
            override fun folder(kind: String, id: Long): CloudFolder = error("TorBox must not be called")
            override fun streamUrl(kind: String, folderId: Long, fileId: Long): String = error("TorBox must not be called")
        }
        val resolver = SourceResolver({ "" }, torbox, publicJson = {
            JSONObject("""{"streams":[{"infoHash":"$hash","title":"Movie.mkv"}]}""")
        }, offcloud = { h.api })
        assertTrue(resolver.sources(movie).single().cached)
        assertTrue(resolver.prepare(source, movie) is PreparedSource.OffcloudFiles)
    }

    @Test fun `cloud history is read only and file browsing preserves selected URL`() {
        val h = Harness()
        h.override = { path -> if (path == "cloud/history")
            """[{"requestId":"transfer1","fileName":"Example","status":"downloaded"}]""" else null }
        val download = h.api.cloud().single()
        assertEquals("downloaded", download.status)
        assertEquals(listOf("GET cloud/history"), h.calls)
        assertEquals("https://files.example/Movie.mkv", h.api.cloudFiles(download).single().url)
        assertFalse(h.calls.any { it == "POST cloud" })
    }

    @Test fun `cloud browsing rejects stale accounts before requesting files`() {
        val h = Harness()
        h.override = { path -> if (path == "cloud/history")
            """[{"requestId":"transfer1","fileName":"Example","status":"downloaded"}]""" else null }
        val download = h.api.cloud().single()
        h.api.disconnect()
        assertThrows(IllegalStateException::class.java) { h.api.cloudFiles(download) }
        assertFalse(h.calls.any { it.contains("explore") })
    }

    @Test fun `overlapping preparations create only one transfer`() {
        val account = Account()
        val created = java.util.concurrent.atomic.AtomicInteger()
        val api = OffcloudApi(account, { path, _, _, _ -> when (path) {
            "cloud" -> { created.incrementAndGet(); """{"requestId":"shared"}""" }
            "cloud/status" -> """{"status":"downloaded"}"""
            "cloud/explore/shared" -> """["https://files.example/Movie.mkv"]"""
            else -> error("Unexpected endpoint")
        } })
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val gate = java.util.concurrent.CountDownLatch(1)
            val tasks = (1..2).map { executor.submit<PreparedSource.OffcloudFiles> {
                gate.await(); api.prepare(source, movie)
            } }
            gate.countDown()
            tasks.forEach { assertEquals("shared", it.get(5, java.util.concurrent.TimeUnit.SECONDS).requestId) }
            assertEquals(1, created.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun `key validation uses authenticated account endpoint rather than removed proxy endpoint`() {
        val h = Harness()
        h.api.setApiKey("replacement-fixture-key")
        assertEquals(listOf("GET account/info"), h.calls)
        assertNull(h.fields.single())
        assertEquals("replacement-fixture-key", h.account.token)
    }

    @Test fun `missing account identity cannot replace working credentials`() {
        listOf("{}", "[]", "{\"user_id\":\"\"}").forEach { invalid ->
            val h = Harness(); h.override = { invalid }
            assertThrows(IllegalStateException::class.java) { h.api.setApiKey("replacement-fixture-key") }
            assertEquals("fixture-secret", h.account.token)
        }
    }

    @Test fun `cache results are associated with input order and missing results fail safely`() {
        val h = Harness(); val second = "b".repeat(40)
        h.override = { """[{"cached":false},{"cached":true}]""" }
        assertEquals(setOf(second), h.api.cached(listOf(hash, second)))
        h.override = { "[]" }
        assertThrows(IllegalStateException::class.java) { h.api.cached(listOf(hash)) }
    }

    @Test fun `cancelling key validation cannot save the submitted key`() {
        val h = Harness()
        assertThrows(IllegalStateException::class.java) { h.api.setApiKey("replacement-fixture-key") { true } }
        assertEquals("fixture-secret", h.account.token)
    }
}
