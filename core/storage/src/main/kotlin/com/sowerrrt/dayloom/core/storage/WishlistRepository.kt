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
) : WishlistRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "wishlist.json",
            payloadSerializer = WishlistSnapshot.serializer(),
            currentSchemaVersion = 1,
            defaultValue = ::WishlistSnapshot,
        )

    override suspend fun loadGoals(): List<WishGoal> = store.read().sortedGoals()

    override suspend fun replaceAll(goals: List<WishGoal>): List<WishGoal> {
        require(goals.map(WishGoal::id).distinct().size == goals.size) { "Wish IDs must be unique" }
        goals.forEach { goal ->
            normalizeGoalFields(goal.title, goal.targetMinor, goal.currencyCode, goal.note, goal.purchaseUrl)
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
        store.write(WishlistSnapshot(goals))
        return WishlistSnapshot(goals).sortedGoals()
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
            .sortedGoals()

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
            }.sortedGoals()

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

    private fun WishlistSnapshot.sortedGoals(): List<WishGoal> =
        goals.sortedByDescending(WishGoal::updatedAtEpochMillis)

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
        const val MAX_AMOUNT_MINOR = 999_999_999_999_99L
    }
}
