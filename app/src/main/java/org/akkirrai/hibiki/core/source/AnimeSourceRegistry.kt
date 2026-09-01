package org.akkirrai.hibiki.core.source

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.api.context.SourceConfig
import org.akkirrai.beakokit.api.context.SourceLogLevel
import org.akkirrai.beakokit.api.context.SourceLogger
import org.akkirrai.beakokit.api.execution.SourceExecutionPolicy
import org.akkirrai.beakokit.api.health.SourceHealthReporter
import org.akkirrai.beakokit.extension.InvalidScriptExtension
import org.akkirrai.beakokit.extension.ScriptExtensionManifest
import org.akkirrai.beakokit.extension.ScriptExtensionRepository
import org.akkirrai.beakokit.extension.PlayerResolverExtensionRepository
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.network.AndroidBrowserFetchProvider
import org.akkirrai.hibiki.core.network.AndroidChallengeSessionProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class AnimeSourceDescriptor(
    val info: SourceInfo,
    @param:DrawableRes val iconRes: Int,
) {
    val id: SourceId
        get() = info.id

    val name: String
        get() = info.name

    val iconUrl: String?
        get() = info.iconUrl

    val language: SourceLanguage
        get() = info.primaryLanguage

    val supportsPlayback: Boolean
        get() = SourceCapability.PLAYBACK in info.capabilities

    val contentFeatures: Set<SourceCapability>
        get() = info.capabilities.intersect(CONTENT_CAPABILITIES)

    companion object {
        private val CONTENT_CAPABILITIES = setOf(
            SourceCapability.RELATED_TITLES,
            SourceCapability.SIMILAR_TITLES,
        )

        /** A capability-less placeholder for a source id that isn't currently installed. */
        fun unavailable(sourceId: SourceId): AnimeSourceDescriptor = AnimeSourceDescriptor(
            info = SourceInfo(
                id = sourceId,
                name = sourceId.value,
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
            ),
            iconRes = R.drawable.animite_media_type_anime,
        )
    }
}

/**
 * Registers every anime source Hibiki knows about - all of them are dynamically loaded scripted
 * (JS) extensions now, none compiled in and none installed by default. Call [initialize] once
 * (from application startup) before any UI reads [sources]/[catalog]; without it, [sources] is
 * simply empty - which is exactly what plain JVM unit tests get today, since
 * [ScriptExtensionRepository] takes a plain [File] rather than an Android `Context`.
 */
object AnimeSourceRegistry {
    private data class Registration(
        val info: SourceInfo,
        @param:DrawableRes val iconRes: Int,
        val localizeFilters: (AnimeSearchFilterCatalog, Boolean) -> AnimeSearchFilterCatalog = { catalog, _ -> catalog },
        val normalizeTitleId: (String) -> String = { it },
    ) {
        val descriptor = AnimeSourceDescriptor(info = info, iconRes = iconRes)
    }

    @Volatile
    private var scriptRepository: ScriptExtensionRepository? = null
    private var playerResolverRepository: PlayerResolverExtensionRepository? = null

    // Compose state (not just @Volatile) so screens reading `sources`/`catalog` recompose the
    // moment an extension is installed/uninstalled, without needing their own ViewModel plumbing.
    private var registrationsState by mutableStateOf<List<Registration>>(emptyList())
    private var scriptCatalogEntries by mutableStateOf<List<SourceCatalogEntry>>(emptyList())
    private var invalidScriptExtensionsState by mutableStateOf<List<InvalidScriptExtension>>(emptyList())
    private var installedManifestsState by mutableStateOf<List<ScriptExtensionManifest>>(emptyList())

    // Bumped by every refresh() (i.e. every install/uninstall) so long-lived
    // AnimeSourceRuntimeManager instances (one per repository, surviving across screen
    // navigation) know a runtime they already created might be running a JS payload that's since
    // been overwritten on disk, and should be thrown away instead of reused as-is.
    private val extensionGenerationCounter = AtomicInteger(0)
    val extensionGeneration: Int
        get() = extensionGenerationCounter.get()

    /** Points the registry at [extensionsDir] and loads whatever extensions are already installed there. */
    fun initialize(extensionsDir: File) {
        scriptRepository = ScriptExtensionRepository(extensionsDir)
        playerResolverRepository = PlayerResolverExtensionRepository(extensionsDir)
        refresh()
    }

    /** Re-reads every scripted extension from disk; call after installing/uninstalling one. */
    fun refresh() {
        val result = scriptRepository?.loadAll() ?: ScriptExtensionRepository.LoadResult.EMPTY
        invalidScriptExtensionsState = result.invalid
        scriptCatalogEntries = result.entries
        registrationsState = result.entries.map(::registrationFor)
        installedManifestsState = scriptRepository?.installedManifests().orEmpty()
        extensionGenerationCounter.incrementAndGet()
    }

    fun uninstallScriptExtension(id: SourceId) {
        scriptRepository?.uninstall(id.value)
        refresh()
    }

    /**
     * Validates and persists a manifest fetched from a repository, then reloads the catalog.
     * [originRepositoryUrl] identifies which repository this manifest came from, so
     * [ScriptExtensionRepository.install] can refuse a different repository silently overwriting
     * an id it doesn't own.
     */
    fun installScriptExtension(manifestJson: String, originRepositoryUrl: String) {
        val repository = scriptRepository
            ?: error("AnimeSourceRegistry.initialize must be called before installing extensions")
        repository.install(manifestJson, originRepositoryUrl)
        refresh()
    }

    /** Installs a portable player resolver. Resolver files never appear as catalog sources. */
    fun installPlayerResolverExtension(manifestJson: String, originRepositoryUrl: String) {
        val repository = playerResolverRepository
            ?: error("AnimeSourceRegistry.initialize must be called before installing resolvers")
        repository.install(manifestJson, originRepositoryUrl)
        extensionGenerationCounter.incrementAndGet()
    }

    fun createPlayerResolvers(context: Context, client: HttpClient): List<StreamExtractor> {
        val appContext = context.applicationContext
        return playerResolverRepository?.loadAll(
            DefaultSourceContext(
                httpClient = client,
                preferredLanguages = listOf(SourceLanguage.ENGLISH),
                logger = SourceLogger { level, message, throwable ->
                    when (level) {
                        SourceLogLevel.DEBUG -> AppLogger.d("BeakoKit/resolver", message)
                        SourceLogLevel.WARNING -> AppLogger.w("BeakoKit/resolver", message, throwable)
                        SourceLogLevel.ERROR -> AppLogger.e("BeakoKit/resolver", message, throwable)
                    }
                },
                challengeSessionProvider = AndroidChallengeSessionProvider(appContext),
            ),
        ).orEmpty()
    }

    fun invalidScriptExtensions(): List<InvalidScriptExtension> = invalidScriptExtensionsState

    /** Installed script-extension ids mapped to their installed version, for update checks. */
    fun installedScriptExtensionVersions(): Map<String, String> =
        installedManifestsState.associate { it.id to it.version }

    /** Installed player-resolver ids mapped to their installed version. A resolver fix ships to no
     * one if only its own version is bumped: the updater only surfaces "update available" on the
     * type=source entry a user can actually see and tap, so the UI needs this to notice when a
     * source's declared resolver has moved even though the source's own version hasn't. */
    fun installedPlayerResolverVersions(): Map<String, String> =
        playerResolverRepository?.installedManifests()?.associate { it.id to it.version }.orEmpty()


    val sources: List<AnimeSourceDescriptor>
        get() = registrationsState.map(Registration::descriptor)

    val catalog: SourceCatalog
        get() = SourceCatalog(scriptCatalogEntries)

    fun createRuntime(
        context: Context,
        client: HttpClient,
        sourceId: SourceId = AppPreferences.readState(context).animeSource,
        sourceHealthReporter: SourceHealthReporter = HibikiSourceHealth.store.reporter,
        sourceExecutionPolicy: SourceExecutionPolicy = HibikiSourceHealth.store.executionPolicy,
    ): AnimeSourceRuntime {
        val appContext = context.applicationContext
        val registration = registration(sourceId)
        val effectiveCatalog = catalog
        val source = effectiveCatalog.create(
            sourceId,
            createSourceContext(
                context = appContext,
                client = client,
                sourceId = sourceId,
                catalog = effectiveCatalog,
                sourceHealthReporter = sourceHealthReporter,
                sourceExecutionPolicy = sourceExecutionPolicy,
            ),
        )
        val runtime = AnimeSourceRuntime(
            descriptor = registration.descriptor,
            source = source,
            localizeFilters = registration.localizeFilters,
            normalizeTitleId = registration.normalizeTitleId,
        )
        return runtime
    }

    /** Descriptor for [sourceId], or null if it isn't currently installed - safe to call from composition. */
    fun descriptorOrNull(sourceId: SourceId): AnimeSourceDescriptor? =
        registrationsState.firstOrNull { it.descriptor.id == sourceId }?.descriptor

    fun descriptor(sourceId: SourceId): AnimeSourceDescriptor =
        descriptorOrNull(sourceId) ?: error("Anime source is not registered: $sourceId")

    fun descriptorForTitle(titleId: String, fallbackSourceId: SourceId): AnimeSourceDescriptor =
        descriptor(AnimeKey.parse(titleId)?.sourceId ?: fallbackSourceId)

    fun descriptorForTitleOrNull(titleId: String, fallbackSourceId: SourceId): AnimeSourceDescriptor? =
        descriptorOrNull(AnimeKey.parse(titleId)?.sourceId ?: fallbackSourceId)

    fun descriptorForStoredTitle(titleId: String): AnimeSourceDescriptor =
        descriptor(
            AnimeKey.parse(titleId)?.sourceId
                ?: AppPreferences.DEFAULT_ANIME_SOURCE_ID,
        )

    fun descriptorForStoredTitleOrNull(titleId: String): AnimeSourceDescriptor? =
        descriptorOrNull(
            AnimeKey.parse(titleId)?.sourceId
                ?: AppPreferences.DEFAULT_ANIME_SOURCE_ID,
        )

    private fun registration(sourceId: SourceId): Registration =
        registrationsState.firstOrNull { it.descriptor.id == sourceId }
            ?: throw NoSourcesInstalledException(sourceId)

    /** Known sources get their dedicated icon/legacy-id hooks; anything else gets a generic look. */
    private fun registrationFor(entry: SourceCatalogEntry): Registration = when (entry.info.id.value) {
        "yummy-anime" -> Registration(
            info = entry.info,
            iconRes = R.drawable.animite_media_type_anime,
            normalizeTitleId = YummyIdMigration::normalizeTitleId,
        )
        else -> Registration(info = entry.info, iconRes = R.drawable.animite_media_type_anime)
    }

    private fun createSourceContext(
        context: Context,
        client: HttpClient,
        sourceId: SourceId,
        catalog: SourceCatalog,
        sourceHealthReporter: SourceHealthReporter,
        sourceExecutionPolicy: SourceExecutionPolicy,
    ): DefaultSourceContext = DefaultSourceContext(
        httpClient = client,
        preferredLanguages = listOf(catalog.require(sourceId).primaryLanguage),
        config = SourceConfig.EMPTY,
        logger = SourceLogger { level, message, throwable ->
            val tag = "BeakoKit/${sourceId.value}"
            when (level) {
                SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
            }
        },
        challengeSessionProvider = AndroidChallengeSessionProvider(context),
        browserFetchProvider = AndroidBrowserFetchProvider(context),
        sourceHealthReporter = sourceHealthReporter,
        sourceExecutionPolicy = sourceExecutionPolicy,
    )
}

/** Thrown when no installed source matches - callers already generically handle [org.akkirrai.beakokit.api.SourceException]. */
class NoSourcesInstalledException(sourceId: SourceId) : org.akkirrai.beakokit.api.SourceUnavailableException(
    if (sourceId == AppPreferences.DEFAULT_ANIME_SOURCE_ID) {
        "No anime sources are installed. Install one from the Sources tab."
    } else {
        "Anime source is not installed: ${sourceId.value}. Install it from the Sources tab."
    },
)
