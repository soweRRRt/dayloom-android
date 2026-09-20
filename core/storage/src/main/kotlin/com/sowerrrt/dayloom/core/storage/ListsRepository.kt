package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.ListsSnapshot
import java.io.File

interface ListsRepository {
    suspend fun loadLists(): List<DayList>

    suspend fun loadArchivedLists(): List<DayList> = emptyList()

    suspend fun loadAllLists(): List<DayList> = loadLists() + loadArchivedLists()

    suspend fun replaceAll(lists: List<DayList>): List<DayList> =
        error("This lists repository does not support replacement")

    suspend fun createList(
        title: String,
        kind: ListKind,
        customKind: String = "",
    ): List<DayList>

    suspend fun updateList(
        id: EntityId,
        title: String,
        kind: ListKind,
        customKind: String = "",
    ): List<DayList>

    suspend fun deleteList(id: EntityId): List<DayList>

    suspend fun archiveList(id: EntityId): List<DayList> = error("Archiving is not supported")

    suspend fun restoreList(id: EntityId): List<DayList> = error("Restoring is not supported")

    suspend fun duplicateList(id: EntityId): List<DayList> = error("Duplicating is not supported")

    suspend fun addItem(
        listId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList>

    suspend fun updateItem(
        listId: EntityId,
        itemId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList>

    suspend fun toggleItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList>

    suspend fun deleteItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList>

    suspend fun moveItem(
        listId: EntityId,
        itemId: EntityId,
        offset: Int,
    ): List<DayList>

    suspend fun setItemsCompleted(
        listId: EntityId,
        itemIds: Set<EntityId>,
        completed: Boolean,
    ): List<DayList> = error("Bulk completion is not supported")

    suspend fun deleteItems(
        listId: EntityId,
        itemIds: Set<EntityId>,
    ): List<DayList> = error("Bulk deletion is not supported")

    suspend fun moveItems(
        sourceListId: EntityId,
        targetListId: EntityId,
        itemIds: Set<EntityId>,
    ): List<DayList> = error("Moving between lists is not supported")
}

class FileListsRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
) : ListsRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "lists.json",
            payloadSerializer = ListsSnapshot.serializer(),
            currentSchemaVersion = 1,
            defaultValue = ::ListsSnapshot,
        )

    override suspend fun loadLists(): List<DayList> = readWithoutExpiredArchives().activeLists()

    override suspend fun loadArchivedLists(): List<DayList> = readWithoutExpiredArchives().archivedLists()

    override suspend fun loadAllLists(): List<DayList> = readWithoutExpiredArchives().sortedLists()

    override suspend fun replaceAll(lists: List<DayList>): List<DayList> {
        require(lists.map(DayList::id).distinct().size == lists.size) { "List IDs must be unique" }
        lists.forEach { list ->
            normalizeListTitle(list.title)
            normalizeCustomKind(list.customKind)
            require(
                list.items
                    .map(DayListItem::id)
                    .distinct()
                    .size == list.items.size,
            ) {
                "List item IDs must be unique"
            }
            list.items.forEach { item -> normalizeItemFields(item.title, item.quantity, item.note) }
        }
        val now = clock()
        val normalized =
            lists.map { list ->
                when {
                    !list.archived -> list.copy(archivedAtEpochMillis = null)
                    list.archivedAtEpochMillis == null -> list.copy(archivedAtEpochMillis = now)
                    else -> list
                }
            }
        store.write(ListsSnapshot(normalized))
        return readWithoutExpiredArchives().activeLists()
    }

    override suspend fun createList(
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> {
        val normalizedTitle = normalizeListTitle(title)
        val normalizedCustomKind = normalizeCustomKind(customKind)
        val now = clock()
        return store
            .update { snapshot ->
                snapshot.copy(
                    lists =
                        snapshot.lists +
                            DayList(
                                id = idFactory(),
                                title = normalizedTitle,
                                kind = kind,
                                customKind = normalizedCustomKind,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                            ),
                )
            }.sortedLists()
    }

    override suspend fun updateList(
        id: EntityId,
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> {
        val normalizedTitle = normalizeListTitle(title)
        return mutateList(id) { list ->
            list.copy(
                title = normalizedTitle,
                kind = kind,
                customKind = normalizeCustomKind(customKind),
                updatedAtEpochMillis = clock(),
            )
        }
    }

    override suspend fun deleteList(id: EntityId): List<DayList> =
        store
            .update { snapshot -> snapshot.copy(lists = snapshot.lists.filterNot { it.id == id }) }
            .activeLists()

    override suspend fun archiveList(id: EntityId): List<DayList> =
        mutateList(id) { list ->
            list.copy(archived = true, archivedAtEpochMillis = list.archivedAtEpochMillis ?: clock())
        }

    override suspend fun restoreList(id: EntityId): List<DayList> =
        mutateList(id) { list -> list.copy(archived = false, archivedAtEpochMillis = null) }

    override suspend fun duplicateList(id: EntityId): List<DayList> {
        val now = clock()
        return store
            .update { snapshot ->
                val source = snapshot.lists.firstOrNull { it.id == id } ?: error("List does not exist")
                val copy =
                    source.copy(
                        id = idFactory(),
                        title = "${source.title} — copy".take(MAX_LIST_TITLE_LENGTH),
                        items =
                            source.items.mapIndexed { index, item ->
                                item.copy(id = idFactory(), order = index, createdAtEpochMillis = now)
                            },
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        archived = false,
                        archivedAtEpochMillis = null,
                    )
                snapshot.copy(lists = snapshot.lists + copy)
            }.activeLists()
    }

    override suspend fun addItem(
        listId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> {
        val fields = normalizeItemFields(title, quantity, note)
        return mutateList(listId) { list ->
            list.copy(
                items =
                    list.items +
                        DayListItem(
                            id = idFactory(),
                            title = fields.title,
                            quantity = fields.quantity,
                            note = fields.note,
                            order = list.items.size,
                            createdAtEpochMillis = clock(),
                        ),
                updatedAtEpochMillis = clock(),
            )
        }
    }

    override suspend fun updateItem(
        listId: EntityId,
        itemId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> {
        val fields = normalizeItemFields(title, quantity, note)
        return mutateItem(listId, itemId) { item ->
            item.copy(title = fields.title, quantity = fields.quantity, note = fields.note)
        }
    }

    override suspend fun toggleItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> = mutateItem(listId, itemId) { item -> item.copy(completed = !item.completed) }

    override suspend fun deleteItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> =
        mutateList(listId) { list ->
            require(list.items.any { it.id == itemId }) { "List item does not exist" }
            list.copy(
                items = list.items.filterNot { it.id == itemId }.withNormalizedOrder(),
                updatedAtEpochMillis = clock(),
            )
        }

    override suspend fun moveItem(
        listId: EntityId,
        itemId: EntityId,
        offset: Int,
    ): List<DayList> {
        require(offset == -1 || offset == 1) { "Offset must be -1 or 1" }
        return mutateList(listId) { list ->
            val ordered = list.items.sortedBy(DayListItem::order).toMutableList()
            val index = ordered.indexOfFirst { it.id == itemId }
            require(index >= 0) { "List item does not exist" }
            val destination = index + offset
            if (destination in ordered.indices) {
                val moved = ordered.removeAt(index)
                ordered.add(destination, moved)
            }
            list.copy(items = ordered.withNormalizedOrder(), updatedAtEpochMillis = clock())
        }
    }

    override suspend fun setItemsCompleted(
        listId: EntityId,
        itemIds: Set<EntityId>,
        completed: Boolean,
    ): List<DayList> =
        mutateList(listId) { list ->
            list.copy(
                items = list.items.map { item -> if (item.id in itemIds) item.copy(completed = completed) else item },
                updatedAtEpochMillis = clock(),
            )
        }

    override suspend fun deleteItems(
        listId: EntityId,
        itemIds: Set<EntityId>,
    ): List<DayList> =
        mutateList(listId) { list ->
            list.copy(
                items = list.items.filterNot { it.id in itemIds }.withNormalizedOrder(),
                updatedAtEpochMillis = clock(),
            )
        }

    override suspend fun moveItems(
        sourceListId: EntityId,
        targetListId: EntityId,
        itemIds: Set<EntityId>,
    ): List<DayList> {
        require(sourceListId != targetListId) { "Source and target must differ" }
        val now = clock()
        return store
            .update { snapshot ->
                val source = snapshot.lists.firstOrNull { it.id == sourceListId } ?: error("Source list does not exist")
                val target = snapshot.lists.firstOrNull { it.id == targetListId } ?: error("Target list does not exist")
                val moved = source.items.filter { it.id in itemIds }
                require(moved.isNotEmpty()) { "No list items selected" }
                snapshot.copy(
                    lists =
                        snapshot.lists.map { list ->
                            when (list.id) {
                                sourceListId ->
                                    source.copy(
                                        items = source.items.filterNot { it.id in itemIds }.withNormalizedOrder(),
                                        updatedAtEpochMillis = now,
                                    )
                                targetListId ->
                                    target.copy(
                                        items =
                                            (target.items + moved)
                                                .mapIndexed { index, item -> item.copy(order = index) },
                                        updatedAtEpochMillis = now,
                                    )
                                else -> list
                            }
                        },
                )
            }.activeLists()
    }

    private suspend fun mutateItem(
        listId: EntityId,
        itemId: EntityId,
        transform: (DayListItem) -> DayListItem,
    ): List<DayList> =
        mutateList(listId) { list ->
            require(list.items.any { it.id == itemId }) { "List item does not exist" }
            list.copy(
                items = list.items.map { item -> if (item.id == itemId) transform(item) else item },
                updatedAtEpochMillis = clock(),
            )
        }

    private suspend fun mutateList(
        id: EntityId,
        transform: (DayList) -> DayList,
    ): List<DayList> =
        store
            .update { snapshot ->
                require(snapshot.lists.any { it.id == id }) { "List does not exist" }
                snapshot.copy(lists = snapshot.lists.map { list -> if (list.id == id) transform(list) else list })
            }.activeLists()

    private suspend fun readWithoutExpiredArchives(): ListsSnapshot {
        val now = clock()
        val current = store.read()
        if (current.lists.none { it.archiveExpired(now) }) return current
        return store.update { snapshot ->
            snapshot.copy(lists = snapshot.lists.filterNot { it.archiveExpired(now) })
        }
    }

    private fun normalizeListTitle(title: String): String {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "List title must not be blank" }
        require(normalized.length <= MAX_LIST_TITLE_LENGTH) { "List title is too long" }
        return normalized
    }

    private fun normalizeCustomKind(value: String): String {
        val normalized = value.trim()
        require(normalized.length <= MAX_CUSTOM_KIND_LENGTH) { "Custom list type is too long" }
        return normalized
    }

    private fun normalizeItemFields(
        title: String,
        quantity: String,
        note: String,
    ): ItemFields {
        val normalizedTitle = title.trim()
        val normalizedQuantity = quantity.trim()
        val normalizedNote = note.trim()
        require(normalizedTitle.isNotEmpty()) { "Item title must not be blank" }
        require(normalizedTitle.length <= MAX_ITEM_TITLE_LENGTH) { "Item title is too long" }
        require(normalizedQuantity.length <= MAX_QUANTITY_LENGTH) { "Item quantity is too long" }
        require(normalizedNote.length <= MAX_NOTE_LENGTH) { "Item note is too long" }
        return ItemFields(normalizedTitle, normalizedQuantity, normalizedNote)
    }

    private fun ListsSnapshot.sortedLists(): List<DayList> =
        lists
            .map { list -> list.copy(items = list.items.sortedBy(DayListItem::order)) }
            .sortedByDescending(DayList::updatedAtEpochMillis)

    private fun ListsSnapshot.activeLists(): List<DayList> =
        copy(lists = lists.filterNot(DayList::archived)).sortedLists()

    private fun ListsSnapshot.archivedLists(): List<DayList> =
        lists.filter(DayList::archived).sortedByDescending { it.archivedAtEpochMillis }

    private fun List<DayListItem>.withNormalizedOrder(): List<DayListItem> =
        mapIndexed { index, item -> item.copy(order = index) }

    private data class ItemFields(
        val title: String,
        val quantity: String,
        val note: String,
    )

    private companion object {
        const val MAX_LIST_TITLE_LENGTH = 80
        const val MAX_CUSTOM_KIND_LENGTH = 40
        const val MAX_ITEM_TITLE_LENGTH = 120
        const val MAX_QUANTITY_LENGTH = 32
        const val MAX_NOTE_LENGTH = 500
    }
}

private const val ARCHIVE_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L

private fun DayList.archiveExpired(nowEpochMillis: Long): Boolean =
    archived && archivedAtEpochMillis?.let { nowEpochMillis - it >= ARCHIVE_RETENTION_MILLIS } == true
