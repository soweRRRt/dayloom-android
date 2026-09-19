package com.sowerrrt.dayloom.core.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class TransportResponse(
    val statusCode: Int,
    val body: String,
)

fun interface ReleaseTransport {
    suspend fun get(url: String): TransportResponse
}

class UrlConnectionReleaseTransport : ReleaseTransport {
    override suspend fun get(url: String): TransportResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 3_000
                connection.readTimeout = 4_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Dayloom-Android")
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                TransportResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
}

class GitHubUpdateSource(
    private val transport: ReleaseTransport = UrlConnectionReleaseTransport(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateSource {
    override suspend fun check(currentVersion: SemVer): UpdateCheckResult =
        try {
            mapResponse(transport.get(RELEASES_URL), currentVersion)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.OFFLINE)
        } catch (_: Exception) {
            UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.MALFORMED_RESPONSE)
        }

    internal fun mapResponse(
        response: TransportResponse,
        currentVersion: SemVer,
    ): UpdateCheckResult {
        if (response.statusCode == 403 || response.statusCode == 429) {
            return UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.RATE_LIMITED)
        }
        if (response.statusCode !in 200..299) {
            return UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.SERVER_ERROR)
        }
        val releases =
            runCatching {
                json.decodeFromString(ListSerializer(GitHubRelease.serializer()), response.body)
            }.getOrElse {
                return UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.MALFORMED_RESPONSE)
            }
        val newest =
            releases
                .asSequence()
                .filterNot { it.draft || it.prerelease }
                .mapNotNull { release ->
                    SemVer.parseOrNull(release.tagName)?.takeIf(SemVer::isStable)?.let {
                        it to
                            release
                    }
                }.maxByOrNull { it.first }
                ?: return UpdateCheckResult.NoRelease
        if (newest.first <= currentVersion) return UpdateCheckResult.UpToDate
        if (!isSafeReleaseUrl(newest.second.htmlUrl)) {
            return UpdateCheckResult.Unavailable(UpdateCheckResult.Unavailable.Reason.MALFORMED_RESPONSE)
        }
        return UpdateCheckResult.Available(
            UpdateInfo(
                version = newest.first,
                title = newest.second.name.ifBlank { "Dayloom ${newest.first}" },
                notes = newest.second.body.take(MAX_NOTES_LENGTH),
                releasePageUrl = newest.second.htmlUrl,
            ),
        )
    }

    private fun isSafeReleaseUrl(value: String): Boolean =
        runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true)
        }.getOrDefault(false)

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/soweRRRt/dayloom-android/releases?per_page=10"
        const val MAX_NOTES_LENGTH = 1_500
    }
}
