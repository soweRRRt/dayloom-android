package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.VaultEntry
import com.sowerrrt.dayloom.core.security.VaultCipher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedFileVaultRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `secrets are encrypted on disk and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("vault")
            val repository = EncryptedFileVaultRepository(directory, TestVaultCipher())
            val entry = sampleEntry("secret-password")

            repository.saveEntries(listOf(entry))

            val diskBytes = directory.resolve("vault.enc").readBytes()
            assertFalse(diskBytes.toString(Charsets.UTF_8).contains("secret-password"))
            assertEquals(entry, repository.loadEntries().single())
            assertTrue(repository.hasVault())
        }

    @Test
    fun `backup restores previous encrypted version after corruption`() =
        runTest {
            val directory = temporaryFolder.newFolder("recovery")
            val repository = EncryptedFileVaultRepository(directory, TestVaultCipher())
            val first = sampleEntry("first")
            repository.saveEntries(listOf(first))
            repository.saveEntries(listOf(sampleEntry("second")))
            directory.resolve("vault.enc").writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(first, repository.loadEntries().single())
        }

    @Test
    fun `delete removes encrypted files`() =
        runTest {
            val directory = temporaryFolder.newFolder("delete")
            val repository = EncryptedFileVaultRepository(directory, TestVaultCipher())
            repository.saveEntries(listOf(sampleEntry("temporary")))

            repository.deleteVault()

            assertFalse(repository.hasVault())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    private fun sampleEntry(password: String) =
        VaultEntry(
            id = EntityId("entry-1"),
            title = "Mail",
            username = "person@example.com",
            password = password,
            website = "https://example.com",
            note = "Private",
            category = "Personal",
            createdAtEpochMillis = 1L,
        )
}

private class TestVaultCipher : VaultCipher {
    override suspend fun ensureKey(): Result<Unit> = Result.success(Unit)

    override suspend fun encrypt(plaintext: ByteArray): Result<ByteArray> =
        Result.success(byteArrayOf(MARKER) + plaintext.map { (it.toInt() xor MASK).toByte() })

    override suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray> =
        runCatching {
            require(ciphertext.firstOrNull() == MARKER)
            ciphertext.drop(1).map { (it.toInt() xor MASK).toByte() }.toByteArray()
        }

    override suspend fun destroyKey(): Result<Unit> = Result.success(Unit)

    companion object {
        private const val MARKER: Byte = 42
        private const val MASK = 0x5A
    }
}
