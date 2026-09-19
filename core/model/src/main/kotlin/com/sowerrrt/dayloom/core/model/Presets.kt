package com.sowerrrt.dayloom.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HabitPreset(
    val title: String,
    val scheduledWeekdays: Set<Weekday> = Weekday.entries.toSet(),
    val repeatEveryDays: Int? = null,
    val scheduledMonthDays: Set<Int> = emptySet(),
    val reminderMinutesOfDay: Int? = null,
    val targetAmount: String = "",
    val targetUnit: String = "",
)

@Serializable
data class PlanPreset(
    val title: String,
    val reminderMinutesOfDay: Int? = null,
)

@Serializable
data class ListItemPreset(
    val title: String,
    val quantity: String = "",
    val note: String = "",
)

private const val PRESET_PREFIX = "dayloom-preset-v2:"

private val presetJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

fun HabitPreset.toStorageValue(): String = PRESET_PREFIX + presetJson.encodeToString(this)

fun PlanPreset.toStorageValue(): String = PRESET_PREFIX + presetJson.encodeToString(this)

fun ListItemPreset.toStorageValue(): String = PRESET_PREFIX + presetJson.encodeToString(this)

fun String.toHabitPresetOrNull(): HabitPreset? =
    decodePreset { presetJson.decodeFromString<HabitPreset>(it) }
        ?: takeIf { it.isNotBlank() && !it.startsWith(PRESET_PREFIX) }?.let(::HabitPreset)

fun String.toPlanPresetOrNull(): PlanPreset? =
    decodePreset { presetJson.decodeFromString<PlanPreset>(it) }
        ?: takeIf { it.isNotBlank() && !it.startsWith(PRESET_PREFIX) }?.let(::PlanPreset)

fun String.toListItemPresetOrNull(): ListItemPreset? =
    decodePreset { presetJson.decodeFromString<ListItemPreset>(it) }
        ?: takeIf { it.isNotBlank() && !it.startsWith(PRESET_PREFIX) }?.let(::ListItemPreset)

private inline fun <T> String.decodePreset(decode: (String) -> T): T? {
    if (!startsWith(PRESET_PREFIX)) return null
    return runCatching { decode(removePrefix(PRESET_PREFIX)) }.getOrNull()
}
