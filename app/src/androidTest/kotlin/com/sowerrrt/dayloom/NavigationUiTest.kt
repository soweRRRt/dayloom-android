package com.sowerrrt.dayloom

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryNavigationOpensHabits() {
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithText("Your first rhythm starts here").assertExists()
    }

    @Test
    fun themeAndStartScreenSelectionsSurviveRecreation() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Dark").performClick().assertIsSelected()
        composeRule.onNodeWithTag("start_destination_planner").performClick().assertIsSelected()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.onNodeWithTag("start_destination_planner").assertIsSelected()
    }
}
