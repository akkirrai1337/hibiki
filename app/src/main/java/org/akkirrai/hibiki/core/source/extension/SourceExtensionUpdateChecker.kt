package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/** App-wide update count for the Sources navigation item. */
class SourceExtensionUpdateChecker private constructor(context: Context) {
    private val client: HttpClient = AndroidHttpClientFactory.create()
    private val _updateCount = MutableStateFlow(0)
    val updateCount: StateFlow<Int> = _updateCount.asStateFlow()

    suspend fun refresh(repositoryUrls: List<String>) {
        val extensions = coroutineScope {
            repositoryUrls.map { url ->
                async {
                    runCatching { ExtensionMarketplaceClient(client, url).fetchIndex().extensions }
                        .getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
        }.distinctBy(MarketplaceExtension::id)
        updateFrom(extensions)
    }

    fun updateFrom(extensions: List<MarketplaceExtension>) {
        val installedVersions = AnimeSourceRegistry.installedScriptExtensionVersions()
        val installedResolverVersions = AnimeSourceRegistry.installedPlayerResolverVersions()
        _updateCount.value = extensions
            .filter { it.type == "source" }
            .count { extension ->
                extension.isUpdateAvailable(installedVersions, installedResolverVersions, extensions)
            }
    }

    companion object {
        @Volatile
        private var instance: SourceExtensionUpdateChecker? = null

        fun get(context: Context): SourceExtensionUpdateChecker = instance ?: synchronized(this) {
            instance ?: SourceExtensionUpdateChecker(context.applicationContext).also { instance = it }
        }
    }
}

fun MarketplaceExtension.isUpdateAvailable(
    installedVersions: Map<String, String>,
    installedResolverVersions: Map<String, String>,
    allExtensions: List<MarketplaceExtension>,
): Boolean {
    val installedVersion = installedVersions[id] ?: return false
    return isExtensionVersionNewer(version, installedVersion) || resolverDependencies.any { resolverId ->
        val installedResolver = installedResolverVersions[resolverId] ?: return@any false
        val availableResolver = allExtensions.firstOrNull {
            it.id == resolverId && it.type == "player-resolver"
        }
        availableResolver != null && isExtensionVersionNewer(availableResolver.version, installedResolver)
    }
}
