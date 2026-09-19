package com.sowerrrt.dayloom.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAccentPalette(value: AccentPalette)

    suspend fun setStartDestination(value: StartDestination)

    suspend fun setAutomaticUpdateChecks(enabled: Boolean)

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
                    accentPalette =
                        preferences[Keys.accent]?.enumOrDefault(AccentPalette.VIOLET)
                            ?: AccentPalette.VIOLET,
                    startDestination =
                        preferences[Keys.start]?.enumOrDefault(StartDestination.HOME)
                            ?: StartDestination.HOME,
                    automaticUpdateChecks = preferences[Keys.autoUpdates] ?: true,
                    lastUpdateCheckEpochMillis = preferences[Keys.lastUpdateCheck],
                )
            }

    override suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[Keys.theme] = value.name }
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

    override suspend fun markUpdateChecked(epochMillis: Long) {
        dataStore.edit { it[Keys.lastUpdateCheck] = epochMillis }
    }

    private inline fun <reified T : Enum<T>> String.enumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val start = stringPreferencesKey("start_destination")
        val autoUpdates = booleanPreferencesKey("automatic_update_checks")
        val lastUpdateCheck = longPreferencesKey("last_update_check_epoch_millis")
    }
}
