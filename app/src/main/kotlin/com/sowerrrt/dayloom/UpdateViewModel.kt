package com.sowerrrt.dayloom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import com.sowerrrt.dayloom.core.updates.SemVer
import com.sowerrrt.dayloom.core.updates.UpdateCheckResult
import com.sowerrrt.dayloom.core.updates.UpdateInfo
import com.sowerrrt.dayloom.core.updates.UpdateSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    val feedback: UpdateFeedback? = null,
)

enum class UpdateFeedback {
    UP_TO_DATE,
    UNAVAILABLE,
}

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val source: UpdateSource,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(UpdateUiState())
        val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
        private val currentVersion = requireNotNull(SemVer.parseOrNull(BuildConfig.VERSION_NAME))

        init {
            viewModelScope.launch {
                val settings = settingsRepository.settings.first()
                val elapsed = System.currentTimeMillis() - (settings.lastUpdateCheckEpochMillis ?: 0L)
                if (settings.automaticUpdateChecks && elapsed >= CHECK_INTERVAL_MILLIS) check(showFeedback = false)
            }
        }

        fun checkManually() {
            viewModelScope.launch { check(showFeedback = true) }
        }

        fun dismissAvailable() = mutableState.update { it.copy(available = null) }

        fun consumeFeedback() = mutableState.update { it.copy(feedback = null) }

        private suspend fun check(showFeedback: Boolean) {
            if (mutableState.value.checking) return
            mutableState.update { it.copy(checking = true, feedback = null) }
            val result = source.check(currentVersion)
            settingsRepository.markUpdateChecked(System.currentTimeMillis())
            mutableState.update { previous ->
                when (result) {
                    is UpdateCheckResult.Available -> previous.copy(checking = false, available = result.info)
                    UpdateCheckResult.UpToDate,
                    UpdateCheckResult.NoRelease,
                    ->
                        previous.copy(
                            checking = false,
                            feedback = if (showFeedback) UpdateFeedback.UP_TO_DATE else null,
                        )
                    is UpdateCheckResult.Unavailable ->
                        previous.copy(
                            checking = false,
                            feedback = if (showFeedback) UpdateFeedback.UNAVAILABLE else null,
                        )
                }
            }
        }

        private companion object {
            const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1_000L
        }
    }
