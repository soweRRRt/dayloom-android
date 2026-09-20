package com.sowerrrt.dayloom.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomMotion
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.model.isCompletedOn

@Composable
fun HomeScreen(
    sections: List<HomeSection> = HomeSection.entries,
    onOpenHabits: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenWishlist: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    HomeContent(
        state = state,
        sections = sections,
        onOpenHabits = onOpenHabits,
        onOpenPlanner = onOpenPlanner,
        onOpenLists = onOpenLists,
        onOpenWishlist = onOpenWishlist,
        onToggleHabit = viewModel::toggleHabit,
        onTogglePlan = viewModel::togglePlan,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    sections: List<HomeSection>,
    onOpenHabits: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenWishlist: () -> Unit,
    onToggleHabit: (EntityId) -> Unit,
    onTogglePlan: (EntityId) -> Unit,
) {
    val cards =
        mapOf(
            HomeSection.HABITS to
                HomeCardData(
                    title = stringResource(R.string.home_habits_title),
                    metric =
                        if (state.habitsToday == 0) {
                            stringResource(R.string.home_habits_metric)
                        } else {
                            stringResource(
                                R.string.home_habits_metric_value,
                                state.habitsCompletedToday,
                                state.habitsToday,
                            )
                        },
                    icon = Icons.Rounded.AutoAwesome,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onOpenHabits,
                ),
            HomeSection.PLANNER to
                HomeCardData(
                    title = stringResource(R.string.home_plan_title),
                    metric =
                        if (state.plansToday == 0) {
                            stringResource(R.string.home_plan_metric)
                        } else {
                            stringResource(
                                R.string.home_plan_metric_value,
                                state.plansCompletedToday,
                                state.plansToday,
                            )
                        },
                    icon = Icons.Rounded.CalendarMonth,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenPlanner,
                ),
            HomeSection.LISTS to
                HomeCardData(
                    title = stringResource(R.string.home_lists_title),
                    metric =
                        if (state.listCount == 0) {
                            stringResource(R.string.home_lists_metric)
                        } else {
                            stringResource(R.string.home_lists_metric_value, state.listCount, state.openListItems)
                        },
                    icon = Icons.Rounded.Checklist,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenLists,
                ),
            HomeSection.WISHLIST to
                HomeCardData(
                    title = stringResource(R.string.home_wishes_title),
                    metric =
                        if (state.wishCount == 0) {
                            stringResource(R.string.home_wishes_metric)
                        } else {
                            stringResource(R.string.home_wishes_metric_value, state.completedWishCount, state.wishCount)
                        },
                    icon = Icons.Rounded.Savings,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onOpenWishlist,
                ),
        )
    val visibleCards = sections.distinct().mapNotNull { section -> cards[section]?.let { section to it } }
    val todayItems =
        buildList {
            if (HomeSection.HABITS in sections) {
                state.todayHabits.forEach { habit ->
                    add(
                        TodayRowData(
                            key = "habit-${habit.id.value}",
                            testTag = "habit_${habit.title}",
                            title = habit.title,
                            typeLabel = stringResource(R.string.home_today_habit),
                            reminderMinutes = habit.reminderMinutesOfDay,
                            detail =
                                if (habit.targetAmount.isBlank() && habit.targetUnit.isBlank()) {
                                    ""
                                } else {
                                    listOf(
                                        "${habit.progressByEpochDay[state.todayEpochDay].orEmpty().ifBlank {
                                            "—"
                                        }} / ${habit.targetAmount}",
                                        habit.targetUnit,
                                    ).filter(String::isNotBlank).joinToString(" ")
                                },
                            completed = state.todayEpochDay in habit.completedEpochDays,
                            color = MaterialTheme.colorScheme.primary,
                            onToggle = { onToggleHabit(habit.id) },
                        ),
                    )
                }
            }
            if (HomeSection.PLANNER in sections) {
                state.todayPlans.forEach { plan ->
                    add(
                        TodayRowData(
                            key = "plan-${plan.id.value}",
                            testTag = "plan_${plan.title}",
                            title = plan.title,
                            typeLabel = stringResource(R.string.home_today_plan),
                            reminderMinutes = plan.reminderMinutesOfDay,
                            detail = "",
                            completed = plan.isCompletedOn(state.todayEpochDay),
                            color = MaterialTheme.colorScheme.secondary,
                            onToggle = { onTogglePlan(plan.id) },
                        ),
                    )
                }
            }
        }.sortedWith(compareBy<TodayRowData> { it.reminderMinutes ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })

    Column {
        DayloomTopBar(title = stringResource(R.string.home_app_name), showLogo = true)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("home_list"),
            contentPadding =
                PaddingValues(
                    start = DayloomSpacing.md,
                    end = DayloomSpacing.md,
                    bottom = DayloomSpacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
        ) {
            item { WelcomeCard(state) }
            item {
                SectionHeader(
                    title = stringResource(R.string.home_overview),
                    action = stringResource(R.string.home_open_calendar),
                    onAction = onOpenPlanner,
                )
            }
            if (state.hasError) {
                item {
                    DayloomCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.home_update_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (!state.isLoading && todayItems.isEmpty()) {
                item { TodayEmptyCard(onOpenHabits, onOpenPlanner) }
            } else {
                item(key = "today_timeline") {
                    Box(Modifier.animateItem()) { TodayTimeline(todayItems) }
                }
            }
            item {
                Text(
                    stringResource(R.string.home_sections),
                    modifier = Modifier.padding(top = DayloomSpacing.sm),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(
                items = visibleCards.chunked(2),
                key = { row -> row.joinToString(separator = "-") { it.first.name } },
            ) { cardRow ->
                Box(Modifier.animateItem()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
                    ) {
                        cardRow.forEach { (section, data) ->
                            ModuleSummaryCard(section, data, Modifier.weight(1f))
                        }
                        if (cardRow.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = DayloomSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = DayloomSpacing.sm),
        ) {
            Text(action, maxLines = 1)
        }
    }
}

@Composable
private fun TodayEmptyCard(
    onOpenHabits: () -> Unit,
    onOpenPlanner: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("home_today_empty")) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Text(stringResource(R.string.home_today_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                TextButton(onClick = onOpenHabits) { Text(stringResource(R.string.home_add_habit)) }
                TextButton(onClick = onOpenPlanner) { Text(stringResource(R.string.home_add_plan)) }
            }
        }
    }
}

@Composable
private fun TodayTimeline(items: List<TodayRowData>) {
    DayloomCard(
        modifier = Modifier.fillMaxWidth().testTag("home_today_timeline"),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                TodayRow(item)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = DayloomSpacing.md),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayRow(item: TodayRowData) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("home_today_${item.testTag}")
                .padding(horizontal = DayloomSpacing.regular, vertical = DayloomSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(item.color.copy(alpha = 0.72f)),
        )
        IconButton(
            onClick = item.onToggle,
            modifier =
                Modifier.testTag(
                    "home_toggle_${item.testTag}_${if (item.completed) "completed" else "open"}",
                ),
        ) {
            Icon(
                if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(R.string.home_toggle_item, item.title),
                tint = if (item.completed) item.color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
            ) {
                Text(item.typeLabel, style = MaterialTheme.typography.labelMedium, color = item.color)
                if (item.detail.isNotBlank()) {
                    Text("· ${item.detail}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item.reminderMinutes?.let { minutes ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    String.format(locale, "%02d:%02d", minutes / 60, minutes % 60),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WelcomeCard(state: HomeUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val gradientTransition = rememberInfiniteTransition(label = "welcomeGradient")
    val gradientShift by
        gradientTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(8_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "welcomeGradientShift",
        )
    val total = state.habitsToday + state.plansToday
    val completed = state.habitsCompletedToday + state.plansCompletedToday
    val progressTarget = if (total == 0) 0f else completed.toFloat() / total
    val progress by
        animateFloatAsState(
            targetValue = progressTarget,
            animationSpec = tween(DayloomMotion.EMPHASIZED_MILLIS),
            label = "dailyProgress",
        )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .drawBehind {
                        val travel = size.width * 0.34f
                        drawRect(
                            brush =
                                Brush.linearGradient(
                                    colors = listOf(primary, secondary, tertiary),
                                    start = Offset(-travel + travel * gradientShift, 0f),
                                    end = Offset(size.width + travel * gradientShift, size.height),
                                ),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.055f),
                            radius = size.minDimension * 0.74f,
                            center = Offset(size.width * (0.18f + 0.52f * gradientShift), 0f),
                        )
                    }.padding(DayloomSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
        ) {
            Text(
                stringResource(R.string.home_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                stringResource(R.string.home_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    HeroStat(
                        label = stringResource(R.string.home_habits_title),
                        value = "${state.habitsCompletedToday}/${state.habitsToday}",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStat(
                        label = stringResource(R.string.home_plan_title),
                        value = "${state.plansCompletedToday}/${state.plansToday}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(
                    MaterialTheme.shapes.medium,
                ).background(Color.White.copy(alpha = 0.14f))
                .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun ModuleSummaryCard(
    section: HomeSection,
    data: HomeCardData,
    modifier: Modifier = Modifier,
) {
    DayloomCard(
        modifier =
            modifier
                .height(128.dp)
                .testTag("home_card_${section.name.lowercase()}"),
        onClick = data.onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(data.color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(data.icon, contentDescription = null, tint = data.color, modifier = Modifier.size(22.dp))
                }
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(DayloomSpacing.sm))
            Text(data.title, style = MaterialTheme.typography.titleMedium)
            Text(
                data.metric,
                style = MaterialTheme.typography.bodySmall,
                color = data.color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Immutable
private data class HomeCardData(
    val title: String,
    val metric: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
)

@Immutable
private data class TodayRowData(
    val key: String,
    val testTag: String,
    val title: String,
    val typeLabel: String,
    val reminderMinutes: Int?,
    val detail: String,
    val completed: Boolean,
    val color: Color,
    val onToggle: () -> Unit,
)

@Preview(name = "Light · English", locale = "en", showBackground = true)
@Preview(name = "Dark · Русский", locale = "ru", showBackground = true, uiMode = 0x20)
@Composable
private fun HomeScreenPreview() {
    DayloomTheme(
        themeMode = ThemeMode.SYSTEM,
        accentPalette = AccentPalette.VIOLET,
    ) {
        HomeContent(HomeUiState(isLoading = false), HomeSection.entries, {}, {}, {}, {}, {}, {})
    }
}
