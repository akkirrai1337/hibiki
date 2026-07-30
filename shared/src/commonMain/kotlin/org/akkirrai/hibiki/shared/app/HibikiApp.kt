package org.akkirrai.hibiki.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.prototype.PrototypeLocalProfileDataRepository
import org.akkirrai.hibiki.shared.prototype.PrototypeLibraryRepository
import org.akkirrai.hibiki.shared.prototype.HibikiAppShell
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.home.HomeDataRepository

/** Canonical shared application entry point for platform hosts. */
@Composable
fun HibikiApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository = PrototypeLibraryRepository,
    profileRepository: LocalProfileDataRepository = PrototypeLocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    onRequestOnboardingNotificationPermission: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
    onProfileAvatarEdit: (((String) -> Unit) -> Unit) = {},
    sources: List<AppSourceDescriptor> = emptyList(),
    selectedSourceId: String? = null,
    onSourceSelected: (String) -> Unit = {},
    showSettingsBackButton: Boolean = false,
    includeNavigationBarPadding: Boolean = true,
    applyStatusBarPadding: Boolean = false,
) {
    HibikiAppShell(
        modifier = modifier,
        repository = repository,
        homeRepository = homeRepository,
        libraryRepository = libraryRepository,
        profileRepository = profileRepository,
        settingsStore = settingsStore,
        systemLanguage = systemLanguage,
        appVersionName = appVersionName,
        enableOnboarding = enableOnboarding,
        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
        onRequestOnboardingNotificationPermission = onRequestOnboardingNotificationPermission,
        onConfigureNotifications = onConfigureNotifications,
        onProfileAvatarEdit = onProfileAvatarEdit,
        sources = sources,
        selectedSourceId = selectedSourceId,
        onSourceSelected = onSourceSelected,
        showSettingsBackButton = showSettingsBackButton,
        includeNavigationBarPadding = includeNavigationBarPadding,
        applyStatusBarPadding = applyStatusBarPadding,
    )
}
