package org.akkirrai.hibiki.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionStateTest {
    @Test
    fun exposesStablePermissionStates() {
        assertEquals(
            listOf(NotificationPermissionState.NOT_ASKED, NotificationPermissionState.GRANTED, NotificationPermissionState.DENIED),
            NotificationPermissionState.entries,
        )
    }
}
