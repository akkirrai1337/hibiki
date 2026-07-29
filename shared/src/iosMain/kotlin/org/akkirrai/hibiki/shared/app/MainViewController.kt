package org.akkirrai.hibiki.shared.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.catalog.IosAnimeCatalogRepository
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val repository = remember { IosAnimeCatalogRepository() }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    MaterialTheme(
        colorScheme = HibikiLightColorScheme,
        typography = HibikiTypography,
    ) {
        Surface {
            HibikiApp(repository = repository)
        }
    }
}
