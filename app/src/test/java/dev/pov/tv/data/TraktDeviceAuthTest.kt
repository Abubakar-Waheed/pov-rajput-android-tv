package dev.pov.tv.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic responses only: these tests never contact Trakt or use account credentials. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TraktDeviceAuthTest {
    private class MemoryCredentials : TraktCredentials {
        val values = mutableMapOf("trakt.client_id" to "test-client")
        override fun get(key: String, default: String) = values[key] ?: default
        override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        override fun remove(key: String) { values.remove(key) }
    }

    private class Fixture {
        val credentials = MemoryCredentials()
        var now = 1_000L
        val requests = mutableListOf<Triple<String, Map<String, String>, JSONObject>>()
        val replies = ArrayDeque<() -> JSONObject>()
        val api = TraktApi(credentials, { url, headers, body ->
            requests.add(Triple(url, headers, body))
            check(replies.isNotEmpty()) { "Unexpected OAuth request" }
            replies.removeFirst().invoke()
        }, { now })

        fun start(response: JSONObject = codes()): DeviceAuth {
            replies.add { response }
            return api.startDeviceAuth()
        }
    }

    @Test fun `missing ID fails with setup instructions without making a request`() {
        val f = Fixture()
        f.credentials.values.remove("trakt.client_id")
        val error = failure { f.api.startDeviceAuth() }
        assertTrue(error.message.orEmpty().contains("Client ID"))
        assertTrue(error.message.orEmpty().contains("Settings"))
        assertFalse(f.api.configured())
        assertTrue(f.requests.isEmpty())
    }

    @Test fun `invalid IDs fail before network and never appear in messages`() {
        for (id in listOf("bad\nsecret", "https://trakt.tv/secret", "ü-client", "x".repeat(257))) {
            val f = Fixture()
            f.credentials.values["trakt.client_id"] = id
            val error = failure { f.api.startDeviceAuth() }
            assertTrue(error.message.orEmpty().contains("Client ID is invalid"))
            assertFalse(error.message.orEmpty().contains(id))
            assertFalse(f.api.configured())
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `device code request uses auth host and public client ID without a secret or callback`() {
        val f = Fixture()
        val auth = f.start()
        assertEquals("ABCD1234", auth.userCode)
        assertEquals("https://auth.trakt.tv/activate", auth.verificationUrl)
        val (url, headers, body) = f.requests.single()
        assertEquals("https://auth.trakt.tv/oauth/device/code", url)
        assertEquals("test-client", headers["trakt-api-key"])
        assertEquals("2", headers["trakt-api-version"])
        assertFalse(headers.containsKey("Authorization"))
        assertEquals(setOf("client_id"), body.keys().asSequence().toSet())
    }

    @Test fun `incomplete and wrongly typed code responses produce useful error`() {
        for (response in listOf(
            codes().apply { remove("user_code") }, codes().put("device_code", 12345),
            codes().put("user_code", "ABCD\n1234"), codes().apply { remove("interval") },
            codes().put("interval", 0), codes().put("interval", 2.5),
            codes().put("expires_in", -5), codes().put("expires_in", "600")
        )) {
            val error = failure { Fixture().start(response) }
            assertTrue(error.message.orEmpty().contains("usable activation code"))
        }
    }

    @Test fun `activation URL must be a safe Trakt activation page`() {
        for (url in listOf(
            "http://auth.trakt.tv/activate", "https://auth.trakt.tv.evil.invalid/activate",
            "https://evil.invalid/activate", "https://auth.trakt.tv@evil.invalid/activate",
            "https://evil@auth.trakt.tv/activate", "https://auth.trakt.tv:8443/activate",
            "https://auth.trakt.tv/oauth/token", "https://auth.trakt.tv/activate?token=hidden",
            "https://auth.trakt.tv/activate#hidden"
        )) {
            val error = failure { Fixture().start(codes().put("verification_url", url)) }
            assertTrue(error.message.orEmpty().contains("usable activation code"))
            assertFalse(error.message.orEmpty().contains(url))
        }
        for (url in listOf("https://trakt.tv/activate", "https://www.trakt.tv/activate/")) {
            assertEquals(url, Fixture().start(codes().put("verification_url", url)).verificationUrl)
        }
    }

    @Test fun `invalid application response tells user to check Client ID without echoing server content`() {
        for ((status, key) in listOf(401 to "", 403 to "", 400 to "invalid_client", 400 to "unauthorized_client")) {
            val f = Fixture()
            f.replies.add { throw ApiException(status, "secret server body", key) }
            val error = failure { f.api.startDeviceAuth() }
            assertTrue(error.message.orEmpty().contains("rejected this app's Client ID"))
            assertFalse(error.message.orEmpty().contains("secret"))
        }
    }

    @Test fun `starting failures explain network timeout service unavailable and rate limiting`() {
        for ((source, expected) in listOf(
            ApiException(0, "The service request timed out. Please try again.") to "timed out",
            ApiException(0, "The service could not be reached. Check your connection.") to "internet connection",
            ApiException(0, "A secure connection to the service could not be established.") to "date, time",
            ApiException(0, "The service returned an unreadable response.") to "unreadable response",
            ApiException(503, "secret") to "temporarily unavailable",
            ApiException(400, "secret") to "could not issue an activation code",
            ApiException(429, "secret", retryAfterSeconds = 30) to "Wait 30 seconds"
        )) {
            val f = Fixture()
            f.replies.add { throw source }
            val error = failure { f.api.startDeviceAuth() }
            assertEquals(source.code, error.code)
            assertTrue(error.message.orEmpty().contains(expected))
            assertFalse(error.message.orEmpty().contains("secret"))
        }
    }

    @Test fun `poll waits the server interval and pending response remains active`() {
        val f = Fixture()
        val auth = f.start()
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(1, f.requests.size)
        f.now += 5_000
        f.replies.add { throw ApiException(400, "Pending") }
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(2, f.requests.size)
        assertEquals("https://auth.trakt.tv/oauth/device/token", f.requests.last().first)
        assertEquals("synthetic-device-code", f.requests.last().third.getString("code"))
        f.now += 4_999
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(2, f.requests.size)
        f.now += 1
        f.replies.add { throw ApiException(400, "Pending", "authorization_pending") }
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(3, f.requests.size)
    }

    @Test fun `invalid client during polling is an error rather than endless pending`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 5_000
        f.replies.add { throw ApiException(400, "hidden body", "invalid_client") }
        assertTrue(failure { f.api.pollDeviceAuth(auth) }.message.orEmpty().contains("Client ID"))
    }

    @Test fun `slow down and Retry After delay the next poll`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 5_000
        f.replies.add { throw ApiException(429, "slow", retryAfterSeconds = 20) }
        assertFalse(f.api.pollDeviceAuth(auth))
        f.now += 19_999
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(2, f.requests.size)
        f.now += 1
        f.replies.add { throw ApiException(400, "pending") }
        assertFalse(f.api.pollDeviceAuth(auth))
        assertEquals(3, f.requests.size)
    }

    @Test fun `expired session stops polling with a new code instruction`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 600_000
        val error = failure { f.api.pollDeviceAuth(auth) }
        assertEquals(410, error.code)
        assertTrue(error.message.orEmpty().contains("expired"))
        assertEquals(1, f.requests.size)
    }

    @Test fun `terminal device states are explained`() {
        for ((status, expected) in listOf(404 to "invalid", 409 to "already used", 410 to "expired", 418 to "declined")) {
            val f = Fixture()
            val auth = f.start()
            f.now += 5_000
            f.replies.add { throw ApiException(status, "hidden") }
            assertTrue(failure { f.api.pollDeviceAuth(auth) }.message.orEmpty().contains(expected))
        }
    }

    @Test fun `authorized tokens save together only after complete response`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 5_000
        f.replies.add { tokens() }
        assertTrue(f.api.pollDeviceAuth(auth))
        assertEquals("synthetic-access", f.credentials.values["trakt.access_token"])
        assertEquals("synthetic-refresh", f.credentials.values["trakt.refresh_token"])
        assertTrue(f.credentials.values["trakt.account_revision"].orEmpty().isNotBlank())
        assertTrue(f.api.connected())
    }

    @Test fun `incomplete tokens cannot partially save authorization`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 5_000
        f.replies.add { tokens().apply { remove("refresh_token") } }
        failure { f.api.pollDeviceAuth(auth) }
        assertFalse(f.credentials.values.containsKey("trakt.access_token"))
    }

    @Test fun `canceling prevents future polls and ignores a network response already in flight`() {
        val f = Fixture()
        val auth = f.start()
        f.now += 5_000
        f.replies.add { f.api.cancelDeviceAuth(auth); tokens() }
        assertEquals(410, failure { f.api.pollDeviceAuth(auth) }.code)
        assertFalse(f.credentials.values.containsKey("trakt.access_token"))
        assertEquals(410, failure { f.api.pollDeviceAuth(auth) }.code)
        assertEquals(2, f.requests.size)
    }

    @Test fun `changing app configuration invalidates pending linking before another network call`() {
        val f = Fixture()
        val auth = f.start()
        f.credentials.values["trakt.client_id"] = "different-client"
        f.now += 5_000
        assertTrue(failure { f.api.pollDeviceAuth(auth) }.message.orEmpty().contains("Client ID changed"))
        assertEquals(1, f.requests.size)
    }

    companion object {
        private fun codes() = JSONObject().put("device_code", "synthetic-device-code")
            .put("user_code", "ABCD1234").put("verification_url", "https://auth.trakt.tv/activate")
            .put("interval", 5).put("expires_in", 600)

        private fun tokens() = JSONObject().put("access_token", "synthetic-access")
            .put("refresh_token", "synthetic-refresh").put("expires_in", 604800).put("created_at", 1800000000)

        private fun failure(action: () -> Any?): ApiException {
            try { action() } catch (error: ApiException) { return error }
            throw AssertionError("Expected ApiException")
        }
    }
}
