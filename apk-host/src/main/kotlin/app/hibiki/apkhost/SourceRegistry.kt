package app.hibiki.apkhost

import android.util.Log
import app.hibiki.apk.ApkSourceAdapter
import app.hibiki.apk.InstalledApkExtension
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
 * The APK sources available right now: every installed, trusted extension's code loaded, and its
 * sources by the id the page addresses them with.
 */
class SourceRegistry(private val store: ExtensionStore, private val cacheDir: File) {
    @Volatile private var loaded: Map<String, LoadedApkExtension> = emptyMap()
    @Volatile private var failures: List<ApkLoadFailure> = emptyList()
    private val lock = Any()

    fun extensions(): Collection<LoadedApkExtension> = loaded.values
    fun failures(): List<ApkLoadFailure> = failures

    fun adapter(sourceId: String): ApkSourceAdapter? =
        loaded.values.asSequence().flatMap { it.adapters.asSequence() }.firstOrNull { it.id == sourceId }

    /** Loads what is installed and trusted, keeping extensions already loaded at the same version. */
    fun refresh() = synchronized(lock) {
        val next = mutableMapOf<String, LoadedApkExtension>()
        val failed = mutableListOf<ApkLoadFailure>()
        for (extension in store.list()) {
            if (!extension.isTrusted) continue
            val current = loaded[extension.packageName]
            if (current != null && current.extension.versionCode == extension.versionCode) {
                next[extension.packageName] = current
                continue
            }
            try {
                val jar = ExtensionLoader.jarFor(extension, cacheDir)
                val classLoader = ExtensionLoader.classLoader(jar, extension.file)
                next[extension.packageName] = LoadedApkExtension(extension, classLoader, ExtensionLoader.adapters(extension, classLoader))
            } catch (error: Throwable) {
                Log.w(TAG, "loading ${extension.packageName} failed", error)
                failed += ApkLoadFailure(extension, error.message ?: error.javaClass.simpleName)
            }
        }
        // Extensions that left (uninstalled, or replaced by a new version) release their class loaders.
        (loaded.values - next.values.toSet()).forEach { runCatching { (it.classLoader as? java.io.Closeable)?.close() } }
        loaded = next
        failures = failed
    }

    /**
     * Lets go of an extension's class loader, and with it the open handle on its files: Windows
     * will not replace or delete a file something still holds open. The next [refresh] loads it
     * again if it is still installed.
     */
    fun unload(packageName: String) = synchronized(lock) {
        val gone = loaded[packageName] ?: return@synchronized
        loaded = loaded - packageName
        runCatching { (gone.classLoader as? java.io.Closeable)?.close() }
    }

    private companion object {
        const val TAG = "ApkSources"
    }
}
