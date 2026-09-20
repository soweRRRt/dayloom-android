package com.sowerrrt.dayloom.feature.habits

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.sowerrrt.dayloom.core.model.HabitPreset
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.bestStreak
import com.sowerrrt.dayloom.core.model.currentStreak
import com.sowerrrt.dayloom.core.model.isScheduledOn
import com.sowerrrt.dayloom.core.model.periodStats
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState
import com.sowerrrt.dayloom.core.ui.loadSampledImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var pendingArchive by remember { mutableStateOf<Habit?>(null) }
    var progressTarget by remember { mutableStateOf<HabitProgressTarget?>(null) }
    var imageTarget by remember { mutableStateOf<Habit?>(null) }
    var insightHabitId by rememberSaveable { mutableStateOf<String?>(null) }
    val insightHabit = state.habits.firstOrNull { it.id.value == insightHabitId }
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val target = imageTarget
            if (uri != null && target != null) viewModel.setImage(target.id, uri)
            imageTarget = null
        }

    BackHandler(enabled = insightHabit != null) { insightHabitId = null }

    Column(Modifier.fillMaxSize().testTag("habits_screen")) {
        DayloomTopBar(
            title = insightHabit?.title ?: stringResource(R.string.habits_title),
            navigationIcon = {
                if (insightHabit != null) {
                    IconButton(
                        onClick = { insightHabitId = null },
                        modifier = Modifier.testTag("habit_insights_back"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.habits_insights_back),
                        )
                    }
                }
            },
            actions = {
                if (insightHabit != null) {
                    IconButton(
                        onClick = {
                            editingHabit = insightHabit
                            showCreateDialog = true
                        },
                        modifier = Modifier.testTag("habit_insights_edit"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.habits_edit))
                    }
                }
            },
        )
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
            insightHabit != null ->
                HabitInsightsScreen(
                    habit = insightHabit,
                    todayEpochDay = state.todayEpochDay,
                    onToggle = { epochDay -> viewModel.toggleCompletion(insightHabit.id, epochDay) },
                    onProgress = { epochDay -> progressTarget = HabitProgressTarget(insightHabit, epochDay) },
                    modifier = Modifier.weight(1f),
                )
            state.habits.isEmpty() ->
                EmptyHabits(
                    presets = state.presets,
                    onCreate = {
                        editingHabit = null
                        showCreateDialog = true
                    },
                    onUsePreset = { preset ->
                        if (
                            preset.reminderMinutesOfDay != null &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.createFromPreset(preset)
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
                    onRestore = viewModel::restoreHabit,
                    onProgress = { habit, epochDay -> progressTarget = HabitProgressTarget(habit, epochDay) },
                    onToggleHistory = viewModel::toggleCompletion,
                    onInsights = { insightHabitId = it.id.value },
                    onChooseImage = { habit ->
                        imageTarget = habit
                        imagePicker.launch("image/*")
                    },
                    onCreate = {
                        editingHabit = null
                        showCreateDialog = true
                    },
                    onUsePreset = { preset ->
                        if (
                            preset.reminderMinutesOfDay != null &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.createFromPreset(preset)
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
            onSave = { title, weekdays, repeatEveryDays, monthDays, reminderMinutesOfDay, targetAmount, targetUnit ->
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
                    viewModel.createHabit(
                        title,
                        weekdays,
                        repeatEveryDays,
                        monthDays,
                        reminderMinutesOfDay,
                        targetAmount,
                        targetUnit,
                    )
                } else {
                    viewModel.updateHabit(
                        habit.id,
                        title,
                        weekdays,
                        repeatEveryDays,
                        monthDays,
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
                    modifier = Modifier.testTag("confirm_archive_habit"),
                ) {
                    Text(
                        stringResource(R.string.habits_archive_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text(stringResource(R.string.habits_cancel)) }
            },
        )
    }

    progressTarget?.let { target ->
        HabitProgressDialog(
            habit = target.habit,
            epochDay = target.epochDay,
            onDismiss = { progressTarget = null },
            onSave = { value ->
                viewModel.setProgress(target.habit.id, target.epochDay, value)
                progressTarget = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHabits(
    presets: List<HabitPreset>,
    onCreate: () -> Unit,
    onUsePreset: (HabitPreset) -> Unit,
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
                if (presets.isNotEmpty()) {
                    Text(
                        stringResource(R.string.habits_quick_add),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        presets.forEach { preset ->
                            AssistChip(
                                onClick = { onUsePreset(preset) },
                                label = { Text(preset.title) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                modifier = Modifier.testTag("quick_habit_preset_${preset.title}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitsList(
    state: HabitsUiState,
    onToggle: (EntityId) -> Unit,
    onToggleHistory: (EntityId, Long) -> Unit,
    onInsights: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
    onArchive: (Habit) -> Unit,
    onRestore: (EntityId) -> Unit,
    onProgress: (Habit, Long) -> Unit,
    onChooseImage: (Habit) -> Unit,
    onCreate: () -> Unit,
    onUsePreset: (HabitPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewMode by rememberSaveable { mutableStateOf(HabitViewMode.TODAY) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(HabitSortMode.TIME) }
    var sortExpanded by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim()
    val matchingActive = state.habits.filter { it.matchesQuery(normalizedQuery) }
    val sourceHabits =
        when (viewMode) {
            HabitViewMode.TODAY -> state.scheduledToday
            HabitViewMode.ALL, HabitViewMode.HISTORY -> state.habits
            HabitViewMode.ARCHIVED -> state.archivedHabits
        }
    val visibleHabits = sourceHabits.filter { it.matchesQuery(normalizedQuery) }.sorted(sortMode, state.todayEpochDay)
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("habits_list"),
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                ) {
                    HabitViewMode.entries.forEach { mode ->
                        val leadingIcon: (@Composable () -> Unit)? =
                            when (mode) {
                                HabitViewMode.HISTORY ->
                                    {
                                        { Icon(Icons.Rounded.History, contentDescription = null) }
                                    }
                                HabitViewMode.ARCHIVED ->
                                    {
                                        { Icon(Icons.Rounded.Archive, contentDescription = null) }
                                    }
                                else -> null
                            }
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(stringResource(mode.labelResource())) },
                            leadingIcon = leadingIcon,
                            modifier = Modifier.testTag("habit_view_${mode.name.lowercase()}"),
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.habits_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon =
                            if (query.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { query = "" },
                                        modifier = Modifier.testTag("habit_search_clear"),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.habits_search_clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.weight(1f).testTag("habit_search"),
                    )
                    if (viewMode != HabitViewMode.HISTORY) {
                        Box {
                            IconButton(
                                onClick = { sortExpanded = true },
                                modifier = Modifier.testTag("habit_sort"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription =
                                        stringResource(
                                            R.string.habits_sort_description,
                                            stringResource(sortMode.labelResource()),
                                        ),
                                )
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                            ) {
                                HabitSortMode.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelResource())) },
                                        leadingIcon =
                                            if (option == sortMode) {
                                                { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                        onClick = {
                                            sortMode = option
                                            sortExpanded = false
                                        },
                                        modifier = Modifier.testTag("habit_sort_${option.name.lowercase()}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (viewMode == HabitViewMode.ARCHIVED) {
                if (visibleHabits.isEmpty()) {
                    item {
                        DayloomCard(Modifier.fillMaxWidth().testTag("habits_archive_empty")) {
                            Text(
                                stringResource(
                                    if (normalizedQuery.isEmpty()) {
                                        R.string.habits_archive_empty
                                    } else {
                                        R.string.habits_search_empty
                                    },
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(visibleHabits, key = { "archived-${it.id.value}" }) { habit ->
                    ArchivedHabitRow(
                        habit = habit,
                        imagePath = state.imagePaths[habit.id],
                        onRestore = { onRestore(habit.id) },
                    )
                }
            } else if (viewMode == HabitViewMode.HISTORY) {
                item { HabitAnalyticsCard(state.copy(habits = matchingActive)) }
                items(
                    items = (0L until HISTORY_DAYS).map { state.todayEpochDay - it },
                    key = { "history-$it" },
                ) { epochDay ->
                    HabitHistoryDayCard(
                        habits = matchingActive.filter { it.isScheduledOn(epochDay) },
                        epochDay = epochDay,
                        onToggle = { habit -> onToggleHistory(habit.id, epochDay) },
                        onProgress = { habit -> onProgress(habit, epochDay) },
                    )
                }
            } else {
                item {
                    Text(
                        text =
                            stringResource(
                                R.string.habits_today_progress,
                                state.completedToday,
                                state.scheduledToday.size,
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.presets.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                            Text(
                                stringResource(R.string.habits_quick_add),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                            ) {
                                state.presets.forEach { preset ->
                                    AssistChip(
                                        onClick = { onUsePreset(preset) },
                                        label = { Text(preset.title) },
                                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                        modifier = Modifier.testTag("quick_habit_preset_${preset.title}"),
                                    )
                                }
                            }
                        }
                    }
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
                if (visibleHabits.isEmpty()) {
                    item {
                        DayloomCard(Modifier.fillMaxWidth().testTag("habits_today_empty")) {
                            Text(
                                stringResource(
                                    when {
                                        normalizedQuery.isNotEmpty() -> R.string.habits_search_empty
                                        viewMode == HabitViewMode.ALL -> R.string.habits_all_empty
                                        else -> R.string.habits_today_empty
                                    },
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(visibleHabits, key = { it.id.value }) { habit ->
                    HabitRow(
                        habit = habit,
                        completed = state.todayEpochDay in habit.completedEpochDays,
                        scheduledToday = habit.isScheduledOn(state.todayEpochDay),
                        onToggle = { onToggle(habit.id) },
                        onEdit = { onEdit(habit) },
                        onArchive = { onArchive(habit) },
                        onProgress = { onProgress(habit, state.todayEpochDay) },
                        onInsights = { onInsights(habit) },
                        onChooseImage = { onChooseImage(habit) },
                        imagePath = state.imagePaths[habit.id],
                        todayEpochDay = state.todayEpochDay,
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitInsightsScreen(
    habit: Habit,
    todayEpochDay: Long,
    onToggle: (Long) -> Unit,
    onProgress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var period by rememberSaveable(habit.id.value) { mutableStateOf(HabitInsightPeriod.MONTH) }
    var monthOffset by rememberSaveable(habit.id.value) { mutableStateOf(0) }
    val today = LocalDate.ofEpochDay(todayEpochDay)
    val currentMonth = YearMonth.from(today)
    val shownMonth = currentMonth.plusMonths(monthOffset.toLong())
    val firstHabitMonth = YearMonth.from(LocalDate.ofEpochDay(habit.startEpochDay))
    val fromEpochDay =
        period.days?.let { days ->
            maxOf(habit.startEpochDay, todayEpochDay - days + 1)
        } ?: habit.startEpochDay
    val stats = habit.periodStats(fromEpochDay, todayEpochDay)
    val hasTarget = habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()
    val locale = currentLocale()

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("habit_insights_screen"),
        contentPadding =
            PaddingValues(
                start = DayloomSpacing.md,
                top = DayloomSpacing.sm,
                end = DayloomSpacing.md,
                bottom = DayloomSpacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                Text(
                    stringResource(R.string.habits_insights_period),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                ) {
                    HabitInsightPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(stringResource(option.labelResource())) },
                            modifier = Modifier.testTag("habit_insights_period_${option.name.lowercase()}"),
                        )
                    }
                }
            }
        }
        item {
            DayloomCard(Modifier.fillMaxWidth().testTag("habit_insights_summary")) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.habits_insights_completion),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.habits_analytics_summary,
                                    stats.completedCount,
                                    stats.scheduledCount,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${stats.completionPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("habit_insights_percent"),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { stats.completionPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                    ) {
                        InsightMetric(
                            value = stats.completedCount.toString(),
                            label = stringResource(R.string.habits_insights_completed),
                            modifier = Modifier.weight(1f),
                        )
                        InsightMetric(
                            value = habit.currentStreak(todayEpochDay).toString(),
                            label = stringResource(R.string.habits_insights_current_streak),
                            modifier = Modifier.weight(1f),
                        )
                        InsightMetric(
                            value = habit.bestStreak().toString(),
                            label = stringResource(R.string.habits_insights_best_streak),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            DayloomCard(Modifier.fillMaxWidth().testTag("habit_insights_calendar")) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { monthOffset-- },
                            enabled = shownMonth > firstHabitMonth,
                            modifier = Modifier.testTag("habit_insights_previous_month"),
                        ) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                contentDescription = stringResource(R.string.habits_insights_previous_month),
                            )
                        }
                        Text(
                            shownMonth
                                .atDay(1)
                                .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                                .replaceFirstChar { it.titlecase(locale) },
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { monthOffset++ },
                            enabled = shownMonth < currentMonth,
                            modifier = Modifier.testTag("habit_insights_next_month"),
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = stringResource(R.string.habits_insights_next_month),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Weekday.entries.forEach { weekday ->
                            Text(
                                weekdayShortLabel(weekday),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val leadingEmptyDays = shownMonth.atDay(1).dayOfWeek.value - 1
                    val calendarDays =
                        buildList<LocalDate?> {
                            repeat(leadingEmptyDays) { add(null) }
                            repeat(shownMonth.lengthOfMonth()) { day -> add(shownMonth.atDay(day + 1)) }
                            while (size % 7 != 0) add(null)
                        }
                    calendarDays.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    HabitCalendarDay(
                                        habit = habit,
                                        epochDay = date.toEpochDay(),
                                        todayEpochDay = todayEpochDay,
                                        hasTarget = hasTarget,
                                        onToggle = onToggle,
                                        onProgress = onProgress,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(
                            if (hasTarget) {
                                R.string.habits_insights_calendar_hint_progress
                            } else {
                                R.string.habits_insights_calendar_hint_toggle
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            DayloomCard(Modifier.fillMaxWidth().testTag("habit_insights_schedule")) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                    Text(
                        stringResource(R.string.habits_insights_schedule),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(scheduleLabel(habit), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    habit.reminderMinutesOfDay?.let { reminder ->
                        Text(
                            stringResource(
                                R.string.habits_insights_reminder,
                                formatReminderTime(reminder, locale),
                            ),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (hasTarget) {
                        Text(
                            stringResource(
                                R.string.habits_target_value,
                                habit.targetAmount,
                                habit.targetUnit,
                            ).trim(),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HabitCalendarDay(
    habit: Habit,
    epochDay: Long,
    todayEpochDay: Long,
    hasTarget: Boolean,
    onToggle: (Long) -> Unit,
    onProgress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheduled = habit.isScheduledOn(epochDay)
    val completed = epochDay in habit.completedEpochDays
    val editable = scheduled && epochDay <= todayEpochDay
    val colors = MaterialTheme.colorScheme
    val cellModifier =
        modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .then(
                when {
                    completed -> Modifier.background(colors.primary)
                    scheduled && epochDay <= todayEpochDay ->
                        Modifier
                            .background(colors.secondaryContainer)
                            .border(1.dp, colors.secondary.copy(alpha = 0.45f), CircleShape)
                    epochDay == todayEpochDay -> Modifier.border(1.dp, colors.primary, CircleShape)
                    else -> Modifier
                },
            ).then(
                if (editable) {
                    Modifier.clickable { if (hasTarget) onProgress(epochDay) else onToggle(epochDay) }
                } else {
                    Modifier
                },
            ).testTag(
                "habit_insights_day_${epochDay}_${if (completed) "completed" else "open"}",
            )
    Box(cellModifier, contentAlignment = Alignment.Center) {
        Text(
            LocalDate.ofEpochDay(epochDay).dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color =
                when {
                    completed -> colors.onPrimary
                    scheduled -> colors.onSecondaryContainer
                    else -> colors.onSurfaceVariant.copy(alpha = 0.55f)
                },
        )
    }
}

@Composable
private fun HabitAnalyticsCard(state: HabitsUiState) {
    val stats = state.habits.periodStats(state.todayEpochDay - ANALYTICS_DAYS + 1, state.todayEpochDay)
    val weekDays = (6L downTo 0L).map { state.todayEpochDay - it }
    DayloomCard(Modifier.fillMaxWidth().testTag("habit_analytics")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.habits_analytics_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.habits_analytics_period),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${stats.completionPercent}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { stats.completionPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    R.string.habits_analytics_summary,
                    stats.completedCount,
                    stats.scheduledCount,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
            ) {
                weekDays.forEach { epochDay ->
                    val dayStats = state.habits.periodStats(epochDay, epochDay)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        LinearProgressIndicator(
                            progress = { dayStats.completionPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            LocalDate
                                .ofEpochDay(epochDay)
                                .dayOfWeek
                                .getDisplayName(TextStyle.SHORT, currentLocale()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitHistoryDayCard(
    habits: List<Habit>,
    epochDay: Long,
    onToggle: (Habit) -> Unit,
    onProgress: (Habit) -> Unit,
) {
    val locale = currentLocale()
    val completed = habits.count { epochDay in it.completedEpochDays }
    DayloomCard(Modifier.fillMaxWidth().testTag("habit_history_day_$epochDay")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Text(
                LocalDate
                    .ofEpochDay(epochDay)
                    .format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale))
                    .replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.habits_history_summary, completed, habits.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (habits.isEmpty()) {
                Text(
                    stringResource(R.string.habits_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            habits.forEach { habit ->
                val isCompleted = epochDay in habit.completedEpochDays
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(habit) }
                            .padding(vertical = DayloomSpacing.xs)
                            .testTag("habit_history_${habit.title}_$epochDay"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                ) {
                    Icon(
                        if (isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint =
                            if (isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier =
                            Modifier.testTag(
                                "habit_history_status_${habit.title}_${epochDay}_${if (isCompleted) "completed" else "open"}",
                            ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(habit.title, style = MaterialTheme.typography.bodyLarge)
                        if (habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()) {
                            Text(
                                stringResource(
                                    R.string.habits_history_progress_value,
                                    habit.progressByEpochDay[epochDay].orEmpty().ifBlank { "—" },
                                    habit.targetAmount,
                                    habit.targetUnit,
                                ).trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()) {
                        IconButton(
                            onClick = { onProgress(habit) },
                            modifier = Modifier.testTag("habit_history_progress_${habit.title}_$epochDay"),
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.habits_update_progress),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedHabitRow(
    habit: Habit,
    imagePath: String?,
    onRestore: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("archived_habit_${habit.title}")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
            ) {
                if (imagePath != null) {
                    LocalHabitImage(
                        imagePath = imagePath,
                        title = habit.title,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                ) {
                    Text(habit.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        scheduleLabel(habit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.habits_archive_best_streak, habit.bestStreak()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.align(Alignment.End).testTag("restore_habit_${habit.title}"),
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = null)
                Spacer(Modifier.size(DayloomSpacing.xs))
                Text(stringResource(R.string.habits_restore))
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    completed: Boolean,
    scheduledToday: Boolean,
    todayEpochDay: Long,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onProgress: () -> Unit,
    onInsights: () -> Unit,
    onChooseImage: () -> Unit,
    imagePath: String?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val action =
        if (completed) {
            stringResource(R.string.habits_mark_incomplete, habit.title)
        } else {
            stringResource(R.string.habits_mark_complete, habit.title)
        }
    DayloomCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("habit_toggle_${habit.title}"),
        onClick = onToggle.takeIf { scheduledToday },
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
                            if (!scheduledToday) {
                                R.string.habits_not_scheduled_today
                            } else if (completed) {
                                R.string.habits_completed_today
                            } else {
                                R.string.habits_tap_to_complete
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.testTag(
                            "habit_status_${habit.title}_${if (completed) "completed" else "open"}",
                        ),
                )
                Text(
                    text = scheduleLabel(habit),
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
                            R.string.habits_progress_value,
                            habit.progressByEpochDay[todayEpochDay].orEmpty().ifBlank { "—" },
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
            IconButton(
                onClick = onInsights,
                modifier = Modifier.testTag("habit_insights_${habit.title}"),
            ) {
                Icon(
                    Icons.Rounded.Insights,
                    contentDescription = stringResource(R.string.habits_open_insights, habit.title),
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("habit_more_${habit.title}"),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.habits_edit))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habits_update_progress)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onProgress()
                            },
                            modifier = Modifier.testTag("habit_progress_${habit.title}"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habits_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (habit.image == null) {
                                        R.string.habits_add_image
                                    } else {
                                        R.string.habits_change_image
                                    },
                                ),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onChooseImage()
                        },
                        modifier = Modifier.testTag("habit_image_action_${habit.title}"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habits_archive)) },
                        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onArchive()
                        },
                        modifier = Modifier.testTag("archive_habit_${habit.title}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitProgressDialog(
    habit: Habit,
    epochDay: Long,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(habit.id, epochDay) { mutableStateOf(habit.progressByEpochDay[epochDay].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_progress_title, habit.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                Text(
                    LocalDate
                        .ofEpochDay(epochDay)
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", currentLocale())),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.habits_progress_goal, habit.targetAmount, habit.targetUnit).trim(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(MAX_TARGET_LENGTH) },
                    label = { Text(stringResource(R.string.habits_progress_label)) },
                    suffix = { if (habit.targetUnit.isNotBlank()) Text(habit.targetUnit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("habit_progress_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                modifier = Modifier.testTag("save_habit_progress"),
            ) {
                Text(stringResource(R.string.habits_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) } },
    )
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
    presets: List<HabitPreset>,
    onSavePreset: (String, Set<Weekday>, Int?, Set<Int>, Int?, String, String) -> Unit,
    onRemovePreset: (HabitPreset) -> Unit,
    onSave: (String, Set<Weekday>, Int?, Set<Int>, Int?, String, String) -> Unit,
) {
    var title by remember(habit?.id) { mutableStateOf(habit?.title.orEmpty()) }
    var selectedDays by
        remember(habit?.id) {
            mutableStateOf(habit?.scheduledWeekdays ?: Weekday.entries.toSet())
        }
    var scheduleMode by remember(habit?.id) {
        mutableStateOf(
            when {
                habit?.scheduledMonthDays?.isNotEmpty() == true -> HabitScheduleMode.MONTH_DAYS
                habit?.repeatEveryDays != null -> HabitScheduleMode.INTERVAL
                else -> HabitScheduleMode.WEEKDAYS
            },
        )
    }
    var repeatEveryDaysText by remember(habit?.id) {
        mutableStateOf(habit?.repeatEveryDays?.toString().orEmpty())
    }
    var scheduledMonthDaysText by remember(habit?.id) {
        mutableStateOf(
            habit
                ?.scheduledMonthDays
                ?.sorted()
                ?.joinToString(", ")
                .orEmpty(),
        )
    }
    var reminderMinutesOfDay by remember(habit?.id) { mutableStateOf(habit?.reminderMinutesOfDay) }
    var targetAmount by remember(habit?.id) { mutableStateOf(habit?.targetAmount.orEmpty()) }
    var targetUnit by remember(habit?.id) { mutableStateOf(habit?.targetUnit.orEmpty()) }
    val context = LocalContext.current
    val locale = currentLocale()
    val intervalDays = repeatEveryDaysText.toIntOrNull()
    val monthDays = parseMonthDays(scheduledMonthDaysText)
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { value -> title = value.take(MAX_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    label = { Text(stringResource(R.string.habits_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_TITLE_LENGTH") },
                    singleLine = true,
                )
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.habits_presets), style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        presets.forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    title = preset.title
                                    selectedDays = preset.scheduledWeekdays
                                    scheduleMode =
                                        when {
                                            preset.scheduledMonthDays.isNotEmpty() -> HabitScheduleMode.MONTH_DAYS
                                            preset.repeatEveryDays != null -> HabitScheduleMode.INTERVAL
                                            else -> HabitScheduleMode.WEEKDAYS
                                        }
                                    repeatEveryDaysText = preset.repeatEveryDays?.toString().orEmpty()
                                    scheduledMonthDaysText = preset.scheduledMonthDays.sorted().joinToString(", ")
                                    reminderMinutesOfDay = preset.reminderMinutesOfDay
                                    targetAmount = preset.targetAmount
                                    targetUnit = preset.targetUnit
                                },
                                label = { Text(preset.title) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription =
                                            stringResource(R.string.habits_remove_preset, preset.title),
                                        modifier = Modifier.size(18.dp).clickable { onRemovePreset(preset) },
                                    )
                                },
                                modifier = Modifier.testTag("habit_preset_${preset.title}"),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        onSavePreset(
                            title,
                            selectedDays,
                            repeatEveryDaysText.toIntOrNull().takeIf {
                                scheduleMode == HabitScheduleMode.INTERVAL
                            },
                            parseMonthDays(scheduledMonthDaysText)
                                .takeIf {
                                    scheduleMode == HabitScheduleMode.MONTH_DAYS
                                }.orEmpty(),
                            reminderMinutesOfDay,
                            targetAmount,
                            targetUnit,
                        )
                    },
                    enabled =
                        title.isNotBlank() &&
                            scheduleIsValid(scheduleMode, selectedDays, repeatEveryDaysText, scheduledMonthDaysText),
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
                    FilterChip(
                        selected = scheduleMode == HabitScheduleMode.WEEKDAYS,
                        onClick = { scheduleMode = HabitScheduleMode.WEEKDAYS },
                        label = { Text(stringResource(R.string.habits_schedule_weekdays)) },
                        modifier = Modifier.testTag("habit_schedule_weekdays"),
                    )
                    FilterChip(
                        selected = scheduleMode == HabitScheduleMode.INTERVAL,
                        onClick = { scheduleMode = HabitScheduleMode.INTERVAL },
                        label = { Text(stringResource(R.string.habits_schedule_interval)) },
                        modifier = Modifier.testTag("habit_schedule_interval"),
                    )
                    FilterChip(
                        selected = scheduleMode == HabitScheduleMode.MONTH_DAYS,
                        onClick = { scheduleMode = HabitScheduleMode.MONTH_DAYS },
                        label = { Text(stringResource(R.string.habits_schedule_month_days)) },
                        modifier = Modifier.testTag("habit_schedule_month_days"),
                    )
                }
                when (scheduleMode) {
                    HabitScheduleMode.INTERVAL -> {
                        OutlinedTextField(
                            value = repeatEveryDaysText,
                            onValueChange = { repeatEveryDaysText = it.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.habits_interval_days)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        if (
                                            repeatEveryDaysText.isNotBlank() &&
                                            (intervalDays == null || intervalDays !in 1..MAX_REPEAT_INTERVAL_DAYS)
                                        ) {
                                            R.string.habits_interval_error
                                        } else {
                                            R.string.habits_interval_description
                                        },
                                    ),
                                )
                            },
                            isError =
                                repeatEveryDaysText.isNotBlank() &&
                                    (intervalDays == null || intervalDays !in 1..MAX_REPEAT_INTERVAL_DAYS),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("habit_repeat_interval"),
                        )
                    }
                    HabitScheduleMode.MONTH_DAYS -> {
                        OutlinedTextField(
                            value = scheduledMonthDaysText,
                            onValueChange = { value ->
                                scheduledMonthDaysText =
                                    value.filter { it.isDigit() || it == ',' || it == ';' || it.isWhitespace() }.take(
                                        96,
                                    )
                            },
                            label = { Text(stringResource(R.string.habits_month_days)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        if (scheduledMonthDaysText.isNotBlank() && monthDays.isEmpty()) {
                                            R.string.habits_month_days_error
                                        } else {
                                            R.string.habits_month_days_description
                                        },
                                    ),
                                )
                            },
                            isError = scheduledMonthDaysText.isNotBlank() && monthDays.isEmpty(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("habit_month_days"),
                        )
                    }
                    HabitScheduleMode.WEEKDAYS -> {
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
                onClick = {
                    onSave(
                        title,
                        selectedDays,
                        repeatEveryDaysText.toIntOrNull().takeIf {
                            scheduleMode == HabitScheduleMode.INTERVAL
                        },
                        parseMonthDays(scheduledMonthDaysText)
                            .takeIf {
                                scheduleMode == HabitScheduleMode.MONTH_DAYS
                            }.orEmpty(),
                        reminderMinutesOfDay,
                        targetAmount,
                        targetUnit,
                    )
                },
                enabled =
                    title.isNotBlank() &&
                        scheduleIsValid(scheduleMode, selectedDays, repeatEveryDaysText, scheduledMonthDaysText),
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
            value = loadSampledImage(imagePath)
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
private fun scheduleLabel(habit: Habit): String {
    if (habit.scheduledMonthDays.isNotEmpty()) {
        return stringResource(
            R.string.habits_on_month_days,
            habit.scheduledMonthDays.sorted().joinToString(", "),
        )
    }
    habit.repeatEveryDays?.let { interval ->
        return stringResource(R.string.habits_every_n_days, interval)
    }
    val days = habit.scheduledWeekdays
    if (days.size == Weekday.entries.size) {
        return stringResource(R.string.habits_every_day)
    }

    var result = ""
    for (weekday in days.sortedBy(Weekday::ordinal)) {
        result = listOf(result, weekdayShortLabel(weekday)).filter(String::isNotEmpty).joinToString(" · ")
    }
    return result
}

private fun scheduleIsValid(
    scheduleMode: HabitScheduleMode,
    selectedDays: Set<Weekday>,
    repeatEveryDaysText: String,
    scheduledMonthDaysText: String,
): Boolean =
    when (scheduleMode) {
        HabitScheduleMode.WEEKDAYS -> selectedDays.isNotEmpty()
        HabitScheduleMode.INTERVAL -> repeatEveryDaysText.toIntOrNull() in 1..MAX_REPEAT_INTERVAL_DAYS
        HabitScheduleMode.MONTH_DAYS -> parseMonthDays(scheduledMonthDaysText).isNotEmpty()
    }

private fun parseMonthDays(value: String): Set<Int> {
    val parts = value.trim().split(Regex("[,;\\s]+"))
    if (parts.isEmpty() || parts.any(String::isBlank)) return emptySet()
    val days = parts.mapNotNull(String::toIntOrNull)
    return if (days.size == parts.size && days.all { it in 1..31 }) days.toSortedSet() else emptySet()
}

private enum class HabitScheduleMode {
    WEEKDAYS,
    INTERVAL,
    MONTH_DAYS,
}

private enum class HabitViewMode {
    TODAY,
    ALL,
    HISTORY,
    ARCHIVED,
}

private enum class HabitSortMode {
    TIME,
    CREATED,
    NAME,
    STREAK,
    COMPLETION,
}

private enum class HabitInsightPeriod(
    val days: Long?,
) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    ALL(null),
}

private data class HabitProgressTarget(
    val habit: Habit,
    val epochDay: Long,
)

private fun HabitViewMode.labelResource(): Int =
    when (this) {
        HabitViewMode.TODAY -> R.string.habits_view_today
        HabitViewMode.ALL -> R.string.habits_view_all
        HabitViewMode.HISTORY -> R.string.habits_view_history
        HabitViewMode.ARCHIVED -> R.string.habits_view_archived
    }

private fun HabitSortMode.labelResource(): Int =
    when (this) {
        HabitSortMode.TIME -> R.string.habits_sort_time
        HabitSortMode.CREATED -> R.string.habits_sort_created
        HabitSortMode.NAME -> R.string.habits_sort_name
        HabitSortMode.STREAK -> R.string.habits_sort_streak
        HabitSortMode.COMPLETION -> R.string.habits_sort_completion
    }

private fun Habit.matchesQuery(query: String): Boolean =
    query.isEmpty() ||
        title.contains(query, ignoreCase = true) ||
        targetAmount.contains(query, ignoreCase = true) ||
        targetUnit.contains(query, ignoreCase = true)

private fun List<Habit>.sorted(
    mode: HabitSortMode,
    todayEpochDay: Long,
): List<Habit> =
    when (mode) {
        HabitSortMode.TIME -> sortedByReminderTime()
        HabitSortMode.CREATED -> sortedBy(Habit::createdAtEpochMillis)
        HabitSortMode.NAME -> sortedBy { it.title.lowercase() }
        HabitSortMode.STREAK -> sortedByDescending { it.currentStreak(todayEpochDay) }
        HabitSortMode.COMPLETION ->
            sortedByDescending { habit ->
                habit
                    .periodStats(
                        maxOf(habit.startEpochDay, todayEpochDay - ANALYTICS_DAYS + 1),
                        todayEpochDay,
                    ).completionPercent
            }
    }

private fun HabitInsightPeriod.labelResource(): Int =
    when (this) {
        HabitInsightPeriod.WEEK -> R.string.habits_insights_7_days
        HabitInsightPeriod.MONTH -> R.string.habits_insights_30_days
        HabitInsightPeriod.QUARTER -> R.string.habits_insights_90_days
        HabitInsightPeriod.ALL -> R.string.habits_insights_all_time
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
private const val MAX_REPEAT_INTERVAL_DAYS = 3650
private const val DEFAULT_REMINDER_MINUTES = 9 * 60
private const val ANALYTICS_DAYS = 30L
private const val HISTORY_DAYS = 14L
