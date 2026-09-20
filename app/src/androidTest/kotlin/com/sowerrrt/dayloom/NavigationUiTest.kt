package com.sowerrrt.dayloom

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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
        composeRule.onNodeWithTag("habit_target_amount").performScrollTo().performTextInput("5")
        composeRule.onNodeWithTag("habit_target_unit").performScrollTo().performTextInput("times")
        composeRule.onNodeWithTag("habit_target_amount").assertTextContains("5")
        composeRule.onNodeWithTag("habit_target_unit").assertTextContains("times")
        composeRule.onNodeWithTag("save_habit").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")
        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithTag("habit_target_$title", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("habit_more_$title").performClick()
        composeRule.onNodeWithTag("habit_image_action_$title").assertExists()
        pressBack()

        composeRule.onNodeWithTag("habit_toggle_$title").performClick()
        composeRule.onNodeWithText("Completed today").assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithText("Completed today").assertExists()
    }

    @Test
    fun fullHabitPresetCanBeSavedAndUsedInOneTap() {
        val title = "Preset ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("habit_target_amount").performScrollTo().performTextInput("12")
        composeRule.onNodeWithTag("habit_target_unit").performScrollTo().performTextInput("times")
        composeRule.onNodeWithTag("save_habit_preset").performClick()

        composeRule.onNodeWithTag("habit_preset_$title").assertExists()
        composeRule.onNodeWithTag("habit_target_amount").performTextClearance()
        composeRule.onNodeWithTag("habit_target_unit").performTextClearance()
        composeRule.onNodeWithTag("habit_preset_$title").performClick()
        composeRule.onNodeWithTag("habit_target_amount").assertTextContains("12")
        composeRule.onNodeWithTag("habit_target_unit").assertTextContains("times")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("quick_habit_preset_$title").performClick()
        composeRule
            .onNodeWithTag("habits_list")
            .performScrollToNode(hasTestTag("habit_toggle_$title"))
        composeRule.onNodeWithTag("habit_toggle_$title").assertExists()
    }

    @Test
    fun habitEditorOffersRepeatingReminderTime() {
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("set_habit_reminder").assertExists()
    }

    @Test
    fun habitCanRepeatEveryTenDays() {
        val title = "Interval habit ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("habit_schedule_interval").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_repeat_interval").performTextInput("10")
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule
            .onNodeWithTag("habits_list")
            .performScrollToNode(hasTestTag("habit_toggle_$title"))
        composeRule.onNodeWithText("Every 10 days").assertExists()
    }

    @Test
    fun habitCanRepeatOnSeveralMonthDays() {
        val title = "Monthly habit ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("habit_schedule_month_days").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_month_days").performTextInput("3, 15")
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule.onNodeWithTag("habit_view_all").performClick()
        composeRule
            .onNodeWithTag("habits_list")
            .performScrollToNode(hasTestTag("habit_toggle_$title"))
        composeRule.onNodeWithText("On day 3, 15 of each month").assertExists()
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
        waitUntilScrollable("habits_list", "habit_toggle_$habitTitle")

        composeRule.onNodeWithTag("primary_nav_planner").performClick()
        composeRule.onNodeWithTag("month_calendar").assertExists()
        waitUntilScrollable("planner_list", "calendar_habit_$habitTitle")
        composeRule.onNodeWithTag("calendar_habit_$habitTitle").assertExists()
        composeRule.onNodeWithTag("create_plan").performClick()
        composeRule.onNodeWithTag("plan_name_input").performTextInput(planTitle)
        closeSoftKeyboard()
        composeRule.onNodeWithTag("save_plan").performClick()
        waitUntilScrollable("planner_list", "plan_$planTitle")
        composeRule.onNodeWithTag("plan_$planTitle").assertExists()
        composeRule.onNodeWithTag("plan_image_action_$planTitle").assertExists()
        composeRule.onNodeWithTag("plan_toggle_$planTitle").performClick()

        composeRule.activityRule.scenario.recreate()
        waitUntilScrollable("planner_list", "calendar_habit_$habitTitle")
        composeRule.onNodeWithTag("calendar_habit_$habitTitle").assertExists()
        waitUntilScrollable("planner_list", "plan_$planTitle")
        composeRule.onNodeWithTag("plan_$planTitle").assertExists()
    }

    @Test
    fun calendarSwitchesBetweenWeekAndMonth() {
        composeRule.onNodeWithTag("primary_nav_planner").performClick()
        composeRule.onNodeWithTag("month_calendar").assertExists()

        composeRule.onNodeWithTag("calendar_mode_week").performClick()
        composeRule.onNodeWithTag("week_calendar").assertExists()

        composeRule.onNodeWithTag("calendar_mode_month").performClick()
        composeRule.onNodeWithTag("month_calendar").assertExists()
    }

    @Test
    fun todaysHabitCanBeCompletedFromHome() {
        val title = "Home habit ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("save_habit").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")

        composeRule.onNodeWithTag("primary_nav_home").performClick()
        composeRule
            .onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_toggle_habit_${title}_open"))
        composeRule.onNodeWithTag("home_toggle_habit_${title}_open").performClick()
        waitForTag("home_toggle_habit_${title}_completed")
    }

    @Test
    fun planEditorSeparatesExactTimeAndNotification() {
        composeRule.onNodeWithTag("primary_nav_planner").performClick()
        composeRule.onNodeWithTag("create_plan").performClick()
        composeRule.onNodeWithTag("set_plan_reminder").assertExists()
        composeRule.onNodeWithTag("plan_notification_toggle").assertExists()
    }

    @Test
    fun flexibleRecurringPlanCanBeCreatedFromEditor() {
        val title = "Interval plan ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_planner").performClick()
        composeRule.onNodeWithTag("create_plan").performClick()
        composeRule.onNodeWithTag("plan_name_input").performTextInput(title)
        composeRule.onNodeWithTag("plan_schedule_weekdays").performScrollTo().assertExists()
        composeRule.onNodeWithTag("plan_schedule_month_days").performScrollTo().assertExists()
        composeRule.onNodeWithTag("plan_schedule_interval").performScrollTo().performClick()
        composeRule.onNodeWithTag("plan_interval_input").performTextInput("7")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("save_plan").performClick()

        waitUntilScrollable("planner_list", "plan_$title")
        composeRule.onNodeWithTag("plan_repeat_value_$title").assertExists()
    }

    @Test
    fun habitProgressCompletesNumericGoal() {
        val title = "Progress habit ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("habit_target_amount").performScrollTo().performTextInput("5")
        composeRule.onNodeWithTag("habit_target_unit").performScrollTo().performTextInput("times")
        composeRule.onNodeWithTag("save_habit").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")
        composeRule.onNodeWithTag("habit_more_$title").performClick()
        composeRule.onNodeWithTag("habit_progress_$title").performClick()
        composeRule.onNodeWithTag("habit_progress_input").performTextInput("5")
        composeRule.onNodeWithTag("save_habit_progress").performClick()

        waitForTag("habit_status_${title}_completed", useUnmergedTree = true)
    }

    @Test
    fun habitHistoryShowsInsightsAndAllowsCompletion() {
        val title = "History habit ${System.currentTimeMillis()}"
        val today = LocalDate.now().toEpochDay()
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("save_habit").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")

        waitUntilScrollable("habits_list", "habit_view_history")
        composeRule.onNodeWithTag("habit_view_history").performClick()
        composeRule.onNodeWithTag("habit_analytics").assertExists()
        waitUntilScrollable("habits_list", "habit_history_${title}_$today")
        composeRule.onNodeWithTag("habit_history_${title}_$today").performClick()

        waitForTag(
            "habit_history_status_${title}_${today}_completed",
            useUnmergedTree = true,
        )
    }

    @Test
    fun habitDetailsShowPeriodStatsAndEditableCalendar() {
        val title = "Insights habit ${System.currentTimeMillis()}"
        val today = LocalDate.now().toEpochDay()
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("save_habit").performClick()
        waitUntilScrollable("habits_list", "habit_insights_$title")

        composeRule.onNodeWithTag("habit_insights_$title").performClick()
        composeRule.onNodeWithTag("habit_insights_screen").assertExists()
        composeRule.onNodeWithTag("habit_insights_period_quarter").performClick()
        composeRule.onNodeWithTag("habit_insights_summary").assertExists()
        composeRule.onNodeWithTag("habit_insights_day_${today}_open").performClick()

        waitForTag("habit_insights_day_${today}_completed")
        composeRule.onNodeWithTag("habit_insights_back").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")
        waitForTag("habit_status_${title}_completed", useUnmergedTree = true)
    }

    @Test
    fun habitCanBeSearchedArchivedAndRestored() {
        val title = "Restore habit ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("primary_nav_habits").performClick()
        composeRule.onNodeWithTag("create_habit").performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput(title)
        composeRule.onNodeWithTag("save_habit").performClick()

        waitUntilScrollable("habits_list", "habit_search")
        composeRule.onNodeWithTag("habit_search").performTextInput("Restore habit")
        waitUntilScrollable("habits_list", "habit_toggle_$title")
        composeRule.onNodeWithTag("habit_toggle_$title").assertExists()

        waitUntilScrollable("habits_list", "habit_sort")
        composeRule.onNodeWithTag("habit_sort").performClick()
        composeRule.onNodeWithTag("habit_sort_name").performClick()
        waitUntilScrollable("habits_list", "habit_more_$title")
        composeRule.onNodeWithTag("habit_more_$title").performClick()
        composeRule.onNodeWithTag("archive_habit_$title").performClick()
        composeRule.onNodeWithTag("confirm_archive_habit").performClick()

        waitUntilScrollable("habits_list", "habit_view_archived")
        composeRule.onNodeWithTag("habit_view_archived").performClick()
        waitUntilScrollable("habits_list", "archived_habit_$title")
        composeRule.onNodeWithTag("restore_habit_$title").performClick()
        composeRule.onNodeWithTag("habits_archive_empty").assertExists()

        composeRule.onNodeWithTag("habit_view_all").performClick()
        waitUntilScrollable("habits_list", "habit_toggle_$title")
        composeRule.onNodeWithTag("habit_toggle_$title").assertExists()
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
        composeRule.onNodeWithTag("list_kind_shopping").assertIsSelected()
        composeRule.onNodeWithTag("custom_list_kind_input").performScrollTo().performTextInput("Errands")
        composeRule.onNodeWithTag("save_list").performClick()
        composeRule.onNodeWithTag("list_details").assertExists()
        composeRule.onNodeWithText("Errands").assertExists()

        composeRule.onNodeWithTag("create_list_item").performClick()
        composeRule.onNodeWithTag("list_item_name_input").performTextInput(itemTitle)
        composeRule.onNodeWithTag("list_item_quantity_input").performTextInput("2 cartons")
        composeRule.onNodeWithTag("list_item_note_input").performTextInput("Unsweetened")
        composeRule.onNodeWithTag("save_list_item").performClick()
        composeRule.onNodeWithTag("list_item_$itemTitle").assertExists()
        composeRule.onNodeWithTag("list_item_toggle_$itemTitle").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("list_item_$itemTitle").assertExists()
        composeRule.onNodeWithTag("list_item_quantity_$itemTitle").assertTextContains("2 cartons", substring = true)
        composeRule.onNodeWithTag("list_item_note_$itemTitle").assertTextContains("Unsweetened")
    }

    @Test
    fun wishAndSavingsCanBeCreatedAndRestored() {
        val suffix = System.currentTimeMillis()
        val title = "Camera $suffix"

        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_wishlist").performClick()
        composeRule.onNodeWithTag("create_wish").performClick()
        composeRule.onNodeWithTag("wish_title_input").performTextInput(title)
        composeRule.onNodeWithTag("wish_target_input").performTextInput("2500")
        composeRule.onNodeWithTag("wish_currency_usd").performClick()
        composeRule.onNodeWithTag("wish_currency_usd").assertIsSelected()
        composeRule.onNodeWithTag("wish_priority_high").performScrollTo().performClick()
        composeRule.onNodeWithTag("wish_priority_high").assertIsSelected()
        composeRule
            .onNodeWithTag("wish_purchase_url_input")
            .performScrollTo()
            .performTextInput("https://example.com/camera")
        composeRule.onNodeWithTag("save_wish").performClick()
        composeRule.onNodeWithTag("wish_details").assertExists()
        composeRule.onNodeWithTag("open_purchase_link").assertExists()

        composeRule.onNodeWithTag("add_contribution").performClick()
        composeRule.onNodeWithTag("contribution_amount_input").performTextInput("125.50")
        composeRule.onNodeWithTag("contribution_note_input").performTextInput("First step")
        composeRule.onNodeWithTag("save_contribution").performClick()
        composeRule.onNodeWithTag("contribution_12550").assertExists()
        composeRule.onNodeWithText("First step").assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("contribution_12550").assertExists()
    }

    @Test
    fun vaultOpensInLockedState() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_vault").performClick()
        composeRule.onNodeWithTag("vault_screen").assertExists()
        composeRule.onNodeWithTag("vault_locked").assertExists()
    }

    @Test
    fun exampleContentCanBeAddedFromSettings() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("add_demo_content"))
        composeRule.onNodeWithTag("add_demo_content").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("demo_content_feedback").assertExists() }.isSuccess
        }
    }

    @Test
    fun settingsExposeWholeAppProtection() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("app_lock_switch"))
        composeRule.onNodeWithTag("app_lock_switch").assertExists()
    }

    @Test
    fun settingsHaveVisibleBackNavigation() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()

        composeRule.onNodeWithTag("settings_back").assertExists().performClick()

        composeRule.onNodeWithTag("more_settings").assertExists()
    }

    @Test
    fun settingsExposeCustomizableBottomNavigation() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("bottom_section_home"))
        composeRule.onNodeWithTag("bottom_section_home").assertExists()
        composeRule.onNodeWithTag("bottom_section_more").assertExists()
    }

    @Test
    fun languageCanSwitchToRussianAndSurviveRecreation() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule.onNodeWithTag("language_system").assertExists()
        composeRule.onNodeWithTag("language_english").assertExists()
        composeRule.onNodeWithTag("language_russian").performClick()
        composeRule.waitUntilSelected {
            composeRule.onNodeWithTag("language_russian").assertIsSelected()
            composeRule.onNodeWithText("Настройки").assertExists()
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntilSelected {
            composeRule.onNodeWithTag("language_russian").assertIsSelected()
            composeRule.onNodeWithText("Настройки").assertExists()
        }

        // Keep the shared emulator locale deterministic for tests that run after this one.
        composeRule.onNodeWithTag("language_english").performClick()
        composeRule.waitUntilSelected {
            composeRule.onNodeWithTag("language_english").assertIsSelected()
            composeRule.onNodeWithText("Settings").assertExists()
        }
    }

    @Test
    fun homeDashboardCardCanBeHiddenFromSettings() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("home_section_wishlist"))
        composeRule.onNodeWithTag("home_section_wishlist").performClick()

        composeRule.onNodeWithTag("primary_nav_home").performClick()
        composeRule.onAllNodesWithTag("home_card_wishlist").assertCountEquals(0)
        composeRule.onNodeWithTag("home_card_habits").assertExists()
    }

    @Test
    fun settingsExposeNotificationPermissionCenter() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("notification_permission_card"))
        composeRule.onNodeWithTag("notification_permission_status").assertExists()
        composeRule.onNodeWithTag("open_notification_settings").assertExists()
    }

    @Test
    fun settingsExposeBackupRestoreAndProtectedErase() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("data_management_card"))
        composeRule.onNodeWithTag("export_data").assertExists()
        composeRule.onNodeWithTag("import_data").assertExists()
        composeRule.onNodeWithTag("clear_all_data").performClick()
        composeRule.onNodeWithTag("confirm_clear_all_data").assertExists()
    }

    @Test
    fun demoWishHasLocalCoverAndPhotoControls() {
        composeRule.onNodeWithTag("primary_nav_more").performClick()
        composeRule.onNodeWithTag("more_settings").performClick()
        composeRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("add_demo_content"))
        composeRule.onNodeWithTag("add_demo_content").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("demo_content_feedback").assertExists() }.isSuccess
        }
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("more_wishlist").performClick()
        composeRule
            .onNodeWithTag("wishlist_overview")
            .performScrollToNode(hasTestTag("wish_New laptop"))
        composeRule.onNodeWithTag("wish_New laptop").performClick()

        composeRule.onNodeWithTag("wish_image_New laptop").assertExists()
        composeRule.onNodeWithTag("add_wish_image").assertExists()
        composeRule.onNodeWithTag("remove_wish_image").assertExists()
    }

    @Test
    fun themeAndStartScreenSelectionsSurviveRecreation() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.waitUntilSelected { composeRule.onNodeWithText("Dark").assertIsSelected() }
        composeRule.onNodeWithTag("start_destination_planner").performScrollTo().performClick()
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

    private fun waitForTag(
        tag: String,
        useUnmergedTree: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilScrollable(
        listTag: String,
        itemTag: String,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(itemTag))
            }.isSuccess
        }
    }
}
