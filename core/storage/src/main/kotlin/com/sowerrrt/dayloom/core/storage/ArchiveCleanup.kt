package com.sowerrrt.dayloom.core.storage

fun interface ArchiveCleanup {
    suspend fun removeExpiredEntries()
}

class RepositoryArchiveCleanup(
    private val habitsRepository: HabitsRepository,
    private val plannerRepository: PlannerRepository,
    private val listsRepository: ListsRepository,
    private val wishlistRepository: WishlistRepository,
) : ArchiveCleanup {
    override suspend fun removeExpiredEntries() {
        habitsRepository.loadArchivedHabits()
        plannerRepository.loadArchivedPlans()
        listsRepository.loadArchivedLists()
        wishlistRepository.loadArchivedGoals()
    }
}
