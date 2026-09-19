package com.sowerrrt.dayloom.core.updates

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateSourceTest {
    @Test
    fun `maps newest stable release and ignores prerelease`() =
        runTest {
            val body =
                """
                [
                    {"tag_name":"v2.0.0-beta.1","html_url":"https://github.com/soweRRRt/dayloom-android/releases/tag/v2.0.0-beta.1","prerelease":true},
                    {"tag_name":"v1.2.0","name":"Calm update","body":"Notes","html_url":"https://github.com/soweRRRt/dayloom-android/releases/tag/v1.2.0"}
                ]
                """.trimIndent()
            val source = GitHubUpdateSource(ReleaseTransport { TransportResponse(200, body) })

            val result = source.check(requireNotNull(SemVer.parseOrNull("1.0.0")))

            assertTrue(result is UpdateCheckResult.Available)
            assertEquals("1.2.0", (result as UpdateCheckResult.Available).info.version.toString())
        }

    @Test
    fun `malformed response is reported without throwing`() =
        runTest {
            val source = GitHubUpdateSource(ReleaseTransport { TransportResponse(200, "not-json") })

            val result = source.check(requireNotNull(SemVer.parseOrNull("1.0.0")))

            assertEquals(
                UpdateCheckResult.Unavailable.Reason.MALFORMED_RESPONSE,
                (result as UpdateCheckResult.Unavailable).reason,
            )
        }

    @Test
    fun `rate limit is mapped explicitly`() =
        runTest {
            val source = GitHubUpdateSource(ReleaseTransport { TransportResponse(403, "{}") })

            val result = source.check(requireNotNull(SemVer.parseOrNull("1.0.0")))

            assertEquals(
                UpdateCheckResult.Unavailable.Reason.RATE_LIMITED,
                (result as UpdateCheckResult.Unavailable).reason,
            )
        }
}
