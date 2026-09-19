package com.sowerrrt.dayloom.feature.wishlist

import android.net.Uri
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishContribution
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `new goal is selected and contributions update progress`() =
        runTest(dispatcher) {
            val viewModel = WishlistViewModel(FakeWishlistRepository(), FakeAttachmentRepository())
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.createGoal(
                "Camera",
                100_000,
                "EUR",
                WishPriority.HIGH,
                "Travel",
                "https://example.com/camera",
            )
            runCurrent()
            assertEquals(
                "Camera",
                viewModel.uiState.value.selectedGoal
                    ?.title,
            )
            assertEquals(
                "https://example.com/camera",
                viewModel.uiState.value.selectedGoal
                    ?.purchaseUrl,
            )

            viewModel.addContribution(EntityId("goal-1"), 25_000, "First step")
            runCurrent()
            assertEquals(
                25_000L,
                viewModel.uiState.value.selectedGoal
                    ?.contributions
                    ?.single()
                    ?.amountMinor,
            )

            viewModel.deleteGoal(EntityId("goal-1"))
            runCurrent()
            assertNull(viewModel.uiState.value.selectedGoal)
        }

    @Test
    fun `amount parser accepts comma and rejects extra precision`() {
        assertEquals(12_345L, parseAmountToMinor("123,45"))
        assertEquals(100L, parseAmountToMinor("1"))
        assertNull(parseAmountToMinor("1.234"))
        assertNull(parseAmountToMinor("0"))
    }

    @Test
    fun `attached image is exposed and removed from goal and private storage`() =
        runTest(dispatcher) {
            val attachment =
                AttachmentRef(
                    EntityId("00000000-0000-0000-0000-000000000010"),
                    "camera.png",
                    "image/png",
                )
            val repository =
                FakeWishlistRepository(
                    listOf(
                        WishGoal(
                            id = EntityId("goal-image"),
                            title = "Camera",
                            targetMinor = 100_000,
                            currencyCode = "EUR",
                            image = attachment,
                            createdAtEpochMillis = 1L,
                        ),
                    ),
                )
            val attachments = FakeAttachmentRepository()
            val viewModel = WishlistViewModel(repository, attachments)
            runCurrent()

            assertEquals("/private/camera.png", viewModel.uiState.value.imagePaths[EntityId("goal-image")])
            viewModel.removeImage(EntityId("goal-image"))
            runCurrent()

            assertNull(viewModel.uiState.value.selectedGoal)
            assertNull(
                viewModel.uiState.value.goals
                    .single()
                    .image,
            )
            assertEquals(listOf(attachment), attachments.deleted)
        }
}

private class FakeWishlistRepository(
    initialGoals: List<WishGoal> = emptyList(),
) : WishlistRepository {
    private var goals = initialGoals

    override suspend fun loadGoals(): List<WishGoal> = goals

    override suspend fun createGoal(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> {
        goals =
            listOf(
                WishGoal(
                    id = EntityId("goal-1"),
                    title = title,
                    targetMinor = targetMinor,
                    currencyCode = currencyCode,
                    priority = priority,
                    note = note,
                    purchaseUrl = purchaseUrl,
                    createdAtEpochMillis = 1L,
                ),
            )
        return goals
    }

    override suspend fun updateGoal(
        id: EntityId,
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> = error("Not needed")

    override suspend fun deleteGoal(id: EntityId): List<WishGoal> {
        goals = goals.filterNot { it.id == id }
        return goals
    }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<WishGoal> {
        goals = goals.map { if (it.id == id) it.copy(image = image) else it }
        return goals
    }

    override suspend fun addContribution(
        goalId: EntityId,
        amountMinor: Long,
        note: String,
    ): List<WishGoal> {
        goals =
            goals.map { goal ->
                if (goal.id == goalId) {
                    goal.copy(
                        contributions =
                            goal.contributions +
                                WishContribution(EntityId("contribution-1"), amountMinor, note, 2L),
                    )
                } else {
                    goal
                }
            }
        return goals
    }

    override suspend fun deleteContribution(
        goalId: EntityId,
        contributionId: EntityId,
    ): List<WishGoal> = error("Not needed")
}

private class FakeAttachmentRepository : AttachmentRepository {
    val deleted = mutableListOf<AttachmentRef>()

    override suspend fun importImage(uri: Uri): AttachmentRef = error("Not needed")

    override suspend fun importImage(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): AttachmentRef = error("Not needed")

    override suspend fun delete(attachment: AttachmentRef): Boolean {
        deleted += attachment
        return true
    }

    override fun localPath(attachment: AttachmentRef): String = "/private/${attachment.displayName}"
}
