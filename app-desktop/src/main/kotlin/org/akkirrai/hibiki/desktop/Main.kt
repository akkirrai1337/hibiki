package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.app.HibikiApp
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

/** Desktop entry point for the production shared shell. */
fun main() = application {
    val catalogRepository = remember { DesktopCatalogRepository() }
    val homeRepository = remember(catalogRepository) { DesktopHomeRepository(catalogRepository) }
    val watchRepository = remember { DesktopAnimeWatchRepository() }
    Window(
        onCloseRequest = {
            catalogRepository.close()
            watchRepository.close()
            exitApplication()
        },
        title = "hibiki",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = HibikiLightColorScheme, typography = HibikiTypography) {
            CompositionLocalProvider(
                LocalAppLayoutEnvironment provides AppLayoutEnvironment(
                    isProvided = true,
                ),
            ) {
                DisposableEffect(Unit) {
                    onDispose {
                        catalogRepository.close()
                        watchRepository.close()
                    }
                }
                Surface(modifier = Modifier) {
                    HibikiApp(
                        repository = catalogRepository,
                        homeRepository = homeRepository,
                        watchRepository = watchRepository,
                        playbackHost = { playback, context, onBack, _ ->
                            DesktopVlcPlaybackHost(
                                playback = playback,
                                context = context,
                                onBack = onBack,
                            )
                        },
                        systemLanguage = "en",
                        includeNavigationBarPadding = true,
                    )
                }
            }
        }
    }
}
