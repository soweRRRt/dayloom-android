package com.sowerrrt.dayloom.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setAccentPalette(value: AccentPalette)

    suspend fun setStartDestination(value: StartDestination)

    suspend fun setAutomaticUpdateChecks(enabled: Boolean)

    suspend fun setWholeAppLock(enabled: Boolean)

    suspend fun setBottomSections(sections: List<BottomSection>)

    suspend fun setHomeSections(sections: List<HomeSection>)

    suspend fun addPreset(
        type: PresetType,
        title: String,
    )

    suspend fun removePreset(
        type: PresetType,
        title: String,
    )

    suspend fun markUpdateChecked(epochMillis: Long)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(
                        androidx.datastore.preferences.core
                            .emptyPreferences(),
                    )
                } else {
                    throw error
                }
            }.map { preferences ->
                AppSettings(
                    themeMode = preferences[Keys.theme]?.enumOrDefault(ThemeMode.SYSTEM) ?: ThemeMode.SYSTEM,
                    appLanguage =
                        preferences[Keys.appLanguage]?.enumOrDefault(AppLanguage.SYSTEM)
                            ?: AppLanguage.SYSTEM,
                    accentPalette =
                        preferences[Keys.accent]?.enumOrDefault(AccentPalette.VIOLET)
                            ?: AccentPalette.VIOLET,
                    startDestination =
                        preferences[Keys.start]?.enumOrDefault(StartDestination.HOME)
                            ?: StartDestination.HOME,
                    automaticUpdateChecks = preferences[Keys.autoUpdates] ?: true,
                    lockWholeApp = preferences[Keys.lockWholeApp] ?: false,
                    lastUpdateCheckEpochMillis = preferences[Keys.lastUpdateCheck],
                    bottomSections = preferences[Keys.bottomSections].toBottomSections(),
                    homeSections = preferences[Keys.homeSections].toHomeSections(),
                    habitPresets = preferences[Keys.habitPresets].orEmpty(),
                    planPresets = preferences[Keys.planPresets].orEmpty(),
                    listItemPresets = preferences[Keys.listItemPresets].orEmpty(),
                )
            }

    override suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[Keys.theme] = value.name }
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        dataStore.edit { it[Keys.appLanguage] = value.name }
    }

    override suspend fun setAccentPalette(value: AccentPalette) {
        dataStore.edit { it[Keys.accent] = value.name }
    }

    override suspend fun setStartDestination(value: StartDestination) {
        dataStore.edit { it[Keys.start] = value.name }
    }

    override suspend fun setAutomaticUpdateChecks(enabled: Boolean) {
        dataStore.edit { it[Keys.autoUpdates] = enabled }
    }

    override suspend fun setWholeAppLock(enabled: Boolean) {
        dataStore.edit { it[Keys.lockWholeApp] = enabled }
    }

    override suspend fun setBottomSections(sections: List<BottomSection>) {
        val normalized = sections.distinct().filter { it in BottomSection.entries }.toMutableList()
        if (BottomSection.MORE !in normalized) normalized += BottomSection.MORE
        if (normalized.size < 2) normalized.add(0, BottomSection.HOME)
        dataStore.edit { it[Keys.bottomSections] = normalized.joinToString(",", transform = BottomSection::name) }
    }

    override suspend fun setHomeSections(sections: List<HomeSection>) {
        val normalized = sections.distinct().ifEmpty { listOf(HomeSection.HABITS) }
        dataStore.edit { it[Keys.homeSections] = normalized.joinToString(",", transform = HomeSection::name) }
    }

    override suspend fun addPreset(
        type: PresetType,
        title: String,
    ) {
        val normalized = title.trim().take(1_024)
        if (normalized.isEmpty()) return
        dataStore.edit { preferences ->
            val key = Keys.presetKey(type)
            preferences[key] = (preferences[key].orEmpty() + normalized).takeLastSorted(12)
        }
    }

    override suspend fun removePreset(
        type: PresetType,
        title: String,
    ) {
        dataStore.edit { preferences ->
            val key = Keys.presetKey(type)
            preferences[key] = preferences[key].orEmpty() - title
        }
    }

    override suspend fun markUpdateChecked(epochMillis: Long) {
        dataStore.edit { it[Keys.lastUpdateCheck] = epochMillis }
    }

    private inline fun <reified T : Enum<T>> String.enumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private fun String?.toBottomSections(): List<BottomSection> {
        val parsed =
            this
                ?.split(',')
                ?.mapNotNull { name -> BottomSection.entries.firstOrNull { it.name == name } }
                ?.distinct()
                .orEmpty()
        return if (parsed.size >= 2 && BottomSection.MORE in parsed) parsed else BottomSection.entries
    }

    private fun String?.toHomeSections(): List<HomeSection> {
        val parsed =
            this
                ?.split(',')
                ?.mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }
                ?.distinct()
                .orEmpty()
        return parsed.ifEmpty { HomeSection.entries }
    }

    private fun Set<String>.takeLastSorted(max: Int): Set<String> =
        sortedWith(String.CASE_INSENSITIVE_ORDER).take(max).toSet()

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val appLanguage = stringPreferencesKey("app_language")
        val accent = stringPreferencesKey("accent")
        val start = stringPreferencesKey("start_destination")
        val autoUpdates = booleanPreferencesKey("automatic_update_checks")
        val lockWholeApp = booleanPreferencesKey("lock_whole_app")
        val lastUpdateCheck = longPreferencesKey("last_update_check_epoch_millis")
        val bottomSections = stringPreferencesKey("bottom_sections")
        val homeSections = stringPreferencesKey("home_sections")
        val habitPresets = stringSetPreferencesKey("habit_presets")
        val planPresets = stringSetPreferencesKey("plan_presets")
        val listItemPresets = stringSetPreferencesKey("list_item_presets")

        fun presetKey(type: PresetType) =
            when (type) {
                PresetType.HABIT -> habitPresets
                PresetType.PLAN -> planPresets
                PresetType.LIST_ITEM -> listItemPresets
            }
    }
}
