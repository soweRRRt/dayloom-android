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

    private companion object {
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val MAX_DISPLAY_NAME_LENGTH = 160
    }
}
