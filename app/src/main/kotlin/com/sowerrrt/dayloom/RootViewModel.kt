package com.sowerrrt.dayloom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootUiState(
    val settings: AppSettings? = null,
    val isAppUnlocked: Boolean = false,
    val authenticationRequest: Long = 0L,
    val lockError: AppLockError? = null,
)

enum class AppLockError {
    UNAVAILABLE,
    FAILED,
}

private data class AppLockSession(
    val unlocked: Boolean = false,
    val authenticationRequest: Long = 0L,
    val error: AppLockError? = null,
)

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) : ViewModel() {
        private val session = MutableStateFlow(AppLockSession())
        private var previousWholeAppLock = false

        val uiState: StateFlow<RootUiState> =
            combine(
                repository.settings.onEach { settings ->
                    if (settings.lockWholeApp && !previousWholeAppLock) {
                        session.update { AppLockSession() }
                    }
                    previousWholeAppLock = settings.lockWholeApp
                },
                session,
            ) { settings, currentSession ->
                RootUiState(
                    settings = settings,
                    isAppUnlocked = !settings.lockWholeApp || currentSession.unlocked,
                    authenticationRequest = currentSession.authenticationRequest,
                    lockError = currentSession.error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())

        fun requestAuthentication() {
            session.update {
                it.copy(
                    authenticationRequest = it.authenticationRequest + 1,
                    error = null,
                )
            }
        }

        fun authenticationSucceeded() {
            session.update { it.copy(unlocked = true, error = null) }
        }

        fun authenticationCancelled() {
            session.update { it.copy(unlocked = false, error = null) }
        }

        fun authenticationFailed() {
            session.update { it.copy(unlocked = false, error = AppLockError.FAILED) }
        }

        fun authenticationUnavailable() {
            session.update { it.copy(unlocked = false, error = AppLockError.UNAVAILABLE) }
        }

        fun lock() {
            session.update { it.copy(unlocked = false, error = null) }
        }

        fun disableUnavailableLock() {
            viewModelScope.launch { repository.setWholeAppLock(false) }
        }
    }
