package com.sowerrrt.dayloom.core.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class StorageEnvelope<T>(
    val schemaVersion: Int,
    val updatedAtEpochMillis: Long,
    val payload: T,
)

fun interface StorageMigration {
    fun migrate(envelope: JsonObject): JsonObject
}

sealed class StorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Corrupt(
        val primary: File,
        cause: Throwable,
    ) : StorageException("Both primary and backup data are unreadable: ${primary.name}", cause)

    class UnsupportedSchema(
        val found: Int,
        val supported: Int,
    ) : StorageException("Schema $found is newer than supported schema $supported")

    class MissingMigration(
        val from: Int,
    ) : StorageException("No migration is registered from schema $from")
}

class FileJsonStore<T>(
    directory: File,
    fileName: String,
    private val payloadSerializer: KSerializer<T>,
    private val currentSchemaVersion: Int,
    private val defaultValue: () -> T,
    private val migrations: Map<Int, StorageMigration> = emptyMap(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        },
) {
    private val mutex = Mutex()
    private val primary = File(directory, fileName)
    private val backup = File(directory, "$fileName.bak")
    private val temporary = File(directory, "$fileName.tmp")
    private val envelopeSerializer = StorageEnvelope.serializer(payloadSerializer)

    init {
        require(currentSchemaVersion > 0) { "Schema version must be positive" }
        require(!fileName.contains(File.separatorChar)) { "fileName must not contain path separators" }
    }

    suspend fun read(): T = withContext(ioDispatcher) { mutex.withLock { readLocked() } }

    suspend fun write(value: T) = withContext(ioDispatcher) { mutex.withLock { writeLocked(value) } }

    suspend fun update(transform: (T) -> T): T =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = transform(readLocked())
                writeLocked(updated)
                updated
            }
        }

    private fun readLocked(): T {
        if (!primary.exists()) return defaultValue()
        return runCatching { decode(primary) }.getOrElse { primaryError ->
            if (!backup.exists()) throw StorageException.Corrupt(primary, primaryError)
            runCatching { decode(backup) }
                .onSuccess { recovered -> writeLocked(recovered) }
                .getOrElse { throw StorageException.Corrupt(primary, it) }
        }
    }

    private fun decode(file: File): T {
        var element =
            json.parseToJsonElement(file.readText()) as? JsonObject
                ?: error("Storage envelope must be a JSON object")
        var schema =
            element["schemaVersion"]?.jsonPrimitive?.int
                ?: error("Storage envelope has no schemaVersion")
        if (schema > currentSchemaVersion) throw StorageException.UnsupportedSchema(schema, currentSchemaVersion)
        while (schema < currentSchemaVersion) {
            val migration = migrations[schema] ?: throw StorageException.MissingMigration(schema)
            element = migration.migrate(element)
            val next =
                element["schemaVersion"]?.jsonPrimitive?.int
                    ?: error("Migration from schema $schema did not set schemaVersion")
            check(next > schema) { "Migration from schema $schema did not advance the version" }
            schema = next
        }
        return json.decodeFromJsonElement(envelopeSerializer, element).payload
    }

    private fun writeLocked(value: T) {
        primary.parentFile?.mkdirs()
        val envelope = StorageEnvelope(currentSchemaVersion, clock(), value)
        val bytes = json.encodeToString(envelopeSerializer, envelope).toByteArray(Charsets.UTF_8)
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
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
