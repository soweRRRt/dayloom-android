package com.sowerrrt.dayloom.feature.planner

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.sowerrrt.dayloom.core.designsystem.DayloomMotion
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanPreset
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState
import com.sowerrrt.dayloom.core.ui.loadSampledImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlannerScreen(viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    var showPlanDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<PlanItem?>(null) }
    var pendingDelete by remember { mutableStateOf<PlanItem?>(null) }
    var imageTarget by remember { mutableStateOf<PlanItem?>(null) }
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val target = imageTarget
            if (uri != null && target != null) viewModel.setPlanImage(target.id, uri)
            imageTarget = null
        }

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
                    onTogglePlan = viewModel::togglePlan,
                    onMovePlan = viewModel::movePlan,
                    onCreatePlan = {
                        editingPlan = null
                        showPlanDialog = true
                    },
                    onEditPlan = {
                        editingPlan = it
                        showPlanDialog = true
                    },
                    onDeletePlan = { pendingDelete = it },
                    onChoosePlanImage = { plan ->
                        imageTarget = plan
                        imagePicker.launch("image/*")
                    },
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
            isChangingImage = state.isChangingImage,
            hasImageError = state.hasImageError,
            onChooseImage = { plan ->
                imageTarget = plan
                imagePicker.launch("image/*")
            },
            onRemoveImage = viewModel::removePlanImage,
            presets = state.presets,
            onSavePreset = viewModel::savePreset,
            onRemovePreset = viewModel::removePreset,
            onSave = {
                title,
                reminderMinutesOfDay,
                repeat,
                reminderEnabled,
                scheduledWeekdays,
                repeatEveryDays,
                scheduledMonthDays,
                ->
                val plan = editingPlan
                if (
                    reminderMinutesOfDay != null &&
                    reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (plan == null) {
                    viewModel.createPlan(
                        title,
                        reminderMinutesOfDay,
                        repeat,
                        reminderEnabled,
                        scheduledWeekdays,
                        repeatEveryDays,
                        scheduledMonthDays,
                    )
                } else {
                    viewModel.updatePlan(
                        plan.id,
                        title,
                        reminderMinutesOfDay,
                        repeat,
                        reminderEnabled,
                        scheduledWeekdays,
                        repeatEveryDays,
                        scheduledMonthDays,
                    )
                }
                showPlanDialog = false
            },
        )
    }

    pendingDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.planner_delete_title)) },
            text = { Text(stringResource(R.string.planner_delete_description, plan.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlan(plan.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.planner_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.planner_cancel)) }
            },
        )
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
    onTogglePlan: (com.sowerrrt.dayloom.core.model.EntityId) -> Unit,
    onMovePlan: (com.sowerrrt.dayloom.core.model.EntityId, Long) -> Unit,
    onCreatePlan: () -> Unit,
    onEditPlan: (PlanItem) -> Unit,
    onDeletePlan: (PlanItem) -> Unit,
    onChoosePlanImage: (PlanItem) -> Unit,
    onUsePreset: (PlanPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    var calendarMode by rememberSaveable { mutableStateOf(CalendarMode.MONTH) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("planner_list"),
            contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
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
                    }
                    Crossfade(
                        targetState = calendarMode,
                        animationSpec = tween(DayloomMotion.STANDARD_MILLIS),
                        label = "calendarMode",
                    ) { mode ->
                        if (mode == CalendarMode.MONTH) {
                            MonthCalendar(
                                state = state,
                                locale = locale,
                                onSelectDate = onSelectDate,
                                onPreviousMonth = onPreviousMonth,
                                onNextMonth = onNextMonth,
                                onToday = onToday,
                            )
                        } else {
                            WeekCalendar(
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        ) {
                            state.presets.forEach { preset ->
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
            }
            item { SectionTitle(stringResource(R.string.planner_habits_section), MaterialTheme.colorScheme.primary) }
            if (state.selectedHabits.isEmpty()) {
                item { EmptyDayCard(stringResource(R.string.planner_no_habits)) }
            } else {
                items(state.selectedHabits, key = { "habit-${it.id.value}" }) { habit ->
                    CalendarHabitRow(
                        habit = habit,
                        epochDay = state.selectedEpochDay,
                        canComplete = state.selectedEpochDay <= state.todayEpochDay,
                        onToggle = { onToggleHabit(habit.id) },
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.planner_plans_section), MaterialTheme.colorScheme.secondary) }
            if (state.selectedPlans.isEmpty()) {
                item { EmptyDayCard(stringResource(R.string.planner_no_plans)) }
            } else {
                items(state.selectedPlans, key = { "plan-${it.id.value}" }) { plan ->
                    PlanRow(
                        plan = plan,
                        completed = plan.isCompletedOn(state.selectedEpochDay),
                        onToggle = { onTogglePlan(plan.id) },
                        onMoveTomorrow = { onMovePlan(plan.id, 1) },
                        onMoveNextWeek = { onMovePlan(plan.id, 7) },
                        onEdit = { onEditPlan(plan) },
                        onDelete = { onDeletePlan(plan) },
                        onChooseImage = { onChoosePlanImage(plan) },
                        imagePath = state.planImagePaths[plan.id],
                    )
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

private enum class CalendarMode { WEEK, MONTH }

@Composable
private fun WeekCalendar(
    state: PlannerUiState,
    locale: Locale,
    onSelectDate: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val selected = LocalDate.ofEpochDay(state.selectedEpochDay)
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val days = (0L..6L).map(weekStart::plusDays)
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
                    Text(
                        weekLabel(days.first(), days.last(), locale),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onToday) { Text(stringResource(R.string.planner_today)) }
                }
                IconButton(onClick = { onSelectDate(selected.plusWeeks(1).toEpochDay()) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.planner_next_week))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { date ->
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
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val cells = List(leadingEmptyDays) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val rows = cells.chunked(7)
    val habitCounts =
        remember(state.habits, month) {
            cells.filterNotNull().associate { date -> date.toEpochDay() to state.habitCount(date.toEpochDay()) }
        }
    val planCounts =
        remember(state.plans, month) {
            cells.filterNotNull().associate { date -> date.toEpochDay() to state.planCount(date.toEpochDay()) }
        }
    val completedHabitCounts =
        remember(state.habits, month) {
            cells.filterNotNull().associate { date ->
                date.toEpochDay() to state.completedHabitCount(date.toEpochDay())
            }
        }
    val completedPlanCounts =
        remember(state.plans, month) {
            cells.filterNotNull().associate { date -> date.toEpochDay() to state.completedPlanCount(date.toEpochDay()) }
        }
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
                Text(monthLabel(month, locale), style = MaterialTheme.typography.titleLarge)
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
            Crossfade(
                targetState = rows,
                animationSpec = tween(DayloomMotion.STANDARD_MILLIS),
                label = "calendarMonth",
            ) { visibleRows ->
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
                                        habitCount = habitCounts[date.toEpochDay()] ?: 0,
                                        planCount = planCounts[date.toEpochDay()] ?: 0,
                                        completedHabitCount = completedHabitCounts[date.toEpochDay()] ?: 0,
                                        completedPlanCount = completedPlanCounts[date.toEpochDay()] ?: 0,
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
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val borderModifier =
        if (today && !selected) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .then(borderModifier)
                .clip(shape)
                .background(background)
                .clickable(onClick = onClick)
                .testTag("calendar_day_${date.toEpochDay()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (habitCount > 0) {
                CalendarDot(MaterialTheme.colorScheme.primary, completedHabitCount == habitCount)
            }
            if (planCount > 0) {
                CalendarDot(MaterialTheme.colorScheme.secondary, completedPlanCount == planCount)
            }
        }
    }
}

@Composable
private fun CalendarDot(
    color: Color,
    completed: Boolean,
) {
    Box(
        Modifier
            .size(
                if (completed) 6.dp else 5.dp,
            ).clip(CircleShape)
            .background(color.copy(alpha = if (completed) 1f else 0.48f)),
    )
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
private fun PlanRow(
    plan: PlanItem,
    completed: Boolean,
    imagePath: String?,
    onToggle: () -> Unit,
    onMoveTomorrow: () -> Unit,
    onMoveNextWeek: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onChooseImage: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    DayloomCard(Modifier.fillMaxWidth().testTag("plan_${plan.title}")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            if (imagePath != null) {
                LocalPlanImage(
                    imagePath = imagePath,
                    title = plan.title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clickable(onClick = onChooseImage)
                            .testTag("plan_image_preview_${plan.title}"),
                )
            }
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
                }
                IconButton(
                    onClick = onChooseImage,
                    modifier = Modifier.testTag("plan_image_action_${plan.title}"),
                ) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription =
                            stringResource(
                                if (plan.image == null) {
                                    R.string.planner_add_image
                                } else {
                                    R.string.planner_change_image
                                },
                            ),
                    )
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
                            text = {
                                Text(
                                    stringResource(
                                        if (plan.image == null) {
                                            R.string.planner_add_image
                                        } else {
                                            R.string.planner_change_image
                                        },
                                    ),
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onChooseImage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.planner_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPlanImage(
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
            contentDescription = stringResource(R.string.planner_image_description, title),
            modifier = modifier.clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanEditorDialog(
    plan: PlanItem?,
    selectedEpochDay: Long,
    onDismiss: () -> Unit,
    isChangingImage: Boolean,
    hasImageError: Boolean,
    onChooseImage: (PlanItem) -> Unit,
    onRemoveImage: (com.sowerrrt.dayloom.core.model.EntityId) -> Unit,
    presets: List<PlanPreset>,
    onSavePreset: (String, Int?, PlanRepeat, Boolean, Set<Weekday>, Int?, Set<Int>) -> Unit,
    onRemovePreset: (PlanPreset) -> Unit,
    onSave: (String, Int?, PlanRepeat, Boolean, Set<Weekday>, Int?, Set<Int>) -> Unit,
) {
    var title by remember(plan?.id) { mutableStateOf(plan?.title.orEmpty()) }
    var reminderMinutesOfDay by remember(plan?.id) { mutableStateOf(plan?.reminderMinutesOfDay) }
    var reminderEnabled by remember(plan?.id) { mutableStateOf(plan?.reminderEnabled ?: false) }
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_PLAN_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.planner_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_PLAN_TITLE_LENGTH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_name_input"),
                )
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
                            supportingText = { Text(stringResource(R.string.planner_interval_description)) },
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
                            supportingText = { Text(stringResource(R.string.planner_month_days_description)) },
                            isError = scheduledMonthDaysText.isNotBlank() && monthDays.isEmpty(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("plan_month_days_input"),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        onSavePreset(
                            title,
                            reminderMinutesOfDay,
                            PlanRepeat.NONE,
                            reminderEnabled,
                            savedWeekdays,
                            savedIntervalDays,
                            savedMonthDays,
                        )
                    },
                    enabled = title.isNotBlank() && scheduleIsValid,
                    modifier = Modifier.testTag("save_plan_preset"),
                ) {
                    Text(stringResource(R.string.planner_save_preset))
                }
                if (plan != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onChooseImage(plan) },
                            enabled = !isChangingImage,
                            modifier = Modifier.weight(1f).testTag("edit_plan_image"),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(DayloomSpacing.xs))
                            Text(
                                stringResource(
                                    if (plan.image ==
                                        null
                                    ) {
                                        R.string.planner_add_image
                                    } else {
                                        R.string.planner_change_image
                                    },
                                ),
                            )
                        }
                        if (plan.image != null) {
                            IconButton(
                                onClick = { onRemoveImage(plan.id) },
                                enabled = !isChangingImage,
                                modifier = Modifier.testTag("remove_plan_image"),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.planner_remove_image),
                                )
                            }
                        }
                    }
                    if (hasImageError) {
                        Text(
                            stringResource(R.string.planner_image_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("plan_image_error"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        reminderMinutesOfDay,
                        PlanRepeat.NONE,
                        reminderEnabled,
                        savedWeekdays,
                        savedIntervalDays,
                        savedMonthDays,
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
private const val DEFAULT_REMINDER_MINUTES = 9 * 60
private const val MAX_REPEAT_INTERVAL_DAYS = 3650

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
