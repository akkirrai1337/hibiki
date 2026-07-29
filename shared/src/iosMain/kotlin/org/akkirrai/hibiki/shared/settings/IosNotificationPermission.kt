package org.akkirrai.hibiki.shared.settings

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

internal fun requestIosNotificationPermission(
    onResult: (NotificationPermissionState) -> Unit,
) {
    UNUserNotificationCenter.currentNotificationCenter()
        .requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionBadge or
                UNAuthorizationOptionSound,
        ) { granted, _ ->
            onResult(
                if (granted) {
                    NotificationPermissionState.GRANTED
                } else {
                    NotificationPermissionState.DENIED
                },
            )
        }
}
