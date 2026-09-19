package com.sowerrrt.dayloom.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            repository.settings
                .map(::SettingsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setTheme(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }

        fun setAccent(value: AccentPalette) = viewModelScope.launch { repository.setAccentPalette(value) }

        fun setStartDestination(value: StartDestination) =
            viewModelScope.launch { repository.setStartDestination(value) }

        fun setAutomaticUpdateChecks(enabled: Boolean) =
            viewModelScope.launch { repository.setAutomaticUpdateChecks(enabled) }
    }
