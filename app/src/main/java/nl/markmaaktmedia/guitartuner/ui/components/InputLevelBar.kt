package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * A bare input level meter.
 *
 * This exists because of a real failure: on a handset with a broken rear microphone the app was
 * simply inert, and there was no way to tell "nothing is arriving" from "that is not a note".
 * A level bar answers that question in under a second and costs almost nothing.
 *
 * Like the tuning meter it never lets the audio rate reach composition: the flow drives an
 * [Animatable] that is read inside the `Canvas` draw lambda.
 */
@Composable
fun InputLevelBar(
    levelDb: StateFlow<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val level = remember { Animatable(0f) }

    LaunchedEffect(levelDb) {
        levelDb.collectLatest { db ->
            // -70 dBFS is silence, -12 is a hard strum. Anything below the floor reads as zero
            // rather than as a sliver, so a dead microphone is visibly dead.
            val normalised = ((db + 70f) / 58f).coerceIn(0f, 1f)
            level.animateTo(
                targetValue = normalised,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        }
    }

    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = colors.onSurfaceVariant.copy(alpha = 0.15f),
            size = size,
            cornerRadius = radius,
        )
        val filled = size.width * level.value
        if (filled > size.height) {
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset.Zero,
                size = Size(filled, size.height),
                cornerRadius = radius,
            )
        }
    }
}
