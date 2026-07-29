package org.akkirrai.hibiki.shared.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.catalog.IosMultiSourceAnimeCatalogRepository
import org.akkirrai.hibiki.shared.home.CatalogBackedHomeDataRepository
import org.akkirrai.hibiki.shared.library.IosLibraryRepository
import platform.UIKit.UIViewController
import platform.Foundation.NSBundle
import org.akkirrai.hibiki.shared.settings.IosAppSettingsStore
import org.akkirrai.hibiki.shared.profile.IosLocalProfileRepository
import org.akkirrai.hibiki.shared.profile.IosAvatarPicker
import org.akkirrai.hibiki.shared.source.IosSourceRegistry
import org.akkirrai.hibiki.shared.source.IosSourceSelectionRepository

fun MainViewController(systemLanguage: String): UIViewController {
    val avatarPicker = IosAvatarPicker()
    lateinit var hostController: UIViewController
    hostController = ComposeUIViewController {
    val sourceSelectionRepository = remember { IosSourceSelectionRepository() }
    val initialSourceId = remember { sourceSelectionRepository.loadSelectedSourceId() }
    val repository = remember(systemLanguage, initialSourceId) {
        IosMultiSourceAnimeCatalogRepository(
            preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
            initialSourceId = initialSourceId,
        )
    }
    val libraryRepository = remember { IosLibraryRepository() }
    val homeRepository = remember(repository, libraryRepository) {
        CatalogBackedHomeDataRepository(repository, libraryRepository)
    }
    val profileRepository = remember(libraryRepository) {
        IosLocalProfileRepository(libraryRepository)
    }
    val settingsStore = remember { IosAppSettingsStore() }
    var selectedSourceId = remember {
        androidx.compose.runtime.mutableStateOf(initialSourceId)
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
                homeRepository = homeRepository,
                libraryRepository = libraryRepository,
                profileRepository = profileRepository,
                settingsStore = settingsStore,
                systemLanguage = systemLanguage,
                appVersionName = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
                    ?: "dev",
                onProfileAvatarEdit = { onPicked ->
                    avatarPicker.present(hostController, onPicked)
                },
                sources = IosSourceRegistry.sources,
                selectedSourceId = selectedSourceId.value,
                onSourceSelected = { sourceId ->
                    repository.selectSource(sourceId)
                    sourceSelectionRepository.saveSelectedSourceId(sourceId)
                    selectedSourceId.value = sourceId
                },
            )
        }
    }
    }
    return hostController
}
