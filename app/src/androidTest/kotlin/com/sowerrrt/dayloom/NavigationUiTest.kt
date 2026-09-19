package com.sowerrrt.dayloom

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryNavigationOpensHabits() {
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("habits_screen").assertExists()
    }

    @Test
    fun habitCanBeCreatedCompletedAndRestored() {
        val title = "Read before bed ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("save_habit").performClick()
        composeRule.onNodeWithText(title).assertExists()

        composeRule.onNodeWithTag("habit_toggle_$title").performClick()
        composeRule.onNodeWithText("Completed today").assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithText("Completed today").assertExists()
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
