package com.sowerrrt.dayloom.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sowerrrt.dayloom.core.model.AttachmentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AttachmentRepository {
    suspend fun importImage(uri: Uri): AttachmentRef

    suspend fun importImage(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): AttachmentRef

    suspend fun delete(attachment: AttachmentRef): Boolean

    fun localPath(attachment: AttachmentRef): String?
}

class LocalAttachmentRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val store: AttachmentStore,
    ) : AttachmentRepository {
        override suspend fun importImage(uri: Uri): AttachmentRef {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/*"
            val displayName =
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "image"
            val input = requireNotNull(resolver.openInputStream(uri)) { "Unable to open selected image" }
            return store.importImage(displayName, mimeType, input)
        }

        override suspend fun importImage(
            displayName: String,
            mimeType: String,
            bytes: ByteArray,
        ): AttachmentRef = store.importImage(displayName, mimeType, bytes)

        override suspend fun delete(attachment: AttachmentRef): Boolean = store.delete(attachment.id)

        override fun localPath(attachment: AttachmentRef): String? = store.existingFileFor(attachment.id)?.absolutePath
    }
