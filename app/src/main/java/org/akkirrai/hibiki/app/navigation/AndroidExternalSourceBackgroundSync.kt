package org.akkirrai.hibiki.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.hibiki.shared.source.ExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.LocalExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRepositoryPlatform
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRuntimeFactory
import org.akkirrai.hibiki.shared.source.validateAndroidExternalSourceRuntime
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.AndroidExternalSourceConfigStore
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.BuildConfig

/** Refreshes external sources in the background without changing the active built-in path. */
@Composable
internal fun AndroidExternalSourceBackgroundSync(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configStore = remember(context) { AndroidExternalSourceConfigStore(context) }
    val platform = remember(context) {
        createAndroidExternalSourceRepositoryPlatform(context)
    }
    // External host requests must not silently follow a redirect outside the manifest policy.
    val runtimeHttpClient = remember {
        HttpClient(OkHttp) {
            followRedirects = false
        }
    }
    DisposableEffect(runtimeHttpClient) {
        onDispose { runtimeHttpClient.close() }
    }
    val coordinator = remember(platform, configStore) {
        ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = createAndroidExternalSourceRuntimeFactory(context),
            sourceContextFactory = { sourceId ->
                DefaultSourceContext(
                    httpClient = runtimeHttpClient,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
                    config = configStore.load(sourceId),
                    logger = SourceLogger { level, message, throwable ->
                        val tag = "BeakoKit/${sourceId.value}"
                        when (level) {
                            SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                            SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                            SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
                        }
                    },
                )
            },
            runtimeInitializer = { sourcePackage, _ ->
                validateAndroidExternalSourceRuntime(sourcePackage)
            },
            reservedSourceIds = if (BuildConfig.DEBUG) {
                emptySet()
            } else {
                AnimeSourceRegistry.sources.mapTo(linkedSetOf()) { it.id }
            },
            autoInstallRebuiltPackages = BuildConfig.DEBUG,
        )
    }
    LaunchedEffect(coordinator) {
        AppLogger.i("BeakoKitExternal", "Starting external repository refresh")
        try {
            withContext(Dispatchers.IO) {
                coordinator.refresh()
            }
            val snapshot = coordinator.snapshot.value
            AppLogger.i(
                "BeakoKitExternal",
                "External repository refresh completed: loaded=${snapshot.repository.loaded.size}, " +
                    "failures=${snapshot.repository.failures.size}, " +
                    "sources=${snapshot.registry?.sources?.size ?: 0}, " +
                    "failure=${snapshot.repository.failures.firstOrNull()?.error?.message}",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w(
                tag = "BeakoKitExternal",
                message = "External repository refresh failed",
                throwable = error,
            )
        }
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.close() }
    }
    CompositionLocalProvider(
        LocalExternalSourceRuntimeCoordinator provides coordinator,
        content = content,
    )
}
