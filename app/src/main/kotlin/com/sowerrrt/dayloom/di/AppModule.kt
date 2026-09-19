package com.sowerrrt.dayloom.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.WorkManagerNotificationScheduler
import com.sowerrrt.dayloom.core.security.AndroidKeystoreVaultCipher
import com.sowerrrt.dayloom.core.security.VaultCipher
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.AttachmentStore
import com.sowerrrt.dayloom.core.storage.DataStoreSettingsRepository
import com.sowerrrt.dayloom.core.storage.DemoContentRepository
import com.sowerrrt.dayloom.core.storage.DemoImage
import com.sowerrrt.dayloom.core.storage.DemoImageAsset
import com.sowerrrt.dayloom.core.storage.DemoImageSource
import com.sowerrrt.dayloom.core.storage.EncryptedFileVaultRepository
import com.sowerrrt.dayloom.core.storage.FileHabitsRepository
import com.sowerrrt.dayloom.core.storage.FileListsRepository
import com.sowerrrt.dayloom.core.storage.FilePlannerRepository
import com.sowerrrt.dayloom.core.storage.FileWishlistRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.LocalAttachmentRepository
import com.sowerrrt.dayloom.core.storage.LocalDemoContentRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import com.sowerrrt.dayloom.core.storage.VaultRepository
import com.sowerrrt.dayloom.core.storage.WishlistRepository
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
    fun provideNotificationScheduler(
        @ApplicationContext context: Context,
    ): NotificationScheduler = WorkManagerNotificationScheduler(context)

    @Provides
    @Singleton
    fun provideListsRepository(
        @ApplicationContext context: Context,
    ): ListsRepository = FileListsRepository(context.filesDir)

    @Provides
    @Singleton
    fun provideWishlistRepository(
        @ApplicationContext context: Context,
    ): WishlistRepository = FileWishlistRepository(context.filesDir)

    @Provides
    @Singleton
    fun provideAttachmentStore(
        @ApplicationContext context: Context,
    ): AttachmentStore = AttachmentStore(context.filesDir.resolve("attachments"))

    @Provides
    @Singleton
    fun provideAttachmentRepository(repository: LocalAttachmentRepository): AttachmentRepository = repository

    @Provides
    @Singleton
    fun provideDemoImageSource(
        @ApplicationContext context: Context,
    ): DemoImageSource =
        DemoImageSource { image ->
            val resource =
                when (image) {
                    DemoImage.LAPTOP -> com.sowerrrt.dayloom.feature.wishlist.R.drawable.demo_wish_laptop
                    DemoImage.TRAVEL -> com.sowerrrt.dayloom.feature.wishlist.R.drawable.demo_wish_travel
                }
            DemoImageAsset(
                displayName = "${image.name.lowercase()}.png",
                mimeType = "image/png",
                bytes = context.resources.openRawResource(resource).use { it.readBytes() },
            )
        }

    @Provides
    @Singleton
    fun provideDemoContentRepository(
        habitsRepository: HabitsRepository,
        plannerRepository: PlannerRepository,
        listsRepository: ListsRepository,
        wishlistRepository: WishlistRepository,
        attachmentRepository: AttachmentRepository,
        demoImageSource: DemoImageSource,
    ): DemoContentRepository =
        LocalDemoContentRepository(
            habitsRepository = habitsRepository,
            plannerRepository = plannerRepository,
            listsRepository = listsRepository,
            wishlistRepository = wishlistRepository,
            attachmentRepository = attachmentRepository,
            demoImageSource = demoImageSource,
        )

    @Provides
    @Singleton
    fun provideVaultCipher(): VaultCipher = AndroidKeystoreVaultCipher()

    @Provides
    @Singleton
    fun provideVaultRepository(
        @ApplicationContext context: Context,
        cipher: VaultCipher,
    ): VaultRepository = EncryptedFileVaultRepository(context.filesDir, cipher)

    @Provides
    @Singleton
    fun provideUpdateSource(): UpdateSource = GitHubUpdateSource()
}
