package com.sowerrrt.dayloom.feature.lists

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sowerrrt.dayloom.core.ui.ModulePreviewScreen

@Composable
fun ListsScreen() {
    ModulePreviewScreen(
        title = stringResource(R.string.lists_title),
        description = stringResource(R.string.lists_description),
        emptyTitle = stringResource(R.string.lists_empty_title),
        emptyDescription = stringResource(R.string.lists_empty_description),
        actionLabel = stringResource(R.string.lists_action),
        icon = Icons.Rounded.Checklist,
    )
}
