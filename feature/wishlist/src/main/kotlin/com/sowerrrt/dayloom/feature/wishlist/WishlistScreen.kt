package com.sowerrrt.dayloom.feature.wishlist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sowerrrt.dayloom.core.ui.ModulePreviewScreen

@Composable
fun WishlistScreen() {
    ModulePreviewScreen(
        title = stringResource(R.string.wishlist_title),
        description = stringResource(R.string.wishlist_description),
        emptyTitle = stringResource(R.string.wishlist_empty_title),
        emptyDescription = stringResource(R.string.wishlist_empty_description),
        actionLabel = stringResource(R.string.wishlist_action),
        icon = Icons.Rounded.Savings,
    )
}
