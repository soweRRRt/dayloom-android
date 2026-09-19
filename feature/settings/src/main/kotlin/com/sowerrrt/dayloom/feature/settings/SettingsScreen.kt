package com.sowerrrt.dayloom.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode

@Composable
fun SettingsScreen(
    onCheckUpdates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column {
        DayloomTopBar(stringResource(R.string.settings_title))
        LazyColumn(
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
                    StartDestination.entries.forEach { destination ->
                        FilterChip(
                            selected = state.settings.startDestination == destination,
                            onClick = { viewModel.setStartDestination(destination) },
                            label = { Text(startDestinationLabel(destination)) },
                        )
                    }
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

@Composable
private fun AccentChoice(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = accentLabel(palette)
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
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Text(stringResource(R.string.settings_selected), color = MaterialTheme.colorScheme.primary)
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
