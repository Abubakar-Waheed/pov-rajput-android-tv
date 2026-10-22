package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TorBoxDeviceAuthTest {
    private class Credentials : TorBoxCredentials {
        val values = mutableMapOf("torbox.token" to "previous-account-token")
        override fun get(key: String) = values[key].orEmpty()
        override fun put(key: String, value: String) { values[key] = value }
    }

    private class Harness {
        val credentials = Credentials()
        var now = 1_000L
        var onStart: () -> Unit = {}
        var onToken: () -> Unit = {}
        var onVerify: () -> Unit = {}
        var tokenRequests = 0
        var accountRequests = 0
        var deviceCode = "device-one"
        val api = TorBoxApi(credentials, { address, _, _, _ ->
            when {
                address.contains("/device/start?") -> {
                    onStart()
                    success(JSONObject().put("device_code", deviceCode).put("code", "USER-CODE")
                        .put("verification_url", "https://torbox.app/activate").put("interval", 5).put("expires_in", 600))
                }
                address.endsWith("/device/token") -> {
                    tokenRequests++; onToken(); success(JSONObject().put("access_token", "replacement-account-token"))
                }
                address.endsWith("/user/me") -> { accountRequests++; onVerify(); success(JSONObject().put("id", 1)) }
                else -> error("Unexpected test request")
            }
        }, { now })

        private fun success(data: JSONObject) = JSONObject().put("success", true).put("data", data)
    }

    @Test fun `verified current session replaces the account token`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.now += 5_000
        assertTrue(h.api.pollDeviceAuth(auth))
        assertEquals("replacement-account-token", h.credentials.get("torbox.token"))
        assertEquals(1, h.accountRequests)
    }

    @Test fun `cancelled code cannot recreate a polling session`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.api.cancelDeviceAuth(auth)
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals(0, h.tokenRequests)
        assertEquals("previous-account-token", h.credentials.get("torbox.token"))
    }

    @Test fun `cancel while starting prevents a late code becoming usable`() {
        val h = Harness()
        h.onStart = { h.api.cancelDeviceAuth() }
        assertEnded { h.api.startDeviceAuth() }
        assertEquals("previous-account-token", h.credentials.get("torbox.token"))
    }

    @Test fun `cancel during token request prevents verification and account replacement`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.onToken = { h.api.cancelDeviceAuth(auth) }
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals(0, h.accountRequests)
        assertEquals("previous-account-token", h.credentials.get("torbox.token"))
    }

    @Test fun `cancel during account verification prevents late token persistence`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.onVerify = { h.api.cancelDeviceAuth(auth) }
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals(1, h.accountRequests)
        assertEquals("previous-account-token", h.credentials.get("torbox.token"))
    }

    @Test fun `requesting a new code invalidates the older code`() {
        val h = Harness()
        val older = h.api.startDeviceAuth()
        h.deviceCode = "device-two"
        val current = h.api.startDeviceAuth()
        assertEnded { h.api.pollDeviceAuth(older) }
        assertTrue(h.api.pollDeviceAuth(current))
        assertEquals(1, h.tokenRequests)
    }

    @Test fun `expired session preserves the existing connected account`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.now += 600_000
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals(0, h.tokenRequests)
        assertEquals("previous-account-token", h.credentials.get("torbox.token"))
    }

    @Test fun `cancel returns without waiting for a token network request`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        h.onToken = { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val polling = workers.submit<Boolean> {
            try { h.api.pollDeviceAuth(auth); false }
            catch (error: ApiException) { error.code == 410 }
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            workers.submit { h.api.cancelDeviceAuth(auth) }.get(1, TimeUnit.SECONDS)
            assertEquals("previous-account-token", h.credentials.get("torbox.token"))
        } finally {
            release.countDown()
            try { assertTrue(polling.get(3, TimeUnit.SECONDS)) } finally { workers.shutdownNow() }
        }
    }

    @Test fun `disconnect while verifying an API key prevents a late reconnect`() {
        val h = Harness()
        h.onVerify = { h.api.disconnect() }
        try { h.api.setApiKey("candidate-key"); fail("Expected the disconnected verification to be rejected") }
        catch (error: ApiException) { assertEquals(401, error.code) }
        assertEquals("", h.credentials.get("torbox.token"))
    }

    @Test fun `disconnect while verifying a device token prevents a late reconnect`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.onVerify = { h.api.disconnect() }
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals("", h.credentials.get("torbox.token"))
    }

    @Test fun `a newer manual key wins over older device verification`() {
        val h = Harness()
        val auth = h.api.startDeviceAuth()
        h.onVerify = {
            h.onVerify = {}
            h.api.setApiKey("newer-manual-key")
        }
        assertEnded { h.api.pollDeviceAuth(auth) }
        assertEquals("newer-manual-key", h.credentials.get("torbox.token"))
    }

    private fun assertEnded(work: () -> Any?) {
        try { work(); fail("Expected an ended authorization session") }
        catch (error: ApiException) { assertEquals(410, error.code) }
    }
}
