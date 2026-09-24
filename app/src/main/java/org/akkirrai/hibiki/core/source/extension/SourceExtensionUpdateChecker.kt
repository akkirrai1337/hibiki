package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory

/** App-wide update count for the Sources navigation item. */
class SourceExtensionUpdateChecker private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val client: HttpClient = AndroidHttpClientFactory.create()
    private val _updateCount = MutableStateFlow(0)
    val updateCount: StateFlow<Int> = _updateCount.asStateFlow()

    suspend fun refresh(repositoryUrls: List<String>) {
        val extensions = coroutineScope {
            repositoryUrls.map { url ->
                async {
                    runCatching { ExtensionMarketplaceClient(client, url).fetchCatalog() }
                        .getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
        }.distinctBy(ApkRepositoryExtension::pkg)
        updateFrom(extensions)
    }

    /** Counts installed extensions for which the repositories offer a newer version. */
    suspend fun updateFrom(extensions: List<ApkRepositoryExtension>) {
        val installedVersions = withContext(Dispatchers.IO) { installedVersions(appContext) }
        _updateCount.value = extensions.count { it.isUpdateAvailable(installedVersions) }
    }

    companion object {
        @Volatile
        private var instance: SourceExtensionUpdateChecker? = null

        fun get(context: Context): SourceExtensionUpdateChecker = instance ?: synchronized(this) {
            instance ?: SourceExtensionUpdateChecker(context.applicationContext).also { instance = it }
        }

        /** Package name to version name of every extension installed through Android's installer. */
        fun installedVersions(context: Context): Map<String, String> =
            InstalledApkExtensions.scan(context)
                .filterValues(InstalledApkExtensionInfo::isSystemInstalled)
                .mapValues { it.value.versionName }
    }
}

fun ApkRepositoryExtension.isUpdateAvailable(installedVersions: Map<String, String>): Boolean {
    val installedVersion = installedVersions[pkg] ?: return false
    return isExtensionVersionNewer(version, installedVersion)
}
