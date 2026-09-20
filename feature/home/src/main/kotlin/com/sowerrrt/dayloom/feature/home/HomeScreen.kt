package com.sowerrrt.dayloom.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomMotion
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.ThemeMode

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

    Column {
        DayloomTopBar(title = stringResource(R.string.home_app_name), showLogo = true)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = DayloomSpacing.md,
                    end = DayloomSpacing.md,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            item { WelcomeCard(state) }
            item {
                Text(
                    stringResource(R.string.home_overview),
                    modifier = Modifier.padding(top = DayloomSpacing.xs),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(visibleCards.chunked(2)) { cardRow ->
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

@Composable
private fun WelcomeCard(state: HomeUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
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
                    .background(Brush.linearGradient(listOf(primary, secondary)))
                    .padding(DayloomSpacing.lg),
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
    Card(
        modifier =
            modifier
                .height(148.dp)
                .testTag("home_card_${section.name.lowercase()}")
                .clickable(onClick = data.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(DayloomSpacing.md),
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

@Preview(name = "Light · English", locale = "en", showBackground = true)
@Preview(name = "Dark · Русский", locale = "ru", showBackground = true, uiMode = 0x20)
@Composable
private fun HomeScreenPreview() {
    DayloomTheme(
        themeMode = ThemeMode.SYSTEM,
        accentPalette = AccentPalette.VIOLET,
    ) {
        HomeContent(HomeUiState(), HomeSection.entries, {}, {}, {}, {})
    }
}
