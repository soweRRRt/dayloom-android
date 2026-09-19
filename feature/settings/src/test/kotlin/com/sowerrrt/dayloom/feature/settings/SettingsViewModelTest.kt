package com.sowerrrt.dayloom.feature.settings

import app.cash.turbine.test
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `theme selection is persisted and reflected in state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.uiState.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem().settings.themeMode)
                viewModel.setTheme(ThemeMode.DARK)
                assertEquals(ThemeMode.DARK, awaitItem().settings.themeMode)
            }
        }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())
    override val settings = mutableSettings

    override suspend fun setThemeMode(value: ThemeMode) {
        mutableSettings.value = mutableSettings.value.copy(themeMode = value)
    }

    override suspend fun setAccentPalette(value: AccentPalette) {
        mutableSettings.value = mutableSettings.value.copy(accentPalette = value)
    }

    override suspend fun setStartDestination(value: StartDestination) {
        mutableSettings.value = mutableSettings.value.copy(startDestination = value)
    }

    override suspend fun setAutomaticUpdateChecks(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(automaticUpdateChecks = enabled)
    }

    override suspend fun markUpdateChecked(epochMillis: Long) {
        mutableSettings.value = mutableSettings.value.copy(lastUpdateCheckEpochMillis = epochMillis)
    }
}
