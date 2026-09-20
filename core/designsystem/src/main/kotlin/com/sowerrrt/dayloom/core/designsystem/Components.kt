package com.sowerrrt.dayloom.core.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DayloomAnimatedBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val motionEnabled = rememberDayloomMotionEnabled()
    if (!motionEnabled) {
        DayloomStaticBackground(modifier, primary, secondary, tertiary)
        return
    }

    val transition = rememberInfiniteTransition(label = "dayloomBackground")
    val horizontalDrift =
        transition.animateFloat(
            initialValue = -0.08f,
            targetValue = 0.10f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(18_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "horizontalDrift",
        )
    val verticalDrift =
        transition.animateFloat(
            initialValue = -0.06f,
            targetValue = 0.08f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(22_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "verticalDrift",
        )

    Box(modifier.fillMaxSize()) {
        BackgroundGlow(
            color = primary.copy(alpha = 0.11f),
            centerX = 0.10f,
            centerY = 0.14f,
            radiusFactor = 0.62f,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = horizontalDrift.value * 240.dp.toPx()
                },
        )
        BackgroundGlow(
            color = secondary.copy(alpha = 0.09f),
            centerX = 0.92f,
            centerY = 0.50f,
            radiusFactor = 0.51f,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = -horizontalDrift.value * 210.dp.toPx()
                    translationY = verticalDrift.value * 210.dp.toPx()
                },
        )
        BackgroundGlow(
            color = tertiary.copy(alpha = 0.07f),
            centerX = 0.24f,
            centerY = 0.92f,
            radiusFactor = 0.43f,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = -verticalDrift.value * 180.dp.toPx()
                },
        )
    }
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
    contentPadding: PaddingValues = PaddingValues(DayloomSpacing.md),
    content: @Composable () -> Unit,
) {
    val motionEnabled = rememberDayloomMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed && onClick != null) 0.982f else 1f,
            animationSpec = spring(stiffness = 520f, dampingRatio = 0.78f),
            label = "cardPress",
        )
    val motionModifier =
        if (motionEnabled) {
            modifier.animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
        } else {
            modifier
        }
    val interactiveModifier =
        if (onClick == null) {
            motionModifier
        } else {
            motionModifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
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

@Composable
fun Modifier.dayloomDialogMotion(): Modifier {
    val motionEnabled = rememberDayloomMotionEnabled()
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
        Text(text)
    }
}
