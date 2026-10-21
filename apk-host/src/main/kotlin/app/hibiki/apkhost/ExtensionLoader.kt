package app.hibiki.apkhost

import app.hibiki.apk.ApkSourceAdapter
import app.hibiki.apk.InstalledApkExtension
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Loads extension code on a JVM. An APK holds Dalvik bytecode; dex2jar turns it into class files
 * once (cached beside the extension, keyed by its version), and the classes are then loaded by a
 * class loader that prefers the extension's own classes and falls back to the host's - which is
 * where the Aniyomi API, OkHttp, Jsoup, Kotlin and the android.* stand-ins come from.
 */
object ExtensionLoader {
    /** Bumped when the conversion or the repairs change, so jars made by an older host are made again. */
    const val CONVERTER_VERSION = 2

    /** Class files for [apk], converted now unless an up-to-date copy is in [cacheDir]. */
    fun jarFor(extension: InstalledApkExtension, cacheDir: File): File {
        cacheDir.mkdirs()
        val jar = File(cacheDir, "${extension.packageName}-${extension.versionCode}-c$CONVERTER_VERSION.jar")
        if (jar.isFile && jar.lastModified() >= extension.file.lastModified()) return jar
        // Old conversions of this package (earlier versions) are dropped.
        cacheDir.listFiles { f -> f.name.startsWith("${extension.packageName}-") && f != jar }?.forEach(File::delete)
        val temporary = File(cacheDir, jar.name + ".part")
        convert(extension.file, temporary)
        JarFixer.fix(temporary)
        if (!temporary.renameTo(jar)) {
            jar.delete()
            check(temporary.renameTo(jar)) { "Could not store the converted extension" }
        }
        return jar
    }

    fun convert(apk: File, jar: File) {
        val handler = BaksmaliBaseDexExceptionHandler()
        // The whole APK, not one dex: the reader takes every classes*.dex out of a zip.
        Dex2jar.from(MultiDexFileReader.open(apk.readBytes()))
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(true)
            .to(jar.toPath())
        check(!handler.hasException()) { "The extension's code could not be fully converted" }
    }

    /** The entry class an extension declares, with a leading dot or a bare name read as inside its own package. */
    fun entryClassName(extension: InstalledApkExtension): String {
        val declared = extension.sourceClassName ?: error("The extension does not declare a source class")
        return when {
            declared.startsWith('.') -> extension.packageName + declared
            '.' !in declared -> "${extension.packageName}.$declared"
            else -> declared
        }
    }

    /** The converted classes first, then the APK itself, which is where an extension's resources (its assets) live. */
    fun classLoader(jar: File, apk: File): URLClassLoader =
        ExtensionClassLoader(arrayOf(jar.toURI().toURL(), apk.toURI().toURL()), AnimeSource::class.java.classLoader)

    /** The sources an extension creates; throws with a message fit to show when it cannot start. */
    fun createSources(extension: InstalledApkExtension, classLoader: ClassLoader): List<AnimeCatalogueSource> {
        val entryClassName = entryClassName(extension)
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
        val catalogues = sources.map { it as? AnimeCatalogueSource ?: error("A source from ${extension.packageName} is not an anime catalogue source") }
        check(catalogues.isNotEmpty()) { "The extension created no sources" }
        return catalogues
    }

    fun adapters(extension: InstalledApkExtension, classLoader: ClassLoader): List<ApkSourceAdapter> =
        createSources(extension, classLoader).map { ApkSourceAdapter(extension, it) }
}

/**
 * Child-first: a class the extension ships is taken from its jar before the host's, as on Android,
 * where extension code is its own dex. The JDK is never overridden, and a class the jar lacks
 * comes from the host (the Aniyomi API, OkHttp, Kotlin and the rest).
 */
class ExtensionClassLoader(urls: Array<URL>, parent: ClassLoader?) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        var loaded = findLoadedClass(name)
        if (loaded == null && !name.startsWith("java.") && !name.startsWith("javax.") && !isHostClass(name)) {
            loaded = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
        if (loaded == null) loaded = super.loadClass(name, false)
        if (resolve) resolveClass(loaded)
        loaded!!
    }

    /** What the extension must share with the host rather than carry its own copy of: the stand-in platform, the Aniyomi API and Rhino. */
    private fun isHostClass(name: String) =
        name.startsWith("android.") || name.startsWith("androidx.") || name.startsWith("eu.kanade.tachiyomi.") ||
            // A copy of Rhino inside an extension does not survive dex2jar (its parser fails verification); the host has the real one.
            name.startsWith("org.mozilla.javascript.")
}
