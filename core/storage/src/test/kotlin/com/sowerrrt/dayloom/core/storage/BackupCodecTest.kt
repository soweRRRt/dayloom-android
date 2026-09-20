package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.PlanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun `portable backup round trips settings entities and image bytes`() {
        val imageId = EntityId("00000000-0000-0000-0000-000000000099")
        val backup =
            DayloomBackup(
                createdAtEpochMillis = 123,
                settings = AppSettings(appLanguage = AppLanguage.RUSSIAN),
                habits = emptyList(),
                plans =
                    listOf(
                        PlanItem(
                            id = EntityId.random(),
                            title = "Plan",
                            dateEpochDay = 20_000,
                            createdAtEpochMillis = 100,
                            note = "Preserved in export",
                            archived = true,
                        ),
                    ),
                lists = emptyList(),
                goals = emptyList(),
                attachments =
                    listOf(
                        BackupAttachment(
                            AttachmentRef(imageId, "cover.png", "image/png"),
                            "AQID",
                        ),
                    ),
            )

        assertEquals(backup, codec.decode(codec.encode(backup)))
    }

    @Test
    fun `foreign json is rejected before import`() {
        val foreign = """{"format":"other","schemaVersion":1}""".toByteArray()

        assertThrows(Exception::class.java) { codec.decode(foreign) }
    }

    @Test
    fun `schema one backup remains import compatible and defaults template bundles`() {
        val current =
            DayloomBackup(
                createdAtEpochMillis = 123,
                settings = AppSettings(),
                habits = emptyList(),
                plans = emptyList(),
                lists = emptyList(),
                goals = emptyList(),
                attachments = emptyList(),
            )
        val legacyJson =
            codec
                .encode(current)
                .toString(Charsets.UTF_8)
                .replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
                .replace(Regex("\\s*\"templateBundles\": \\[\\],"), "")

        val restored = codec.decode(legacyJson.toByteArray())

        assertEquals(1, restored.schemaVersion)
        assertEquals(emptyList<Any>(), restored.templateBundles)
    }
}
