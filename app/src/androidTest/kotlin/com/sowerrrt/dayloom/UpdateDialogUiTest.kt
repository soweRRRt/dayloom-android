package com.sowerrrt.dayloom

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.updates.SemVer
import com.sowerrrt.dayloom.core.updates.UpdateInfo
import org.junit.Rule
import org.junit.Test

class UpdateDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun updateDialogShowsVersionNotesAndActions() {
        composeRule.setContent {
            DayloomTheme(ThemeMode.LIGHT, AccentPalette.VIOLET) {
                UpdateAvailableDialog(
                    update =
                        UpdateInfo(
                            version = requireNotNull(SemVer.parseOrNull("1.2.0")),
                            title = "Dayloom 1.2.0",
                            notes = "A calmer release.",
                            releasePageUrl = "https://github.com/soweRRRt/dayloom-android/releases/tag/v1.2.0",
                        ),
                    onOpenRelease = {},
                    onLater = {},
                )
            }
        }

        composeRule.onNodeWithTag("update_dialog_title").assertExists()
        composeRule.onNodeWithText("1.2.0", substring = true).assertExists()
        composeRule.onNodeWithText("A calmer release.").assertExists()
        composeRule.onNodeWithTag("update_dialog_open").assertExists()
        composeRule.onNodeWithTag("update_dialog_later").assertExists()
    }
}
