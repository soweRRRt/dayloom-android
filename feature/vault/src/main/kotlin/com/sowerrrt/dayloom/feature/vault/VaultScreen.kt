package com.sowerrrt.dayloom.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.security.LockedContent

@Composable
fun VaultScreen(onUnlockRequest: () -> Unit = {}) {
    Column {
        DayloomTopBar(stringResource(R.string.vault_title))
        LockedContent(
            title = stringResource(R.string.vault_locked_title),
            description = stringResource(R.string.vault_locked_description),
            actionLabel = stringResource(R.string.vault_unlock_action),
            onUnlock = onUnlockRequest,
            modifier = Modifier.weight(1f),
        )
    }
}
