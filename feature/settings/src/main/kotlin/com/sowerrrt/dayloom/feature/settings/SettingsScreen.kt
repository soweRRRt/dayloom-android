package com.sowerrrt.dayloom.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomButton
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.storage.DemoContent
import com.sowerrrt.dayloom.core.storage.DemoGoal
import com.sowerrrt.dayloom.core.storage.DemoHabit
import com.sowerrrt.dayloom.core.storage.DemoList
import com.sowerrrt.dayloom.core.storage.DemoListItem
import com.sowerrrt.dayloom.core.storage.DemoPlan
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onCheckUpdates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val demoContent = rememberDemoContent()
    Column {
        DayloomTopBar(stringResource(R.string.settings_title))
        LazyColumn(
            modifier = Modifier.testTag("settings_list"),
            contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            item {
                SettingsSection(stringResource(R.string.settings_appearance)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    ChoiceRow {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = { Text(themeLabel(mode)) },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_accent)) {
                    AccentPalette.entries.forEach { palette ->
                        AccentChoice(
                            palette = palette,
                            selected = state.settings.accentPalette == palette,
                            onClick = { viewModel.setAccent(palette) },
                        )
                    }
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_start_screen)) {
                    StartDestinationChoices(
                        selected = state.settings.startDestination,
                        onSelected = viewModel::setStartDestination,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_updates)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_auto_updates),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.settings_auto_updates_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.automaticUpdateChecks,
                            onCheckedChange = viewModel::setAutomaticUpdateChecks,
                        )
                    }
                    DayloomButton(stringResource(R.string.settings_check_updates), onCheckUpdates)
                }
            }
            item {
                SettingsSection(stringResource(R.string.settings_examples_title)) {
                    Text(
                        stringResource(R.string.settings_examples_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DayloomButton(
                        text =
                            stringResource(
                                if (state.isAddingExamples) {
                                    R.string.settings_examples_adding
                                } else {
                                    R.string.settings_examples_action
                                },
                            ),
                        onClick = { viewModel.addExamples(demoContent, LocalDate.now().toEpochDay()) },
                        modifier = Modifier.testTag("add_demo_content"),
                    )
                    state.demoFeedback?.let { feedback ->
                        Text(
                            stringResource(
                                when (feedback) {
                                    DemoFeedback.ADDED -> R.string.settings_examples_added
                                    DemoFeedback.ALREADY_PRESENT -> R.string.settings_examples_already_present
                                    DemoFeedback.ERROR -> R.string.settings_examples_error
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (feedback == DemoFeedback.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            modifier = Modifier.testTag("demo_content_feedback"),
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(DayloomSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun rememberDemoContent(): DemoContent =
    DemoContent(
        habits =
            listOf(
                DemoHabit(
                    title = stringResource(R.string.settings_example_habit_water),
                    scheduledWeekdays = Weekday.entries.toSet(),
                    completedDayOffsets = listOf(-4, -3, -2, -1, 0),
                ),
                DemoHabit(
                    title = stringResource(R.string.settings_example_habit_reading),
                    scheduledWeekdays = Weekday.entries.toSet(),
                    completedDayOffsets = listOf(-2, -1),
                ),
                DemoHabit(
                    title = stringResource(R.string.settings_example_habit_walk),
                    scheduledWeekdays = setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
                    completedDayOffsets = listOf(-7, -5, -3),
                ),
            ),
        plans =
            listOf(
                DemoPlan(stringResource(R.string.settings_example_plan_review), 0, completed = true),
                DemoPlan(stringResource(R.string.settings_example_plan_groceries), 0),
                DemoPlan(stringResource(R.string.settings_example_plan_call), 1),
                DemoPlan(stringResource(R.string.settings_example_plan_dentist), 3),
            ),
        lists =
            listOf(
                DemoList(
                    title = stringResource(R.string.settings_example_list_groceries),
                    kind = ListKind.SHOPPING,
                    items =
                        listOf(
                            DemoListItem(stringResource(R.string.settings_example_item_milk), "2", completed = true),
                            DemoListItem(stringResource(R.string.settings_example_item_avocado), "3"),
                            DemoListItem(
                                stringResource(R.string.settings_example_item_coffee),
                                "1",
                                stringResource(R.string.settings_example_item_coffee_note),
                            ),
                        ),
                ),
                DemoList(
                    title = stringResource(R.string.settings_example_list_trip),
                    kind = ListKind.PACKING,
                    items =
                        listOf(
                            DemoListItem(stringResource(R.string.settings_example_item_passport), completed = true),
                            DemoListItem(stringResource(R.string.settings_example_item_charger)),
                            DemoListItem(stringResource(R.string.settings_example_item_camera)),
                        ),
                ),
                DemoList(
                    title = stringResource(R.string.settings_example_list_ideas),
                    kind = ListKind.IDEAS,
                    items =
                        listOf(
                            DemoListItem(stringResource(R.string.settings_example_item_weekend)),
                            DemoListItem(stringResource(R.string.settings_example_item_recipe)),
                        ),
                ),
            ),
        goals =
            listOf(
                DemoGoal(
                    title = stringResource(R.string.settings_example_goal_laptop),
                    targetMinor = 150_000_00,
                    currencyCode = stringResource(R.string.settings_example_currency),
                    priority = WishPriority.HIGH,
                    note = stringResource(R.string.settings_example_goal_laptop_note),
                    contributionsMinor = listOf(20_000_00, 15_000_00, 10_000_00),
                ),
                DemoGoal(
                    title = stringResource(R.string.settings_example_goal_trip),
                    targetMinor = 80_000_00,
                    currencyCode = stringResource(R.string.settings_example_currency),
                    priority = WishPriority.MEDIUM,
                    note = stringResource(R.string.settings_example_goal_trip_note),
                    contributionsMinor = listOf(12_000_00, 8_000_00),
                ),
            ),
    )

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartDestinationChoices(
    selected: StartDestination,
    onSelected: (StartDestination) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
    ) {
        StartDestination.entries.forEach { destination ->
            FilterChip(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                label = { Text(startDestinationLabel(destination)) },
                modifier = Modifier.testTag("start_destination_${destination.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun AccentChoice(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = accentLabel(palette)
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label }
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
    ) {
        Box(Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(accentColor(palette)))
        Text(label, modifier = Modifier.weight(1f), color = contentColor)
        if (selected) Text(stringResource(R.string.settings_selected), color = contentColor)
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )

@Composable
private fun accentLabel(palette: AccentPalette): String =
    stringResource(
        when (palette) {
            AccentPalette.VIOLET -> R.string.settings_accent_violet
            AccentPalette.OCEAN -> R.string.settings_accent_ocean
            AccentPalette.CORAL -> R.string.settings_accent_coral
            AccentPalette.FOREST -> R.string.settings_accent_forest
        },
    )

@Composable
private fun startDestinationLabel(destination: StartDestination): String =
    stringResource(
        when (destination) {
            StartDestination.HOME -> R.string.settings_start_home
            StartDestination.HABITS -> R.string.settings_start_habits
            StartDestination.PLANNER -> R.string.settings_start_planner
            StartDestination.LISTS -> R.string.settings_start_lists
        },
    )

private fun accentColor(palette: AccentPalette): Color =
    when (palette) {
        AccentPalette.VIOLET -> Color(0xFF7357D3)
        AccentPalette.OCEAN -> Color(0xFF007C91)
        AccentPalette.CORAL -> Color(0xFFC4475D)
        AccentPalette.FOREST -> Color(0xFF357A4F)
    }
