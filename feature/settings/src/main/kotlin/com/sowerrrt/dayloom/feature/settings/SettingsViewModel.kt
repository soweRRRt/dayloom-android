package com.sowerrrt.dayloom.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.storage.DataTransferRepository
import com.sowerrrt.dayloom.core.storage.DemoContent
import com.sowerrrt.dayloom.core.storage.DemoContentRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isAddingExamples: Boolean = false,
    val demoFeedback: DemoFeedback? = null,
    val dataOperation: DataOperation? = null,
    val dataFeedback: DataFeedback? = null,
)

enum class DemoFeedback {
    ADDED,
    ALREADY_PRESENT,
    ERROR,
}

enum class DataOperation {
    EXPORT,
    IMPORT,
    CLEAR,
}

enum class DataFeedback {
    EXPORTED,
    IMPORTED,
    CLEARED,
    ERROR,
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val demoContentRepository: DemoContentRepository,
        private val dataTransferRepository: DataTransferRepository,
    ) : ViewModel() {
        private val demoState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> =
            combine(repository.settings, demoState) { settings, demo -> demo.copy(settings = settings) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setTheme(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }

        fun setLanguage(
            value: AppLanguage,
            onPersisted: () -> Unit,
        ) = viewModelScope.launch {
            repository.setAppLanguage(value)
            onPersisted()
        }

        fun setAccent(value: AccentPalette) = viewModelScope.launch { repository.setAccentPalette(value) }

        fun setStartDestination(value: StartDestination) =
            viewModelScope.launch { repository.setStartDestination(value) }

        fun setBottomSections(value: List<BottomSection>) =
            viewModelScope.launch { repository.setBottomSections(value) }

        fun setHomeSections(value: List<HomeSection>) = viewModelScope.launch { repository.setHomeSections(value) }

        fun setAutomaticUpdateChecks(enabled: Boolean) =
            viewModelScope.launch { repository.setAutomaticUpdateChecks(enabled) }

        fun setWholeAppLock(enabled: Boolean) = viewModelScope.launch { repository.setWholeAppLock(enabled) }

        fun addExamples(
            content: DemoContent,
            todayEpochDay: Long,
        ) {
            if (demoState.value.isAddingExamples) return
            viewModelScope.launch {
                demoState.update { it.copy(isAddingExamples = true, demoFeedback = null) }
                runCatching { demoContentRepository.seedMissing(content, todayEpochDay) }.fold(
                    onSuccess = { result ->
                        demoState.update {
                            it.copy(
                                isAddingExamples = false,
                                demoFeedback =
                                    if (result.totalAdded > 0) {
                                        DemoFeedback.ADDED
                                    } else {
                                        DemoFeedback.ALREADY_PRESENT
                                    },
                            )
                        }
                    },
                    onFailure = {
                        demoState.update { it.copy(isAddingExamples = false, demoFeedback = DemoFeedback.ERROR) }
                    },
                )
            }
        }

        fun exportData(uri: Uri) =
            runDataOperation(DataOperation.EXPORT) {
                dataTransferRepository.exportTo(uri)
                DataFeedback.EXPORTED
            }

        fun importData(
            uri: Uri,
            onImported: () -> Unit,
        ) = runDataOperation(DataOperation.IMPORT, onImported) {
            dataTransferRepository.importFrom(uri)
            DataFeedback.IMPORTED
        }

        fun clearAllData(onCleared: () -> Unit) =
            runDataOperation(DataOperation.CLEAR, onCleared) {
                dataTransferRepository.clearAll()
                DataFeedback.CLEARED
            }

        fun consumeDataFeedback() {
            demoState.update { it.copy(dataFeedback = null) }
        }

        private fun runDataOperation(
            operation: DataOperation,
            onSuccess: () -> Unit = {},
            block: suspend () -> DataFeedback,
        ) {
            if (demoState.value.dataOperation != null) return
            viewModelScope.launch {
                demoState.update { it.copy(dataOperation = operation, dataFeedback = null) }
                runCatching { block() }.fold(
                    onSuccess = { feedback ->
                        demoState.update { it.copy(dataOperation = null, dataFeedback = feedback) }
                        onSuccess()
                    },
                    onFailure = {
                        demoState.update { it.copy(dataOperation = null, dataFeedback = DataFeedback.ERROR) }
                    },
                )
            }
        }
    }
