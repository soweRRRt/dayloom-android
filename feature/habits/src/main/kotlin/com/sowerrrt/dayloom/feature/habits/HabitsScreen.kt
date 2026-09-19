package com.sowerrrt.dayloom.feature.habits

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var pendingArchive by remember { mutableStateOf<Habit?>(null) }
    var imageTarget by remember { mutableStateOf<Habit?>(null) }
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val target = imageTarget
            if (uri != null && target != null) viewModel.setImage(target.id, uri)
            imageTarget = null
        }

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
                    onChooseImage = { habit ->
                        imageTarget = habit
                        imagePicker.launch("image/*")
                    },
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
            isChangingImage = state.isChangingImage,
            hasImageError = state.hasImageError,
            onChooseImage = { habit ->
                imageTarget = habit
                imagePicker.launch("image/*")
            },
            onRemoveImage = viewModel::removeImage,
            presets = state.presets,
            onSavePreset = viewModel::savePreset,
            onRemovePreset = viewModel::removePreset,
            onSave = { title, weekdays, reminderMinutesOfDay, targetAmount, targetUnit ->
                val habit = editingHabit
                if (
                    reminderMinutesOfDay != null &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (habit == null) {
                    viewModel.createHabit(title, weekdays, reminderMinutesOfDay, targetAmount, targetUnit)
                } else {
                    viewModel.updateHabit(
                        habit.id,
                        title,
                        weekdays,
                        reminderMinutesOfDay,
                        targetAmount,
                        targetUnit,
                    )
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
    onChooseImage: (Habit) -> Unit,
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
            if (state.reminderSchedulingFailed) {
                item {
                    DayloomCard(Modifier.fillMaxWidth().testTag("habit_reminder_error")) {
                        Text(
                            stringResource(R.string.habits_reminder_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            items(state.habits, key = { it.id.value }) { habit ->
                HabitRow(
                    habit = habit,
                    completed = state.todayEpochDay in habit.completedEpochDays,
                    onToggle = { onToggle(habit.id) },
                    onEdit = { onEdit(habit) },
                    onArchive = { onArchive(habit) },
                    onChooseImage = { onChooseImage(habit) },
                    imagePath = state.imagePaths[habit.id],
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
    onChooseImage: () -> Unit,
    imagePath: String?,
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
            if (imagePath != null) {
                LocalHabitImage(
                    imagePath = imagePath,
                    title = habit.title,
                    modifier = Modifier.size(76.dp),
                )
            }
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
                habit.reminderMinutesOfDay?.let { reminderMinutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        modifier = Modifier.testTag("habit_reminder_${habit.title}"),
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            formatReminderTime(reminderMinutes, currentLocale()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()) {
                    Text(
                        stringResource(
                            R.string.habits_target_value,
                            habit.targetAmount,
                            habit.targetUnit,
                        ).trim(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("habit_target_${habit.title}"),
                    )
                }
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
                IconButton(
                    onClick = onChooseImage,
                    modifier = Modifier.testTag("habit_image_action_${habit.title}"),
                ) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription =
                            stringResource(
                                if (habit.image == null) R.string.habits_add_image else R.string.habits_change_image,
                            ),
                    )
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
    isChangingImage: Boolean,
    hasImageError: Boolean,
    onChooseImage: (Habit) -> Unit,
    onRemoveImage: (EntityId) -> Unit,
    presets: Set<String>,
    onSavePreset: (String) -> Unit,
    onRemovePreset: (String) -> Unit,
    onSave: (String, Set<Weekday>, Int?, String, String) -> Unit,
) {
    var title by remember(habit?.id) { mutableStateOf(habit?.title.orEmpty()) }
    var selectedDays by
        remember(habit?.id) {
            mutableStateOf(habit?.scheduledWeekdays ?: Weekday.entries.toSet())
        }
    var reminderMinutesOfDay by remember(habit?.id) { mutableStateOf(habit?.reminderMinutesOfDay) }
    var targetAmount by remember(habit?.id) { mutableStateOf(habit?.targetAmount.orEmpty()) }
    var targetUnit by remember(habit?.id) { mutableStateOf(habit?.targetUnit.orEmpty()) }
    val context = LocalContext.current
    val locale = currentLocale()
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.habits_presets), style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        presets.sorted().forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = { title = preset },
                                label = { Text(preset) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.habits_remove_preset, preset),
                                        modifier = Modifier.size(18.dp).clickable { onRemovePreset(preset) },
                                    )
                                },
                                modifier = Modifier.testTag("habit_preset_$preset"),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { value -> title = value.take(MAX_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    label = { Text(stringResource(R.string.habits_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_TITLE_LENGTH") },
                    singleLine = true,
                )
                TextButton(
                    onClick = { onSavePreset(title) },
                    enabled = title.isNotBlank() && title !in presets,
                    modifier = Modifier.testTag("save_habit_preset"),
                ) {
                    Text(stringResource(R.string.habits_save_preset))
                }
                if (habit != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onChooseImage(habit) },
                            enabled = !isChangingImage,
                            modifier = Modifier.weight(1f).testTag("edit_habit_image"),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(DayloomSpacing.xs))
                            Text(
                                stringResource(
                                    if (habit.image ==
                                        null
                                    ) {
                                        R.string.habits_add_image
                                    } else {
                                        R.string.habits_change_image
                                    },
                                ),
                            )
                        }
                        if (habit.image != null) {
                            IconButton(
                                onClick = { onRemoveImage(habit.id) },
                                enabled = !isChangingImage,
                                modifier = Modifier.testTag("remove_habit_image"),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.habits_remove_image),
                                )
                            }
                        }
                    }
                    if (hasImageError) {
                        Text(
                            stringResource(R.string.habits_image_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("habit_image_error"),
                        )
                    }
                }
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
                Text(stringResource(R.string.habits_target), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.habits_target_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    OutlinedTextField(
                        value = targetAmount,
                        onValueChange = { targetAmount = it.take(MAX_TARGET_LENGTH) },
                        label = { Text(stringResource(R.string.habits_target_amount)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("habit_target_amount"),
                    )
                    OutlinedTextField(
                        value = targetUnit,
                        onValueChange = { targetUnit = it.take(MAX_TARGET_LENGTH) },
                        label = { Text(stringResource(R.string.habits_target_unit)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("habit_target_unit"),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                    listOf(
                        stringResource(R.string.habits_unit_times),
                        stringResource(R.string.habits_unit_steps),
                        stringResource(R.string.habits_unit_minutes),
                        stringResource(R.string.habits_unit_kg),
                        stringResource(R.string.habits_unit_pieces),
                    ).forEach { unit ->
                        InputChip(
                            selected = targetUnit == unit,
                            onClick = { targetUnit = unit },
                            label = { Text(unit) },
                        )
                    }
                }
                Text(stringResource(R.string.habits_reminder), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.habits_reminder_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = {
                            val initial = reminderMinutesOfDay ?: DEFAULT_REMINDER_MINUTES
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> reminderMinutesOfDay = hour * 60 + minute },
                                initial / 60,
                                initial % 60,
                                true,
                            ).show()
                        },
                        modifier = Modifier.testTag("set_habit_reminder"),
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Text(
                            if (reminderMinutesOfDay == null) {
                                stringResource(R.string.habits_add_reminder)
                            } else {
                                formatReminderTime(requireNotNull(reminderMinutesOfDay), locale)
                            },
                            modifier = Modifier.padding(start = DayloomSpacing.xs),
                        )
                    }
                    if (reminderMinutesOfDay != null) {
                        TextButton(
                            onClick = { reminderMinutesOfDay = null },
                            modifier = Modifier.testTag("clear_habit_reminder"),
                        ) {
                            Text(stringResource(R.string.habits_remove_reminder))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, selectedDays, reminderMinutesOfDay, targetAmount, targetUnit) },
                enabled = title.isNotBlank() && selectedDays.isNotEmpty(),
                modifier = Modifier.testTag("save_habit"),
            ) {
                Text(
                    stringResource(
                        if (habit == null) R.string.habits_create_confirm else R.string.habits_save,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) }
        },
    )
}

@Composable
private fun LocalHabitImage(
    imagePath: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = imagePath) {
            value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }
        }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.habits_image_description, title),
            modifier = modifier.clip(MaterialTheme.shapes.medium).testTag("habit_image_$title"),
            contentScale = ContentScale.Crop,
        )
    }
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

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private fun formatReminderTime(
    minutesOfDay: Int,
    locale: Locale,
): String = String.format(locale, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

private const val MAX_TITLE_LENGTH = 80
private const val MAX_TARGET_LENGTH = 24
private const val DEFAULT_REMINDER_MINUTES = 9 * 60
