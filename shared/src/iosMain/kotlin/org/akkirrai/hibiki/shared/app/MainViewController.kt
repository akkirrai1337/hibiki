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
import org.akkirrai.hibiki.shared.profile.IosAvatarPicker
import org.akkirrai.hibiki.shared.source.IosSourceRegistry
import org.akkirrai.hibiki.shared.source.IosSourceSelectionRepository

fun MainViewController(systemLanguage: String): UIViewController {
    val avatarPicker = IosAvatarPicker()
    lateinit var hostController: UIViewController
    hostController = ComposeUIViewController {
    val repository = remember(systemLanguage) {
        IosAnimeCatalogRepository(preferEnglish = !systemLanguage.lowercase().startsWith("ru"))
    }
    val libraryRepository = remember { IosLibraryRepository() }
    val profileRepository = remember(libraryRepository) {
        IosLocalProfileRepository(libraryRepository)
    }
    val settingsStore = remember { IosAppSettingsStore() }
    val sourceSelectionRepository = remember { IosSourceSelectionRepository() }
    var selectedSourceId = remember {
        androidx.compose.runtime.mutableStateOf(sourceSelectionRepository.loadSelectedSourceId())
    }
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
                onProfileAvatarEdit = { onPicked ->
                    avatarPicker.present(hostController, onPicked)
                },
                sources = IosSourceRegistry.sources,
                selectedSourceId = selectedSourceId.value,
                onSourceSelected = { sourceId ->
                    sourceSelectionRepository.saveSelectedSourceId(sourceId)
                    selectedSourceId.value = sourceId
                },
            )
        }
    }
    }
    return hostController
}
