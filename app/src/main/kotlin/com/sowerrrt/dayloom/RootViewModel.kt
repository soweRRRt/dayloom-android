package com.sowerrrt.dayloom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RootUiState(
    val settings: AppSettings? = null,
)

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        repository: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<RootUiState> =
            repository.settings
                .map { RootUiState(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())
    }
