package com.sowerrrt.dayloom.feature.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sowerrrt.dayloom.core.ui.ModulePreviewScreen

@Composable
fun HabitsScreen() {
    ModulePreviewScreen(
        title = stringResource(R.string.habits_title),
        description = stringResource(R.string.habits_description),
        emptyTitle = stringResource(R.string.habits_empty_title),
        emptyDescription = stringResource(R.string.habits_empty_description),
        actionLabel = stringResource(R.string.habits_action),
        icon = Icons.Rounded.AutoAwesome,
    )
}
