package com.sowerrrt.dayloom.core.security

enum class LockScope {
    VAULT_ONLY,
    WHOLE_APP,
}

data class LockConfiguration(
    val scope: LockScope = LockScope.VAULT_ONLY,
    val biometricEnabled: Boolean = false,
    val autoLockAfterMillis: Long = 60_000,
)

sealed interface UnlockResult {
    data object Success : UnlockResult

    data object Cancelled : UnlockResult

    data object Unavailable : UnlockResult

    data class Failure(
        val retryAllowed: Boolean,
    ) : UnlockResult
}

interface AppLockManager {
    val isLocked: kotlinx.coroutines.flow.Flow<Boolean>

    suspend fun lock()

    suspend fun unlock(): UnlockResult
}

interface VaultCipher {
    suspend fun ensureKey(): Result<Unit>

    suspend fun encrypt(plaintext: ByteArray): Result<ByteArray>

    suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray>

    suspend fun destroyKey(): Result<Unit>
}
