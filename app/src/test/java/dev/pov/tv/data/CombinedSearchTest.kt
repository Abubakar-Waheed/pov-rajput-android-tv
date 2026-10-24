package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CombinedSearchTest {
    @Test fun `both catalogs run concurrently and keep matching identities with different types`() {
        val entered = CountDownLatch(2)
        val api = CatalogApi { address ->
            entered.countDown()
            check(entered.await(3, TimeUnit.SECONDS))
            assertTrue(address.endsWith("search=Example.json"))
            JSONObject("""{"metas":[{"id":"tt1234567","name":"Example"}]}""")
        }
        val result = api.searchAll(" Example ")
        assertEquals(listOf("movie", "series"), result.items.map { it.type })
        assertEquals(2, result.items.size)
        assertTrue(result.unavailableTypes.isEmpty())
    }

    @Test fun `one failed catalog preserves the other and identifies missing results`() {
        val api = CatalogApi { address ->
            if (address.contains("/movie/")) error("offline")
            JSONObject("""{"metas":[{"id":"tt7654321","name":"A show"}]}""")
        }
        val result = api.searchAll("show")
        assertEquals("series", result.items.single().type)
        assertEquals(listOf("movie"), result.unavailableTypes)
    }

    @Test fun `total failure is retryable instead of claiming no matching titles`() {
        val api = CatalogApi { error("offline") }
        assertThrows(IllegalStateException::class.java) { api.searchAll("show") }
    }

    @Test fun `blank query makes no network requests and empty catalogs are valid`() {
        assertTrue(CatalogApi { error("Unexpected request") }.searchAll("  ").items.isEmpty())
        val result = CatalogApi { JSONObject("""{"metas":[]}""") }.searchAll("no match")
        assertTrue(result.items.isEmpty())
        assertTrue(result.unavailableTypes.isEmpty())
    }
}
