package com.sowerrrt.dayloom

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test

class AppLockUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lockedScreenKeepsContentHiddenAndOffersRecoveryWhenAuthenticationIsUnavailable() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                DayloomTheme(ThemeMode.SYSTEM, AccentPalette.VIOLET) {
                    AppLockedContent(
                        error = AppLockError.UNAVAILABLE,
                        onUnlock = {},
                        onDisableUnavailableLock = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("app_locked").assertExists()
        composeRule.onNodeWithTag("unlock_app").assertExists()
        composeRule.onNodeWithTag("app_lock_error").assertExists()
        composeRule.onNodeWithTag("disable_app_lock").assertExists()
    }
}
