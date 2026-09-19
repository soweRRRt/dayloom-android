package com.sowerrrt.dayloom.feature.planner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sowerrrt.dayloom.core.ui.ModulePreviewScreen

@Composable
fun PlannerScreen() {
    ModulePreviewScreen(
        title = stringResource(R.string.planner_title),
        description = stringResource(R.string.planner_description),
        emptyTitle = stringResource(R.string.planner_empty_title),
        emptyDescription = stringResource(R.string.planner_empty_description),
        actionLabel = stringResource(R.string.planner_action),
        icon = Icons.Rounded.CalendarMonth,
    )
}
