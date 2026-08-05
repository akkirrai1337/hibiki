package org.akkirrai.hibiki.shared.app.shell.onboarding

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.onboarding.AppOnboardingScreen
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

@Composable
internal fun HibikiOnboardingOverlayLayer(
    enabled: Boolean,
    completed: Boolean,
    sources: List<AppSourceDescriptor>,
    initialSourceId: String?,
    systemLanguage: String,
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (String) -> Unit,
) {
    if (enabled && !completed) {
        AppOnboardingScreen(
            sources = sources,
            initialSourceId = initialSourceId,
            systemLanguage = systemLanguage,
            notificationPermissionState = notificationPermissionState,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onComplete = onComplete,
        )
    }
}
