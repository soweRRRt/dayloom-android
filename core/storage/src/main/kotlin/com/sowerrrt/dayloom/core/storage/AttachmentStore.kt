package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredAttachment(
    val reference: AttachmentRef,
    val bytes: ByteArray,
)

class AttachmentStore(
    private val root: File,
    private val idFactory: () -> EntityId = EntityId::random,
) {
    fun fileFor(id: EntityId): File {
        require(id.value.matches(Regex("[0-9a-fA-F-]{36}"))) { "Attachment ID must be a UUID" }
        return File(root, id.value)
    }

    private val mutex = Mutex()

    suspend fun importImage(
        displayName: String,
        mimeType: String,
        input: InputStream,
    ): AttachmentRef =
        withContext(Dispatchers.IO) {
            require(mimeType.startsWith("image/")) { "Only images can be attached" }
            mutex.withLock {
                root.mkdirs()
                val id = idFactory()
                val destination = fileFor(id)
                val temporary = File(root, "${id.value}.tmp")
                try {
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_IMAGE_BYTES) { "Image is too large" }
                            output.write(buffer, 0, read)
                        }
                        require(total > 0) { "Image is empty" }
                        output.fd.sync()
                    }
                    check(temporary.renameTo(destination)) { "Unable to save attachment" }
                    AttachmentRef(
                        id = id,
                        displayName = displayName.trim().ifEmpty { "image" }.take(MAX_DISPLAY_NAME_LENGTH),
                        mimeType = mimeType,
                    )
                } finally {
                    temporary.delete()
                    input.close()
                }
            }
        }

    suspend fun importImage(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): AttachmentRef = importImage(displayName, mimeType, ByteArrayInputStream(bytes))

    suspend fun delete(id: EntityId) =
        withContext(Dispatchers.IO) {
            mutex.withLock { !fileFor(id).exists() || fileFor(id).delete() }
        }

    fun existingFileFor(id: EntityId): File? = fileFor(id).takeIf(File::isFile)

    suspend fun exportAll(references: Collection<AttachmentRef>): List<StoredAttachment> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                references.distinctBy { it.id }.map { reference ->
                    val file =
                        requireNotNull(existingFileFor(reference.id)) {
                            "Attachment ${reference.id.value} is missing"
                        }
                    require(file.length() in 1..MAX_IMAGE_BYTES) { "Attachment size is invalid" }
                    StoredAttachment(reference, file.readBytes())
                }
            }
        }

    suspend fun replaceAll(attachments: Collection<StoredAttachment>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(attachments.map { it.reference.id }.distinct().size == attachments.size) {
                    "Attachment IDs must be unique"
                }
                require(attachments.sumOf { it.bytes.size.toLong() } <= MAX_TOTAL_BACKUP_BYTES) {
                    "Attachments are too large"
                }
                val parent = requireNotNull(root.parentFile) { "Attachment root must have a parent" }
                parent.mkdirs()
                val staging = File(parent, "${root.name}.import-${EntityId.random().value}")
                val previous = File(parent, "${root.name}.previous-${EntityId.random().value}")
                try {
                    check(staging.mkdirs()) { "Unable to prepare attachment import" }
                    attachments.forEach { attachment ->
                        validate(attachment)
                        FileOutputStream(File(staging, attachment.reference.id.value)).use { output ->
                            output.write(attachment.bytes)
                            output.fd.sync()
                        }
                    }
                    if (root.exists()) {
                        Files.move(root.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    try {
                        Files.move(staging.toPath(), root.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } catch (error: Throwable) {
                        if (previous.exists() && !root.exists()) {
                            Files.move(previous.toPath(), root.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        throw error
                    }
                    previous.deleteRecursively()
                } finally {
                    staging.deleteRecursively()
                    if (previous.exists() && root.exists()) previous.deleteRecursively()
                }
            }
        }

    private fun validate(attachment: StoredAttachment) {
        fileFor(attachment.reference.id)
        require(attachment.reference.mimeType.startsWith("image/")) { "Only images can be restored" }
        require(attachment.reference.displayName.length <= MAX_DISPLAY_NAME_LENGTH) { "Image name is too long" }
        require(attachment.bytes.size.toLong() in 1..MAX_IMAGE_BYTES) { "Image size is invalid" }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val MAX_TOTAL_BACKUP_BYTES = 100L * 1024 * 1024
        const val MAX_DISPLAY_NAME_LENGTH = 160
    }
}
