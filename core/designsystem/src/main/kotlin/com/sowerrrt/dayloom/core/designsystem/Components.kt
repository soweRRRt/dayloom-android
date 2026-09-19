package com.sowerrrt.dayloom.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DayloomLogo(Modifier.size(30.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun DayloomCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.padding(DayloomSpacing.md)) { content() }
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
    ) {
        Text(text)
    }
}
