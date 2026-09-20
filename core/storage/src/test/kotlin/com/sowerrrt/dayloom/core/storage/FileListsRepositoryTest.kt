package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.ListKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileListsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `list and items can be edited reordered completed and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("lists")
            val ids = ArrayDeque(listOf(EntityId("list-1"), EntityId("item-1"), EntityId("item-2")))
            var now = 10L
            val repository =
                FileListsRepository(
                    directory = directory,
                    clock = { now++ },
                    idFactory = { ids.removeFirst() },
                )

            repository.createList("  Weekend trip  ", ListKind.PACKING, "Expeditions")
            repository.addItem(EntityId("list-1"), "  Passport  ", "1", "Top drawer")
            repository.addItem(EntityId("list-1"), "Socks", "3 pairs", "")
            repository.toggleItem(EntityId("list-1"), EntityId("item-1"))
            repository.moveItem(EntityId("list-1"), EntityId("item-2"), -1)
            repository.updateItem(EntityId("list-1"), EntityId("item-2"), "Warm socks", "4 pairs", "Wool")
            repository.updateList(EntityId("list-1"), "Mountain trip", ListKind.GENERAL, "Adventure kit")

            val restored = FileListsRepository(directory).loadLists().single()
            assertEquals("Mountain trip", restored.title)
            assertEquals(ListKind.GENERAL, restored.kind)
            assertEquals("Adventure kit", restored.customKind)
            assertEquals(listOf("Warm socks", "Passport"), restored.items.map { it.title })
            assertEquals("4 pairs", restored.items.first().quantity)
            assertEquals("Wool", restored.items.first().note)
            assertFalse(restored.items.first().completed)
            assertTrue(restored.items.last().completed)
            assertEquals(listOf(0, 1), restored.items.map { it.order })
        }

    @Test
    fun `items and lists can be deleted`() =
        runTest {
            val ids = ArrayDeque(listOf(EntityId("list-2"), EntityId("item-3")))
            val repository =
                FileListsRepository(
                    directory = temporaryFolder.newFolder("delete"),
                    idFactory = { ids.removeFirst() },
                )

            repository.createList("Temporary", ListKind.GENERAL)
            repository.addItem(EntityId("list-2"), "Temporary item", "", "")
            val withoutItem = repository.deleteItem(EntityId("list-2"), EntityId("item-3"))
            assertTrue(withoutItem.single().items.isEmpty())
            assertTrue(repository.deleteList(EntityId("list-2")).isEmpty())
        }

    @Test
    fun `lists support duplicate bulk actions archive restore and individual expiry`() =
        runTest {
            val week = 7L * 24 * 60 * 60 * 1_000
            var now = 1_000L
            val ids =
                ArrayDeque(
                    listOf(
                        EntityId("source"),
                        EntityId("target"),
                        EntityId("one"),
                        EntityId("two"),
                        EntityId("copy"),
                        EntityId("copy-one"),
                        EntityId("copy-two"),
                    ),
                )
            val repository = FileListsRepository(temporaryFolder.newFolder("advanced"), { now }, { ids.removeFirst() })
            repository.createList("Source", ListKind.GENERAL)
            repository.createList("Target", ListKind.GENERAL)
            repository.addItem(EntityId("source"), "One", "", "")
            repository.addItem(EntityId("source"), "Two", "", "")
            repository.setItemsCompleted(EntityId("source"), setOf(EntityId("one")), true)
            repository.moveItems(EntityId("source"), EntityId("target"), setOf(EntityId("one")))
            assertEquals(
                listOf("One"),
                repository
                    .loadLists()
                    .first { it.id.value == "target" }
                    .items
                    .map { it.title },
            )
            assertTrue(
                repository
                    .loadLists()
                    .first { it.id.value == "target" }
                    .items
                    .single()
                    .completed,
            )

            repository.duplicateList(EntityId("source"))
            assertTrue(repository.loadLists().any { it.title.contains("copy", true) })
            repository.archiveList(EntityId("source"))
            assertEquals("Source", repository.loadArchivedLists().single().title)
            repository.restoreList(EntityId("source"))
            repository.archiveList(EntityId("source"))
            now += week
            assertTrue(repository.loadArchivedLists().isEmpty())
        }
}
