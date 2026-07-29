package org.akkirrai.hibiki.feature.onboarding

import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingScreen
import org.akkirrai.hibiki.shared.onboarding.includeSelectedOnboardingSource
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver

@Composable
fun SharedAndroidOnboardingScreen(
    initialSource: SourceId?,
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemLanguage = remember { LocaleList.getDefault().get(0)?.language.orEmpty() }
    val allSources = AnimeSourceRegistry.sources
    val localizedSources = remember(allSources, systemLanguage) {
        onboardingSourcesForSystemLanguage(allSources, systemLanguage)
    }
    val visibleSources = remember(localizedSources, allSources, initialSource) {
        val sources = if (localizedSources.isEmpty()) allSources else localizedSources
        includeSelectedOnboardingSource(
            allSources = allSources,
            visibleSources = sources,
            selectedKey = initialSource?.value,
            keyOf = { it.id.value },
        )
    }
    val appSources = remember(visibleSources) {
        visibleSources.map { source ->
            AppSourceDescriptor(
                id = source.id.value,
                name = source.name,
                language = source.language.toString(),
                languageTags = source.info.languages.mapTo(linkedSetOf()) { it.tag },
                iconUrl = source.iconUrl,
                supportsPlayback = source.supportsPlayback,
                supportsSearch = true,
            )
        }
    }

    CompositionLocalProvider(
        LocalAppTextResolver provides DefaultAppTextResolver(
            languageMode = org.akkirrai.hibiki.app.settings.LocalAppPreferencesState.current.languageMode,
            systemLanguage = systemLanguage,
        ),
    ) {
        AppOnboardingScreen(
            sources = appSources,
            initialSourceId = initialSource?.value,
            notificationPermissionState = notificationPermissionState,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onComplete = { sourceId -> onComplete(SourceId(sourceId)) },
            sourceIconContent = { source, iconModifier ->
                val descriptor = AnimeSourceRegistry.sources.firstOrNull { it.id.value == source.id }
                AppSourceIconImage(
                    url = source.iconUrl,
                    placeholder = descriptor?.let { painterResource(it.iconRes) },
                    modifier = iconModifier,
                )
            },
            modifier = modifier,
        )
    }
}
