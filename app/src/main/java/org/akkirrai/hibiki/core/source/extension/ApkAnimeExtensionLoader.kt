package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.io.File

/** Loads an Android-installed Aniyomi APK from Hibiki's private execution copy. */
class ApkAnimeExtensionLoader(context: Context) {
    private val appContext = context.applicationContext

    fun load(packageName: String): LoadedAnimeExtension {
        val info = InstalledApkExtensions.scan(appContext)[packageName]
            ?: throw ApkExtensionLoadException("Extension '$packageName' is not installed")
        if (!info.isSystemInstalled) {
            throw ApkExtensionLoadException("Extension '$packageName' is not installed as an Android package")
        }
        if (!info.isTrusted) {
            throw ApkExtensionLoadException("Extension '$packageName' signing certificate is not trusted")
        }
        if (info.apiVersion == null || info.apiVersion !in SUPPORTED_API_VERSIONS) {
            throw ApkExtensionLoadException(
                "This extension uses Aniyomi API ${info.apiVersion ?: "unknown"}; " +
                    "Hibiki currently supports API ${SUPPORTED_API_VERSIONS.first}-${SUPPORTED_API_VERSIONS.last}.",
            )
        }

        val apk = InstalledApkExtensions.apkFile(appContext, packageName)
            ?: throw ApkExtensionLoadException("APK file for '$packageName' is missing")
        val declaredEntryClassName = info.sourceClassName
            ?: throw ApkExtensionLoadException("Extension '$packageName' does not declare a source class")
        val entryClassName = when {
            declaredEntryClassName.startsWith('.') -> packageName + declaredEntryClassName
            '.' !in declaredEntryClassName -> "$packageName.$declaredEntryClassName"
            else -> declaredEntryClassName
        }

        try {
            // Also fixes copies created by older builds, which left this DEX input writable.
            InstalledApkExtensions.makeReadOnly(apk)
            val optimizedDirectory = File(appContext.codeCacheDir, "aniyomi/$packageName").apply { mkdirs() }
            val classLoader = DexClassLoader(
                apk.absolutePath,
                optimizedDirectory.absolutePath,
                null,
                AnimeSource::class.java.classLoader,
            )
            val entryClass = Class.forName(entryClassName, true, classLoader)
            val sources = when {
                AnimeSourceFactory::class.java.isAssignableFrom(entryClass) -> {
                    (entryClass.getDeclaredConstructor().newInstance() as AnimeSourceFactory).createSources()
                }
                AnimeCatalogueSource::class.java.isAssignableFrom(entryClass) -> {
                    listOf(entryClass.getDeclaredConstructor().newInstance() as AnimeCatalogueSource)
                }
                else -> throw ApkExtensionLoadException(
                    "Entry class '$entryClassName' is neither an anime catalogue source nor a source factory",
                )
            }

            if (sources.isEmpty()) {
                throw ApkExtensionLoadException("Extension '$packageName' did not create any sources")
            }
            val catalogSources = sources.mapIndexed { index, source ->
                source as? AnimeCatalogueSource
                    ?: throw ApkExtensionLoadException(
                        "Source #${index + 1} from '$packageName' is not an anime catalogue source",
                    )
            }
            val duplicateIds = catalogSources.groupingBy(AnimeCatalogueSource::id).eachCount()
                .filterValues { it > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                throw ApkExtensionLoadException(
                    "Extension '$packageName' created duplicate source IDs: ${duplicateIds.joinToString()}",
                )
            }

            return LoadedAnimeExtension(
                packageName = packageName,
                versionName = info.versionName,
                classLoader = classLoader,
                sources = catalogSources,
            )
        } catch (error: ApkExtensionLoadException) {
            throw error
        } catch (error: java.lang.reflect.InvocationTargetException) {
            // The constructor threw; its real cause (often a missing class) is what matters.
            val cause = error.targetException ?: error
            throw ApkExtensionLoadException(
                "Source class '$entryClassName' from '$packageName' failed to initialize: " +
                    (cause.message ?: cause.javaClass.simpleName),
                cause,
            )
        } catch (error: Exception) {
            throw ApkExtensionLoadException(
                "Could not load source class '$entryClassName' from '$packageName': ${error.message ?: error.javaClass.simpleName}",
                error,
            )
        } catch (error: LinkageError) {
            throw ApkExtensionLoadException(
                "Extension '$packageName' references an API class or method Hibiki does not provide: " +
                    (error.message ?: error.javaClass.simpleName),
                error,
            )
        }
    }

    private companion object {
        /** extensions-lib v14 through v16 (hosters, seasons and related titles arrived in 16). */
        val SUPPORTED_API_VERSIONS = 14..16
    }
}

/** Keeps the DEX class loader alive for as long as any of its source instances are registered. */
class LoadedAnimeExtension internal constructor(
    val packageName: String,
    val versionName: String,
    val classLoader: ClassLoader,
    val sources: List<AnimeCatalogueSource>,
)

/** A third-party extension failed while running, or exceeded its time budget. */
class ApkExtensionRuntimeException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class ApkExtensionLoadException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
