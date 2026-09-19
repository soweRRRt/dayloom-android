package com.sowerrrt.dayloom.core.storage

import android.content.Context
import android.net.Uri
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.security.VaultCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64

data class DataTransferSummary(
    val habits: Int,
    val plans: Int,
    val lists: Int,
    val goals: Int,
    val attachments: Int,
)

interface DataTransferRepository {
    suspend fun exportTo(uri: Uri): DataTransferSummary

    suspend fun importFrom(uri: Uri): DataTransferSummary

    suspend fun clearAll()
}

@Serializable
internal data class DayloomBackup(
    val format: String = BACKUP_FORMAT,
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val createdAtEpochMillis: Long,
    val settings: AppSettings,
    val habits: List<Habit>,
    val plans: List<PlanItem>,
    val lists: List<DayList>,
    val goals: List<WishGoal>,
    val attachments: List<BackupAttachment>,
    val vaultIncluded: Boolean = false,
)

@Serializable
internal data class BackupAttachment(
    val reference: AttachmentRef,
    val dataBase64: String,
)

internal class BackupCodec(
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        },
) {
    fun encode(backup: DayloomBackup): ByteArray =
        json.encodeToString(DayloomBackup.serializer(), backup).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): DayloomBackup {
        require(bytes.size <= MAX_BACKUP_BYTES) { "Backup is too large" }
        val backup = json.decodeFromString<DayloomBackup>(bytes.toString(Charsets.UTF_8))
        require(backup.format == BACKUP_FORMAT) { "This is not a Dayloom backup" }
        require(backup.schemaVersion == BACKUP_SCHEMA_VERSION) { "Unsupported backup version" }
        require(!backup.vaultIncluded) { "Vault data cannot be imported" }
        require(backup.habits.size <= MAX_ENTITY_COUNT) { "Too many habits" }
        require(backup.plans.size <= MAX_ENTITY_COUNT) { "Too many plans" }
        require(backup.lists.size <= MAX_ENTITY_COUNT) { "Too many lists" }
        require(backup.goals.size <= MAX_ENTITY_COUNT) { "Too many goals" }
        require(backup.attachments.size <= MAX_ATTACHMENT_COUNT) { "Too many attachments" }
        return backup
    }
}

class LocalDataTransferRepository
    constructor(
        @param:ApplicationContext private val context: Context,
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val listsRepository: ListsRepository,
        private val wishlistRepository: WishlistRepository,
        private val settingsRepository: SettingsRepository,
        private val attachmentStore: AttachmentStore,
        private val vaultRepository: VaultRepository,
        private val vaultCipher: VaultCipher,
    ) : DataTransferRepository {
        private val mutex = Mutex()
        private val codec = BackupCodec()

        override suspend fun exportTo(uri: Uri): DataTransferSummary =
            mutex.withLock {
                val state = captureState()
                val backup = state.toBackup(System.currentTimeMillis())
                val bytes = codec.encode(backup)
                require(bytes.size <= MAX_BACKUP_BYTES) { "Backup is too large" }
                withContext(Dispatchers.IO) {
                    val output =
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                            "Unable to open backup destination"
                        }
                    output.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    }
                }
                backup.summary()
            }

        override suspend fun importFrom(uri: Uri): DataTransferSummary =
            mutex.withLock {
                val bytes =
                    withContext(Dispatchers.IO) {
                        val input =
                            requireNotNull(context.contentResolver.openInputStream(uri)) {
                                "Unable to open backup"
                            }
                        input.use { it.readBytesLimited(MAX_BACKUP_BYTES) }
                    }
                val backup = codec.decode(bytes)
                val incoming = backup.toState()
                validateReferences(incoming)
                val previous = captureState()
                runCatching { applyState(incoming) }
                    .onFailure { runCatching { applyState(previous) } }
                    .getOrThrow()
                backup.summary()
            }

        override suspend fun clearAll() =
            mutex.withLock {
                habitsRepository.replaceAll(emptyList())
                plannerRepository.replaceAll(emptyList())
                listsRepository.replaceAll(emptyList())
                wishlistRepository.replaceAll(emptyList())
                attachmentStore.replaceAll(emptyList())
                vaultRepository.deleteVault()
                vaultCipher.destroyKey().getOrThrow()
                settingsRepository.replaceAll(AppSettings())
            }

        private suspend fun captureState(): TransferState {
            val habits = habitsRepository.loadHabits()
            val plans = plannerRepository.loadPlans()
            val lists = listsRepository.loadLists()
            val goals = wishlistRepository.loadGoals()
            val references =
                buildList {
                    habits.mapNotNullTo(this) { it.image }
                    plans.mapNotNullTo(this) { it.image }
                    goals.mapNotNullTo(this) { it.image }
                }
            return TransferState(
                settings = settingsRepository.settings.first(),
                habits = habits,
                plans = plans,
                lists = lists,
                goals = goals,
                attachments = attachmentStore.exportAll(references),
            )
        }

        private suspend fun applyState(state: TransferState) {
            habitsRepository.replaceAll(state.habits)
            plannerRepository.replaceAll(state.plans)
            listsRepository.replaceAll(state.lists)
            wishlistRepository.replaceAll(state.goals)
            attachmentStore.replaceAll(state.attachments)
            settingsRepository.replaceAll(state.settings)
        }

        private fun validateReferences(state: TransferState) {
            val attachments = state.attachments.associateBy { it.reference.id }
            val referenced =
                buildList {
                    state.habits.mapNotNullTo(this) { it.image }
                    state.plans.mapNotNullTo(this) { it.image }
                    state.goals.mapNotNullTo(this) { it.image }
                }
            require(referenced.map { it.id }.toSet() == attachments.keys) { "Backup attachments are inconsistent" }
            referenced.forEach { reference ->
                require(attachments[reference.id]?.reference == reference) { "Attachment metadata is inconsistent" }
            }
        }
    }

private data class TransferState(
    val settings: AppSettings,
    val habits: List<Habit>,
    val plans: List<PlanItem>,
    val lists: List<DayList>,
    val goals: List<WishGoal>,
    val attachments: List<StoredAttachment>,
) {
    fun toBackup(createdAtEpochMillis: Long): DayloomBackup =
        DayloomBackup(
            createdAtEpochMillis = createdAtEpochMillis,
            settings = settings.copy(lastUpdateCheckEpochMillis = null),
            habits = habits,
            plans = plans,
            lists = lists,
            goals = goals,
            attachments =
                attachments.map { attachment ->
                    BackupAttachment(
                        reference = attachment.reference,
                        dataBase64 = Base64.getEncoder().encodeToString(attachment.bytes),
                    )
                },
        )
}

private fun DayloomBackup.toState(): TransferState {
    require(
        settings.bottomSections.size >= 2 &&
            com.sowerrrt.dayloom.core.model.BottomSection.MORE in settings.bottomSections,
    ) {
        "Backup navigation settings are invalid"
    }
    require(settings.homeSections.isNotEmpty()) { "Backup dashboard settings are invalid" }
    require(
        listOf(settings.habitPresets, settings.planPresets, settings.listItemPresets)
            .all { presets -> presets.size <= 12 && presets.all { it.length <= 1_024 } },
    ) { "Backup presets are invalid" }
    require(lists.sumOf { it.items.size } <= MAX_NESTED_ENTITY_COUNT) { "Too many list items" }
    require(goals.sumOf { it.contributions.size } <= MAX_NESTED_ENTITY_COUNT) { "Too many contributions" }
    var totalBytes = 0L
    val decoded =
        attachments.map { attachment ->
            val bytes = Base64.getDecoder().decode(attachment.dataBase64)
            totalBytes += bytes.size
            require(totalBytes <= MAX_TOTAL_ATTACHMENT_BYTES) { "Backup attachments are too large" }
            StoredAttachment(attachment.reference, bytes)
        }
    return TransferState(settings, habits, plans, lists, goals, decoded)
}

private fun DayloomBackup.summary(): DataTransferSummary =
    DataTransferSummary(habits.size, plans.size, lists.size, goals.size, attachments.size)

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Backup is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val BACKUP_FORMAT = "dayloom-backup"
private const val BACKUP_SCHEMA_VERSION = 1
private const val MAX_BACKUP_BYTES = 140 * 1024 * 1024
private const val MAX_TOTAL_ATTACHMENT_BYTES = 100L * 1024 * 1024
private const val MAX_ENTITY_COUNT = 10_000
private const val MAX_NESTED_ENTITY_COUNT = 100_000
private const val MAX_ATTACHMENT_COUNT = 1_000
