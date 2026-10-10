package app.hibiki.apk

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.io.File

/** One extension whose code is loaded, with the sources it created (one APK can hold several). */
class LoadedApkExtension(
    val extension: InstalledApkExtension,
    /** Kept alive with the sources: their classes come from it. */
    val classLoader: ClassLoader,
    val adapters: List<ApkSourceAdapter>,
)

/** An installed extension that could not be loaded, and why - shown on the Sources screen. */
data class ApkLoadFailure(val extension: InstalledApkExtension, val message: String)

/**
 * The APK sources available right now: every installed, trusted extension's code loaded (with the
 * host's class loader as parent, so the Aniyomi API and its libraries come from the app - see
 * eu/kanade/tachiyomi), and its sources by the id the page addresses them with.
 */
object ApkSourceRegistry {
    private const val TAG = "ApkSources"

    @Volatile private var loaded: Map<String, LoadedApkExtension> = emptyMap()
    @Volatile private var failures: List<ApkLoadFailure> = emptyList()
    private val lock = Any()
    private val firstLoad = java.util.concurrent.CountDownLatch(1)

    /** Waits (at most [timeoutMs]) for the installed extensions' first load - for callers that may
     * arrive before it, such as a poster the page shows from its cache at startup. */
    fun awaitFirstLoad(timeoutMs: Long) {
        firstLoad.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun extensions(): Collection<LoadedApkExtension> = loaded.values
    fun failures(): List<ApkLoadFailure> = failures

    fun adapter(sourceId: String): ApkSourceAdapter? =
        loaded.values.asSequence().flatMap { it.adapters.asSequence() }.firstOrNull { it.id == sourceId }

    /** Loads what is installed and trusted, keeping extensions already loaded at the same version. */
    fun refresh(context: Context) = synchronized(lock) {
        try {
            loadInstalled(context)
        } finally {
            firstLoad.countDown()
        }
    }

    private fun loadInstalled(context: Context) {
        val installed = ApkExtensionStore.list(context)
        val next = mutableMapOf<String, LoadedApkExtension>()
        val failed = mutableListOf<ApkLoadFailure>()
        for (extension in installed) {
            if (!extension.isTrusted) continue
            val current = loaded[extension.packageName]
            if (current != null && current.extension.versionCode == extension.versionCode) {
                next[extension.packageName] = current
                continue
            }
            try {
                next[extension.packageName] = load(context, extension)
            } catch (error: Throwable) {
                Log.w(TAG, "loading ${extension.packageName} failed", error)
                failed += ApkLoadFailure(extension, error.message ?: error.javaClass.simpleName)
            }
        }
        loaded = next
        failures = failed
    }

    private fun load(context: Context, extension: InstalledApkExtension): LoadedApkExtension {
        val declared = extension.sourceClassName ?: error("The extension does not declare a source class")
        val packageName = extension.packageName
        val entryClassName = when {
            declared.startsWith('.') -> packageName + declared
            '.' !in declared -> "$packageName.$declared"
            else -> declared
        }
        val optimized = File(context.codeCacheDir, "apk-sources/$packageName").apply { mkdirs() }
        val classLoader = DexClassLoader(extension.file.absolutePath, optimized.absolutePath, null, AnimeSource::class.java.classLoader)
        val sources = try {
            val entry = Class.forName(entryClassName, true, classLoader)
            when {
                AnimeSourceFactory::class.java.isAssignableFrom(entry) ->
                    (entry.getDeclaredConstructor().newInstance() as AnimeSourceFactory).createSources()
                AnimeCatalogueSource::class.java.isAssignableFrom(entry) ->
                    listOf(entry.getDeclaredConstructor().newInstance() as AnimeCatalogueSource)
                else -> error("Entry class '$entryClassName' is neither an anime catalogue source nor a source factory")
            }
        } catch (error: java.lang.reflect.InvocationTargetException) {
            val cause = error.targetException ?: error
            throw IllegalStateException("The source failed to start: ${cause.message ?: cause.javaClass.simpleName}", cause)
        } catch (error: LinkageError) {
            throw IllegalStateException("The extension needs something the app does not provide: ${error.message ?: error.javaClass.simpleName}", error)
        }
        val catalogues = sources.map { it as? AnimeCatalogueSource ?: error("A source from $packageName is not an anime catalogue source") }
        check(catalogues.isNotEmpty()) { "The extension created no sources" }
        return LoadedApkExtension(extension, classLoader, catalogues.map { ApkSourceAdapter(extension, it) })
    }
}
