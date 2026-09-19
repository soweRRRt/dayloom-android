package com.sowerrrt.dayloom.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@JvmInline
value class EntityId(
    val value: String,
) {
    companion object {
        fun random(): EntityId = EntityId(UUID.randomUUID().toString())
    }
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class AccentPalette {
    VIOLET,
    OCEAN,
    CORAL,
    FOREST,
}

@Serializable
enum class StartDestination {
    HOME,
    HABITS,
    PLANNER,
    LISTS,
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.VIOLET,
    val startDestination: StartDestination = StartDestination.HOME,
    val automaticUpdateChecks: Boolean = true,
    val lastUpdateCheckEpochMillis: Long? = null,
)

@Serializable
data class AttachmentRef(
    val id: EntityId,
    val displayName: String,
    val mimeType: String,
)

@Serializable
data class HomeSectionPreference(
    val id: EntityId,
    val moduleKey: String,
    val visible: Boolean = true,
    val order: Int,
)

@Serializable
data class HomeLayout(
    val sections: List<HomeSectionPreference> = emptyList(),
)

@Serializable
data class Habit(
    val id: EntityId,
    val title: String,
    val createdAtEpochMillis: Long,
    val completedEpochDays: Set<Long> = emptySet(),
    val archived: Boolean = false,
)

@Serializable
data class HabitsSnapshot(
    val habits: List<Habit> = emptyList(),
)
