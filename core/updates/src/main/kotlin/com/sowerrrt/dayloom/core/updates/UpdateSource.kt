package com.sowerrrt.dayloom.core.updates

data class UpdateInfo(
    val version: SemVer,
    val title: String,
    val notes: String,
    val releasePageUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(
        val info: UpdateInfo,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data object NoRelease : UpdateCheckResult

    data class Unavailable(
        val reason: Reason,
    ) : UpdateCheckResult {
        enum class Reason {
            OFFLINE,
            RATE_LIMITED,
            MALFORMED_RESPONSE,
            SERVER_ERROR,
        }
    }
}

fun interface UpdateSource {
    suspend fun check(currentVersion: SemVer): UpdateCheckResult
}
