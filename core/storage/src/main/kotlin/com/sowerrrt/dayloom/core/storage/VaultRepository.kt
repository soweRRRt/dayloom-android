package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.VaultEntry
import com.sowerrrt.dayloom.core.model.VaultSnapshot
import com.sowerrrt.dayloom.core.security.VaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface VaultRepository {
    suspend fun hasVault(): Boolean

    suspend fun loadEntries(): List<VaultEntry>

    suspend fun saveEntries(entries: List<VaultEntry>)

    suspend fun deleteVault()
}

class EncryptedFileVaultRepository(
    directory: File,
    private val cipher: VaultCipher,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) : VaultRepository {
    private val mutex = Mutex()
    private val primary = File(directory, FILE_NAME)
    private val backup = File(directory, "$FILE_NAME.bak")
    private val temporary = File(directory, "$FILE_NAME.tmp")

    override suspend fun hasVault(): Boolean = withContext(Dispatchers.IO) { primary.exists() }

    override suspend fun loadEntries(): List<VaultEntry> = mutex.withLock { readLocked() }

    override suspend fun saveEntries(entries: List<VaultEntry>) =
        mutex.withLock {
            validate(entries)
            writeLocked(entries)
        }

    override suspend fun deleteVault() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                listOf(primary, backup, temporary).forEach { file ->
                    if (file.exists() && !file.delete()) error("Could not delete ${file.name}")
                }
            }
        }

    private suspend fun readLocked(): List<VaultEntry> {
        if (!primary.exists()) return emptyList()
        return runCatching { decode(primary) }.getOrElse { primaryError ->
            if (!backup.exists()) throw VaultStorageException.Unreadable(primaryError)
            runCatching { decode(backup) }
                .onSuccess { recovered -> writeLocked(recovered) }
                .getOrElse { throw VaultStorageException.Unreadable(primaryError) }
        }
    }

    private suspend fun decode(file: File): List<VaultEntry> =
        withContext(Dispatchers.IO) { file.readBytes() }.let { encrypted ->
            val plaintext = cipher.decrypt(encrypted).getOrThrow()
            try {
                val envelope = json.decodeFromString<VaultEnvelope>(plaintext.toString(Charsets.UTF_8))
                if (envelope.schemaVersion > CURRENT_SCHEMA_VERSION) {
                    throw VaultStorageException.UnsupportedSchema(envelope.schemaVersion)
                }
                require(envelope.schemaVersion == CURRENT_SCHEMA_VERSION) { "Invalid vault schema" }
                validate(envelope.payload.entries)
                envelope.payload.entries.sortedByDescending(VaultEntry::updatedAtEpochMillis)
            } finally {
                plaintext.fill(0)
            }
        }

    private suspend fun writeLocked(entries: List<VaultEntry>) {
        val plaintext =
            json
                .encodeToString(
                    VaultEnvelope.serializer(),
                    VaultEnvelope(schemaVersion = CURRENT_SCHEMA_VERSION, payload = VaultSnapshot(entries)),
                ).toByteArray(Charsets.UTF_8)
        val encrypted =
            try {
                cipher.encrypt(plaintext).getOrThrow()
            } finally {
                plaintext.fill(0)
            }
        withContext(Dispatchers.IO) {
            primary.parentFile?.mkdirs()
            FileOutputStream(temporary).use { stream ->
                stream.write(encrypted)
                stream.fd.sync()
            }
            if (primary.exists()) {
                Files.copy(primary.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                FileOutputStream(backup, true).use { it.fd.sync() }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    primary.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), primary.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun validate(entries: List<VaultEntry>) {
        require(entries.map(VaultEntry::id).distinct().size == entries.size) { "Vault entry IDs must be unique" }
        entries.forEach { entry ->
            require(entry.title.trim().length in 1..200) { "Vault title is invalid" }
            require(entry.username.length <= 500) { "Vault username is too long" }
            require(entry.password.length in 1..4096) { "Vault password is invalid" }
            require(entry.website.length <= 2_000) { "Vault website is too long" }
            require(entry.note.length <= 10_000) { "Vault note is too long" }
            require(entry.category.length <= 200) { "Vault category is too long" }
        }
    }

    @Serializable
    private data class VaultEnvelope(
        val schemaVersion: Int,
        val payload: VaultSnapshot,
    )

    companion object {
        private const val FILE_NAME = "vault.enc"
        private const val CURRENT_SCHEMA_VERSION = 1
    }
}

sealed class VaultStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unreadable(
        cause: Throwable,
    ) : VaultStorageException("Encrypted vault cannot be read", cause)

    class UnsupportedSchema(
        val found: Int,
    ) : VaultStorageException("Vault schema $found is newer than supported schema")
}
