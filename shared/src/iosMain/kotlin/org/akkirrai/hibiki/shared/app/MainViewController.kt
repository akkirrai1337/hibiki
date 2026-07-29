package org.akkirrai.hibiki.shared.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.catalog.IosAnimeCatalogRepository
import org.akkirrai.hibiki.shared.library.IosLibraryRepository
import platform.UIKit.UIViewController
import org.akkirrai.hibiki.shared.settings.IosAppSettingsStore
import org.akkirrai.hibiki.shared.profile.IosLocalProfileRepository

fun MainViewController(systemLanguage: String): UIViewController = ComposeUIViewController {
    val repository = remember(systemLanguage) {
        IosAnimeCatalogRepository(preferEnglish = !systemLanguage.lowercase().startsWith("ru"))
    }
    val libraryRepository = remember { IosLibraryRepository() }
    val profileRepository = remember(libraryRepository) {
        IosLocalProfileRepository(libraryRepository)
    }
    val settingsStore = remember { IosAppSettingsStore() }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    MaterialTheme(
        colorScheme = HibikiLightColorScheme,
        typography = HibikiTypography,
    ) {
        Surface {
            HibikiApp(
                repository = repository,
                libraryRepository = libraryRepository,
                profileRepository = profileRepository,
                settingsStore = settingsStore,
                systemLanguage = systemLanguage,
            )
        }
    }
}
