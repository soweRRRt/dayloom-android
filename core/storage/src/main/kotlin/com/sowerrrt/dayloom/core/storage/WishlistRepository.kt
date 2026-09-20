package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishContribution
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.WishlistSnapshot
import java.io.File

interface WishlistRepository {
    suspend fun loadGoals(): List<WishGoal>

    suspend fun loadArchivedGoals(): List<WishGoal> = emptyList()

    suspend fun loadAllGoals(): List<WishGoal> = loadGoals() + loadArchivedGoals()

    suspend fun replaceAll(goals: List<WishGoal>): List<WishGoal> =
        error("This wishlist repository does not support replacement")

    suspend fun createGoal(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String = "",
    ): List<WishGoal>

    suspend fun updateGoal(
        id: EntityId,
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String = "",
    ): List<WishGoal>

    suspend fun deleteGoal(id: EntityId): List<WishGoal>

    suspend fun setCategory(
        id: EntityId,
        category: String,
    ): List<WishGoal> = error("Categories are not supported")

    suspend fun archiveGoal(id: EntityId): List<WishGoal> = error("Archiving is not supported")

    suspend fun restoreGoal(id: EntityId): List<WishGoal> = error("Restoring is not supported")

    suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<WishGoal>

    suspend fun addContribution(
        goalId: EntityId,
        amountMinor: Long,
        note: String,
    ): List<WishGoal>

    suspend fun deleteContribution(
        goalId: EntityId,
        contributionId: EntityId,
    ): List<WishGoal>
}

class FileWishlistRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
    private val deleteAttachment: suspend (EntityId) -> Boolean = { true },
) : WishlistRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "wishlist.json",
            payloadSerializer = WishlistSnapshot.serializer(),
            currentSchemaVersion = 1,
            defaultValue = ::WishlistSnapshot,
        )

    override suspend fun loadGoals(): List<WishGoal> = readWithoutExpiredArchives().activeGoals()

    override suspend fun loadArchivedGoals(): List<WishGoal> = readWithoutExpiredArchives().archivedGoals()

    override suspend fun loadAllGoals(): List<WishGoal> = readWithoutExpiredArchives().sortedGoals()

    override suspend fun replaceAll(goals: List<WishGoal>): List<WishGoal> {
        require(goals.map(WishGoal::id).distinct().size == goals.size) { "Wish IDs must be unique" }
        goals.forEach { goal ->
            normalizeGoalFields(goal.title, goal.targetMinor, goal.currencyCode, goal.note, goal.purchaseUrl)
            normalizeCategory(goal.category)
            require(
                goal.contributions
                    .map(WishContribution::id)
                    .distinct()
                    .size == goal.contributions.size,
            ) {
                "Contribution IDs must be unique"
            }
            goal.contributions.forEach { contribution ->
                require(contribution.amountMinor in 1..MAX_AMOUNT_MINOR) { "Contribution is invalid" }
                normalizeNote(contribution.note)
            }
        }
        val now = clock()
        val normalized =
            goals.map { goal ->
                when {
                    !goal.archived -> goal.copy(archivedAtEpochMillis = null)
                    goal.archivedAtEpochMillis == null -> goal.copy(archivedAtEpochMillis = now)
                    else -> goal
                }
            }
        store.write(WishlistSnapshot(normalized))
        return readWithoutExpiredArchives().activeGoals()
    }

    override suspend fun createGoal(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> {
        val fields = normalizeGoalFields(title, targetMinor, currencyCode, note, purchaseUrl)
        val now = clock()
        return store
            .update { snapshot ->
                snapshot.copy(
                    goals =
                        snapshot.goals +
                            WishGoal(
                                id = idFactory(),
                                title = fields.title,
                                targetMinor = fields.targetMinor,
                                currencyCode = fields.currencyCode,
                                priority = priority,
                                note = fields.note,
                                purchaseUrl = fields.purchaseUrl,
                                createdAtEpochMillis = now,
                            ),
                )
            }.sortedGoals()
    }

    override suspend fun updateGoal(
        id: EntityId,
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> {
        val fields = normalizeGoalFields(title, targetMinor, currencyCode, note, purchaseUrl)
        return mutateGoal(id) { goal ->
            goal.copy(
                title = fields.title,
                targetMinor = fields.targetMinor,
                currencyCode = fields.currencyCode,
                priority = priority,
                note = fields.note,
                purchaseUrl = fields.purchaseUrl,
                updatedAtEpochMillis = clock(),
            )
        }
    }

    override suspend fun deleteGoal(id: EntityId): List<WishGoal> =
        store
            .update { snapshot -> snapshot.copy(goals = snapshot.goals.filterNot { it.id == id }) }
            .activeGoals()

    override suspend fun setCategory(
        id: EntityId,
        category: String,
    ): List<WishGoal> =
        mutateGoal(id) { goal -> goal.copy(category = normalizeCategory(category), updatedAtEpochMillis = clock()) }

    override suspend fun archiveGoal(id: EntityId): List<WishGoal> =
        mutateGoal(id) { goal ->
            goal.copy(archived = true, archivedAtEpochMillis = goal.archivedAtEpochMillis ?: clock())
        }

    override suspend fun restoreGoal(id: EntityId): List<WishGoal> =
        mutateGoal(id) { goal -> goal.copy(archived = false, archivedAtEpochMillis = null) }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<WishGoal> =
        mutateGoal(id) { goal ->
            goal.copy(image = image, updatedAtEpochMillis = clock())
        }

    override suspend fun addContribution(
        goalId: EntityId,
        amountMinor: Long,
        note: String,
    ): List<WishGoal> {
        require(amountMinor > 0) { "Contribution must be positive" }
        require(amountMinor <= MAX_AMOUNT_MINOR) { "Contribution is too large" }
        val normalizedNote = normalizeNote(note)
        val now = clock()
        return mutateGoal(goalId) { goal ->
            goal.copy(
                contributions =
                    goal.contributions +
                        WishContribution(
                            id = idFactory(),
                            amountMinor = amountMinor,
                            note = normalizedNote,
                            createdAtEpochMillis = now,
                        ),
                updatedAtEpochMillis = now,
            )
        }
    }

    override suspend fun deleteContribution(
        goalId: EntityId,
        contributionId: EntityId,
    ): List<WishGoal> =
        mutateGoal(goalId) { goal ->
            require(goal.contributions.any { it.id == contributionId }) { "Contribution does not exist" }
            goal.copy(
                contributions = goal.contributions.filterNot { it.id == contributionId },
                updatedAtEpochMillis = clock(),
            )
        }

    private suspend fun mutateGoal(
        id: EntityId,
        transform: (WishGoal) -> WishGoal,
    ): List<WishGoal> =
        store
            .update { snapshot ->
                require(snapshot.goals.any { it.id == id }) { "Wish goal does not exist" }
                snapshot.copy(goals = snapshot.goals.map { goal -> if (goal.id == id) transform(goal) else goal })
            }.activeGoals()

    private suspend fun readWithoutExpiredArchives(): WishlistSnapshot {
        val now = clock()
        val current = store.read()
        if (current.goals.none { it.archiveExpired(now) }) return current
        val expiredAttachments = current.goals.filter { it.archiveExpired(now) }.mapNotNull(WishGoal::image)
        val updated =
            store.update { snapshot ->
                snapshot.copy(goals = snapshot.goals.filterNot { it.archiveExpired(now) })
            }
        expiredAttachments.forEach { attachment -> runCatching { deleteAttachment(attachment.id) } }
        return updated
    }

    private fun normalizeGoalFields(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        note: String,
        purchaseUrl: String,
    ): GoalFields {
        val normalizedTitle = title.trim()
        val normalizedCurrency = currencyCode.trim().uppercase()
        require(normalizedTitle.isNotEmpty()) { "Wish title must not be blank" }
        require(normalizedTitle.length <= MAX_TITLE_LENGTH) { "Wish title is too long" }
        require(targetMinor > 0) { "Target must be positive" }
        require(targetMinor <= MAX_AMOUNT_MINOR) { "Target is too large" }
        require(normalizedCurrency.matches(Regex("[A-Z0-9]{1,8}"))) { "Currency code is invalid" }
        val normalizedUrl = purchaseUrl.trim()
        require(
            normalizedUrl.isEmpty() ||
                (
                    normalizedUrl.length <= MAX_URL_LENGTH &&
                        Regex("https?://.+", RegexOption.IGNORE_CASE).matches(normalizedUrl)
                ),
        ) { "Purchase URL is invalid" }
        return GoalFields(normalizedTitle, targetMinor, normalizedCurrency, normalizeNote(note), normalizedUrl)
    }

    private fun normalizeNote(note: String): String {
        val normalized = note.trim()
        require(normalized.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        return normalized
    }

    private fun normalizeCategory(category: String): String {
        val normalized = category.trim()
        require(normalized.length <= MAX_CATEGORY_LENGTH) { "Category is too long" }
        return normalized
    }

    private fun WishlistSnapshot.sortedGoals(): List<WishGoal> =
        goals.sortedByDescending(WishGoal::updatedAtEpochMillis)

    private fun WishlistSnapshot.activeGoals(): List<WishGoal> =
        copy(goals = goals.filterNot(WishGoal::archived)).sortedGoals()

    private fun WishlistSnapshot.archivedGoals(): List<WishGoal> =
        goals.filter(WishGoal::archived).sortedByDescending { it.archivedAtEpochMillis }

    private data class GoalFields(
        val title: String,
        val targetMinor: Long,
        val currencyCode: String,
        val note: String,
        val purchaseUrl: String,
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_NOTE_LENGTH = 500
        const val MAX_URL_LENGTH = 2_048
        const val MAX_CATEGORY_LENGTH = 40
        const val MAX_AMOUNT_MINOR = 999_999_999_999_99L
    }
}

private const val WISHLIST_ARCHIVE_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L

private fun WishGoal.archiveExpired(nowEpochMillis: Long): Boolean =
    archived &&
        archivedAtEpochMillis?.let { nowEpochMillis - it >= WISHLIST_ARCHIVE_RETENTION_MILLIS } == true
