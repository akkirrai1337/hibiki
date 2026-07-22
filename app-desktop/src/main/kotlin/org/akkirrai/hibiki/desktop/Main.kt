package org.akkirrai.hibiki.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.akkirrai.hibiki.shared.HibikiSharedSmoke

/**
 * Development-only Desktop entry point. This module is a CMP compilation smoke
 * target and is intentionally not configured as a published Hibiki release.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hibiki CMP smoke test",
    ) {
        MaterialTheme {
            Surface {
                HibikiSharedSmoke()
            }
        }
    }
}
