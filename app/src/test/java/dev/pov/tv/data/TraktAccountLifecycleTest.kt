package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TraktAccountLifecycleTest {
    private class Credentials : TraktCredentials {
        val values = ConcurrentHashMap(mapOf("trakt.client_id" to "client-original", "trakt.access_token" to "access-original",
            "trakt.refresh_token" to "refresh-original", "trakt.expires_at" to "0", "trakt.account_revision" to "account-original"))
        override fun get(key: String, default: String) = values[key] ?: default
        override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        override fun remove(key: String) { values.remove(key) }
    }

    private class Harness {
        val credentials = Credentials()
        var onRefresh: () -> Unit = {}
        var onCode: () -> Unit = {}
        var now = 1_000L
        var apiRequests = 0
        var sentAccess = ""
        val api = TraktApi(credentials, { address, _, _ ->
            when {
                address.endsWith("/oauth/token") -> { onRefresh(); tokens("refreshed") }
                address.endsWith("/oauth/device/code") -> { onCode(); codes() }
                address.endsWith("/oauth/device/token") -> tokens("connected")
                else -> error("Unexpected synthetic OAuth request")
            }
        }, { now }, { _, _, headers, _ ->
            apiRequests++; sentAccess = headers["Authorization"].orEmpty(); "[]"
        })
    }

    @Test fun `successful refresh persists both rotating tokens before using new access token`() {
        val h = Harness()
        assertTrue(h.api.watchlist("movie").isEmpty())
        assertEquals("access-refreshed", h.credentials.get("trakt.access_token"))
        assertEquals("refresh-refreshed", h.credentials.get("trakt.refresh_token"))
        assertEquals("Bearer access-refreshed", h.sentAccess)
        assertEquals("", h.credentials.get("trakt.refresh_pending"))
        assertEquals("account-original", h.credentials.get("trakt.account_revision"))
    }

    @Test fun `disconnect during a refresh prevents a late response relinking the account`() {
        val h = Harness()
        h.onRefresh = { h.api.disconnect() }
        assertAuthorizationChanged { h.api.watchlist("movie") }
        assertFalse(h.api.connected())
        assertEquals("", h.credentials.get("trakt.access_token"))
        assertEquals("", h.credentials.get("trakt.refresh_token"))
        assertEquals("", h.credentials.get("trakt.account_revision"))
        assertEquals(0, h.apiRequests)
    }

    @Test fun `changing Client ID during a refresh cannot attach old tokens to new registration`() {
        val h = Harness()
        h.onRefresh = { assertTrue(h.api.setClientId("client-replacement")) }
        assertAuthorizationChanged { h.api.watchlist("movie") }
        assertEquals("client-replacement", h.credentials.get("trakt.client_id"))
        assertEquals("", h.credentials.get("trakt.access_token"))
        assertEquals("", h.credentials.get("trakt.refresh_token"))
        assertEquals(0, h.apiRequests)
    }

    @Test fun `another activity client can disconnect an in-flight refresh`() {
        val h = Harness()
        val otherClient = TraktApi(h.credentials)
        h.onRefresh = { otherClient.disconnect() }
        assertAuthorizationChanged { h.api.watchlist("movie") }
        assertFalse(otherClient.connected())
        assertFalse(h.api.connected())
    }

    @Test fun `new completed authorization wins over an older refresh`() {
        val h = Harness()
        h.onRefresh = {
            val auth = h.api.startDeviceAuth()
            h.now += 5_000
            assertTrue(h.api.pollDeviceAuth(auth))
        }
        assertAuthorizationChanged { h.api.watchlist("movie") }
        assertEquals("access-connected", h.credentials.get("trakt.access_token"))
        assertEquals("refresh-connected", h.credentials.get("trakt.refresh_token"))
        assertNotEquals("account-original", h.credentials.get("trakt.account_revision"))
        assertEquals(0, h.apiRequests)
    }

    @Test fun `disconnect during a code request prevents late activation session reuse`() {
        val h = Harness()
        h.onCode = { h.api.disconnect() }
        try { h.api.startDeviceAuth(); fail("Expected the pending request to be invalidated") }
        catch (error: ApiException) { assertEquals(410, error.code) }
        assertFalse(h.api.connected())
    }

    @Test fun `saving the same Client ID preserves its working account`() {
        val h = Harness()
        assertFalse(h.api.setClientId(" client-original "))
        assertEquals("access-original", h.credentials.get("trakt.access_token"))
        assertEquals("account-original", h.credentials.get("trakt.account_revision"))
    }

    @Test fun `disconnect returns while a refresh network request is still waiting`() {
        val h = Harness()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        h.onRefresh = { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val refreshing = workers.submit<Boolean> {
            try { h.api.watchlist("movie"); false }
            catch (error: ApiException) { error.code == 401 }
        }
        try {
            assertTrue("Refresh should have reached the network", started.await(2, TimeUnit.SECONDS))
            workers.submit { h.api.disconnect() }.get(1, TimeUnit.SECONDS)
            assertFalse(h.api.connected())
        } finally {
            release.countDown()
            try { assertTrue(refreshing.get(3, TimeUnit.SECONDS)) } finally { workers.shutdownNow() }
        }
    }

    private fun assertAuthorizationChanged(work: () -> Any?) {
        try { work(); fail("Expected the old account request to be rejected") }
        catch (error: ApiException) { assertEquals(401, error.code) }
    }

    companion object {
        private fun tokens(suffix: String) = JSONObject().put("access_token", "access-$suffix").put("refresh_token", "refresh-$suffix")
            .put("created_at", System.currentTimeMillis() / 1000).put("expires_in", 3600)
        private fun codes() = JSONObject().put("device_code", "device-synthetic").put("user_code", "SYNTHETIC")
            .put("verification_url", "https://trakt.tv/activate").put("interval", 5).put("expires_in", 600)
    }
}
