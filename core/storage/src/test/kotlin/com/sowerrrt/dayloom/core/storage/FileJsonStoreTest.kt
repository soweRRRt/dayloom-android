package com.sowerrrt.dayloom.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileJsonStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `write persists an envelope that can be read`() =
        runBlocking {
            val store = store()
            store.write(Counter(7))

            assertEquals(Counter(7), store.read())
        }

    @Test
    fun `concurrent updates are serialized without lost writes`() =
        runBlocking {
            val store = store()
            (1..100)
                .map {
                    async(Dispatchers.Default) { store.update { current -> Counter(current.value + 1) } }
                }.awaitAll()

            assertEquals(Counter(100), store.read())
        }

    @Test
    fun `corrupt primary is recovered from previous backup`() =
        runBlocking {
            val directory = temporaryFolder.newFolder()
            val store = store(directory)
            store.write(Counter(1))
            store.write(Counter(2))
            directory.resolve("counter.json").writeText("not-json")

            assertEquals(Counter(1), store.read())
            assertEquals(Counter(1), store.read())
        }

    @Test
    fun `registered migration upgrades an older envelope`() =
        runBlocking {
            val directory = temporaryFolder.newFolder()
            directory.resolve("counter.json").writeText(
                """{"schemaVersion":1,"updatedAtEpochMillis":0,"payload":{"value":4}}""",
            )
            val migration =
                StorageMigration { old: JsonObject ->
                    buildJsonObject {
                        old.forEach { (key, value) -> put(key, value) }
                        put("schemaVersion", JsonPrimitive(2))
                    }
                }
            val store = store(directory, schema = 2, migrations = mapOf(1 to migration))

            assertEquals(Counter(4), store.read())
        }

    private fun store(
        directory: java.io.File = temporaryFolder.newFolder(),
        schema: Int = 1,
        migrations: Map<Int, StorageMigration> = emptyMap(),
    ) = FileJsonStore(
        directory = directory,
        fileName = "counter.json",
        payloadSerializer = Counter.serializer(),
        currentSchemaVersion = schema,
        defaultValue = { Counter(0) },
        migrations = migrations,
        clock = { 123L },
    )
}

@Serializable
private data class Counter(
    val value: Int,
)
