package com.sowerrrt.dayloom.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.VaultEntry
import com.sowerrrt.dayloom.core.security.VaultCipher
import com.sowerrrt.dayloom.core.storage.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

enum class VaultError {
    AUTHENTICATION_UNAVAILABLE,
    AUTHENTICATION_FAILED,
    SESSION_EXPIRED,
    STORAGE_FAILURE,
}

data class VaultUiState(
    val isLocked: Boolean = true,
    val hasVault: Boolean = false,
    val entries: List<VaultEntry> = emptyList(),
    val selectedEntryId: EntityId? = null,
    val query: String = "",
    val favoriteOnly: Boolean = false,
    val isBusy: Boolean = false,
    val error: VaultError? = null,
    val authenticationRequest: Long = 0,
) {
    val selectedEntry: VaultEntry?
        get() = entries.firstOrNull { it.id == selectedEntryId }

    val filteredEntries: List<VaultEntry>
        get() {
            val normalizedQuery = query.trim()
            return entries.filter { entry ->
                (!favoriteOnly || entry.favorite) &&
                    (
                        normalizedQuery.isEmpty() ||
                            listOf(entry.title, entry.username, entry.website, entry.category)
                                .any { it.contains(normalizedQuery, ignoreCase = true) }
                    )
            }
        }
}

@HiltViewModel
class VaultViewModel
    @Inject
    constructor(
        private val repository: VaultRepository,
        private val cipher: VaultCipher,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(VaultUiState())
        val uiState: StateFlow<VaultUiState> = mutableState.asStateFlow()
        private var autoLockJob: Job? = null

        init {
            viewModelScope.launch {
                mutableState.update { it.copy(hasVault = repository.hasVault()) }
            }
        }

        fun prepareAuthentication() {
            viewModelScope.launch {
                mutableState.update { it.copy(isBusy = true, error = null) }
                cipher.ensureKey().fold(
                    onSuccess = {
                        mutableState.update { state ->
                            state.copy(
                                isBusy = false,
                                authenticationRequest = state.authenticationRequest + 1,
                            )
                        }
                    },
                    onFailure = {
                        mutableState.update {
                            it.copy(isBusy = false, error = VaultError.AUTHENTICATION_UNAVAILABLE)
                        }
                    },
                )
            }
        }

        fun authenticationUnavailable() {
            mutableState.update { it.copy(isBusy = false, error = VaultError.AUTHENTICATION_UNAVAILABLE) }
        }

        fun authenticationFailed() {
            mutableState.update { it.copy(isBusy = false, error = VaultError.AUTHENTICATION_FAILED) }
        }

        fun authenticationCancelled() {
            mutableState.update { it.copy(isBusy = false) }
        }

        fun unlockAfterAuthentication() {
            viewModelScope.launch {
                mutableState.update { it.copy(isBusy = true, error = null) }
                runCatching { repository.loadEntries() }.fold(
                    onSuccess = { entries ->
                        mutableState.update {
                            it.copy(
                                isLocked = false,
                                hasVault = it.hasVault || entries.isNotEmpty(),
                                entries = entries,
                                isBusy = false,
                                error = null,
                            )
                        }
                        resetAutoLock()
                    },
                    onFailure = {
                        mutableState.update {
                            it.copy(
                                isLocked = true,
                                entries = emptyList(),
                                isBusy = false,
                                error = VaultError.STORAGE_FAILURE,
                            )
                        }
                    },
                )
            }
        }

        fun lock() {
            autoLockJob?.cancel()
            mutableState.update {
                it.copy(
                    isLocked = true,
                    entries = emptyList(),
                    selectedEntryId = null,
                    query = "",
                    favoriteOnly = false,
                    isBusy = false,
                )
            }
        }

        fun touch() {
            if (!mutableState.value.isLocked) resetAutoLock()
        }

        fun setQuery(query: String) {
            mutableState.update { it.copy(query = query) }
            touch()
        }

        fun toggleFavoriteFilter() {
            mutableState.update { it.copy(favoriteOnly = !it.favoriteOnly) }
            touch()
        }

        fun openEntry(id: EntityId) {
            mutableState.update { it.copy(selectedEntryId = id) }
            touch()
        }

        fun closeEntry() {
            mutableState.update { it.copy(selectedEntryId = null) }
            touch()
        }

        fun saveEntry(
            id: EntityId?,
            title: String,
            username: String,
            password: String,
            website: String,
            note: String,
            category: String,
        ) {
            val cleanTitle = title.trim()
            if (cleanTitle.isEmpty() || password.isEmpty()) return
            mutateEntries { current ->
                val now = System.currentTimeMillis()
                val existing = id?.let { entryId -> current.firstOrNull { it.id == entryId } }
                val updated =
                    VaultEntry(
                        id = existing?.id ?: EntityId.random(),
                        title = cleanTitle,
                        username = username.trim(),
                        password = password,
                        website = website.trim(),
                        note = note.trim(),
                        category = category.trim(),
                        favorite = existing?.favorite ?: false,
                        createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                    )
                current.filterNot { it.id == updated.id } + updated
            }
        }

        fun toggleFavorite(id: EntityId) {
            mutateEntries { entries ->
                entries.map { entry ->
                    if (entry.id == id) {
                        entry.copy(favorite = !entry.favorite, updatedAtEpochMillis = System.currentTimeMillis())
                    } else {
                        entry
                    }
                }
            }
        }

        fun deleteEntry(id: EntityId) {
            mutateEntries { entries -> entries.filterNot { it.id == id } }
            mutableState.update { it.copy(selectedEntryId = null) }
        }

        fun deleteVault() {
            viewModelScope.launch {
                mutableState.update { it.copy(isBusy = true, error = null) }
                runCatching {
                    repository.deleteVault()
                    cipher.destroyKey().getOrThrow()
                }.fold(
                    onSuccess = {
                        autoLockJob?.cancel()
                        mutableState.value = VaultUiState()
                    },
                    onFailure = {
                        mutableState.update { it.copy(isBusy = false, error = VaultError.STORAGE_FAILURE) }
                    },
                )
            }
        }

        fun clearError() {
            mutableState.update { it.copy(error = null) }
        }

        private fun mutateEntries(transform: (List<VaultEntry>) -> List<VaultEntry>) {
            val state = mutableState.value
            if (state.isLocked || state.isBusy) return
            viewModelScope.launch {
                mutableState.update { it.copy(isBusy = true, error = null) }
                val updated = transform(mutableState.value.entries).sortedByDescending(VaultEntry::updatedAtEpochMillis)
                runCatching { repository.saveEntries(updated) }.fold(
                    onSuccess = {
                        mutableState.update {
                            it.copy(entries = updated, hasVault = true, isBusy = false)
                        }
                        resetAutoLock()
                    },
                    onFailure = {
                        autoLockJob?.cancel()
                        mutableState.update {
                            it.copy(
                                isLocked = true,
                                entries = emptyList(),
                                selectedEntryId = null,
                                isBusy = false,
                                error = VaultError.SESSION_EXPIRED,
                            )
                        }
                    },
                )
            }
        }

        private fun resetAutoLock() {
            autoLockJob?.cancel()
            autoLockJob =
                viewModelScope.launch {
                    delay(AUTO_LOCK_MILLIS)
                    lock()
                }
        }

        private companion object {
            const val AUTO_LOCK_MILLIS = 60_000L
        }
    }

internal fun generatePassword(
    length: Int = 20,
    random: SecureRandom = SecureRandom(),
): String {
    val safeLength = length.coerceIn(12, 64)
    val groups =
        listOf(
            "abcdefghijkmnopqrstuvwxyz",
            "ABCDEFGHJKLMNPQRSTUVWXYZ",
            "23456789",
            "!@#\$%&*+-_=.?",
        )
    val all = groups.joinToString("")
    val characters = groups.map { it[random.nextInt(it.length)] }.toMutableList()
    repeat(safeLength - characters.size) { characters += all[random.nextInt(all.length)] }
    for (index in characters.lastIndex downTo 1) {
        val target = random.nextInt(index + 1)
        val swap = characters[index]
        characters[index] = characters[target]
        characters[target] = swap
    }
    return characters.joinToString("")
}
