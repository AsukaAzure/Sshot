package dev.sj010.ssjanitor.ui.screens.home.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NextCleanupBanner(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "squigglyRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    var clickRotation by remember { mutableStateOf(0f) }
    val animClickRotation by animateFloatAsState(
        targetValue = clickRotation,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "clickRotation"
    )

    val totalRotation = rotation + animClickRotation
    val tertiaryColor = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val onTertiaryColor = if (isPaused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary
    val containerColor = if (isPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = if (isPaused) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 20.dp, bottom = 20.dp, end = 76.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoDelete,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = if (isPaused) "Sshot is Paused" else "Sshot is Running",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = if (isPaused) "Monitoring and cleanup are disabled" else "Everything is operating normally",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(56.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clickRotation += 360f
                    onTogglePause()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = totalRotation
                    }
                    .background(
                        color = tertiaryColor,
                        shape = SquigglyCircleShape
                    )
            )

            Canvas(modifier = Modifier.size(24.dp)) {
                if (isPaused) {
                    // Draw Play icon (triangle) when paused to "resume/play"
                    val path = Path().apply {
                        moveTo(size.width * 0.35f, size.height * 0.23f)
                        lineTo(size.width * 0.35f, size.height * 0.77f)
                        lineTo(size.width * 0.78f, size.height * 0.5f)
                        close()
                    }
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = onTertiaryColor
                            pathEffect = PathEffect.cornerPathEffect(4.dp.toPx())
                        }
                        canvas.drawPath(path = path, paint = paint)
                    }
                } else {
                    // Draw Pause icon (two vertical bars) when playing
                    val barWidth = size.width * 0.2f
                    val barHeight = size.height * 0.55f
                    val spacing = size.width * 0.15f
                    
                    val leftBarX = (size.width - (barWidth * 2 + spacing)) / 2
                    val barsY = (size.height - barHeight) / 2
                    
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = onTertiaryColor
                            pathEffect = PathEffect.cornerPathEffect(2.dp.toPx())
                        }
                        
                        // Left bar
                        val leftPath = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    left = leftBarX,
                                    top = barsY,
                                    right = leftBarX + barWidth,
                                    bottom = barsY + barHeight,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                            )
                        }
                        canvas.drawPath(leftPath, paint)
                        
                        // Right bar
                        val rightPath = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    left = leftBarX + barWidth + spacing,
                                    top = barsY,
                                    right = leftBarX + barWidth + spacing + barWidth,
                                    bottom = barsY + barHeight,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                            )
                        }
                        canvas.drawPath(rightPath, paint)
                    }
                }
            }
        }
    }
}

private val SquigglyCircleShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = minOf(size.width, size.height) / 2f
            val numBumps = 8
            val amplitude = maxRadius * 0.08f
            val numPoints = 180
            for (i in 0 until numPoints) {
                val angleRad = (i * 2 * Math.PI / numPoints).toFloat()
                val currentRadius = maxRadius - amplitude + amplitude * kotlin.math.sin(angleRad * numBumps).toFloat()
                val x = centerX + currentRadius * kotlin.math.cos(angleRad).toFloat()
                val y = centerY + currentRadius * kotlin.math.sin(angleRad).toFloat()
                if (i == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
            close()
        }
        return Outline.Generic(path)
    }
}