package com.sowerrrt.dayloom.feature.wishlist

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.designsystem.dayloomDialogMotion
import com.sowerrrt.dayloom.core.model.WishContribution
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.isCompleted
import com.sowerrrt.dayloom.core.model.savedMinor
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    var showGoalEditor by rememberSaveable { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<WishGoal?>(null) }
    var showContributionEditor by rememberSaveable { mutableStateOf(false) }
    var pendingGoalDelete by remember { mutableStateOf<WishGoal?>(null) }
    var pendingContributionDelete by remember { mutableStateOf<WishContribution?>(null) }
    val selectedGoal = state.selectedGoal
    val uriHandler = LocalUriHandler.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null && selectedGoal != null) viewModel.setImage(selectedGoal.id, uri)
        }

    Box(Modifier.fillMaxSize().testTag("wishlist_screen")) {
        Column(Modifier.fillMaxSize()) {
            DayloomTopBar(
                title = selectedGoal?.title ?: stringResource(R.string.wishlist_title),
                navigationIcon = {
                    IconButton(
                        onClick = if (selectedGoal == null) onBack else viewModel::closeGoal,
                        modifier = Modifier.testTag(if (selectedGoal == null) "wishlist_back" else "close_wish"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.wishlist_back),
                        )
                    }
                },
                actions = {
                    if (selectedGoal != null) {
                        IconButton(
                            onClick = {
                                editingGoal = selectedGoal
                                showGoalEditor = true
                            },
                            modifier = Modifier.testTag("edit_wish"),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.wishlist_edit))
                        }
                        IconButton(
                            onClick = { pendingGoalDelete = selectedGoal },
                            modifier = Modifier.testTag("delete_wish"),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.wishlist_delete),
                            )
                        }
                    }
                },
            )
            when {
                state.isLoading -> LoadingState(Modifier.weight(1f))
                state.hasError ->
                    ErrorState(
                        title = stringResource(R.string.wishlist_error_title),
                        message = stringResource(R.string.wishlist_error_description),
                        retryLabel = stringResource(R.string.wishlist_retry),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.weight(1f),
                    )
                selectedGoal == null ->
                    WishlistOverview(
                        state = state,
                        onOpenGoal = { viewModel.openGoal(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                else ->
                    WishDetails(
                        goal = selectedGoal,
                        imagePath = state.imagePaths[selectedGoal.id],
                        isChangingImage = state.isChangingImage,
                        hasImageError = state.hasImageError,
                        onChooseImage = { imagePicker.launch("image/*") },
                        onRemoveImage = { viewModel.removeImage(selectedGoal.id) },
                        onAddContribution = { showContributionEditor = true },
                        onDeleteContribution = { pendingContributionDelete = it },
                        onOpenPurchase = { url -> uriHandler.openUri(url) },
                        modifier = Modifier.weight(1f),
                    )
            }
        }

        if (!state.isLoading && !state.hasError && selectedGoal == null) {
            FloatingActionButton(
                onClick = {
                    editingGoal = null
                    showGoalEditor = true
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = DayloomSpacing.md, bottom = DayloomSpacing.lg)
                        .testTag("create_wish"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.wishlist_action))
            }
        }
    }

    if (showGoalEditor) {
        GoalEditorDialog(
            goal = editingGoal,
            onDismiss = { showGoalEditor = false },
            onSave = { title, target, currency, priority, note, purchaseUrl ->
                val goal = editingGoal
                if (goal == null) {
                    viewModel.createGoal(title, target, currency, priority, note, purchaseUrl)
                } else {
                    viewModel.updateGoal(goal.id, title, target, currency, priority, note, purchaseUrl)
                }
                showGoalEditor = false
            },
        )
    }

    if (showContributionEditor && selectedGoal != null) {
        ContributionDialog(
            currencyCode = selectedGoal.currencyCode,
            onDismiss = { showContributionEditor = false },
            onSave = { amount, note ->
                viewModel.addContribution(selectedGoal.id, amount, note)
                showContributionEditor = false
            },
        )
    }

    pendingGoalDelete?.let { goal ->
        DeleteDialog(
            title = stringResource(R.string.wishlist_delete_goal_title),
            description = stringResource(R.string.wishlist_delete_goal_description, goal.title),
            onDismiss = { pendingGoalDelete = null },
            onConfirm = {
                viewModel.deleteGoal(goal.id)
                pendingGoalDelete = null
            },
        )
    }

    if (selectedGoal != null) {
        pendingContributionDelete?.let { contribution ->
            DeleteDialog(
                title = stringResource(R.string.wishlist_delete_contribution_title),
                description =
                    stringResource(
                        R.string.wishlist_delete_contribution_description,
                        formatMoney(contribution.amountMinor, selectedGoal.currencyCode),
                    ),
                onDismiss = { pendingContributionDelete = null },
                onConfirm = {
                    viewModel.deleteContribution(selectedGoal.id, contribution.id)
                    pendingContributionDelete = null
                },
            )
        }
    }
}

@Composable
private fun WishlistOverview(
    state: WishlistUiState,
    onOpenGoal: (WishGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("wishlist_overview"),
        contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
    ) {
        item {
            Text(
                stringResource(R.string.wishlist_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.goals.isEmpty()) {
            item { EmptyWishlistCard() }
        } else {
            item {
                Text(
                    stringResource(R.string.wishlist_summary, state.goals.size, state.completedGoals),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.goals, key = { it.id.value }) { goal ->
                Box(Modifier.animateItem()) {
                    WishCard(
                        goal = goal,
                        imagePath = state.imagePaths[goal.id],
                        onClick = { onOpenGoal(goal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWishlistCard() {
    DayloomCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Icon(Icons.Rounded.Savings, contentDescription = null, modifier = Modifier.size(42.dp))
            Text(stringResource(R.string.wishlist_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.wishlist_empty_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WishImageCover(
    imagePath: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = imagePath) {
            value =
                imagePath?.let { path ->
                    withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                }
        }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                ).testTag("wish_image_$title"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = stringResource(R.string.wishlist_goal_image, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Savings,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun WishCard(
    goal: WishGoal,
    imagePath: String?,
    onClick: () -> Unit,
) {
    val progress = goalProgress(goal)
    DayloomCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("wish_${goal.title}"),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            WishImageCover(
                imagePath = imagePath,
                title = goal.title,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (goal.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = if (goal.isCompleted) MaterialTheme.colorScheme.tertiary else priorityColor(goal.priority),
                )
                Spacer(Modifier.size(DayloomSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        priorityLabel(goal.priority),
                        style = MaterialTheme.typography.labelLarge,
                        color = priorityColor(goal.priority),
                    )
                }
                Text(
                    stringResource(R.string.wishlist_percent, (progress * 100).toInt()),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(
                    R.string.wishlist_saved_of_target,
                    formatMoney(goal.savedMinor, goal.currencyCode),
                    formatMoney(goal.targetMinor, goal.currencyCode),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WishDetails(
    goal: WishGoal,
    imagePath: String?,
    isChangingImage: Boolean,
    hasImageError: Boolean,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onAddContribution: () -> Unit,
    onDeleteContribution: (WishContribution) -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("wish_details"),
        contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.regular),
    ) {
        item {
            DayloomCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    WishImageCover(
                        imagePath = imagePath,
                        title = goal.title,
                        modifier = Modifier.fillMaxWidth().height(172.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                        OutlinedButton(
                            onClick = onChooseImage,
                            enabled = !isChangingImage,
                            modifier = Modifier.weight(1f).testTag("add_wish_image"),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(DayloomSpacing.xs))
                            Text(
                                stringResource(
                                    if (goal.image == null) {
                                        R.string.wishlist_add_image
                                    } else {
                                        R.string.wishlist_change_image
                                    },
                                ),
                            )
                        }
                        if (goal.image != null) {
                            IconButton(
                                onClick = onRemoveImage,
                                enabled = !isChangingImage,
                                modifier = Modifier.testTag("remove_wish_image"),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.wishlist_remove_image),
                                )
                            }
                        }
                    }
                    if (hasImageError) {
                        Text(
                            stringResource(R.string.wishlist_image_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("wish_image_error"),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (goal.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Savings,
                            contentDescription = null,
                            tint = priorityColor(goal.priority),
                        )
                        Spacer(Modifier.size(DayloomSpacing.sm))
                        Text(priorityLabel(goal.priority), color = priorityColor(goal.priority))
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.wishlist_percent, (goalProgress(goal) * 100).toInt()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    LinearProgressIndicator(progress = { goalProgress(goal) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(
                            R.string.wishlist_saved_of_target,
                            formatMoney(goal.savedMinor, goal.currencyCode),
                            formatMoney(goal.targetMinor, goal.currencyCode),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (goal.note.isNotEmpty()) {
                        Text(goal.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (goal.purchaseUrl.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onOpenPurchase(goal.purchaseUrl) },
                            modifier = Modifier.fillMaxWidth().testTag("open_purchase_link"),
                        ) {
                            Icon(Icons.Rounded.ShoppingBag, contentDescription = null)
                            Spacer(Modifier.size(DayloomSpacing.xs))
                            Text(stringResource(R.string.wishlist_open_purchase))
                        }
                    }
                    Button(
                        onClick = onAddContribution,
                        modifier = Modifier.fillMaxWidth().testTag("add_contribution"),
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null)
                        Spacer(Modifier.size(DayloomSpacing.sm))
                        Text(stringResource(R.string.wishlist_add_contribution))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.wishlist_history), style = MaterialTheme.typography.titleLarge) }
        if (goal.contributions.isEmpty()) {
            item {
                DayloomCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.wishlist_no_contributions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(goal.contributions.asReversed(), key = { it.id.value }) { contribution ->
                Box(Modifier.animateItem()) {
                    ContributionRow(
                        contribution = contribution,
                        currencyCode = goal.currencyCode,
                        onDelete = { onDeleteContribution(contribution) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributionRow(
    contribution: WishContribution,
    currencyCode: String,
    onDelete: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("contribution_${contribution.amountMinor}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.size(DayloomSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.wishlist_contribution_value,
                        formatMoney(contribution.amountMinor, currencyCode),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (contribution.note.isNotEmpty()) {
                    Text(contribution.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.wishlist_delete_contribution),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditorDialog(
    goal: WishGoal?,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, WishPriority, String, String) -> Unit,
) {
    var title by remember(goal?.id) { mutableStateOf(goal?.title.orEmpty()) }
    var target by remember(goal?.id) { mutableStateOf(goal?.targetMinor?.let(::minorToInput).orEmpty()) }
    var currency by remember(goal?.id) { mutableStateOf(goal?.currencyCode ?: "RUB") }
    var priority by remember(goal?.id) { mutableStateOf(goal?.priority ?: WishPriority.MEDIUM) }
    var note by remember(goal?.id) { mutableStateOf(goal?.note.orEmpty()) }
    var purchaseUrl by remember(goal?.id) { mutableStateOf(goal?.purchaseUrl.orEmpty()) }
    val parsedTarget = parseAmountToMinor(target)
    val normalizedCurrency = currency.trim().uppercase()
    val targetIsValid = parsedTarget != null && parsedTarget > 0
    val currencyIsValid = normalizedCurrency.matches(Regex("[A-Z0-9]{1,8}"))
    val purchaseUrlIsValid =
        purchaseUrl.isBlank() || purchaseUrl.trim().matches(Regex("https?://.+", RegexOption.IGNORE_CASE))
    val canSave =
        title.isNotBlank() &&
            targetIsValid &&
            currencyIsValid &&
            purchaseUrlIsValid

    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (goal == null) R.string.wishlist_create_title else R.string.wishlist_edit_title))
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                        modifier = Modifier.fillMaxWidth().testTag("wish_title_input"),
                        label = { Text(stringResource(R.string.wishlist_name_label)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = sanitizeAmountInput(it) },
                        modifier = Modifier.fillMaxWidth().testTag("wish_target_input"),
                        label = { Text(stringResource(R.string.wishlist_target_label)) },
                        supportingText = {
                            if (target.isNotBlank() && !targetIsValid) {
                                Text(stringResource(R.string.wishlist_target_error))
                            }
                        },
                        isError = target.isNotBlank() && !targetIsValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                        modifier = Modifier.fillMaxWidth().testTag("wish_currency_input"),
                        label = { Text(stringResource(R.string.wishlist_currency_label)) },
                        supportingText = {
                            if (!currencyIsValid) Text(stringResource(R.string.wishlist_currency_error))
                        },
                        isError = !currencyIsValid,
                        singleLine = true,
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                        COMMON_CURRENCIES.forEach { code ->
                            FilterChip(
                                selected = currency == code,
                                onClick = { currency = code },
                                label = { Text(code) },
                                modifier = Modifier.testTag("wish_currency_${code.lowercase()}"),
                            )
                        }
                    }
                }
                item { Text(stringResource(R.string.wishlist_priority_label)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                        WishPriority.entries.forEach { candidate ->
                            FilterChip(
                                selected = priority == candidate,
                                onClick = { priority = candidate },
                                label = { Text(priorityLabel(candidate)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Savings,
                                        contentDescription = null,
                                        tint =
                                            if (priority == candidate) {
                                                priorityColor(candidate)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                },
                                modifier = Modifier.testTag("wish_priority_${candidate.name.lowercase()}"),
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                        modifier = Modifier.fillMaxWidth().testTag("wish_note_input"),
                        label = { Text(stringResource(R.string.wishlist_note_label)) },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
                item {
                    OutlinedTextField(
                        value = purchaseUrl,
                        onValueChange = { purchaseUrl = it.take(MAX_URL_LENGTH) },
                        modifier = Modifier.fillMaxWidth().testTag("wish_purchase_url_input"),
                        label = { Text(stringResource(R.string.wishlist_purchase_url_label)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (purchaseUrlIsValid) {
                                        R.string.wishlist_purchase_url_description
                                    } else {
                                        R.string.wishlist_purchase_url_error
                                    },
                                ),
                            )
                        },
                        isError = !purchaseUrlIsValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, parsedTarget!!, normalizedCurrency, priority, note, purchaseUrl.trim())
                },
                enabled = canSave,
                modifier = Modifier.testTag("save_wish"),
            ) {
                Text(stringResource(R.string.wishlist_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wishlist_cancel)) } },
    )
}

@Composable
private fun ContributionDialog(
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val parsedAmount = parseAmountToMinor(amount)
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wishlist_contribution_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = sanitizeAmountInput(it) },
                    modifier = Modifier.fillMaxWidth().testTag("contribution_amount_input"),
                    label = { Text(stringResource(R.string.wishlist_amount_label, currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("contribution_note_input"),
                    label = { Text(stringResource(R.string.wishlist_contribution_note_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsedAmount!!, note) },
                enabled = parsedAmount != null && parsedAmount > 0,
                modifier = Modifier.testTag("save_contribution"),
            ) {
                Text(stringResource(R.string.wishlist_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wishlist_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_wish_delete")) {
                Text(
                    stringResource(R.string.wishlist_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wishlist_cancel)) } },
    )
}

@Composable
private fun priorityLabel(priority: WishPriority): String =
    stringResource(
        when (priority) {
            WishPriority.LOW -> R.string.wishlist_priority_low
            WishPriority.MEDIUM -> R.string.wishlist_priority_medium
            WishPriority.HIGH -> R.string.wishlist_priority_high
        },
    )

@Composable
private fun priorityColor(priority: WishPriority) =
    when (priority) {
        WishPriority.LOW -> MaterialTheme.colorScheme.secondary
        WishPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        WishPriority.HIGH -> MaterialTheme.colorScheme.tertiary
    }

@Composable
private fun formatMoney(
    amountMinor: Long,
    currencyCode: String,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    return "${formatter.format(BigDecimal.valueOf(amountMinor, 2))} $currencyCode"
}

private fun goalProgress(goal: WishGoal): Float =
    (goal.savedMinor.toDouble() / goal.targetMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()

internal fun parseAmountToMinor(value: String): Long? =
    runCatching {
        value
            .trim()
            .replace(" ", "")
            .replace(',', '.')
            .toBigDecimal()
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it in 1..MAX_AMOUNT_MINOR }
    }.getOrNull()

private fun sanitizeAmountInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_AMOUNT_INPUT_LENGTH)

private fun minorToInput(value: Long): String = BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString()

private val COMMON_CURRENCIES = listOf("RUB", "USD", "EUR", "GBP", "KZT")
private const val MAX_TITLE_LENGTH = 100
private const val MAX_NOTE_LENGTH = 500
private const val MAX_URL_LENGTH = 2_048
private const val MAX_AMOUNT_INPUT_LENGTH = 16
private const val MAX_AMOUNT_MINOR = 999_999_999_999_99L
