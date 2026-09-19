package com.sowerrrt.dayloom.feature.vault

import com.sowerrrt.dayloom.core.model.VaultEntry
import com.sowerrrt.dayloom.core.security.VaultCipher
import com.sowerrrt.dayloom.core.storage.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `authenticated session supports create search favorite and lock`() =
        runTest(dispatcher) {
            val repository = FakeVaultRepository()
            val viewModel = VaultViewModel(repository, FakeVaultCipher())
            runCurrent()

            viewModel.prepareAuthentication()
            runCurrent()
            assertEquals(1L, viewModel.uiState.value.authenticationRequest)
            viewModel.unlockAfterAuthentication()
            runCurrent()
            assertFalse(viewModel.uiState.value.isLocked)

            viewModel.saveEntry(null, "Mail", "person@example.com", "secret", "example.com", "", "Personal")
            runCurrent()
            val entry =
                viewModel.uiState.value.entries
                    .single()
            assertEquals("secret", entry.password)
            assertTrue(viewModel.uiState.value.hasVault)

            viewModel.toggleFavorite(entry.id)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.entries
                    .single()
                    .favorite,
            )
            viewModel.setQuery("missing")
            assertTrue(
                viewModel.uiState.value.filteredEntries
                    .isEmpty(),
            )

            viewModel.lock()
            assertTrue(viewModel.uiState.value.isLocked)
            assertTrue(
                viewModel.uiState.value.entries
                    .isEmpty(),
            )
        }

    @Test
    fun `password generator includes all character groups`() {
        val generated = generatePassword(32)
        assertEquals(32, generated.length)
        assertTrue(generated.any(Char::isLowerCase))
        assertTrue(generated.any(Char::isUpperCase))
        assertTrue(generated.any(Char::isDigit))
        assertTrue(generated.any { !it.isLetterOrDigit() })
    }
}

private class FakeVaultRepository : VaultRepository {
    private var entries = emptyList<VaultEntry>()

    override suspend fun hasVault(): Boolean = entries.isNotEmpty()

    override suspend fun loadEntries(): List<VaultEntry> = entries

    override suspend fun saveEntries(entries: List<VaultEntry>) {
        this.entries = entries
    }

    override suspend fun deleteVault() {
        entries = emptyList()
    }
}

private class FakeVaultCipher : VaultCipher {
    override suspend fun ensureKey(): Result<Unit> = Result.success(Unit)

    override suspend fun encrypt(plaintext: ByteArray): Result<ByteArray> = Result.success(plaintext)

    override suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray> = Result.success(ciphertext)

    override suspend fun destroyKey(): Result<Unit> = Result.success(Unit)
}
