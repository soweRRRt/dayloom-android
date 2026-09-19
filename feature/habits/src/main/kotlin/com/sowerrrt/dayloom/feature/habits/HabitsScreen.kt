package com.sowerrrt.dayloom.feature.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomButton
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.bestStreak
import com.sowerrrt.dayloom.core.model.currentStreak
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var pendingArchive by remember { mutableStateOf<Habit?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().testTag("habits_screen")) {
        DayloomTopBar(stringResource(R.string.habits_title))
        when {
            state.isLoading -> LoadingState(Modifier.weight(1f))
            state.hasError ->
                ErrorState(
                    title = stringResource(R.string.habits_error_title),
                    message = stringResource(R.string.habits_error_description),
                    retryLabel = stringResource(R.string.habits_retry),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )
            state.habits.isEmpty() ->
                EmptyHabits(
                    onCreate = {
                        editingHabit = null
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
            else ->
                HabitsList(
                    state = state,
                    onToggle = viewModel::toggleCompletion,
                    onEdit = { habit ->
                        editingHabit = habit
                        showCreateDialog = true
                    },
                    onArchive = { pendingArchive = it },
                    onCreate = {
                        editingHabit = null
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
        }
    }

    if (showCreateDialog) {
        HabitEditorDialog(
            habit = editingHabit,
            onDismiss = { showCreateDialog = false },
            onSave = { title, weekdays ->
                val habit = editingHabit
                if (habit == null) {
                    viewModel.createHabit(title, weekdays)
                } else {
                    viewModel.updateHabit(habit.id, title, weekdays)
                }
                showCreateDialog = false
            },
        )
    }

    pendingArchive?.let { habit ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text(stringResource(R.string.habits_archive_title)) },
            text = { Text(stringResource(R.string.habits_archive_description, habit.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archiveHabit(habit.id)
                        pendingArchive = null
                    },
                ) {
                    Text(stringResource(R.string.habits_archive_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text(stringResource(R.string.habits_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyHabits(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = DayloomSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.habits_description),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DayloomSpacing.lg))
        DayloomCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = DayloomSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(76.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.habits_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.habits_empty_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                DayloomButton(
                    text = stringResource(R.string.habits_action),
                    onClick = onCreate,
                    modifier = Modifier.testTag("create_habit"),
                )
            }
        }
    }
}

@Composable
private fun HabitsList(
    state: HabitsUiState,
    onToggle: (EntityId) -> Unit,
    onEdit: (Habit) -> Unit,
    onArchive: (Habit) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = DayloomSpacing.md,
                    top = DayloomSpacing.sm,
                    end = DayloomSpacing.md,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
        ) {
            item {
                Text(
                    text =
                        stringResource(
                            R.string.habits_today_progress,
                            state.completedToday,
                            state.habits.size,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = DayloomSpacing.sm),
                )
            }
            items(state.habits, key = { it.id.value }) { habit ->
                HabitRow(
                    habit = habit,
                    completed = state.todayEpochDay in habit.completedEpochDays,
                    onToggle = { onToggle(habit.id) },
                    onEdit = { onEdit(habit) },
                    onArchive = { onArchive(habit) },
                    todayEpochDay = state.todayEpochDay,
                )
            }
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(DayloomSpacing.md).testTag("create_habit"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.habits_action))
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    completed: Boolean,
    todayEpochDay: Long,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val action =
        if (completed) {
            stringResource(R.string.habits_mark_incomplete, habit.title)
        } else {
            stringResource(R.string.habits_mark_complete, habit.title)
        }
    DayloomCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("habit_toggle_${habit.title}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            Icon(
                imageVector =
                    if (completed) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                contentDescription = action,
                tint =
                    if (completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        stringResource(
                            if (completed) {
                                R.string.habits_completed_today
                            } else {
                                R.string.habits_tap_to_complete
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = scheduleLabel(habit.scheduledWeekdays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            R.string.habits_streaks,
                            habit.currentStreak(todayEpochDay),
                            habit.bestStreak(),
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.habits_edit))
                }
                IconButton(onClick = onArchive) {
                    Icon(Icons.Rounded.Archive, contentDescription = stringResource(R.string.habits_archive))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorDialog(
    habit: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, Set<Weekday>) -> Unit,
) {
    var title by remember(habit?.id) { mutableStateOf(habit?.title.orEmpty()) }
    var selectedDays by
        remember(habit?.id) {
            mutableStateOf(habit?.scheduledWeekdays ?: Weekday.entries.toSet())
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (habit == null) R.string.habits_create_title else R.string.habits_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { value -> title = value.take(MAX_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    label = { Text(stringResource(R.string.habits_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_TITLE_LENGTH") },
                    singleLine = true,
                )
                Text(stringResource(R.string.habits_schedule), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                ) {
                    Weekday.entries.forEach { weekday ->
                        FilterChip(
                            selected = weekday in selectedDays,
                            onClick = {
                                selectedDays =
                                    if (weekday in selectedDays) {
                                        selectedDays - weekday
                                    } else {
                                        selectedDays + weekday
                                    }
                            },
                            label = { Text(weekdayShortLabel(weekday)) },
                            modifier = Modifier.testTag("habit_day_${weekday.name.lowercase()}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, selectedDays) },
                enabled = title.isNotBlank() && selectedDays.isNotEmpty(),
                modifier = Modifier.testTag("save_habit"),
            ) {
                Text(stringResource(R.string.habits_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) }
        },
    )
}

@Composable
private fun scheduleLabel(days: Set<Weekday>): String {
    if (days.size == Weekday.entries.size) {
        return stringResource(R.string.habits_every_day)
    }

    var result = ""
    for (weekday in days.sortedBy(Weekday::ordinal)) {
        result = listOf(result, weekdayShortLabel(weekday)).filter(String::isNotEmpty).joinToString(" · ")
    }
    return result
}

@Composable
private fun weekdayShortLabel(weekday: Weekday): String =
    stringResource(
        when (weekday) {
            Weekday.MONDAY -> R.string.weekday_monday_short
            Weekday.TUESDAY -> R.string.weekday_tuesday_short
            Weekday.WEDNESDAY -> R.string.weekday_wednesday_short
            Weekday.THURSDAY -> R.string.weekday_thursday_short
            Weekday.FRIDAY -> R.string.weekday_friday_short
            Weekday.SATURDAY -> R.string.weekday_saturday_short
            Weekday.SUNDAY -> R.string.weekday_sunday_short
        },
    )

private const val MAX_TITLE_LENGTH = 80
