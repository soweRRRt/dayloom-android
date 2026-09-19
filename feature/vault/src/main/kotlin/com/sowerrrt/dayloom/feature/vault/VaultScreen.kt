package com.sowerrrt.dayloom.feature.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.VaultEntry
import com.sowerrrt.dayloom.core.security.LockedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val snackbar = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.vault_copied)
    val scope = rememberCoroutineScope()
    var editorEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var showDeleteVault by remember { mutableStateOf(false) }
    val unlockSuccess by rememberUpdatedState(viewModel::unlockAfterAuthentication)
    val authFailed by rememberUpdatedState(viewModel::authenticationFailed)
    val authCancelled by rememberUpdatedState(viewModel::authenticationCancelled)
    val prompt =
        remember(activity) {
            activity?.let {
                BiometricPrompt(
                    it,
                    ContextCompat.getMainExecutor(it),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                            unlockSuccess()

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (
                                errorCode == BiometricPrompt.ERROR_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                authCancelled()
                            } else {
                                authFailed()
                            }
                        }
                    },
                )
            }
        }
    val authTitle = stringResource(R.string.vault_auth_title)
    val authSubtitle = stringResource(R.string.vault_auth_subtitle)
    val authCancel = stringResource(R.string.vault_auth_cancel)
    val vaultExamples =
        listOf(
            VaultExample(
                title = stringResource(R.string.vault_example_email_title),
                username = stringResource(R.string.vault_example_email_username),
                password = "Demo-Mail-2026!",
                website = "https://mail.example.com",
                note = stringResource(R.string.vault_example_email_note),
                category = stringResource(R.string.vault_example_category_personal),
                favorite = true,
            ),
            VaultExample(
                title = stringResource(R.string.vault_example_work_title),
                username = stringResource(R.string.vault_example_work_username),
                password = "Demo-Work-42#Safe",
                website = "https://work.example.com",
                note = stringResource(R.string.vault_example_work_note),
                category = stringResource(R.string.vault_example_category_work),
            ),
            VaultExample(
                title = stringResource(R.string.vault_example_streaming_title),
                username = stringResource(R.string.vault_example_streaming_username),
                password = "Demo-Stream-7\$Plus",
                website = "https://video.example.com",
                note = stringResource(R.string.vault_example_streaming_note),
                category = stringResource(R.string.vault_example_category_entertainment),
            ),
        )

    LaunchedEffect(state.authenticationRequest) {
        if (state.authenticationRequest == 0L) return@LaunchedEffect
        val authenticators = supportedAuthenticators()
        if (
            activity == null ||
            prompt == null ||
            BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            viewModel.authenticationUnavailable()
            return@LaunchedEffect
        }
        val builder =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(authTitle)
                .setSubtitle(authSubtitle)
                .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) builder.setNegativeButtonText(authCancel)
        prompt.authenticate(builder.build())
    }

    LaunchedEffect(state.isLocked) {
        if (state.isLocked) {
            showEditor = false
            deleteEntry = null
            showDeleteVault = false
        }
    }

    DisposableEffect(Unit) { onDispose(viewModel::lock) }

    Scaffold(
        modifier = Modifier.testTag("vault_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.isLocked && state.selectedEntry == null) {
                FloatingActionButton(
                    onClick = {
                        editorEntry = null
                        showEditor = true
                        viewModel.touch()
                    },
                    modifier = Modifier.testTag("create_vault_entry"),
                ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.vault_add)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DayloomTopBar(
                title = stringResource(R.string.vault_title),
                navigationIcon = {
                    IconButton(
                        onClick = { if (state.selectedEntry != null) viewModel.closeEntry() else onBack() },
                        modifier = Modifier.testTag("vault_back"),
                    ) { Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.vault_back)) }
                },
                actions = {
                    if (!state.isLocked) {
                        IconButton(onClick = viewModel::lock, modifier = Modifier.testTag("lock_vault")) {
                            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.vault_lock))
                        }
                    }
                },
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isBusy -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.isLocked ->
                        LockedVaultContent(
                            hasVault = state.hasVault,
                            onUnlock = viewModel::prepareAuthentication,
                            onOpenSecuritySettings = {
                                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            },
                        )
                    state.selectedEntry != null ->
                        VaultEntryDetails(
                            entry = state.selectedEntry!!,
                            onFavorite = { viewModel.toggleFavorite(state.selectedEntry!!.id) },
                            onEdit = {
                                editorEntry = state.selectedEntry
                                showEditor = true
                                viewModel.touch()
                            },
                            onDelete = { deleteEntry = state.selectedEntry },
                            onCopy = { label, value ->
                                copySensitive(context, label, value)
                                scope.launch {
                                    snackbar.showSnackbar(copiedMessage)
                                    delay(CLIPBOARD_CLEAR_MILLIS)
                                    clearClipboardIfUnchanged(context, value)
                                }
                                viewModel.touch()
                            },
                        )
                    else ->
                        VaultOverview(
                            state = state,
                            onQueryChange = viewModel::setQuery,
                            onFavoriteFilter = viewModel::toggleFavoriteFilter,
                            onOpenEntry = viewModel::openEntry,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddExamples = { viewModel.addExamples(vaultExamples) },
                            onDeleteVault = { showDeleteVault = true },
                        )
                }
            }
        }
    }

    if (showEditor) {
        VaultEditorDialog(
            entry = editorEntry,
            onDismiss = { showEditor = false },
            onInteraction = viewModel::touch,
            onSave = { title, username, password, website, note, category ->
                viewModel.saveEntry(editorEntry?.id, title, username, password, website, note, category)
                showEditor = false
            },
        )
    }
    deleteEntry?.let { entry ->
        ConfirmationDialog(
            title = stringResource(R.string.vault_delete_entry_title),
            description = stringResource(R.string.vault_delete_entry_description),
            confirm = stringResource(R.string.vault_delete),
            onDismiss = { deleteEntry = null },
            onConfirm = {
                viewModel.deleteEntry(entry.id)
                deleteEntry = null
            },
        )
    }
    if (showDeleteVault) {
        ConfirmationDialog(
            title = stringResource(R.string.vault_delete_all_title),
            description = stringResource(R.string.vault_delete_all_description),
            confirm = stringResource(R.string.vault_delete_all),
            onDismiss = { showDeleteVault = false },
            onConfirm = {
                viewModel.deleteVault()
                showDeleteVault = false
            },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.vault_error_title)) },
            text = { Text(stringResource(error.messageResource())) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.vault_understood)) }
            },
        )
    }
}

@Composable
private fun LockedVaultContent(
    hasVault: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LockedContent(
            title = stringResource(R.string.vault_locked_title),
            description =
                stringResource(
                    if (hasVault) R.string.vault_locked_description else R.string.vault_setup_description,
                ),
            actionLabel = stringResource(R.string.vault_unlock_action),
            onUnlock = onUnlock,
            modifier = Modifier.weight(1f).testTag("vault_locked"),
        )
        TextButton(
            onClick = onOpenSecuritySettings,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = DayloomSpacing.md),
        ) { Text(stringResource(R.string.vault_device_security_action)) }
    }
}

@Composable
private fun VaultOverview(
    state: VaultUiState,
    onQueryChange: (String) -> Unit,
    onFavoriteFilter: () -> Unit,
    onOpenEntry: (EntityId) -> Unit,
    onToggleFavorite: (EntityId) -> Unit,
    onAddExamples: () -> Unit,
    onDeleteVault: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DayloomSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("vault_search"),
            label = { Text(stringResource(R.string.vault_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
        )
        FilledTonalButton(onClick = onFavoriteFilter, modifier = Modifier.testTag("vault_favorites_filter")) {
            Icon(
                if (state.favoriteOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
            )
            Spacer(Modifier.size(DayloomSpacing.xs))
            Text(stringResource(R.string.vault_favorites))
        }
        when {
            state.entries.isEmpty() ->
                VaultEmptyState(
                    stringResource(R.string.vault_empty_title),
                    stringResource(R.string.vault_empty_description),
                    actionLabel = stringResource(R.string.vault_add_examples),
                    onAction = onAddExamples,
                )
            state.filteredEntries.isEmpty() ->
                VaultEmptyState(
                    stringResource(R.string.vault_no_results_title),
                    stringResource(R.string.vault_no_results_description),
                )
            else ->
                state.filteredEntries.forEach { entry ->
                    VaultEntryCard(entry, { onOpenEntry(entry.id) }, { onToggleFavorite(entry.id) })
                }
        }
        Text(
            stringResource(R.string.vault_auto_lock_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onDeleteVault, modifier = Modifier.testTag("delete_vault")) {
            Icon(Icons.Rounded.Delete, contentDescription = null)
            Spacer(Modifier.size(DayloomSpacing.xs))
            Text(stringResource(R.string.vault_delete_all))
        }
        Spacer(Modifier.size(88.dp))
    }
}

@Composable
private fun VaultEmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    DayloomCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.testTag("add_vault_examples")) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("vault_entry_${entry.title}").clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                if (entry.username.isNotBlank()) {
                    Text(
                        stringResource(R.string.vault_entry_username, entry.username),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.category.isNotBlank()) {
                    Text(
                        entry.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.testTag("favorite_${entry.id}")) {
                Icon(
                    if (entry.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription =
                        stringResource(
                            if (entry.favorite) R.string.vault_favorite_remove else R.string.vault_favorite_add,
                        ),
                )
            }
        }
    }
}

@Composable
private fun VaultEntryDetails(
    entry: VaultEntry,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    var passwordVisible by remember(entry.id) { mutableStateOf(false) }
    val loginClipboardLabel = stringResource(R.string.vault_clipboard_login_label)
    val passwordClipboardLabel = stringResource(R.string.vault_clipboard_password_label)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(DayloomSpacing.md)
                .testTag("vault_details"),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onFavorite) {
                Icon(
                    if (entry.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                )
            }
        }
        if (entry.username.isNotBlank()) {
            DetailCard(stringResource(R.string.vault_username_label), entry.username) {
                IconButton(onClick = { onCopy(loginClipboardLabel, entry.username) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.vault_copy_username))
                }
            }
        }
        DetailCard(
            stringResource(R.string.vault_password),
            if (passwordVisible) entry.password else "•".repeat(entry.password.length.coerceAtMost(24)),
        ) {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription =
                        stringResource(
                            if (passwordVisible) R.string.vault_hide_password else R.string.vault_show_password,
                        ),
                )
            }
            IconButton(onClick = { onCopy(passwordClipboardLabel, entry.password) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.vault_copy_password))
            }
        }
        if (entry.website.isNotBlank()) DetailCard(stringResource(R.string.vault_website_label), entry.website)
        if (entry.category.isNotBlank()) DetailCard(stringResource(R.string.vault_category_label), entry.category)
        if (entry.note.isNotBlank()) DetailCard(stringResource(R.string.vault_note_label), entry.note)
        Row(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f).testTag("edit_vault_entry")) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.size(DayloomSpacing.xs))
                Text(stringResource(R.string.vault_edit))
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).testTag("delete_vault_entry")) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.size(DayloomSpacing.xs))
                Text(stringResource(R.string.vault_delete))
            }
        }
    }
}

@Composable
private fun DetailCard(
    label: String,
    value: String,
    actions: @Composable () -> Unit = {},
) {
    DayloomCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            actions()
        }
    }
}

@Composable
private fun VaultEditorDialog(
    entry: VaultEntry?,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var title by remember(entry?.id) { mutableStateOf(entry?.title.orEmpty()) }
    var username by remember(entry?.id) { mutableStateOf(entry?.username.orEmpty()) }
    var password by remember(entry?.id) { mutableStateOf(entry?.password.orEmpty()) }
    var website by remember(entry?.id) { mutableStateOf(entry?.website.orEmpty()) }
    var category by remember(entry?.id) { mutableStateOf(entry?.category.orEmpty()) }
    var note by remember(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordLength by remember { mutableIntStateOf(20) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry == null) R.string.vault_create_title else R.string.vault_edit_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                OutlinedTextField(
                    title,
                    {
                        onInteraction()
                        if (it.length <= 200) title = it
                    },
                    Modifier.fillMaxWidth().testTag("vault_title_input"),
                    label = { Text(stringResource(R.string.vault_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    username,
                    {
                        onInteraction()
                        if (it.length <= 500) username = it
                    },
                    Modifier.fillMaxWidth().testTag("vault_username_input"),
                    label = { Text(stringResource(R.string.vault_username_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        onInteraction()
                        if (it.length <= 4096) password = it
                    },
                    modifier = Modifier.fillMaxWidth().testTag("vault_password_input"),
                    label = { Text(stringResource(R.string.vault_password_label)) },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onInteraction()
                                passwordVisible = !passwordVisible
                            },
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                )
                Text(stringResource(R.string.vault_password_length, passwordLength))
                Slider(
                    value = passwordLength.toFloat(),
                    onValueChange = {
                        onInteraction()
                        passwordLength = it.toInt()
                    },
                    valueRange = 12f..64f,
                    steps = 51,
                    modifier = Modifier.testTag("vault_password_length"),
                )
                OutlinedButton(
                    onClick = {
                        onInteraction()
                        password = generatePassword(passwordLength)
                        passwordVisible = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("generate_password"),
                ) { Text(stringResource(R.string.vault_generate)) }
                OutlinedTextField(
                    website,
                    {
                        onInteraction()
                        if (it.length <= 2_000) website = it
                    },
                    Modifier.fillMaxWidth().testTag("vault_website_input"),
                    label = { Text(stringResource(R.string.vault_website_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
                OutlinedTextField(
                    category,
                    {
                        onInteraction()
                        if (it.length <= 200) category = it
                    },
                    Modifier.fillMaxWidth().testTag("vault_category_input"),
                    label = { Text(stringResource(R.string.vault_category_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    note,
                    {
                        onInteraction()
                        if (it.length <= 10_000) note = it
                    },
                    Modifier.fillMaxWidth().testTag("vault_note_input"),
                    label = { Text(stringResource(R.string.vault_note_label)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onInteraction()
                    onSave(title, username, password, website, note, category)
                },
                enabled = title.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.testTag("save_vault_entry"),
            ) { Text(stringResource(R.string.vault_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    description: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
    )
}

private fun supportedAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

private fun VaultError.messageResource(): Int =
    when (this) {
        VaultError.AUTHENTICATION_UNAVAILABLE -> R.string.vault_error_auth_unavailable
        VaultError.AUTHENTICATION_FAILED -> R.string.vault_error_auth_failed
        VaultError.SESSION_EXPIRED -> R.string.vault_error_session_expired
        VaultError.STORAGE_FAILURE -> R.string.vault_error_storage
    }

private fun copySensitive(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
}

private fun clearClipboardIfUnchanged(
    context: Context,
    expected: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val current =
        clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    if (current == expected) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

private const val CLIPBOARD_CLEAR_MILLIS = 30_000L
