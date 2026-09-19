package com.sowerrrt.dayloom.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ThemeMode

@Composable
fun HomeScreen(
    onOpenHabits: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenWishlist: () -> Unit,
) {
    val cards =
        listOf(
            HomeCardData(
                stringResource(R.string.home_habits_title),
                stringResource(R.string.home_habits_body),
                stringResource(R.string.home_habits_metric),
                Icons.Rounded.AutoAwesome,
                MaterialTheme.colorScheme.primary,
                onOpenHabits,
            ),
            HomeCardData(
                stringResource(R.string.home_plan_title),
                stringResource(R.string.home_plan_body),
                stringResource(R.string.home_plan_metric),
                Icons.Rounded.CalendarMonth,
                MaterialTheme.colorScheme.secondary,
                onOpenPlanner,
            ),
            HomeCardData(
                stringResource(R.string.home_lists_title),
                stringResource(R.string.home_lists_body),
                stringResource(R.string.home_lists_metric),
                Icons.Rounded.Checklist,
                MaterialTheme.colorScheme.tertiary,
                onOpenLists,
            ),
            HomeCardData(
                stringResource(R.string.home_wishes_title),
                stringResource(R.string.home_wishes_body),
                stringResource(R.string.home_wishes_metric),
                Icons.Rounded.Savings,
                MaterialTheme.colorScheme.primary,
                onOpenWishlist,
            ),
        )
    Column {
        DayloomTopBar(stringResource(R.string.home_app_name))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = DayloomSpacing.md,
                    end = DayloomSpacing.md,
                    bottom = 104.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            item { WelcomeCard() }
            item {
                Text(
                    stringResource(R.string.home_overview),
                    modifier = Modifier.padding(top = DayloomSpacing.sm),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(cards) { card -> ModuleSummaryCard(card) }
        }
    }
}

@Composable
private fun WelcomeCard() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .background(Brush.linearGradient(listOf(primary, secondary)))
                    .padding(DayloomSpacing.lg),
        ) {
            Text(
                stringResource(R.string.home_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(DayloomSpacing.sm))
            Text(
                stringResource(R.string.home_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun ModuleSummaryCard(data: HomeCardData) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = data.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DayloomSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(data.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(data.icon, contentDescription = null, tint = data.color)
            }
            Column(Modifier.weight(1f)) {
                Text(data.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    data.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(data.metric, style = MaterialTheme.typography.labelLarge, color = data.color)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Immutable
private data class HomeCardData(
    val title: String,
    val body: String,
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
        HomeScreen({}, {}, {}, {})
    }
}
