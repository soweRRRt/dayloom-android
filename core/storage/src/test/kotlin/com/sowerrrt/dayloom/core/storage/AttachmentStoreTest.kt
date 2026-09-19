package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class AttachmentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `image is copied to private stable file and can be deleted`() =
        runTest {
            val id = EntityId("00000000-0000-0000-0000-000000000042")
            val store = AttachmentStore(temporaryFolder.newFolder("attachments"), idFactory = { id })
            val bytes = byteArrayOf(1, 2, 3, 4)

            val attachment = store.importImage(" cover.png ", "image/png", ByteArrayInputStream(bytes))

            assertArrayEquals(bytes, store.existingFileFor(attachment.id)?.readBytes())
            assertTrue(store.delete(attachment.id))
            assertFalse(store.fileFor(attachment.id).exists())
        }

    @Test(expected = IllegalArgumentException::class)
    fun `non image content is rejected`() =
        runTest {
            AttachmentStore(temporaryFolder.newFolder("rejected"))
                .importImage("secret.txt", "text/plain", byteArrayOf(1))
        }
}
