package org.akkirrai.hibiki.core.source

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.core.source.extension.AniyomiAnimeSourceAdapter
import org.akkirrai.hibiki.core.source.extension.ApkAnimeExtensionLoader
import org.akkirrai.hibiki.core.source.extension.LoadedAnimeExtension
import org.akkirrai.hibiki.core.source.extension.InstalledApkExtensionInfo
import org.akkirrai.hibiki.core.source.extension.InstalledApkExtensions
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.RememberedAnimeSourceAppearance
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.log.AppLogger
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    companion object {
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
 * Registers every anime source Hibiki knows about. All of them are Aniyomi-compatible APK
 * extensions installed through Android's package installer; none is compiled in and none is
 * installed by default. Call [initialize] once at application startup; until [refreshApkExtensions]
 * has run, [sources] is simply empty.
 */
object AnimeSourceRegistry {
    private data class Registration(
        val info: SourceInfo,
        @param:DrawableRes val iconRes: Int,
        val localizeFilters: (AnimeSearchFilterCatalog, Boolean) -> AnimeSearchFilterCatalog = { catalog, _ -> catalog },
        val normalizeTitleId: (String) -> String = { it },
        val runtimeSource: org.akkirrai.beakokit.api.AnimeSource,
    ) {
        val descriptor = AnimeSourceDescriptor(info = info, iconRes = iconRes)
    }

    @Volatile
    private var applicationContext: Context? = null

    // Compose state (not just @Volatile) so screens reading `sources`/`catalog` recompose the
    // moment an extension is installed/uninstalled, without needing their own ViewModel plumbing.
    private var registrationsState by mutableStateOf<List<Registration>>(emptyList())
    private var apkRuntimeSourcesState by mutableStateOf<Map<SourceId, org.akkirrai.beakokit.api.AnimeSource>>(emptyMap())
    private var apkExtensionLoadErrorsState by mutableStateOf<Map<String, String>>(emptyMap())
    // What was installed when the extensions were last loaded; null until the first load has finished, so
    // a screen can tell "nothing has loaded yet" from "these extensions produced no source".
    private var installedApkState by mutableStateOf<Map<String, InstalledApkExtensionInfo>?>(null)
    // Retain APK class loaders while their adapters remain registered.
    private var loadedApkExtensionsState by mutableStateOf<List<LoadedAnimeExtension>>(emptyList())
    private val apkRefreshMutex = Mutex()
    // Bumped whenever the set of loaded sources changes, so long-lived AnimeSourceRuntimeManager
    // instances (one per repository, surviving across screen navigation) discard runtimes they
    // created for sources that have since been replaced.
    private val extensionGenerationCounter = AtomicInteger(0)
    val extensionGeneration: Int
        get() = extensionGenerationCounter.get()

    /** Remembers the application context; call once from application startup. */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    /** Loads only APKs installed by Android's package installer. DEX work runs off the UI thread. */
    suspend fun refreshApkExtensions(context: Context) {
        val appContext = context.applicationContext
        apkRefreshMutex.withLock {
            var scanned: Map<String, InstalledApkExtensionInfo> = emptyMap()
            val (loaded, adapters, loadErrors) = withContext(Dispatchers.IO) {
                val loader = ApkAnimeExtensionLoader(appContext)
                val errors = mutableMapOf<String, String>()
                scanned = InstalledApkExtensions.scan(appContext)
                val loadedExtensions = scanned.toSortedMap().asSequence()
                    .filter { (_, info) -> info.isSystemInstalled && info.isTrusted }
                    .mapNotNull { (packageName, _) ->
                        runCatching { loader.load(packageName) }
                            .onFailure { error ->
                                errors[packageName] = error.message ?: error.javaClass.simpleName
                                AppLogger.w("ApkAnimeExtensions", "Could not load $packageName", error)
                            }
                            .getOrNull()
                    }
                    .toList()
                val adaptationErrors = mutableMapOf<String, String>()
                val adaptedSources = loadedExtensions.flatMap { extension ->
                    extension.sources.mapNotNull { source ->
                        runCatching { AniyomiAnimeSourceAdapter(extension.packageName, source) }
                            .onFailure { error ->
                                adaptationErrors[extension.packageName] =
                                    error.message ?: error.javaClass.simpleName
                                AppLogger.w(
                                    "ApkAnimeExtensions",
                                    "Could not adapt ${extension.packageName}/${source.name}",
                                    error,
                                )
                            }
                            .getOrNull()
                    }
                }
                loadedExtensions.forEach { extension ->
                    if (extension.sources.isNotEmpty() && adaptedSources.none { it.packageName == extension.packageName }) {
                        errors.putIfAbsent(
                            extension.packageName,
                            adaptationErrors[extension.packageName] ?: "No compatible catalogue sources were created.",
                        )
                    }
                }
                Triple(loadedExtensions, adaptedSources, errors + adaptationErrors)
            }
            val duplicateIds = adapters.groupingBy { it.info.id }.eachCount().filterValues { it > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                AppLogger.w("ApkAnimeExtensions", "Skipping duplicate adapted source IDs: ${duplicateIds.joinToString()}")
            }
            val uniqueAdapters = adapters.distinctBy { it.info.id }
            withContext(Dispatchers.Main.immediate) {
                loadedApkExtensionsState = loaded
                apkExtensionLoadErrorsState = loadErrors
                installedApkState = scanned
                apkRuntimeSourcesState = uniqueAdapters.associateBy({ it.info.id }, { it })
                registrationsState = currentRegistrations()
                applicationContext?.let { app ->
                    AppPreferences.rememberAnimeSourceAppearances(
                        app,
                        registrationsState.associate { registration ->
                            registration.descriptor.id.value to RememberedAnimeSourceAppearance(
                                name = registration.descriptor.name,
                                iconUrl = registration.descriptor.iconUrl,
                            )
                        },
                    )
                }
                extensionGenerationCounter.incrementAndGet()
            }
        }
    }

    private fun currentRegistrations(): List<Registration> =
        apkRuntimeSourcesState.map { (id, source) ->
            Registration(
                info = source.info,
                iconRes = R.drawable.animite_media_type_anime,
                runtimeSource = source,
            ).also { check(it.info.id == id) }
        }

    val sources: List<AnimeSourceDescriptor>
        get() = registrationsState.map(Registration::descriptor)

    fun createRuntime(
        context: Context,
        sourceId: SourceId = AppPreferences.readState(context).animeSource,
    ): AnimeSourceRuntime {
        val registration = registration(sourceId)
        return AnimeSourceRuntime(
            descriptor = registration.descriptor,
            source = registration.runtimeSource,
            localizeFilters = registration.localizeFilters,
            normalizeTitleId = registration.normalizeTitleId,
            statusLabel = { status -> status.localizedDisplayName(context) },
        )
    }

    /** Descriptor for [sourceId], or null if it isn't currently installed - safe to call from composition. */
    fun descriptorOrNull(sourceId: SourceId): AnimeSourceDescriptor? =
        registrationsState.firstOrNull { it.descriptor.id == sourceId }?.descriptor

    fun sourceIdsForApkPackage(packageName: String): List<SourceId> =
        apkRuntimeSourcesState.entries
            .filter { (_, source) ->
                (source as? AniyomiAnimeSourceAdapter)?.packageName == packageName
            }
            .map { it.key }

    fun apkPackageForSource(sourceId: SourceId): String? =
        (apkRuntimeSourcesState[sourceId] as? AniyomiAnimeSourceAdapter)?.packageName

    /** Settings of an APK source, or null when it has none. */
    fun apkSourceSettings(sourceId: SourceId): ApkSourceSettings? {
        val adapter = apkRuntimeSourcesState[sourceId] as? AniyomiAnimeSourceAdapter ?: return null
        val configurable = adapter.configurableSource ?: return null
        return ApkSourceSettings(adapter.aniyomiSourceId, configurable)
    }

    class ApkSourceSettings(val sourceId: Long, val configurable: eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource)

    fun apkExtensionLoadErrors(): Map<String, String> = apkExtensionLoadErrorsState

    /** The installed extensions as of the last load, or null while the first load has not finished. */
    fun installedApkExtensions(): Map<String, InstalledApkExtensionInfo>? = installedApkState

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

    /** Display information for stored cards, including sources whose extension was removed. */
    fun appearanceForStoredTitleOrNull(titleId: String): StoredSourceAppearance? {
        val sourceId = AnimeKey.parse(titleId)?.sourceId ?: AppPreferences.DEFAULT_ANIME_SOURCE_ID
        descriptorOrNull(sourceId)?.let { source ->
            return StoredSourceAppearance(
                name = source.name,
                iconUrl = source.iconUrl,
                iconRes = source.iconRes,
                isInstalled = true,
            )
        }
        val remembered = applicationContext?.let {
            AppPreferences.rememberedAnimeSourceAppearance(it, sourceId)
        }
        return StoredSourceAppearance(
            name = remembered?.name ?: sourceId.value.takeIf(String::isNotBlank) ?: return null,
            iconUrl = remembered?.iconUrl,
            iconRes = R.drawable.animite_media_type_anime,
            isInstalled = false,
        )
    }

    private fun registration(sourceId: SourceId): Registration =
        registrationsState.firstOrNull { it.descriptor.id == sourceId }
            ?: throw NoSourcesInstalledException(sourceId)
}

data class StoredSourceAppearance(
    val name: String,
    val iconUrl: String?,
    @param:DrawableRes val iconRes: Int,
    val isInstalled: Boolean,
)

/** Thrown when no installed source matches - callers already generically handle [org.akkirrai.beakokit.api.SourceException]. */
class NoSourcesInstalledException(sourceId: SourceId) : org.akkirrai.beakokit.api.SourceUnavailableException(
    if (sourceId == AppPreferences.DEFAULT_ANIME_SOURCE_ID) {
        "No anime sources are installed. Install one from the Sources tab."
    } else {
        "Anime source is not installed: ${sourceId.value}. Install it from the Sources tab."
    },
)
