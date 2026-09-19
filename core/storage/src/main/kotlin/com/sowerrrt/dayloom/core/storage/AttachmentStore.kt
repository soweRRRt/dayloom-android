package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import java.io.File

class AttachmentStore(
    private val root: File,
) {
    fun fileFor(id: EntityId): File {
        require(id.value.matches(Regex("[0-9a-fA-F-]{36}"))) { "Attachment ID must be a UUID" }
        return File(root, id.value)
    }
}
