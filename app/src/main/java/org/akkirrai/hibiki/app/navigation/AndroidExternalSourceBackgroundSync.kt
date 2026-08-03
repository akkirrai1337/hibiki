package org.akkirrai.hibiki.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.shared.source.ExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.LocalExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRepositoryPlatform
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRuntimeFactory
import org.akkirrai.hibiki.shared.source.validateAndroidExternalSourceRuntime
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.AndroidExternalSourceConfigStore

/** Refreshes external sources in the background without changing the active built-in path. */
@Composable
internal fun AndroidExternalSourceBackgroundSync(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configStore = remember(context) { AndroidExternalSourceConfigStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
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
                )
            },
            runtimeInitializer = { sourcePackage, _ ->
                validateAndroidExternalSourceRuntime(sourcePackage)
            },
            reservedSourceIds = AnimeSourceRegistry.sources.mapTo(linkedSetOf()) { it.id },
        )
    }
    LaunchedEffect(coordinator, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                coordinator.refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                println("BeakoKit external repository refresh failed: ${error.message}")
            }
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
