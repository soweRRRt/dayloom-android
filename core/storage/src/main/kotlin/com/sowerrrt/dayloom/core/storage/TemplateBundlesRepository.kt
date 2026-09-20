package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.TemplateBundle
import com.sowerrrt.dayloom.core.model.TemplateBundleEntry
import com.sowerrrt.dayloom.core.model.TemplateBundlesSnapshot
import java.io.File

interface TemplateBundlesRepository {
    suspend fun loadBundles(): List<TemplateBundle>

    suspend fun replaceAll(bundles: List<TemplateBundle>): List<TemplateBundle>

    suspend fun createBundle(
        name: String,
        entries: List<TemplateBundleEntry>,
    ): List<TemplateBundle>

    suspend fun updateBundle(
        id: EntityId,
        name: String,
        entries: List<TemplateBundleEntry>,
    ): List<TemplateBundle>

    suspend fun duplicateBundle(id: EntityId): List<TemplateBundle>

    suspend fun deleteBundle(id: EntityId): List<TemplateBundle>
}

class FileTemplateBundlesRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
) : TemplateBundlesRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "template_bundles.json",
            payloadSerializer = TemplateBundlesSnapshot.serializer(),
            currentSchemaVersion = 1,
            defaultValue = ::TemplateBundlesSnapshot,
            clock = clock,
        )

    override suspend fun loadBundles(): List<TemplateBundle> = store.read().sorted()

    override suspend fun replaceAll(bundles: List<TemplateBundle>): List<TemplateBundle> {
        validateUnique(bundles)
        bundles.forEach(::validateBundle)
        store.write(TemplateBundlesSnapshot(bundles))
        return store.read().sorted()
    }

    override suspend fun createBundle(
        name: String,
        entries: List<TemplateBundleEntry>,
    ): List<TemplateBundle> {
        val normalizedName = normalizeName(name)
        validateEntries(entries)
        val now = clock()
        return store
            .update { snapshot ->
                snapshot.copy(
                    bundles =
                        snapshot.bundles +
                            TemplateBundle(
                                id = idFactory(),
                                name = normalizedName,
                                entries = entries,
                                createdAtEpochMillis = now,
                            ),
                )
            }.sorted()
    }

    override suspend fun updateBundle(
        id: EntityId,
        name: String,
        entries: List<TemplateBundleEntry>,
    ): List<TemplateBundle> {
        val normalizedName = normalizeName(name)
        validateEntries(entries)
        return store
            .update { snapshot ->
                require(snapshot.bundles.any { it.id == id }) { "Template bundle does not exist" }
                snapshot.copy(
                    bundles =
                        snapshot.bundles.map { bundle ->
                            if (bundle.id == id) {
                                bundle.copy(name = normalizedName, entries = entries, updatedAtEpochMillis = clock())
                            } else {
                                bundle
                            }
                        },
                )
            }.sorted()
    }

    override suspend fun duplicateBundle(id: EntityId): List<TemplateBundle> {
        val now = clock()
        return store
            .update { snapshot ->
                val source = snapshot.bundles.firstOrNull { it.id == id } ?: error("Template bundle does not exist")
                snapshot.copy(
                    bundles =
                        snapshot.bundles +
                            source.copy(
                                id = idFactory(),
                                name = "${source.name} — copy".take(MAX_NAME_LENGTH),
                                entries = source.entries.map { it.copy(id = idFactory()) },
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                            ),
                )
            }.sorted()
    }

    override suspend fun deleteBundle(id: EntityId): List<TemplateBundle> =
        store
            .update { snapshot -> snapshot.copy(bundles = snapshot.bundles.filterNot { it.id == id }) }
            .sorted()

    private fun validateBundle(bundle: TemplateBundle) {
        normalizeName(bundle.name)
        validateEntries(bundle.entries)
    }

    private fun validateEntries(entries: List<TemplateBundleEntry>) {
        require(entries.isNotEmpty()) { "Template bundle must not be empty" }
        require(entries.size <= MAX_ENTRIES) { "Too many template entries" }
        require(entries.map(TemplateBundleEntry::id).distinct().size == entries.size) { "Entry IDs must be unique" }
        entries.forEach { entry ->
            require(entry.title.isNotBlank() && entry.title.length <= 120) { "Template entry title is invalid" }
            require(entry.note.length <= 500) { "Template entry note is too long" }
        }
    }

    private fun normalizeName(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Template bundle name must not be blank" }
        require(normalized.length <= MAX_NAME_LENGTH) { "Template bundle name is too long" }
        return normalized
    }

    private fun validateUnique(bundles: List<TemplateBundle>) {
        require(bundles.size <= MAX_BUNDLES) { "Too many template bundles" }
        require(bundles.map(TemplateBundle::id).distinct().size == bundles.size) { "Bundle IDs must be unique" }
    }

    private fun TemplateBundlesSnapshot.sorted(): List<TemplateBundle> =
        bundles.sortedByDescending(TemplateBundle::updatedAtEpochMillis)

    private companion object {
        const val MAX_NAME_LENGTH = 80
        const val MAX_ENTRIES = 50
        const val MAX_BUNDLES = 100
    }
}
