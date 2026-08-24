package org.akkirrai.hibiki.app.di

import android.content.Context
import org.akkirrai.hibiki.core.anilist.AniListCacheRepository
import org.akkirrai.hibiki.core.anilist.AniListRepository
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.profile.LocalProfileRepository
import org.akkirrai.hibiki.app.settings.AppSettingsStoreImpl

class HibikiDependencies(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun offlineDownloadRepository(): OfflineDownloadRepository = OfflineDownloadRepository(appContext)

    fun watchStateRepository(): WatchStateRepository = WatchStateRepository(appContext)

    fun libraryRepository(): LibraryRepository = LibraryRepository(appContext)

    fun localProfileRepository(): LocalProfileRepository = LocalProfileRepository(appContext)

    fun offlineTitleMetadataRepository(): OfflineTitleMetadataRepository = OfflineTitleMetadataRepository(appContext)

    fun resumeFrameRepository(): ResumeFrameRepository = ResumeFrameRepository(appContext)

    fun appSettingsStore(): AppSettingsStoreImpl = AppSettingsStoreImpl(appContext)

    fun aniListRepository(): AniListRepository = AniListRepository(AniListCacheRepository(appContext))
}

fun Context.hibikiDependencies(): HibikiDependencies = HibikiDependencies(applicationContext)
