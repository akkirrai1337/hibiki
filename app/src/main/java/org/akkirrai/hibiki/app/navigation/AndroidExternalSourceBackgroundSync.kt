package org.akkirrai.hibiki.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.hibiki.shared.source.ExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.LocalExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRepositoryPlatform
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRuntimeFactory

/** Refreshes external sources in the background without changing the active built-in path. */
@Composable
internal fun AndroidExternalSourceBackgroundSync(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val platform = remember(context) {
        createAndroidExternalSourceRepositoryPlatform(context)
    }
    val coordinator = remember(platform) {
        ExternalSourceRuntimeCoordinator(
            platform = platform,
            catalogCapabilities = { CatalogCapabilities.FULL },
            runtimeFactory = createAndroidExternalSourceRuntimeFactory(),
        )
    }
    LaunchedEffect(coordinator) {
        try {
            coordinator.refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println("BeakoKit external repository refresh failed: ${error.message}")
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
