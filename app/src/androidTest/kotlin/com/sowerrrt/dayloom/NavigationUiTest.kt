package com.sowerrrt.dayloom

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryNavigationOpensHabits() {
        composeRule.onNodeWithText("Habits").performClick()
        composeRule.onNodeWithText("Your first rhythm starts here").assertExists()
    }

    @Test
    fun themeAndStartScreenSelectionsSurviveRecreation() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Dark").performClick().assertIsSelected()
        composeRule.onNodeWithText("Plan").performClick().assertIsSelected()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("A clear day, ready for you").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
