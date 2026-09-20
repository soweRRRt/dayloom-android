package com.sowerrrt.dayloom

import app.cash.turbine.test
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `whole app protection locks authenticates and resets after background`() =
        runTest(dispatcher) {
            val repository = FakeRootSettingsRepository()
            val viewModel = RootViewModel(repository) {}

            viewModel.uiState.test {
                awaitItem()
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().isAppUnlocked)

                repository.setWholeAppLock(true)
                advanceUntilIdle()
                assertFalse(expectMostRecentItem().isAppUnlocked)

                viewModel.authenticationSucceeded()
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().isAppUnlocked)

                viewModel.lock()
                advanceUntilIdle()
                assertFalse(expectMostRecentItem().isAppUnlocked)

                viewModel.authenticationUnavailable()
                advanceUntilIdle()
                assertEquals(AppLockError.UNAVAILABLE, expectMostRecentItem().lockError)

                viewModel.disableUnavailableLock()
                advanceUntilIdle()
                val unlocked = expectMostRecentItem()
                assertFalse(unlocked.settings!!.lockWholeApp)
                assertTrue(unlocked.isAppUnlocked)
            }
        }

    @Test
    fun `supported app languages map to locale tags`() {
        assertEquals("", AppLanguage.SYSTEM.languageTags)
        assertEquals("ru", AppLanguage.RUSSIAN.languageTags)
        assertEquals("en", AppLanguage.ENGLISH.languageTags)
    }

    @Test
    fun `adaptive navigation uses rail only on wide screens`() {
        assertFalse(isWideLayout(360f))
        assertFalse(isWideLayout(719f))
        assertTrue(isWideLayout(720f))
        assertTrue(isWideLayout(1_024f))
    }
}

private class FakeRootSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())
    override val settings = mutableSettings

    override suspend fun setThemeMode(value: ThemeMode) {
        mutableSettings.value = mutableSettings.value.copy(themeMode = value)
    }

    override suspend fun setAppLanguage(value: AppLanguage) = Unit

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

    override suspend fun setHomeSections(sections: List<HomeSection>) = Unit

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
