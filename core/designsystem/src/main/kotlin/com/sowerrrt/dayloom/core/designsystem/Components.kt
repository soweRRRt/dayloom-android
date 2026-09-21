package com.sowerrrt.dayloom.core.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DayloomAnimatedBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // A calm static mesh keeps the visual depth without redrawing three
    // full-screen gradients on every frame. Motion is reserved for direct
    // interactions, where it feels intentional and remains consistently smooth.
    DayloomStaticBackground(modifier, primary, secondary, tertiary)
}

@Composable
private fun DayloomStaticBackground(
    modifier: Modifier,
    primary: Color,
    secondary: Color,
    tertiary: Color,
) {
    Box(modifier.fillMaxSize()) {
        BackgroundGlow(primary.copy(alpha = 0.11f), 0.10f, 0.14f, 0.62f, Modifier.fillMaxSize())
        BackgroundGlow(secondary.copy(alpha = 0.09f), 0.92f, 0.50f, 0.51f, Modifier.fillMaxSize())
        BackgroundGlow(tertiary.copy(alpha = 0.07f), 0.24f, 0.92f, 0.43f, Modifier.fillMaxSize())
    }
}

@Composable
private fun BackgroundGlow(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusFactor: Float,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val radius = size.maxDimension * radiusFactor
        val center = Offset(size.width * centerX, size.height * centerY)
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center, radius),
            radius = radius,
            center = center,
        )
    }
}

@Composable
fun DayloomLogo(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(42.dp)) {
        fun ribbon(
            offset: Float,
            color: androidx.compose.ui.graphics.Color,
        ) {
            val path =
                Path().apply {
                    moveTo(size.width * 0.08f, size.height * offset)
                    cubicTo(
                        size.width * 0.32f,
                        size.height * (offset - 0.28f),
                        size.width * 0.67f,
                        size.height * (offset + 0.28f),
                        size.width * 0.92f,
                        size.height * offset,
                    )
                }
            drawPath(path, color, style = Stroke(width = size.minDimension * 0.13f, cap = StrokeCap.Round))
        }
        ribbon(0.30f, primary)
        ribbon(0.52f, secondary)
        ribbon(0.74f, tertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayloomTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showLogo) DayloomLogo(Modifier.size(28.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
    )
}

@Composable
fun DayloomCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues =
        PaddingValues(
            horizontal = DayloomSpacing.md,
            vertical = DayloomSpacing.regular,
        ),
    content: @Composable () -> Unit,
) {
    val motionEnabled = dayloomMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (motionEnabled && pressed && onClick != null) 0.988f else 1f,
            animationSpec = spring(stiffness = 650f, dampingRatio = 1f),
            label = "cardPress",
        )
    val interactiveModifier =
        if (onClick == null) {
            modifier.defaultMinSize(minHeight = 48.dp)
        } else {
            modifier
                .defaultMinSize(minHeight = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
    Card(
        modifier = interactiveModifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayloomSwipeToArchive(
    archiveLabel: String,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dismissState =
        androidx.compose.material3.rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart && enabled) {
                    onArchive()
                }
                // The data layer removes the row immediately. Keeping the dismiss state at
                // EndToStart makes a restored item with the same stable key reappear as an
                // empty archive background until the screen is recreated.
                false
            },
            positionalThreshold = { distance -> distance * 0.38f },
        )
    val active = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    val backgroundColor by
        animateColorAsState(
            targetValue =
                if (active) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            animationSpec = tween(DayloomMotion.QUICK_MILLIS),
            label = "archiveSwipeBackground",
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier =
            modifier.semantics {
                if (enabled) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(archiveLabel) {
                                onArchive()
                                true
                            },
                        )
                }
            },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(backgroundColor)
                    .padding(horizontal = DayloomSpacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                ) {
                    Text(
                        archiveLabel,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        },
        content = { content() },
    )
}

@Composable
fun <T> DayloomHorizontalRail(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    Box(modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            contentPadding = PaddingValues(end = DayloomSpacing.xl),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        ) {
            items(items = items, key = key) { item -> itemContent(item) }
        }
        AnimatedVisibility(
            visible = state.canScrollForward,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(DayloomSpacing.xl)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        ),
                    ),
            )
        }
    }
}

@Composable
fun Modifier.dayloomDialogMotion(): Modifier {
    val motionEnabled = dayloomMotionEnabled()
    val progress = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(DayloomMotion.STANDARD_MILLIS, easing = FastOutSlowInEasing),
            )
        } else {
            progress.snapTo(1f)
        }
    }
    return this
        .graphicsLayer {
            alpha = 0.72f + (0.28f * progress.value)
            scaleX = 0.965f + (0.035f * progress.value)
            scaleY = 0.965f + (0.035f * progress.value)
            translationY = (1f - progress.value) * 12.dp.toPx()
        }.then(
            if (motionEnabled) {
                Modifier.animateContentSize(
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
                )
            } else {
                Modifier
            },
        )
}

@Composable
fun rememberDayloomMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver

    val readMotionEnabled = {
        isDayloomMotionEnabled(
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
    }

    var motionEnabled by remember(resolver) { mutableStateOf(readMotionEnabled()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    motionEnabled = readMotionEnabled()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return motionEnabled
}

private val LocalDayloomMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun DayloomMotionProvider(content: @Composable () -> Unit) {
    val motionEnabled = rememberDayloomMotionEnabled()
    CompositionLocalProvider(LocalDayloomMotionEnabled provides motionEnabled, content = content)
}

@Composable
fun dayloomMotionEnabled(): Boolean = LocalDayloomMotionEnabled.current

fun isDayloomMotionEnabled(animatorScale: Float): Boolean = animatorScale > 0f

@Composable
fun DayloomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = DayloomSpacing.lg, vertical = DayloomSpacing.regular),
    ) {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
