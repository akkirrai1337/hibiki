package org.akkirrai.hibiki.app.di

import android.content.Context
import org.akkirrai.hibiki.HibikiApplication
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.profile.LocalProfileRepository
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceRuntimeManager
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.feature.home.HomeRepository
import org.akkirrai.hibiki.feature.catalog.CatalogRepository

class HibikiDependencies(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val parserClient = AndroidHttpClientFactory.create()
    private val sourceRuntimeManager = AnimeSourceRuntimeManager(appContext, parserClient)

    fun animeSearchRepository(): AnimeSearchRepository = AnimeSearchRepository(
        context = appContext,
        client = parserClient,
        sourceManager = sourceRuntimeManager,
        closeClientOnClose = false,
    )

    fun animeWatchRepository(): AnimeWatchRepository = AnimeWatchRepository(
        context = appContext,
        client = parserClient,
        sourceManager = sourceRuntimeManager,
        closeClientOnClose = false,
    )

    fun homeRepository(): HomeRepository = HomeRepository(
        context = appContext,
        client = parserClient,
        sourceManager = sourceRuntimeManager,
        closeClientOnClose = false,
    )

    fun catalogRepository(): CatalogRepository = CatalogRepository(
        context = appContext,
        client = parserClient,
        sourceManager = sourceRuntimeManager,
        closeClientOnClose = false,
    )

    fun offlineDownloadRepository(): OfflineDownloadRepository = OfflineDownloadRepository(appContext)

    fun watchStateRepository(): WatchStateRepository = WatchStateRepository(appContext)

    fun libraryRepository(): LibraryRepository = LibraryRepository(appContext)

    fun localProfileRepository(): LocalProfileRepository = LocalProfileRepository(appContext)

    fun offlineTitleMetadataRepository(): OfflineTitleMetadataRepository = OfflineTitleMetadataRepository(appContext)

    fun resumeFrameRepository(): ResumeFrameRepository = ResumeFrameRepository(appContext)
}

fun Context.hibikiDependencies(): HibikiDependencies =
    (applicationContext as? HibikiApplication)?.dependencies
        ?: HibikiDependencies(applicationContext)
