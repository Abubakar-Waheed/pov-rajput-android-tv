package dev.pov.tv

import dev.pov.tv.data.ApiException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UiErrorMessageTest {
    private val fallback = "This request could not be completed. Check your connection and try again."

    @Test fun `exception URLs and bearer credentials never become public error text`() {
        for (text in listOf(
            "Request failed at https://example.invalid/video?token=synthetic",
            "Redirected to HTTP://example.invalid/private",
            "Authorization: Bearer synthetic-credential",
            "authorization: bearer synthetic-credential",
        )) {
            assertEquals(fallback, uiErrorMessage(ApiException(0, text)))
            assertEquals(fallback, uiErrorMessage(IllegalStateException(text)))
        }
    }

    @Test fun `structured token and secret names are redacted regardless of exception type`() {
        for (text in listOf(
            "access_token=synthetic", "REFRESH_TOKEN: synthetic", "client_secret: synthetic", "api_key=synthetic", "token=synthetic", "password: synthetic", "client_id = synthetic",
        )) {
            assertEquals(fallback, uiErrorMessage(ApiException(401, text)))
            assertEquals(fallback, uiErrorMessage(IllegalArgumentException(text)))
            assertEquals(fallback, uiErrorMessage(RuntimeException(text)))
        }
    }

    @Test fun `sanitized service and mapping explanations remain useful`() {
        val service = "Your Trakt session has expired. Link your account again."
        val mapping = "This anime could not be matched safely. Choose the correct movie or show manually."
        val episode = "This source identifies a different episode. Choose another source."
        assertEquals(service, uiErrorMessage(ApiException(401, service)))
        assertEquals(mapping, uiErrorMessage(ApiException(0, mapping)))
        assertEquals(episode, uiErrorMessage(IllegalArgumentException(episode)))
        assertEquals("The catalog is temporarily unavailable.", uiErrorMessage(IllegalStateException("The catalog is temporarily unavailable.")))
    }

    @Test fun `untrusted exception classes blank and excessive messages use a safe fallback`() {
        assertEquals(fallback, uiErrorMessage(IOException("A server returned arbitrary response content")))
        assertEquals(fallback, uiErrorMessage(RuntimeException("Untrusted implementation detail")))
        assertEquals(fallback, uiErrorMessage(IllegalStateException(" ")))
        assertEquals(fallback, uiErrorMessage(ApiException(0, "x".repeat(701))))
    }
}
