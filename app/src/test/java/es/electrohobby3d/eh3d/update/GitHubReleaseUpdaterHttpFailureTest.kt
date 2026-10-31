// EH3D-ANDROID-CONTROL - HTTP failure message unit tests.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

class GitHubReleaseUpdaterHttpFailureTest {
    @Test
    fun `a spent rate limit is explained in plain language, not just the status code`() {
        val message = describeHttpFailure(
            code = HttpURLConnection.HTTP_FORBIDDEN,
            rateLimitRemaining = "0",
            rateLimitReset = "1000",
            nowEpochSeconds = 400, // 600 seconds left -> ceil(600/60) + 1 = 11 minutes
        )
        assertTrue(message.contains("limitado"))
        assertTrue(message.contains("11 minuto"))
        assertTrue(message.contains("misma casa/Wi-Fi"))
    }

    @Test
    fun `a missing or unparseable reset header still gives a real answer`() {
        val message = describeHttpFailure(
            code = HttpURLConnection.HTTP_FORBIDDEN,
            rateLimitRemaining = "0",
            rateLimitReset = null,
            nowEpochSeconds = 400,
        )
        assertTrue(message.contains("limitado"))
        assertTrue(message.contains("dentro de un rato"))
    }

    @Test
    fun `a reset timestamp already in the past does not claim negative minutes`() {
        val message = describeHttpFailure(
            code = HttpURLConnection.HTTP_FORBIDDEN,
            rateLimitRemaining = "0",
            rateLimitReset = "100", // already passed relative to now
            nowEpochSeconds = 400,
        )
        assertTrue(message.contains("dentro de un rato"))
    }

    @Test
    fun `a genuine 403 unrelated to rate limiting keeps the plain status code`() {
        // No X-RateLimit-Remaining header at all - a real, different kind of 403.
        val message = describeHttpFailure(
            code = HttpURLConnection.HTTP_FORBIDDEN,
            rateLimitRemaining = null,
            rateLimitReset = null,
        )
        assertEquals("GitHub devolvió el código HTTP 403.", message)
    }

    @Test
    fun `a rate limit that still has budget left is not misreported as spent`() {
        val message = describeHttpFailure(
            code = HttpURLConnection.HTTP_FORBIDDEN,
            rateLimitRemaining = "12",
            rateLimitReset = "1000",
        )
        assertEquals("GitHub devolvió el código HTTP 403.", message)
    }

    @Test
    fun `an unrelated HTTP status is reported plainly`() {
        assertEquals(
            "GitHub devolvió el código HTTP 500.",
            describeHttpFailure(code = 500, rateLimitRemaining = null, rateLimitReset = null),
        )
    }
}
