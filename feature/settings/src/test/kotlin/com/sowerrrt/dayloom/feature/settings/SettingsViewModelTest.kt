package com.sowerrrt.dayloom.feature.settings

import android.net.Uri
import app.cash.turbine.test
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.storage.DataTransferRepository
import com.sowerrrt.dayloom.core.storage.DataTransferSummary
import com.sowerrrt.dayloom.core.storage.DemoContent
import com.sowerrrt.dayloom.core.storage.DemoContentRepository
import com.sowerrrt.dayloom.core.storage.DemoSeedResult
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
            val viewModel = SettingsViewModel(repository, FakeDemoContentRepository(), FakeDataTransferRepository())

            viewModel.uiState.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem().settings.themeMode)
                viewModel.setTheme(ThemeMode.DARK)
                assertEquals(ThemeMode.DARK, awaitItem().settings.themeMode)
            }
        }

    @Test
    fun `language selection is persisted before apply callback`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository, FakeDemoContentRepository(), FakeDataTransferRepository())
            var appliedLanguage: AppLanguage? = null

            viewModel.uiState.test {
                awaitItem()
                viewModel.setLanguage(AppLanguage.RUSSIAN) { appliedLanguage = AppLanguage.RUSSIAN }
                assertEquals(AppLanguage.RUSSIAN, awaitItem().settings.appLanguage)
                assertEquals(AppLanguage.RUSSIAN, appliedLanguage)
            }
        }

    @Test
    fun `example content reports added and repeated states`() =
        runTest(dispatcher) {
            val demoRepository = FakeDemoContentRepository()
            val viewModel = SettingsViewModel(FakeSettingsRepository(), demoRepository, FakeDataTransferRepository())
            val emptyContent = DemoContent(emptyList(), emptyList(), emptyList(), emptyList())

            viewModel.uiState.test {
                awaitItem()
                viewModel.addExamples(emptyContent, 20_000)
                advanceUntilIdle()
                assertEquals(DemoFeedback.ADDED, expectMostRecentItem().demoFeedback)
                viewModel.addExamples(emptyContent, 20_000)
                advanceUntilIdle()
                assertEquals(DemoFeedback.ALREADY_PRESENT, expectMostRecentItem().demoFeedback)
            }
        }

    @Test
    fun `whole app lock selection is persisted`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository, FakeDemoContentRepository(), FakeDataTransferRepository())

            viewModel.uiState.test {
                assertEquals(false, awaitItem().settings.lockWholeApp)
                viewModel.setWholeAppLock(true)
                assertEquals(true, awaitItem().settings.lockWholeApp)
            }
        }

    @Test
    fun `bottom navigation customization is reflected in state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository, FakeDemoContentRepository(), FakeDataTransferRepository())
            val sections = listOf(BottomSection.PLANNER, BottomSection.HABITS, BottomSection.MORE)

            viewModel.uiState.test {
                awaitItem()
                viewModel.setBottomSections(sections)
                assertEquals(sections, awaitItem().settings.bottomSections)
            }
        }

    @Test
    fun `home dashboard customization is reflected in state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository, FakeDemoContentRepository(), FakeDataTransferRepository())
            val sections = listOf(HomeSection.WISHLIST, HomeSection.HABITS)

            viewModel.uiState.test {
                awaitItem()
                viewModel.setHomeSections(sections)
                assertEquals(sections, awaitItem().settings.homeSections)
            }
        }

    @Test
    fun `clear all data reports completion after repository succeeds`() =
        runTest(dispatcher) {
            val transferRepository = FakeDataTransferRepository()
            val viewModel =
                SettingsViewModel(
                    FakeSettingsRepository(),
                    FakeDemoContentRepository(),
                    transferRepository,
                )
            var callbackInvoked = false

            viewModel.uiState.test {
                awaitItem()
                viewModel.clearAllData { callbackInvoked = true }
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals(DataFeedback.CLEARED, state.dataFeedback)
                assertEquals(true, transferRepository.cleared)
                assertEquals(true, callbackInvoked)
            }
        }
}

private class FakeDataTransferRepository : DataTransferRepository {
    var exported = false
    var imported = false
    var cleared = false

    override suspend fun exportTo(uri: Uri): DataTransferSummary {
        exported = true
        return DataTransferSummary(1, 2, 3, 4, 5)
    }

    override suspend fun importFrom(uri: Uri): DataTransferSummary {
        imported = true
        return DataTransferSummary(1, 2, 3, 4, 5)
    }

    override suspend fun clearAll() {
        cleared = true
    }
}

private class FakeDemoContentRepository : DemoContentRepository {
    private var first = true

    override suspend fun seedMissing(
        content: DemoContent,
        todayEpochDay: Long,
    ): DemoSeedResult =
        if (first) {
            first = false
            DemoSeedResult(habitsAdded = 1)
        } else {
            DemoSeedResult()
        }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())
    override val settings = mutableSettings

    override suspend fun setThemeMode(value: ThemeMode) {
        mutableSettings.value = mutableSettings.value.copy(themeMode = value)
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        mutableSettings.value = mutableSettings.value.copy(appLanguage = value)
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

    override suspend fun setWholeAppLock(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(lockWholeApp = enabled)
    }

    override suspend fun setBottomSections(sections: List<BottomSection>) {
        mutableSettings.value = mutableSettings.value.copy(bottomSections = sections)
    }

    override suspend fun setHomeSections(sections: List<HomeSection>) {
        mutableSettings.value = mutableSettings.value.copy(homeSections = sections)
    }

    override suspend fun addPreset(
        type: PresetType,
        title: String,
    ) = Unit

    override suspend fun removePreset(
        type: PresetType,
        title: String,
    ) = Unit

    override suspend fun markUpdateChecked(epochMillis: Long) {
        mutableSettings.value = mutableSettings.value.copy(lastUpdateCheckEpochMillis = epochMillis)
    }
}
