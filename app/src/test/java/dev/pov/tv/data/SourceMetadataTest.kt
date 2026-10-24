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
class SourceMetadataTest {
    @Test fun `exact video bytes take precedence over rounded description and generic sizes`() {
        val stream = JSONObject().put("behaviorHints", JSONObject().put("videoSize", 1234567890L))
            .put("size", 999L).put("description", "Movie\nSize 1.2 GB")
        assertEquals(1234567890L, SourceMetadata.sizeBytes(stream))
        stream.getJSONObject("behaviorHints").put("videoSize", -1)
        assertEquals(999L, SourceMetadata.sizeBytes(stream))
    }

    @Test fun `textual sizes preserve decimal and binary units and decimal comma`() {
        for ((value, expected) in listOf("2.5 GB" to 2500000000L, "1.5 GiB" to 1610612736L,
            "700 MB" to 700000000L, "2,5 GB" to 2500000000L, "1,234 MB" to 1234000000L,
            "1 KiB" to 1024L, "2.42 GiB" to 2598455214L, "12 B" to 12L)) {
            assertEquals(value, expected, SourceMetadata.sizeBytes(JSONObject().put("title", "Movie\n💾 $value")))
        }
    }

    @Test fun `invalid absent and pack totals never become video sizes`() {
        for (value in listOf(null, JSONObject.NULL, true, 0, -5, 0.5, "n/a", "9223372036854775808")) {
            assertNull(SourceMetadata.sizeBytes(JSONObject().put("size", value)))
        }
        for (value in listOf("-2 GB", "0 GB", "999999999999 TB", "Pack size: 30 GB", "Torrent size 4 GB", "Season size 8 GB")) {
            assertNull(value, SourceMetadata.sizeBytes(JSONObject().put("description", value)))
        }
        assertNull(SourceMetadata.sizeBytes(JSONObject().put("torrentSize", 9000000000L).put("totalSize", 9000000000L)))
        assertEquals(2000000000L, SourceMetadata.sizeBytes(JSONObject().put("description", "Pack size 20 GB\nVideo size 2 GB")))
    }

    @Test fun `resolution recognizes source metadata without confusing years codecs or audio bitrate`() {
        for ((value, expected) in listOf("Movie.2160p.HEVC.mkv" to 2160, "Addon 4K" to 2160,
            "UHD" to 2160, "1920x1080" to 1080, "8K" to 4320, "Full HD" to 1080, "720" to 720)) {
            assertEquals(value, expected, SourceMetadata.qualityHeight(value))
        }
        for (value in listOf("Movie.2026.10bit.HEVC.DTS.5.1", "Audio 720 kbps", "21600p")) {
            assertNull(value, SourceMetadata.qualityHeight(value))
        }
        val stream = JSONObject().put("name", "Provider 4K")
            .put("behaviorHints", JSONObject().put("filename", "Movie.1080p.mkv"))
        assertEquals(1080, SourceMetadata.qualityHeight(stream))
        assertEquals(720, SourceMetadata.qualityHeight(JSONObject().put("title", "Movie.720p.mkv\n💾 2 GB")))
    }

    private fun source(name: String, quality: Int?, size: Long?) = SourceItem(name, "fixture", directUrl = "https://example.invalid/$name", sizeBytes = size, qualityHeight = quality)

    @Test fun `all four orders keep size inside quality groups and unknown values last`() {
        val items = listOf(source("hd-big", 1080, 4), source("4k-big", 2160, 10), source("unknown", null, 1),
            source("4k-unknown", 2160, null), source("hd-small", 1080, 2), source("4k-small", 2160, 1))
        for ((order, expected) in listOf(
            SourceSortOrder(true, true) to listOf("4k-small", "4k-big", "4k-unknown", "hd-small", "hd-big", "unknown"),
            SourceSortOrder(true, false) to listOf("4k-big", "4k-small", "4k-unknown", "hd-big", "hd-small", "unknown"),
            SourceSortOrder(false, true) to listOf("hd-small", "hd-big", "4k-small", "4k-big", "4k-unknown", "unknown"),
            SourceSortOrder(false, false) to listOf("hd-big", "hd-small", "4k-big", "4k-small", "4k-unknown", "unknown"))) {
            assertEquals(expected, order.sorted(items).map { it.title })
        }
    }

    @Test fun `equal metadata stays stable and invalid metadata sorts as unknown`() {
        val a = source("a", 1080, 2); val b = source("b", 1080, 2)
        assertEquals(listOf(a, b), SourceSortOrder().sorted(listOf(a, b)))
        assertEquals(listOf(a, source("unknown", 0, -1)), SourceSortOrder(false, false).sorted(listOf(source("unknown", 0, -1), a)))
        assertEquals("Size unavailable", SourceMetadata.sizeLabel(null))
        assertEquals("Quality unavailable", SourceMetadata.qualityLabel(0))
    }

    @Test fun `resolver retains exact metadata during cache mapping without resolving or adding downloads`() {
        var cacheCalls = 0
        val hash = "a".repeat(40)
        val cloud = object : SourceTorBox {
            override fun cached(hashes: List<String>): Set<String> { cacheCalls++; return setOf(hash) }
            override fun addMagnet(hash: String): Long = error("Listing must not create a transfer")
            override fun folder(kind: String, id: Long): CloudFolder = error("Listing must not resolve")
            override fun streamUrl(kind: String, folderId: Long, fileId: Long): String = error("Listing must not stream")
            override fun prepareCached(source: SourceItem, identity: PlaybackIdentity): PreparedSource.Files = error("Listing must not prepare")
        }
        val payload = JSONObject().put("streams", JSONArray().put(JSONObject().put("infoHash", hash)
            .put("behaviorHints", JSONObject().put("filename", "Movie.2160p.mkv").put("videoSize", 2500000000L))))
        val resolver = SourceResolver({ "https://sources.example" }, cloud, { payload })
        val item = resolver.sources(PlaybackIdentity("tt1234567", "movie", "Movie")).single()
        assertEquals(1, cacheCalls); assertTrue(item.cached)
        assertEquals(2500000000L, item.sizeBytes); assertEquals(2160, item.qualityHeight)
        assertEquals(hash, item.infoHash)
    }
}
