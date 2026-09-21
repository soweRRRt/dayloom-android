package com.sowerrrt.dayloom.feature.planner

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomHorizontalRail
import com.sowerrrt.dayloom.core.designsystem.DayloomMotion
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomSwipeToArchive
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.designsystem.dayloomDialogMotion
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanPreset
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.occursOn
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private data class PlanEditorValue(
    val title: String,
    val note: String,
    val reminderMinutesOfDay: Int?,
    val repeat: PlanRepeat,
    val reminderEnabled: Boolean,
    val scheduledWeekdays: Set<Weekday>,
    val repeatEveryDays: Int?,
    val scheduledMonthDays: Set<Int>,
    val reminderOffsetsMinutes: Set<Int>,
    val measurementUnit: String,
)

@Composable
fun PlannerScreen(viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    var showPlanDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<PlanItem?>(null) }
    var pendingArchive by remember { mutableStateOf<PlanItem?>(null) }
    var measurementTarget by remember { mutableStateOf<Pair<PlanItem, Long>?>(null) }
    var statisticsTarget by remember { mutableStateOf<PlanItem?>(null) }
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().testTag("planner_screen")) {
        DayloomTopBar(stringResource(R.string.planner_title))
        when {
            state.isLoading -> LoadingState(Modifier.weight(1f))
            state.hasError ->
                ErrorState(
                    title = stringResource(R.string.planner_error_title),
                    message = stringResource(R.string.planner_error_description),
                    retryLabel = stringResource(R.string.planner_retry),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )
            else ->
                PlannerContent(
                    state = state,
                    onSelectDate = viewModel::selectDate,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onToday = viewModel::showToday,
                    onToggleHabit = viewModel::toggleHabit,
                    onTogglePlan = { plan, epochDay ->
                        if (plan.measurementUnit.isBlank()) {
                            viewModel.togglePlan(plan.id, epochDay)
                        } else {
                            measurementTarget = plan to epochDay
                        }
                    },
                    onMovePlan = viewModel::movePlan,
                    onCreatePlan = {
                        editingPlan = null
                        showPlanDialog = true
                    },
                    onEditPlan = {
                        editingPlan = it
                        showPlanDialog = true
                    },
                    onArchivePlan = { pendingArchive = it },
                    onSwipeArchivePlan = { viewModel.archivePlan(it.id) },
                    onRestorePlan = viewModel::restorePlan,
                    onOpenStatistics = { statisticsTarget = it },
                    onUsePreset = { preset ->
                        if (
                            preset.reminderMinutesOfDay != null &&
                            preset.reminderEnabled &&
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

    if (showPlanDialog) {
        PlanEditorDialog(
            plan = editingPlan,
            selectedEpochDay = state.selectedEpochDay,
            onDismiss = { showPlanDialog = false },
            presets = state.presets,
            onSavePreset = { value ->
                viewModel.savePreset(
                    title = value.title,
                    reminderMinutesOfDay = value.reminderMinutesOfDay,
                    repeat = value.repeat,
                    reminderEnabled = value.reminderEnabled,
                    scheduledWeekdays = value.scheduledWeekdays,
                    repeatEveryDays = value.repeatEveryDays,
                    scheduledMonthDays = value.scheduledMonthDays,
                    reminderOffsetsMinutes = value.reminderOffsetsMinutes,
                    measurementUnit = value.measurementUnit,
                )
            },
            onRemovePreset = viewModel::removePreset,
            onSave = { value ->
                val plan = editingPlan
                if (
                    value.reminderMinutesOfDay != null &&
                    value.reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (plan == null) {
                    viewModel.createPlan(
                        title = value.title,
                        reminderMinutesOfDay = value.reminderMinutesOfDay,
                        repeat = value.repeat,
                        reminderEnabled = value.reminderEnabled,
                        scheduledWeekdays = value.scheduledWeekdays,
                        repeatEveryDays = value.repeatEveryDays,
                        scheduledMonthDays = value.scheduledMonthDays,
                        note = value.note,
                        reminderOffsetsMinutes = value.reminderOffsetsMinutes,
                        measurementUnit = value.measurementUnit,
                    )
                } else {
                    viewModel.updatePlan(
                        id = plan.id,
                        title = value.title,
                        reminderMinutesOfDay = value.reminderMinutesOfDay,
                        repeat = value.repeat,
                        reminderEnabled = value.reminderEnabled,
                        scheduledWeekdays = value.scheduledWeekdays,
                        repeatEveryDays = value.repeatEveryDays,
                        scheduledMonthDays = value.scheduledMonthDays,
                        note = value.note,
                        reminderOffsetsMinutes = value.reminderOffsetsMinutes,
                        measurementUnit = value.measurementUnit,
                    )
                }
                showPlanDialog = false
            },
        )
    }

    pendingArchive?.let { plan ->
        AlertDialog(
            modifier = Modifier.dayloomDialogMotion(),
            onDismissRequest = { pendingArchive = null },
            title = { Text(stringResource(R.string.planner_archive_title)) },
            text = { Text(stringResource(R.string.planner_archive_description, plan.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archivePlan(plan.id)
                        pendingArchive = null
                    },
                    modifier = Modifier.testTag("confirm_archive_plan"),
                ) {
                    Text(
                        stringResource(R.string.planner_archive_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text(stringResource(R.string.planner_cancel)) }
            },
        )
    }

    measurementTarget?.let { (plan, epochDay) ->
        MeasurementEntryDialog(
            plan = plan,
            epochDay = epochDay,
            onDismiss = { measurementTarget = null },
            onSave = { value ->
                viewModel.recordMeasurement(plan.id, epochDay, value)
                measurementTarget = null
            },
        )
    }

    statisticsTarget?.let { plan ->
        MeasurementStatisticsDialog(plan = plan, onDismiss = { statisticsTarget = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannerContent(
    state: PlannerUiState,
    onSelectDate: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onToggleHabit: (com.sowerrrt.dayloom.core.model.EntityId) -> Unit,
    onTogglePlan: (PlanItem, Long) -> Unit,
    onMovePlan: (com.sowerrrt.dayloom.core.model.EntityId, Long) -> Unit,
    onCreatePlan: () -> Unit,
    onEditPlan: (PlanItem) -> Unit,
    onArchivePlan: (PlanItem) -> Unit,
    onSwipeArchivePlan: (PlanItem) -> Unit,
    onRestorePlan: (com.sowerrrt.dayloom.core.model.EntityId) -> Unit,
    onOpenStatistics: (PlanItem) -> Unit,
    onUsePreset: (PlanPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    var calendarMode by rememberSaveable { mutableStateOf(CalendarMode.MONTH) }
    var showArchivedPlans by rememberSaveable { mutableStateOf(false) }
    var planQuery by rememberSaveable { mutableStateOf("") }
    var planDateFilter by rememberSaveable { mutableStateOf(PlanDateFilter.SELECTED) }
    var planStatusFilter by rememberSaveable { mutableStateOf(PlanStatusFilter.ALL) }
    var planTimeFilter by rememberSaveable { mutableStateOf(PlanTimeFilter.ALL) }
    var planSortMode by rememberSaveable { mutableStateOf(PlanSortMode.TIME) }
    val filteredPlans =
        (if (showArchivedPlans) state.archivedPlans else state.plans)
            .filter {
                it.matchesQuery(planQuery.trim()) &&
                    it.matchesDateFilter(planDateFilter, state.selectedEpochDay, state.todayEpochDay) &&
                    it.matchesStatusFilter(
                        planStatusFilter,
                        planDateFilter,
                        state.selectedEpochDay,
                        state.todayEpochDay,
                    ) &&
                    it.matchesTimeFilter(planTimeFilter)
            }
    val visiblePlans = filteredPlans.sorted(planSortMode, planDateFilter, state.selectedEpochDay, state.todayEpochDay)
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("planner_list"),
            contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
        ) {
            item {
                Text(
                    stringResource(R.string.planner_calendar_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.reminderSchedulingFailed) {
                item {
                    DayloomCard(Modifier.fillMaxWidth().testTag("reminder_error")) {
                        Text(
                            stringResource(R.string.planner_reminder_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                    ) {
                        FilterChip(
                            selected = calendarMode == CalendarMode.WEEK,
                            onClick = { calendarMode = CalendarMode.WEEK },
                            label = { Text(stringResource(R.string.planner_week)) },
                            modifier = Modifier.weight(1f).testTag("calendar_mode_week"),
                        )
                        FilterChip(
                            selected = calendarMode == CalendarMode.MONTH,
                            onClick = { calendarMode = CalendarMode.MONTH },
                            label = { Text(stringResource(R.string.planner_month)) },
                            modifier = Modifier.weight(1f).testTag("calendar_mode_month"),
                        )
                        FilterChip(
                            selected = calendarMode == CalendarMode.AGENDA,
                            onClick = { calendarMode = CalendarMode.AGENDA },
                            label = { Text(stringResource(R.string.planner_agenda)) },
                            modifier = Modifier.weight(1f).testTag("calendar_mode_agenda"),
                        )
                    }
                    Crossfade(
                        targetState = calendarMode,
                        animationSpec = tween(DayloomMotion.STANDARD_MILLIS),
                        label = "calendarMode",
                    ) { mode ->
                        when (mode) {
                            CalendarMode.MONTH ->
                                MonthCalendar(
                                    state = state,
                                    locale = locale,
                                    onSelectDate = onSelectDate,
                                    onPreviousMonth = onPreviousMonth,
                                    onNextMonth = onNextMonth,
                                    onToday = onToday,
                                )
                            CalendarMode.WEEK ->
                                WeekCalendar(
                                    state = state,
                                    locale = locale,
                                    onSelectDate = onSelectDate,
                                    onToday = onToday,
                                )
                            CalendarMode.AGENDA ->
                                AgendaCalendar(
                                    state = state,
                                    locale = locale,
                                    onSelectDate = onSelectDate,
                                    onToday = onToday,
                                )
                        }
                    }
                }
            }
            item {
                SelectedDateHeader(
                    epochDay = state.selectedEpochDay,
                    locale = locale,
                    habitCount = state.selectedHabits.size,
                    planCount = state.selectedPlans.size,
                )
            }
            if (state.presets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                        Text(
                            stringResource(R.string.planner_quick_add),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        DayloomHorizontalRail(
                            items = state.presets,
                            key = { it.title },
                        ) { preset ->
                            AssistChip(
                                onClick = { onUsePreset(preset) },
                                label = { Text(preset.title) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                modifier = Modifier.testTag("quick_plan_preset_${preset.title}"),
                            )
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.planner_habits_section), MaterialTheme.colorScheme.primary) }
            if (state.selectedHabits.isEmpty()) {
                item { EmptyDayCard(stringResource(R.string.planner_no_habits)) }
            } else {
                items(state.selectedHabits, key = { "habit-${it.id.value}" }) { habit ->
                    Box(Modifier.animateItem()) {
                        CalendarHabitRow(
                            habit = habit,
                            epochDay = state.selectedEpochDay,
                            canComplete = state.selectedEpochDay <= state.todayEpochDay,
                            onToggle = { onToggleHabit(habit.id) },
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.planner_plans_section), MaterialTheme.colorScheme.secondary) }
            item {
                PlanFilterPanel(
                    showArchived = showArchivedPlans,
                    onShowArchivedChange = { showArchivedPlans = it },
                    query = planQuery,
                    onQueryChange = { planQuery = it },
                    dateFilter = planDateFilter,
                    onDateFilterChange = { planDateFilter = it },
                    statusFilter = planStatusFilter,
                    onStatusFilterChange = { planStatusFilter = it },
                    timeFilter = planTimeFilter,
                    onTimeFilterChange = { planTimeFilter = it },
                    sortMode = planSortMode,
                    onSortModeChange = { planSortMode = it },
                    onReset = {
                        planQuery = ""
                        planDateFilter = PlanDateFilter.SELECTED
                        planStatusFilter = PlanStatusFilter.ALL
                        planTimeFilter = PlanTimeFilter.ALL
                        planSortMode = PlanSortMode.TIME
                    },
                )
            }
            if (visiblePlans.isEmpty()) {
                item {
                    EmptyDayCard(
                        stringResource(
                            if (showArchivedPlans && planQuery.isBlank()) {
                                R.string.planner_archive_empty
                            } else if (
                                planQuery.isNotBlank() ||
                                planDateFilter != PlanDateFilter.SELECTED ||
                                planStatusFilter != PlanStatusFilter.ALL ||
                                planTimeFilter != PlanTimeFilter.ALL
                            ) {
                                R.string.planner_filters_empty
                            } else {
                                R.string.planner_no_plans
                            },
                        ),
                    )
                }
            } else if (showArchivedPlans) {
                items(visiblePlans, key = { "archived-plan-${it.id.value}" }) { plan ->
                    Box(Modifier.animateItem()) {
                        ArchivedPlanRow(
                            plan = plan,
                            onRestore = { onRestorePlan(plan.id) },
                        )
                    }
                }
            } else {
                items(visiblePlans, key = { "plan-${it.id.value}" }) { plan ->
                    val displayEpochDay =
                        plan.displayEpochDay(planDateFilter, state.selectedEpochDay, state.todayEpochDay)
                    Box(Modifier.animateItem()) {
                        DayloomSwipeToArchive(
                            archiveLabel = stringResource(R.string.planner_archive_confirm),
                            onArchive = { onSwipeArchivePlan(plan) },
                        ) {
                            PlanRow(
                                plan = plan,
                                completed = plan.isCompletedOn(displayEpochDay),
                                displayEpochDay = displayEpochDay,
                                onToggle = { onTogglePlan(plan, displayEpochDay) },
                                onMoveTomorrow = { onMovePlan(plan.id, 1) },
                                onMoveNextWeek = { onMovePlan(plan.id, 7) },
                                onEdit = { onEditPlan(plan) },
                                onArchive = { onArchivePlan(plan) },
                                onOpenStatistics = { onOpenStatistics(plan) },
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onCreatePlan,
            modifier = Modifier.align(Alignment.BottomEnd).padding(DayloomSpacing.md).testTag("create_plan"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.planner_action))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanFilterPanel(
    showArchived: Boolean,
    onShowArchivedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    dateFilter: PlanDateFilter,
    onDateFilterChange: (PlanDateFilter) -> Unit,
    statusFilter: PlanStatusFilter,
    onStatusFilterChange: (PlanStatusFilter) -> Unit,
    timeFilter: PlanTimeFilter,
    onTimeFilterChange: (PlanTimeFilter) -> Unit,
    sortMode: PlanSortMode,
    onSortModeChange: (PlanSortMode) -> Unit,
    onReset: () -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val activeFilterCount =
        listOf(
            dateFilter != PlanDateFilter.SELECTED,
            statusFilter != PlanStatusFilter.ALL,
            timeFilter != PlanTimeFilter.ALL,
        ).count { it }
    val hasCustomFilters =
        query.isNotBlank() ||
            dateFilter != PlanDateFilter.SELECTED ||
            statusFilter != PlanStatusFilter.ALL ||
            timeFilter != PlanTimeFilter.ALL ||
            sortMode != PlanSortMode.TIME
    DayloomCard(Modifier.fillMaxWidth().testTag("plan_filters")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
            ) {
                FilterChip(
                    selected = !showArchived,
                    onClick = { onShowArchivedChange(false) },
                    label = { Text(stringResource(R.string.planner_active_plans)) },
                    modifier = Modifier.testTag("plan_view_active"),
                )
                FilterChip(
                    selected = showArchived,
                    onClick = { onShowArchivedChange(true) },
                    label = { Text(stringResource(R.string.planner_archived_plans)) },
                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                    modifier = Modifier.testTag("plan_view_archived"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.planner_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon =
                        if (query.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.testTag("plan_search_clear"),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.planner_search_clear),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.weight(1f).testTag("plan_search"),
                )
                Box {
                    IconButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier.testTag("plan_sort"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription =
                                stringResource(
                                    R.string.planner_sort_description,
                                    stringResource(sortMode.labelResource()),
                                ),
                        )
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                    ) {
                        PlanSortMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelResource())) },
                                leadingIcon =
                                    if (option == sortMode) {
                                        { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                onClick = {
                                    onSortModeChange(option)
                                    sortExpanded = false
                                },
                                modifier = Modifier.testTag("plan_sort_${option.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = filtersExpanded || activeFilterCount > 0,
                    onClick = { filtersExpanded = !filtersExpanded },
                    label = {
                        Text(
                            if (activeFilterCount == 0) {
                                stringResource(R.string.planner_filters_title)
                            } else {
                                stringResource(R.string.planner_filters_active, activeFilterCount)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    modifier = Modifier.testTag("plan_filters_toggle"),
                )
                if (hasCustomFilters) {
                    TextButton(onClick = onReset, modifier = Modifier.testTag("plan_filters_reset")) {
                        Text(stringResource(R.string.planner_filters_reset))
                    }
                }
            }
            AnimatedVisibility(visible = filtersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                    Text(stringResource(R.string.planner_filter_date), style = MaterialTheme.typography.labelLarge)
                    DayloomHorizontalRail(
                        items = PlanDateFilter.entries,
                        key = { it.name },
                    ) { option ->
                        FilterChip(
                            selected = dateFilter == option,
                            onClick = { onDateFilterChange(option) },
                            label = { Text(stringResource(option.labelResource())) },
                            modifier = Modifier.testTag("plan_date_${option.name.lowercase()}"),
                        )
                    }
                    Text(stringResource(R.string.planner_filter_status), style = MaterialTheme.typography.labelLarge)
                    DayloomHorizontalRail(
                        items = PlanStatusFilter.entries,
                        key = { it.name },
                    ) { option ->
                        FilterChip(
                            selected = statusFilter == option,
                            onClick = { onStatusFilterChange(option) },
                            label = { Text(stringResource(option.labelResource())) },
                            modifier = Modifier.testTag("plan_status_${option.name.lowercase()}"),
                        )
                    }
                    Text(stringResource(R.string.planner_filter_time), style = MaterialTheme.typography.labelLarge)
                    DayloomHorizontalRail(
                        items = PlanTimeFilter.entries,
                        key = { it.name },
                    ) { option ->
                        FilterChip(
                            selected = timeFilter == option,
                            onClick = { onTimeFilterChange(option) },
                            label = { Text(stringResource(option.labelResource())) },
                            modifier = Modifier.testTag("plan_time_${option.name.lowercase()}"),
                        )
                    }
                }
            }
        }
    }
}

private enum class CalendarMode { WEEK, MONTH, AGENDA }

@Composable
private fun AgendaCalendar(
    state: PlannerUiState,
    locale: Locale,
    onSelectDate: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val center = LocalDate.ofEpochDay(state.selectedEpochDay)
    val days = remember(center) { (-3L..10L).map(center::plusDays) }
    DayloomCard(Modifier.fillMaxWidth().testTag("agenda_calendar")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        center.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.planner_agenda_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onToday) {
                    Text(stringResource(R.string.planner_today), maxLines = 1)
                }
            }
            CalendarLegend()
            DayloomHorizontalRail(items = days, key = { it.toEpochDay() }) { date ->
                val epochDay = date.toEpochDay()
                val selected = epochDay == state.selectedEpochDay
                DayloomCard(
                    modifier = Modifier.width(92.dp).testTag("agenda_day_$epochDay"),
                    onClick = { onSelectDate(epochDay) },
                    contentPadding = PaddingValues(horizontal = DayloomSpacing.sm, vertical = DayloomSpacing.sm),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                ).padding(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall)
                        CalendarActivityBar(
                            color = MaterialTheme.colorScheme.primary,
                            progress =
                                completionProgress(
                                    state.completedHabitCount(epochDay),
                                    state.habitCount(epochDay),
                                ),
                        )
                        CalendarActivityBar(
                            color = MaterialTheme.colorScheme.secondary,
                            progress =
                                completionProgress(
                                    state.completedPlanCount(epochDay),
                                    state.planCount(epochDay),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCalendar(
    state: PlannerUiState,
    locale: Locale,
    onSelectDate: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val selected = LocalDate.ofEpochDay(state.selectedEpochDay)
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    DayloomCard(Modifier.fillMaxWidth().testTag("week_calendar")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onSelectDate(selected.minusWeeks(1).toEpochDay()) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.planner_previous_week))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Crossfade(
                        targetState = weekStart,
                        animationSpec = tween(DayloomMotion.QUICK_MILLIS),
                        label = "weekLabel",
                    ) { visibleWeekStart ->
                        Text(
                            weekLabel(visibleWeekStart, visibleWeekStart.plusDays(6), locale),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TextButton(onClick = onToday) { Text(stringResource(R.string.planner_today)) }
                }
                IconButton(onClick = { onSelectDate(selected.plusWeeks(1).toEpochDay()) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.planner_next_week))
                }
            }
            CalendarLegend()
            AnimatedContent(
                targetState = weekStart.toEpochDay(),
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (
                        slideInHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { direction * it / 3 } +
                            fadeIn(tween(DayloomMotion.STANDARD_MILLIS))
                    ).togetherWith(
                        slideOutHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { -direction * it / 3 } +
                            fadeOut(tween(DayloomMotion.QUICK_MILLIS)),
                    )
                },
                label = "calendarWeek",
            ) { visibleWeekStartEpochDay ->
                val visibleDays =
                    (0L..6L).map { LocalDate.ofEpochDay(visibleWeekStartEpochDay).plusDays(it) }
                Row(Modifier.fillMaxWidth()) {
                    visibleDays.forEach { date ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CalendarDay(
                                date = date,
                                selected = date == selected,
                                today = date.toEpochDay() == state.todayEpochDay,
                                habitCount = state.habitCount(date.toEpochDay()),
                                planCount = state.planCount(date.toEpochDay()),
                                completedHabitCount = state.completedHabitCount(date.toEpochDay()),
                                completedPlanCount = state.completedPlanCount(date.toEpochDay()),
                                onClick = { onSelectDate(date.toEpochDay()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    state: PlannerUiState,
    locale: Locale,
    onSelectDate: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val month = state.displayedMonth
    DayloomCard(Modifier.fillMaxWidth().testTag("month_calendar")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.planner_previous_month),
                    )
                }
                Crossfade(
                    targetState = month,
                    animationSpec = tween(DayloomMotion.QUICK_MILLIS),
                    label = "monthLabel",
                ) { visibleMonth ->
                    Text(monthLabel(visibleMonth, locale), style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onNextMonth) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.planner_next_month),
                    )
                }
            }
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.planner_today))
            }
            CalendarLegend()
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach { dayOfWeek ->
                    Text(
                        dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (
                        slideInHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { direction * it / 3 } +
                            fadeIn(tween(DayloomMotion.STANDARD_MILLIS))
                    ).togetherWith(
                        slideOutHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { -direction * it / 3 } +
                            fadeOut(tween(DayloomMotion.QUICK_MILLIS)),
                    )
                },
                label = "calendarMonth",
            ) { visibleMonth ->
                val firstDay = visibleMonth.atDay(1)
                val leadingEmptyDays = firstDay.dayOfWeek.value - 1
                val cells =
                    List<LocalDate?>(leadingEmptyDays) { null } +
                        (1..visibleMonth.lengthOfMonth()).map(visibleMonth::atDay)
                val visibleRows = cells.chunked(7)
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xxs)) {
                    visibleRows.forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { index ->
                                val date = week.getOrNull(index)
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    CalendarDay(
                                        date = date,
                                        selected = date.toEpochDay() == state.selectedEpochDay,
                                        today = date.toEpochDay() == state.todayEpochDay,
                                        habitCount = state.habitCount(date.toEpochDay()),
                                        planCount = state.planCount(date.toEpochDay()),
                                        completedHabitCount = state.completedHabitCount(date.toEpochDay()),
                                        completedPlanCount = state.completedPlanCount(date.toEpochDay()),
                                        onClick = { onSelectDate(date.toEpochDay()) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    habitCount: Int,
    planCount: Int,
    completedHabitCount: Int,
    completedPlanCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val activityCount = habitCount + planCount
    val background by
        animateColorAsState(
            targetValue =
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    activityCount > 0 ->
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.18f + activityCount.coerceAtMost(6) * 0.035f,
                        )
                    else -> Color.Transparent
                },
            animationSpec = tween(DayloomMotion.STANDARD_MILLIS),
            label = "calendarDayBackground",
        )
    val borderColor by
        animateColorAsState(
            targetValue =
                if (today && !selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            animationSpec = tween(DayloomMotion.STANDARD_MILLIS),
            label = "calendarDayBorder",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (selected) 1.04f else 1f,
            animationSpec = tween(DayloomMotion.QUICK_MILLIS),
            label = "calendarDayScale",
        )
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.border(1.dp, borderColor, shape)
                .clip(shape)
                .background(background)
                .clickable(onClick = onClick)
                .testTag("calendar_day_${date.toEpochDay()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (habitCount > 0) {
                CalendarActivityBar(
                    color = MaterialTheme.colorScheme.primary,
                    progress = completedHabitCount.toFloat() / habitCount,
                    modifier = Modifier.testTag("calendar_habit_progress_${date.toEpochDay()}"),
                )
            }
            if (planCount > 0) {
                CalendarActivityBar(
                    color = MaterialTheme.colorScheme.secondary,
                    progress = completedPlanCount.toFloat() / planCount,
                    modifier = Modifier.testTag("calendar_plan_progress_${date.toEpochDay()}"),
                )
            }
        }
    }
}

@Composable
private fun CalendarActivityBar(
    color: Color,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(22.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

private fun completionProgress(
    completed: Int,
    total: Int,
): Float = if (total == 0) 0f else completed.toFloat() / total

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarLegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.planner_habits_section))
        Spacer(Modifier.width(DayloomSpacing.md))
        CalendarLegendItem(MaterialTheme.colorScheme.secondary, stringResource(R.string.planner_plans_section))
    }
}

@Composable
private fun CalendarLegendItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
    ) {
        Box(
            Modifier
                .width(18.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedDateHeader(
    epochDay: Long,
    locale: Locale,
    habitCount: Int,
    planCount: Int,
) {
    val date = LocalDate.ofEpochDay(epochDay)
    Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.planner_day_summary, habitCount, planCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyDayCard(message: String) {
    DayloomCard(Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarHabitRow(
    habit: Habit,
    epochDay: Long,
    canComplete: Boolean,
    onToggle: () -> Unit,
) {
    val completed = epochDay in habit.completedEpochDays
    DayloomCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("calendar_habit_${habit.title}"),
        onClick = onToggle.takeIf { canComplete },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            Icon(
                if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !canComplete -> stringResource(R.string.planner_future_habit)
                        completed -> stringResource(R.string.planner_habit_completed)
                        else -> stringResource(R.string.planner_habit_open)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (habit.targetAmount.isNotBlank() || habit.targetUnit.isNotBlank()) {
                    Text(
                        stringResource(
                            R.string.planner_habit_progress,
                            habit.progressByEpochDay[epochDay].orEmpty().ifBlank { "—" },
                            habit.targetAmount,
                            habit.targetUnit,
                        ).trim(),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedPlanRow(
    plan: PlanItem,
    onRestore: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("archived_plan_${plan.title}")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium)
            if (plan.note.isNotBlank()) {
                Text(
                    plan.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                LocalDate.ofEpochDay(plan.dateEpochDay).format(
                    DateTimeFormatter.ofPattern("d MMMM yyyy", currentLocale()),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            planScheduleLabel(plan)?.let { schedule ->
                Text(schedule, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth().testTag("restore_plan_${plan.title}"),
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = null)
                Spacer(Modifier.size(DayloomSpacing.xs))
                Text(stringResource(R.string.planner_restore))
            }
        }
    }
}

@Composable
private fun PlanRow(
    plan: PlanItem,
    completed: Boolean,
    displayEpochDay: Long,
    onToggle: () -> Unit,
    onMoveTomorrow: () -> Unit,
    onMoveNextWeek: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    DayloomCard(Modifier.fillMaxWidth().testTag("plan_${plan.title}")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.testTag("plan_toggle_${plan.title}"),
                ) {
                    Icon(
                        if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.planner_toggle_plan, plan.title),
                        tint =
                            if (completed) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (completed) TextDecoration.LineThrough else null,
                    )
                    if (plan.note.isNotBlank()) {
                        Text(
                            plan.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("plan_note_${plan.title}"),
                        )
                    }
                    Text(
                        LocalDate.ofEpochDay(displayEpochDay).format(
                            DateTimeFormatter.ofPattern("d MMMM yyyy", currentLocale()),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    plan.reminderMinutesOfDay?.let { reminderMinutes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                            modifier = Modifier.testTag("plan_reminder_${plan.title}"),
                        ) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                formatReminderTime(reminderMinutes, currentLocale()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (plan.reminderEnabled) {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    contentDescription = stringResource(R.string.planner_notification_enabled),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    planScheduleLabel(plan)?.let { schedule ->
                        Text(
                            schedule,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag("plan_repeat_value_${plan.title}"),
                        )
                    }
                    if (plan.measurementUnit.isNotBlank()) {
                        val value = plan.measurementValuesByEpochDay[displayEpochDay]
                        Text(
                            text =
                                if (value == null) {
                                    stringResource(R.string.planner_measurement_waiting, plan.measurementUnit)
                                } else {
                                    stringResource(
                                        R.string.planner_measurement_value,
                                        formatMeasurement(value),
                                        plan.measurementUnit,
                                    )
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .testTag("plan_measurement_${plan.title}")
                                    .clickable(onClick = onToggle),
                        )
                    }
                }
                if (plan.measurementUnit.isNotBlank()) {
                    IconButton(
                        onClick = onOpenStatistics,
                        modifier = Modifier.testTag("plan_measurement_chart_${plan.title}"),
                    ) {
                        Icon(
                            Icons.Rounded.ShowChart,
                            contentDescription = stringResource(R.string.planner_statistics),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("plan_more_${plan.title}"),
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.planner_edit))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.planner_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        if (plan.measurementUnit.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.planner_statistics)) },
                                leadingIcon = { Icon(Icons.Rounded.ShowChart, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenStatistics()
                                },
                                modifier = Modifier.testTag("plan_measurement_stats_${plan.title}"),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.planner_move_tomorrow)) },
                            onClick = {
                                menuExpanded = false
                                onMoveTomorrow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.planner_move_next_week)) },
                            onClick = {
                                menuExpanded = false
                                onMoveNextWeek()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.planner_archive)) },
                            leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onArchive()
                            },
                            modifier = Modifier.testTag("archive_plan_${plan.title}"),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanEditorDialog(
    plan: PlanItem?,
    selectedEpochDay: Long,
    onDismiss: () -> Unit,
    presets: List<PlanPreset>,
    onSavePreset: (PlanEditorValue) -> Unit,
    onRemovePreset: (PlanPreset) -> Unit,
    onSave: (PlanEditorValue) -> Unit,
) {
    var title by remember(plan?.id) { mutableStateOf(plan?.title.orEmpty()) }
    var note by remember(plan?.id) { mutableStateOf(plan?.note.orEmpty()) }
    var reminderMinutesOfDay by remember(plan?.id) { mutableStateOf(plan?.reminderMinutesOfDay) }
    var reminderEnabled by remember(plan?.id) { mutableStateOf(plan?.reminderEnabled ?: false) }
    var reminderOffsetsMinutes by remember(plan?.id) {
        mutableStateOf(plan?.reminderOffsetsMinutes?.ifEmpty { setOf(0) } ?: setOf(0))
    }
    var customReminderOffset by remember(plan?.id) { mutableStateOf("") }
    var measurementUnit by remember(plan?.id) { mutableStateOf(plan?.measurementUnit.orEmpty()) }
    var selectedDays by remember(plan?.id) {
        mutableStateOf(initialPlanWeekdays(plan, selectedEpochDay))
    }
    var scheduleMode by remember(plan?.id) {
        mutableStateOf(initialPlanScheduleMode(plan))
    }
    var repeatEveryDaysText by remember(plan?.id) {
        mutableStateOf(plan?.repeatEveryDays?.toString().orEmpty())
    }
    var scheduledMonthDaysText by remember(plan?.id) {
        mutableStateOf(initialPlanMonthDays(plan).joinToString(", "))
    }
    val locale = currentLocale()
    val context = LocalContext.current
    val intervalDays = repeatEveryDaysText.toIntOrNull()
    val monthDays = parsePlanMonthDays(scheduledMonthDaysText)
    val scheduleIsValid =
        when (scheduleMode) {
            PlanScheduleMode.ONCE -> true
            PlanScheduleMode.WEEKDAYS -> selectedDays.isNotEmpty()
            PlanScheduleMode.INTERVAL -> intervalDays != null && intervalDays in 1..MAX_REPEAT_INTERVAL_DAYS
            PlanScheduleMode.MONTH_DAYS -> monthDays.isNotEmpty()
        }
    val savedWeekdays = if (scheduleMode == PlanScheduleMode.WEEKDAYS) selectedDays else emptySet()
    val savedIntervalDays = if (scheduleMode == PlanScheduleMode.INTERVAL) intervalDays else null
    val savedMonthDays = if (scheduleMode == PlanScheduleMode.MONTH_DAYS) monthDays else emptySet()
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        title = {
            Text(stringResource(if (plan == null) R.string.planner_create_title else R.string.planner_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                Text(
                    LocalDate.ofEpochDay(selectedEpochDay).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_PLAN_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.planner_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_PLAN_TITLE_LENGTH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_name_input"),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_PLAN_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.planner_note_label)) },
                    supportingText = { Text("${note.length}/$MAX_PLAN_NOTE_LENGTH") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("plan_note_input"),
                )
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.planner_presets), style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        presets.forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    title = preset.title
                                    reminderMinutesOfDay = preset.reminderMinutesOfDay
                                    reminderEnabled = preset.reminderEnabled && preset.reminderMinutesOfDay != null
                                    reminderOffsetsMinutes = preset.reminderOffsetsMinutes.ifEmpty { setOf(0) }
                                    measurementUnit = preset.measurementUnit
                                    selectedDays = initialPresetWeekdays(preset, selectedEpochDay)
                                    scheduleMode = initialPresetScheduleMode(preset)
                                    repeatEveryDaysText = preset.repeatEveryDays?.toString().orEmpty()
                                    scheduledMonthDaysText =
                                        initialPresetMonthDays(preset, selectedEpochDay).joinToString(", ")
                                },
                                label = { Text(preset.title) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription =
                                            stringResource(R.string.planner_remove_preset, preset.title),
                                        modifier = Modifier.size(18.dp).clickable { onRemovePreset(preset) },
                                    )
                                },
                                modifier = Modifier.testTag("plan_preset_${preset.title}"),
                            )
                        }
                    }
                }
                Text(stringResource(R.string.planner_reminder), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.planner_reminder_description),
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
                        modifier = Modifier.testTag("set_plan_reminder"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null)
                        Text(
                            if (reminderMinutesOfDay == null) {
                                stringResource(R.string.planner_add_reminder)
                            } else {
                                formatReminderTime(requireNotNull(reminderMinutesOfDay), locale)
                            },
                            modifier = Modifier.padding(start = DayloomSpacing.xs),
                        )
                    }
                    if (reminderMinutesOfDay != null) {
                        TextButton(
                            onClick = {
                                reminderMinutesOfDay = null
                                reminderEnabled = false
                            },
                            modifier = Modifier.testTag("clear_plan_reminder"),
                        ) {
                            Text(stringResource(R.string.planner_remove_reminder))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.planner_notification),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                if (reminderMinutesOfDay == null) {
                                    R.string.planner_notification_time_required
                                } else {
                                    R.string.planner_notification_description
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = reminderEnabled && reminderMinutesOfDay != null,
                        onCheckedChange = { reminderEnabled = it },
                        enabled = reminderMinutesOfDay != null,
                        modifier = Modifier.testTag("plan_notification_toggle"),
                    )
                }
                if (reminderEnabled && reminderMinutesOfDay != null) {
                    Text(
                        stringResource(R.string.planner_reminder_offsets),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.planner_reminder_offsets_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        (DEFAULT_REMINDER_OFFSETS + reminderOffsetsMinutes).distinct().sorted().forEach { offset ->
                            FilterChip(
                                selected = offset in reminderOffsetsMinutes,
                                onClick = {
                                    reminderOffsetsMinutes =
                                        if (offset in reminderOffsetsMinutes) {
                                            if (reminderOffsetsMinutes.size > 1) {
                                                reminderOffsetsMinutes - offset
                                            } else {
                                                reminderOffsetsMinutes
                                            }
                                        } else {
                                            reminderOffsetsMinutes + offset
                                        }
                                },
                                label = { Text(reminderOffsetLabel(offset)) },
                                modifier = Modifier.testTag("plan_reminder_offset_$offset"),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        OutlinedTextField(
                            value = customReminderOffset,
                            onValueChange = { customReminderOffset = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.planner_custom_offset)) },
                            suffix = { Text(stringResource(R.string.planner_minutes_short)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("plan_custom_reminder_offset"),
                        )
                        TextButton(
                            onClick = {
                                customReminderOffset.toIntOrNull()?.let { offset ->
                                    reminderOffsetsMinutes = reminderOffsetsMinutes + offset
                                    customReminderOffset = ""
                                }
                            },
                            enabled = customReminderOffset.toIntOrNull() in 1..MAX_REMINDER_OFFSET_MINUTES,
                        ) {
                            Text(stringResource(R.string.planner_add_offset))
                        }
                    }
                }
                Text(stringResource(R.string.planner_repeat), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                ) {
                    PlanScheduleMode.entries.forEach { option ->
                        FilterChip(
                            selected = scheduleMode == option,
                            onClick = { scheduleMode = option },
                            label = { Text(stringResource(option.labelResource)) },
                            modifier = Modifier.testTag("plan_schedule_${option.name.lowercase()}"),
                        )
                    }
                }
                when (scheduleMode) {
                    PlanScheduleMode.ONCE -> Unit
                    PlanScheduleMode.WEEKDAYS -> {
                        Text(
                            stringResource(R.string.planner_weekdays_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        ) {
                            Weekday.entries.forEach { day ->
                                FilterChip(
                                    selected = day in selectedDays,
                                    onClick = {
                                        selectedDays =
                                            if (day in selectedDays) selectedDays - day else selectedDays + day
                                    },
                                    label = { Text(stringResource(day.shortLabelResource())) },
                                    modifier = Modifier.testTag("plan_weekday_${day.name.lowercase()}"),
                                )
                            }
                        }
                    }
                    PlanScheduleMode.INTERVAL -> {
                        OutlinedTextField(
                            value = repeatEveryDaysText,
                            onValueChange = { value ->
                                repeatEveryDaysText = value.filter(Char::isDigit).take(4)
                            },
                            label = { Text(stringResource(R.string.planner_interval_days)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        if (
                                            repeatEveryDaysText.isNotEmpty() &&
                                            (intervalDays == null || intervalDays !in 1..MAX_REPEAT_INTERVAL_DAYS)
                                        ) {
                                            R.string.planner_interval_error
                                        } else {
                                            R.string.planner_interval_description
                                        },
                                    ),
                                )
                            },
                            isError =
                                repeatEveryDaysText.isNotEmpty() &&
                                    (intervalDays == null || intervalDays !in 1..MAX_REPEAT_INTERVAL_DAYS),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("plan_interval_input"),
                        )
                    }
                    PlanScheduleMode.MONTH_DAYS -> {
                        OutlinedTextField(
                            value = scheduledMonthDaysText,
                            onValueChange = { value ->
                                scheduledMonthDaysText = value.filter { it.isDigit() || it == ',' || it == ' ' }
                            },
                            label = { Text(stringResource(R.string.planner_month_days)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        if (scheduledMonthDaysText.isNotBlank() && monthDays.isEmpty()) {
                                            R.string.planner_month_days_error
                                        } else {
                                            R.string.planner_month_days_description
                                        },
                                    ),
                                )
                            },
                            isError = scheduledMonthDaysText.isNotBlank() && monthDays.isEmpty(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("plan_month_days_input"),
                        )
                    }
                }
                Text(stringResource(R.string.planner_measurement_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.planner_measurement_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = measurementUnit,
                    onValueChange = { measurementUnit = it.take(MAX_MEASUREMENT_UNIT_LENGTH) },
                    label = { Text(stringResource(R.string.planner_measurement_unit)) },
                    placeholder = { Text(stringResource(R.string.planner_measurement_unit_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_measurement_unit"),
                )
                TextButton(
                    onClick = {
                        onSavePreset(
                            PlanEditorValue(
                                title = title,
                                note = note,
                                reminderMinutesOfDay = reminderMinutesOfDay,
                                repeat = PlanRepeat.NONE,
                                reminderEnabled = reminderEnabled,
                                scheduledWeekdays = savedWeekdays,
                                repeatEveryDays = savedIntervalDays,
                                scheduledMonthDays = savedMonthDays,
                                reminderOffsetsMinutes = reminderOffsetsMinutes,
                                measurementUnit = measurementUnit,
                            ),
                        )
                    },
                    enabled = title.isNotBlank() && scheduleIsValid,
                    modifier = Modifier.testTag("save_plan_preset"),
                ) {
                    Text(stringResource(R.string.planner_save_preset))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PlanEditorValue(
                            title = title,
                            note = note,
                            reminderMinutesOfDay = reminderMinutesOfDay,
                            repeat = PlanRepeat.NONE,
                            reminderEnabled = reminderEnabled,
                            scheduledWeekdays = savedWeekdays,
                            repeatEveryDays = savedIntervalDays,
                            scheduledMonthDays = savedMonthDays,
                            reminderOffsetsMinutes = reminderOffsetsMinutes,
                            measurementUnit = measurementUnit,
                        ),
                    )
                },
                enabled = title.isNotBlank() && scheduleIsValid,
                modifier = Modifier.testTag("save_plan"),
            ) {
                Text(stringResource(R.string.planner_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.planner_cancel)) }
        },
    )
}

@Composable
private fun MeasurementEntryDialog(
    plan: PlanItem,
    epochDay: Long,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    val existingValue = plan.measurementValuesByEpochDay[epochDay]
    var value by remember(plan.id, epochDay) {
        mutableStateOf(existingValue?.let(::formatMeasurement).orEmpty())
    }
    val parsedValue = value.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
    val dateLabel =
        LocalDate.ofEpochDay(epochDay).format(
            DateTimeFormatter.ofPattern("d MMMM yyyy", currentLocale()),
        )

    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_measurement_entry_title, plan.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        value = input.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }.take(24)
                    },
                    label = { Text(stringResource(R.string.planner_measurement_value_label)) },
                    suffix = { Text(plan.measurementUnit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = value.isNotBlank() && parsedValue == null,
                    supportingText = {
                        if (value.isNotBlank() && parsedValue == null) {
                            Text(stringResource(R.string.planner_measurement_value_error))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("plan_measurement_value"),
                )
                if (existingValue != null) {
                    TextButton(
                        onClick = { onSave(null) },
                        modifier = Modifier.testTag("remove_plan_measurement"),
                    ) {
                        Text(stringResource(R.string.planner_measurement_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsedValue) },
                enabled = parsedValue != null,
                modifier = Modifier.testTag("save_plan_measurement"),
            ) {
                Text(stringResource(R.string.planner_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.planner_cancel)) }
        },
    )
}

@Composable
private fun MeasurementStatisticsDialog(
    plan: PlanItem,
    onDismiss: () -> Unit,
) {
    val values = plan.measurementValuesByEpochDay.toList().sortedBy { it.first }
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_statistics_title, plan.title)) },
        text = {
            if (values.isEmpty()) {
                Text(stringResource(R.string.planner_statistics_empty))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
                ) {
                    MeasurementChart(values.map { it.second })
                    values.asReversed().take(20).forEach { (epochDay, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LocalDate.ofEpochDay(epochDay).format(
                                    DateTimeFormatter.ofPattern("d MMM yyyy", currentLocale()),
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${formatMeasurement(value)} ${plan.measurementUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.planner_close)) }
        },
    )
}

@Composable
private fun MeasurementChart(values: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: minimum
    val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(156.dp)
            .testTag("plan_measurement_chart"),
    ) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val points =
            values.mapIndexed { index, value ->
                val x = if (values.size == 1) size.width / 2f else size.width * index / (values.lastIndex.toFloat())
                val y = size.height - ((value - minimum) / range).toFloat() * size.height
                Offset(x, y)
            }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEach { point -> drawCircle(lineColor, radius = 5.dp.toPx(), center = point) }
    }
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private fun monthLabel(
    month: YearMonth,
    locale: Locale,
): String =
    month
        .atDay(1)
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }

private fun weekLabel(
    start: LocalDate,
    end: LocalDate,
    locale: Locale,
): String {
    val startLabel = start.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val endPattern = if (start.year == end.year) "d MMM" else "d MMM yyyy"
    return "$startLabel — ${end.format(DateTimeFormatter.ofPattern(endPattern, locale))}"
}

private const val MAX_PLAN_TITLE_LENGTH = 120
private const val MAX_PLAN_NOTE_LENGTH = 1000
private const val DEFAULT_REMINDER_MINUTES = 9 * 60
private val DEFAULT_REMINDER_OFFSETS = listOf(0, 15, 30, 60)
private const val MAX_REMINDER_OFFSET_MINUTES = 7 * 24 * 60
private const val MAX_MEASUREMENT_UNIT_LENGTH = 24
private const val MAX_REPEAT_INTERVAL_DAYS = 3650
private const val PLAN_OCCURRENCE_LOOKAHEAD_DAYS = 3650L

private enum class PlanDateFilter {
    SELECTED,
    TODAY,
    UPCOMING,
    ANY,
}

private enum class PlanStatusFilter {
    ALL,
    OPEN,
    COMPLETED,
}

private enum class PlanTimeFilter {
    ALL,
    WITH_TIME,
    WITHOUT_TIME,
}

private enum class PlanSortMode {
    TIME,
    DATE,
    CREATED,
    NAME,
}

private fun PlanDateFilter.labelResource(): Int =
    when (this) {
        PlanDateFilter.SELECTED -> R.string.planner_date_selected
        PlanDateFilter.TODAY -> R.string.planner_date_today
        PlanDateFilter.UPCOMING -> R.string.planner_date_upcoming
        PlanDateFilter.ANY -> R.string.planner_date_any
    }

private fun PlanStatusFilter.labelResource(): Int =
    when (this) {
        PlanStatusFilter.ALL -> R.string.planner_status_all
        PlanStatusFilter.OPEN -> R.string.planner_status_open
        PlanStatusFilter.COMPLETED -> R.string.planner_status_completed
    }

private fun PlanTimeFilter.labelResource(): Int =
    when (this) {
        PlanTimeFilter.ALL -> R.string.planner_time_all
        PlanTimeFilter.WITH_TIME -> R.string.planner_time_with
        PlanTimeFilter.WITHOUT_TIME -> R.string.planner_time_without
    }

private fun PlanSortMode.labelResource(): Int =
    when (this) {
        PlanSortMode.TIME -> R.string.planner_sort_time
        PlanSortMode.DATE -> R.string.planner_sort_date
        PlanSortMode.CREATED -> R.string.planner_sort_created
        PlanSortMode.NAME -> R.string.planner_sort_name
    }

private fun PlanItem.matchesQuery(query: String): Boolean =
    query.isEmpty() || title.contains(query, ignoreCase = true) || note.contains(query, ignoreCase = true)

private fun PlanItem.matchesDateFilter(
    filter: PlanDateFilter,
    selectedEpochDay: Long,
    todayEpochDay: Long,
): Boolean {
    val schedule = if (archived) copy(archived = false) else this
    return when (filter) {
        PlanDateFilter.SELECTED -> schedule.occursOn(selectedEpochDay)
        PlanDateFilter.TODAY -> schedule.occursOn(todayEpochDay)
        PlanDateFilter.UPCOMING -> schedule.nextOccurrence(todayEpochDay) != null
        PlanDateFilter.ANY -> true
    }
}

private fun PlanItem.matchesStatusFilter(
    filter: PlanStatusFilter,
    dateFilter: PlanDateFilter,
    selectedEpochDay: Long,
    todayEpochDay: Long,
): Boolean {
    if (filter == PlanStatusFilter.ALL) return true
    val completedOnDisplayDay = isCompletedOn(displayEpochDay(dateFilter, selectedEpochDay, todayEpochDay))
    return if (filter == PlanStatusFilter.COMPLETED) completedOnDisplayDay else !completedOnDisplayDay
}

private fun PlanItem.matchesTimeFilter(filter: PlanTimeFilter): Boolean =
    when (filter) {
        PlanTimeFilter.ALL -> true
        PlanTimeFilter.WITH_TIME -> reminderMinutesOfDay != null
        PlanTimeFilter.WITHOUT_TIME -> reminderMinutesOfDay == null
    }

private fun PlanItem.displayEpochDay(
    filter: PlanDateFilter,
    selectedEpochDay: Long,
    todayEpochDay: Long,
): Long =
    when (filter) {
        PlanDateFilter.SELECTED -> selectedEpochDay
        PlanDateFilter.TODAY -> todayEpochDay
        PlanDateFilter.UPCOMING -> nextOccurrence(todayEpochDay) ?: dateEpochDay
        PlanDateFilter.ANY ->
            if (dateEpochDay >= todayEpochDay) {
                dateEpochDay
            } else {
                nextOccurrence(todayEpochDay) ?: dateEpochDay
            }
    }

private fun PlanItem.nextOccurrence(fromEpochDay: Long): Long? {
    val schedule = if (archived) copy(archived = false) else this
    val start = maxOf(fromEpochDay, dateEpochDay)
    val maximum =
        minOf(
            repeatUntilEpochDay ?: (start + PLAN_OCCURRENCE_LOOKAHEAD_DAYS),
            start + PLAN_OCCURRENCE_LOOKAHEAD_DAYS,
        )
    return (start..maximum).firstOrNull(schedule::occursOn)
}

private fun List<PlanItem>.sorted(
    mode: PlanSortMode,
    dateFilter: PlanDateFilter,
    selectedEpochDay: Long,
    todayEpochDay: Long,
): List<PlanItem> =
    when (mode) {
        PlanSortMode.TIME ->
            sortedWith(compareBy<PlanItem> { it.reminderMinutesOfDay ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
        PlanSortMode.DATE ->
            sortedWith(
                compareBy<PlanItem> { it.displayEpochDay(dateFilter, selectedEpochDay, todayEpochDay) }
                    .thenBy { it.reminderMinutesOfDay ?: Int.MAX_VALUE },
            )
        PlanSortMode.CREATED -> sortedByDescending(PlanItem::createdAtEpochMillis)
        PlanSortMode.NAME -> sortedBy { it.title.lowercase() }
    }

private enum class PlanScheduleMode(
    val labelResource: Int,
) {
    ONCE(R.string.planner_schedule_once),
    WEEKDAYS(R.string.planner_schedule_weekdays),
    INTERVAL(R.string.planner_schedule_interval),
    MONTH_DAYS(R.string.planner_schedule_month_days),
}

private fun initialPlanScheduleMode(plan: PlanItem?): PlanScheduleMode =
    when {
        plan == null -> PlanScheduleMode.ONCE
        plan.scheduledMonthDays.isNotEmpty() || plan.repeat == PlanRepeat.MONTHLY -> PlanScheduleMode.MONTH_DAYS
        plan.repeatEveryDays != null -> PlanScheduleMode.INTERVAL
        plan.scheduledWeekdays.isNotEmpty() || plan.repeat == PlanRepeat.DAILY || plan.repeat == PlanRepeat.WEEKLY ->
            PlanScheduleMode.WEEKDAYS
        else -> PlanScheduleMode.ONCE
    }

private fun initialPlanWeekdays(
    plan: PlanItem?,
    selectedEpochDay: Long,
): Set<Weekday> =
    when {
        plan == null -> setOf(Weekday.fromEpochDay(selectedEpochDay))
        plan.scheduledWeekdays.isNotEmpty() -> plan.scheduledWeekdays
        plan.repeat == PlanRepeat.DAILY -> Weekday.entries.toSet()
        plan.repeat == PlanRepeat.WEEKLY -> setOf(Weekday.fromEpochDay(plan.dateEpochDay))
        else -> setOf(Weekday.fromEpochDay(selectedEpochDay))
    }

private fun initialPlanMonthDays(plan: PlanItem?): Set<Int> =
    when {
        plan == null -> emptySet()
        plan.scheduledMonthDays.isNotEmpty() -> plan.scheduledMonthDays
        plan.repeat == PlanRepeat.MONTHLY -> setOf(LocalDate.ofEpochDay(plan.dateEpochDay).dayOfMonth)
        else -> emptySet()
    }

private fun initialPresetScheduleMode(preset: PlanPreset): PlanScheduleMode =
    when {
        preset.scheduledMonthDays.isNotEmpty() || preset.repeat == PlanRepeat.MONTHLY -> PlanScheduleMode.MONTH_DAYS
        preset.repeatEveryDays != null -> PlanScheduleMode.INTERVAL
        preset.scheduledWeekdays.isNotEmpty() ||
            preset.repeat == PlanRepeat.DAILY ||
            preset.repeat == PlanRepeat.WEEKLY -> PlanScheduleMode.WEEKDAYS
        else -> PlanScheduleMode.ONCE
    }

private fun initialPresetWeekdays(
    preset: PlanPreset,
    selectedEpochDay: Long,
): Set<Weekday> =
    when {
        preset.scheduledWeekdays.isNotEmpty() -> preset.scheduledWeekdays
        preset.repeat == PlanRepeat.DAILY -> Weekday.entries.toSet()
        preset.repeat == PlanRepeat.WEEKLY -> setOf(Weekday.fromEpochDay(selectedEpochDay))
        else -> setOf(Weekday.fromEpochDay(selectedEpochDay))
    }

private fun initialPresetMonthDays(
    preset: PlanPreset,
    selectedEpochDay: Long,
): Set<Int> =
    when {
        preset.scheduledMonthDays.isNotEmpty() -> preset.scheduledMonthDays
        preset.repeat == PlanRepeat.MONTHLY -> setOf(LocalDate.ofEpochDay(selectedEpochDay).dayOfMonth)
        else -> emptySet()
    }

private fun parsePlanMonthDays(value: String): Set<Int> {
    if (value.isBlank()) return emptySet()
    val parts = value.split(',').map(String::trim)
    if (parts.any(String::isEmpty)) return emptySet()
    val days = parts.mapNotNull(String::toIntOrNull)
    return if (days.size == parts.size && days.all { it in 1..31 }) days.toSet() else emptySet()
}

@Composable
private fun planScheduleLabel(plan: PlanItem): String? {
    val locale = currentLocale()
    return when {
        plan.scheduledMonthDays.isNotEmpty() ->
            stringResource(R.string.planner_on_month_days, plan.scheduledMonthDays.sorted().joinToString(", "))
        plan.repeatEveryDays == 1 -> stringResource(R.string.planner_every_day)
        plan.repeatEveryDays != null ->
            stringResource(R.string.planner_every_n_days, requireNotNull(plan.repeatEveryDays))
        plan.scheduledWeekdays.isNotEmpty() ->
            stringResource(
                R.string.planner_on_weekdays,
                plan.scheduledWeekdays
                    .sortedBy(Weekday::ordinal)
                    .joinToString(", ") { it.displayLabel(locale) },
            )
        plan.repeat != PlanRepeat.NONE ->
            stringResource(
                R.string.planner_repeat_value,
                stringResource(plan.repeat.labelResource()),
            )
        else -> null
    }
}

private fun Weekday.displayLabel(locale: Locale): String =
    java.time.DayOfWeek.entries[ordinal]
        .getDisplayName(TextStyle.SHORT, locale)
        .removeSuffix(".")

private fun Weekday.shortLabelResource(): Int =
    when (this) {
        Weekday.MONDAY -> R.string.planner_day_monday
        Weekday.TUESDAY -> R.string.planner_day_tuesday
        Weekday.WEDNESDAY -> R.string.planner_day_wednesday
        Weekday.THURSDAY -> R.string.planner_day_thursday
        Weekday.FRIDAY -> R.string.planner_day_friday
        Weekday.SATURDAY -> R.string.planner_day_saturday
        Weekday.SUNDAY -> R.string.planner_day_sunday
    }

private fun PlanRepeat.labelResource(): Int =
    when (this) {
        PlanRepeat.NONE -> R.string.planner_repeat_none
        PlanRepeat.DAILY -> R.string.planner_repeat_daily
        PlanRepeat.WEEKLY -> R.string.planner_repeat_weekly
        PlanRepeat.MONTHLY -> R.string.planner_repeat_monthly
    }

private fun formatReminderTime(
    minutesOfDay: Int,
    locale: Locale,
): String = String.format(locale, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

@Composable
private fun reminderOffsetLabel(minutes: Int): String =
    when {
        minutes == 0 -> stringResource(R.string.planner_offset_at_time)
        minutes % (24 * 60) == 0 -> stringResource(R.string.planner_offset_days, minutes / (24 * 60))
        minutes % 60 == 0 -> stringResource(R.string.planner_offset_hours, minutes / 60)
        else -> stringResource(R.string.planner_offset_minutes, minutes)
    }

private fun formatMeasurement(value: Double): String =
    if (value ==
        value.roundToInt().toDouble()
    ) {
        value.roundToInt().toString()
    } else {
        "%.2f".format(Locale.US, value).trimEnd('0')
    }
