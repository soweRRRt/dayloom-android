package com.sowerrrt.dayloom.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStatusTest {
    @Test
    fun `permission is enabled only when runtime and system settings allow notifications`() {
        assertEquals(
            NotificationPermissionStatus.ENABLED,
            resolveNotificationPermissionStatus(
                runtimePermissionGranted = true,
                systemNotificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `denied runtime permission disables notifications`() {
        assertEquals(
            NotificationPermissionStatus.DISABLED,
            resolveNotificationPermissionStatus(
                runtimePermissionGranted = false,
                systemNotificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `disabled app notifications override granted runtime permission`() {
        assertEquals(
            NotificationPermissionStatus.DISABLED,
            resolveNotificationPermissionStatus(
                runtimePermissionGranted = true,
                systemNotificationsEnabled = false,
            ),
        )
    }
}
