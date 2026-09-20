package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.TemplateBundleEntry
import com.sowerrrt.dayloom.core.model.TemplateEntryType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTemplateBundlesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `bundle can be created edited duplicated and restored`() =
        runTest {
            val ids = ArrayDeque(listOf(EntityId("bundle"), EntityId("bundle-copy"), EntityId("entry-copy")))
            val repository =
                FileTemplateBundlesRepository(
                    temporaryFolder.newFolder("bundles"),
                    clock = { 10L },
                    idFactory = { ids.removeFirst() },
                )
            val entry =
                TemplateBundleEntry(
                    id = EntityId("entry"),
                    type = TemplateEntryType.HABIT,
                    title = "Drink water",
                )
            repository.createBundle("Morning", listOf(entry))
            repository.updateBundle(EntityId("bundle"), "Morning focus", listOf(entry))
            repository.duplicateBundle(EntityId("bundle"))

            val restored = FileTemplateBundlesRepository(temporaryFolder.root.resolve("bundles")).loadBundles()
            assertEquals(2, restored.size)
            assertTrue(restored.any { it.name == "Morning focus" })
            assertTrue(restored.any { it.name.contains("copy") })
            assertEquals(1, repository.deleteBundle(EntityId("bundle-copy")).size)
        }
}
