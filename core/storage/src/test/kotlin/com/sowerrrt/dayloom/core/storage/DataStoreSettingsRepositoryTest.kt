package com.sowerrrt.dayloom.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.PresetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `bottom navigation order visibility and required more section are persisted`() =
        runTest {
            val repository = repository("navigation")

            repository.setBottomSections(
                listOf(BottomSection.PLANNER, BottomSection.HABITS, BottomSection.PLANNER),
            )

            assertEquals(
                listOf(BottomSection.PLANNER, BottomSection.HABITS, BottomSection.MORE),
                repository.settings.first().bottomSections,
            )
        }

    @Test
    fun `presets are trimmed and persisted per type`() =
        runTest {
            val habitRepository = repository("habit-preset")
            habitRepository.addPreset(PresetType.HABIT, "  Morning walk  ")
            assertTrue("Morning walk" in habitRepository.settings.first().habitPresets)

            val planRepository = repository("plan-preset")
            planRepository.addPreset(PresetType.PLAN, "Call family")
            assertTrue("Call family" in planRepository.settings.first().planPresets)

            val itemRepository = repository("item-preset")
            itemRepository.addPreset(PresetType.LIST_ITEM, "Milk")
            assertTrue("Milk" in itemRepository.settings.first().listItemPresets)
        }

    private fun kotlinx.coroutines.test.TestScope.repository(name: String): DataStoreSettingsRepository =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                temporaryFolder.root.resolve("settings-$name.preferences_pb")
            },
        )
}
