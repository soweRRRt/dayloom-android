package com.sowerrrt.dayloom.core.designsystem

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun DayloomAnimatedBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "dayloomBackground")
    val horizontalDrift by
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
    val verticalDrift by
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

    Canvas(modifier.fillMaxSize()) {
        val radius = size.maxDimension * 0.62f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(size.width * (0.10f + horizontalDrift), size.height * 0.14f),
                    radius = radius,
                ),
            radius = radius,
            center = Offset(size.width * (0.10f + horizontalDrift), size.height * 0.14f),
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * (0.92f - horizontalDrift), size.height * (0.50f + verticalDrift)),
                    radius = radius * 0.82f,
                ),
            radius = radius * 0.82f,
            center = Offset(size.width * (0.92f - horizontalDrift), size.height * (0.50f + verticalDrift)),
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * (0.24f - verticalDrift), size.height * 0.92f),
                    radius = radius * 0.7f,
                ),
            radius = radius * 0.7f,
            center = Offset(size.width * (0.24f - verticalDrift), size.height * 0.92f),
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed && onClick != null) 0.982f else 1f,
            animationSpec = spring(stiffness = 520f, dampingRatio = 0.78f),
            label = "cardPress",
        )
    val interactiveModifier =
        if (onClick == null) {
            modifier
        } else {
            modifier
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
