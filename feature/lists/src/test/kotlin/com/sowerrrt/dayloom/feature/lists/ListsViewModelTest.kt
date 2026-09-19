package com.sowerrrt.dayloom.feature.lists

import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `list is selected after creation and item completion updates totals`() =
        runTest(dispatcher) {
            val repository = FakeListsRepository()
            val viewModel = ListsViewModel(repository, FakeSettingsRepository())
            runCurrent()

            assertFalse(viewModel.uiState.value.isLoading)
            viewModel.createList("Groceries", ListKind.SHOPPING)
            runCurrent()
            assertEquals(
                "Groceries",
                viewModel.uiState.value.selectedList
                    ?.title,
            )

            viewModel.addItem(EntityId("list-1"), "Milk", "2", "Oat")
            runCurrent()
            assertEquals(1, viewModel.uiState.value.totalItems)
            assertEquals(
                "2",
                viewModel.uiState.value.selectedList
                    ?.items
                    ?.single()
                    ?.quantity,
            )

            viewModel.toggleItem(EntityId("list-1"), EntityId("item-1"))
            runCurrent()
            assertEquals(1, viewModel.uiState.value.completedItems)
        }

    @Test
    fun `full item preset adds quantity and note in one action`() =
        runTest(dispatcher) {
            val repository = FakeListsRepository()
            val viewModel = ListsViewModel(repository, FakeSettingsRepository())
            runCurrent()
            viewModel.createList("Groceries", ListKind.SHOPPING)
            runCurrent()
            viewModel.saveItemPreset("Coffee", "1 pack", "Whole beans")
            runCurrent()

            viewModel.addItemFromPreset(
                EntityId("list-1"),
                viewModel.uiState.value.itemPresets
                    .single(),
            )
            runCurrent()

            val item =
                viewModel.uiState.value.selectedList
                    ?.items
                    ?.single()
            assertEquals("Coffee", item?.title)
            assertEquals("1 pack", item?.quantity)
            assertEquals("Whole beans", item?.note)
        }
}

private class FakeListsRepository : ListsRepository {
    private var lists = emptyList<DayList>()

    override suspend fun loadLists(): List<DayList> = lists

    override suspend fun createList(
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> {
        lists = listOf(DayList(EntityId("list-1"), title, kind, customKind = customKind, createdAtEpochMillis = 1L))
        return lists
    }

    override suspend fun updateList(
        id: EntityId,
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> = updateList(id) { it.copy(title = title, kind = kind, customKind = customKind) }

    override suspend fun deleteList(id: EntityId): List<DayList> {
        lists = lists.filterNot { it.id == id }
        return lists
    }

    override suspend fun addItem(
        listId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> =
        updateList(listId) { list ->
            list.copy(
                items =
                    list.items +
                        DayListItem(
                            id = EntityId("item-1"),
                            title = title,
                            quantity = quantity,
                            note = note,
                            order = list.items.size,
                            createdAtEpochMillis = 1L,
                        ),
            )
        }

    override suspend fun updateItem(
        listId: EntityId,
        itemId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> = updateItem(listId, itemId) { it.copy(title = title, quantity = quantity, note = note) }

    override suspend fun toggleItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> = updateItem(listId, itemId) { it.copy(completed = !it.completed) }

    override suspend fun deleteItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> = updateList(listId) { list -> list.copy(items = list.items.filterNot { it.id == itemId }) }

    override suspend fun moveItem(
        listId: EntityId,
        itemId: EntityId,
        offset: Int,
    ): List<DayList> = lists

    private fun updateList(
        id: EntityId,
        transform: (DayList) -> DayList,
    ): List<DayList> {
        lists = lists.map { if (it.id == id) transform(it) else it }
        return lists
    }

    private fun updateItem(
        listId: EntityId,
        itemId: EntityId,
        transform: (DayListItem) -> DayListItem,
    ): List<DayList> =
        updateList(listId) { list ->
            list.copy(items = list.items.map { if (it.id == itemId) transform(it) else it })
        }
}

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(value: ThemeMode) = Unit

    override suspend fun setAppLanguage(value: AppLanguage) = Unit

    override suspend fun setAccentPalette(value: AccentPalette) = Unit

    override suspend fun setStartDestination(value: StartDestination) = Unit

    override suspend fun setAutomaticUpdateChecks(enabled: Boolean) = Unit

    override suspend fun setWholeAppLock(enabled: Boolean) = Unit

    override suspend fun setBottomSections(sections: List<BottomSection>) = Unit

    override suspend fun setHomeSections(sections: List<HomeSection>) = Unit

    override suspend fun addPreset(
        type: PresetType,
        title: String,
    ) {
        state.value =
            when (type) {
                PresetType.HABIT -> state.value.copy(habitPresets = state.value.habitPresets + title)
                PresetType.PLAN -> state.value.copy(planPresets = state.value.planPresets + title)
                PresetType.LIST_ITEM -> state.value.copy(listItemPresets = state.value.listItemPresets + title)
            }
    }

    override suspend fun removePreset(
        type: PresetType,
        title: String,
    ) {
        state.value =
            when (type) {
                PresetType.HABIT -> state.value.copy(habitPresets = state.value.habitPresets - title)
                PresetType.PLAN -> state.value.copy(planPresets = state.value.planPresets - title)
                PresetType.LIST_ITEM -> state.value.copy(listItemPresets = state.value.listItemPresets - title)
            }
    }

    override suspend fun markUpdateChecked(epochMillis: Long) = Unit
}
