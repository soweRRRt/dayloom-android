package com.sowerrrt.dayloom

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
    fun calendarCombinesHabitsAndPlansAndRestoresThem() {
        val suffix = System.currentTimeMillis()
        val habitTitle = "Calendar habit $suffix"
        val planTitle = "Calendar plan $suffix"

        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(habitTitle)
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule.onNodeWithTag("primary_nav_planner").performClick()
        composeRule.onNodeWithTag("month_calendar").assertExists()
        composeRule
            .onNodeWithTag("planner_list")
            .performScrollToNode(hasTestTag("calendar_habit_$habitTitle"))
        composeRule.onNodeWithTag("calendar_habit_$habitTitle").assertExists()
        composeRule.onNodeWithTag("create_plan").performClick()
        composeRule.onNodeWithTag("plan_name_input").performTextInput(planTitle)
        composeRule.onNodeWithTag("save_plan").performClick()
        composeRule
            .onNodeWithTag("planner_list")
            .performScrollToNode(hasTestTag("plan_$planTitle"))
        composeRule.onNodeWithTag("plan_$planTitle").assertExists()
        composeRule.onNodeWithTag("plan_toggle_$planTitle").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule
            .onNodeWithTag("planner_list")
            .performScrollToNode(hasTestTag("calendar_habit_$habitTitle"))
        composeRule.onNodeWithTag("calendar_habit_$habitTitle").assertExists()
        composeRule
            .onNodeWithTag("planner_list")
            .performScrollToNode(hasTestTag("plan_$planTitle"))
        composeRule.onNodeWithTag("plan_$planTitle").assertExists()
    }

    @Test
    fun listAndItemCanBeCreatedCompletedAndRestored() {
        val suffix = System.currentTimeMillis()
        val listTitle = "Groceries $suffix"
        val itemTitle = "Oat milk $suffix"

        composeRule.onNodeWithTag("primary_nav_lists").performClick()
        composeRule.onNodeWithTag("create_list").performClick()
        composeRule.onNodeWithTag("list_name_input").performTextInput(listTitle)
        composeRule.onNodeWithTag("list_kind_shopping").performClick()
        composeRule.onNodeWithTag("save_list").performClick()
        composeRule.onNodeWithTag("list_details").assertExists()

        composeRule.onNodeWithTag("create_list_item").performClick()
        composeRule.onNodeWithTag("list_item_name_input").performTextInput(itemTitle)
        composeRule.onNodeWithTag("list_item_quantity_input").performTextInput("2 cartons")
        composeRule.onNodeWithTag("list_item_note_input").performTextInput("Unsweetened")
        composeRule.onNodeWithTag("save_list_item").performClick()
        composeRule.onNodeWithTag("list_item_$itemTitle").assertExists()
        composeRule.onNodeWithTag("list_item_toggle_$itemTitle").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("list_item_$itemTitle").assertExists()
        composeRule.onNodeWithText("Quantity: 2 cartons").assertExists()
        composeRule.onNodeWithText("Unsweetened").assertExists()
    }

    @Test
    fun themeAndStartScreenSelectionsSurviveRecreation() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.waitUntilSelected { composeRule.onNodeWithText("Dark").assertIsSelected() }
        composeRule.onNodeWithTag("start_destination_planner").performClick()
        composeRule.waitUntilSelected {
            composeRule.onNodeWithTag("start_destination_planner").assertIsSelected()
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.waitUntilSelected { composeRule.onNodeWithText("Dark").assertIsSelected() }
        composeRule.waitUntilSelected {
            composeRule.onNodeWithTag("start_destination_planner").assertIsSelected()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilSelected(assertion: () -> Unit) {
        waitUntil(timeoutMillis = 5_000) {
            runCatching(assertion).isSuccess
        }
    }
}
