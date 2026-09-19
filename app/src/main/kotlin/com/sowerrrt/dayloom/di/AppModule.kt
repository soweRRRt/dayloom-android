package com.sowerrrt.dayloom.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sowerrrt.dayloom.core.storage.DataStoreSettingsRepository
import com.sowerrrt.dayloom.core.storage.FileHabitsRepository
import com.sowerrrt.dayloom.core.storage.FilePlannerRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import com.sowerrrt.dayloom.core.updates.GitHubUpdateSource
import com.sowerrrt.dayloom.core.updates.UpdateSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("dayloom_settings.preferences_pb")
        }

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideHabitsRepository(
        @ApplicationContext context: Context,
    ): HabitsRepository = FileHabitsRepository(context.filesDir)

    @Provides
    @Singleton
    fun providePlannerRepository(
        @ApplicationContext context: Context,
    ): PlannerRepository = FilePlannerRepository(context.filesDir)

    @Provides
    @Singleton
    fun provideUpdateSource(): UpdateSource = GitHubUpdateSource()
}
