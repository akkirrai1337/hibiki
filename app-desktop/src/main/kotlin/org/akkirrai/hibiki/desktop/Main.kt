package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.app.HibikiApp

/**
 * Desktop entry point for the shared Hibiki prototype.
 */
fun main() = application {
    val catalogRepository = DesktopCatalogRepository()
    Window(
        onCloseRequest = {
            catalogRepository.close()
            exitApplication()
        },
        title = "hibiki",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = HibikiLightColorScheme, typography = HibikiTypography) {
            Surface {
                val settingsStore = androidx.compose.runtime.remember { DesktopSettingsStore() }
                HibikiApp(repository = catalogRepository, settingsStore = settingsStore)
            }
        }
    }
}
