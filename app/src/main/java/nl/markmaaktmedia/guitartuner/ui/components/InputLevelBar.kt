package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons

/**
 * How much sound is actually arriving.
 *
 * Without it a dead microphone and a silent room look identical from outside the app,
 * and "the tuner does not work" is then impossible to tell apart from "the phone is not
 * hearing anything". It is drawn from the raw block level, before any filtering, so it
 * reports what the hardware gave us and not what the pipeline made of it.
 */
@Composable
fun InputLevelBar(
    levelDb: StateFlow<Float>,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = remember { Animatable(0f) }
    LaunchedEffect(levelDb) {
        levelDb.collectLatest { db ->
            val normalised = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
            level.animateTo(normalised, tween(90))
        }
    }

    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = if (listening) TunerIcons.Mic else TunerIcons.MicOff,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(6.dp),
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = signals.track, cornerRadius = radius)
            val filled = size.width * level.value
            if (filled > 1f) {
                drawRoundRect(
                    color = scheme.primary,
                    topLeft = Offset.Zero,
                    size = Size(filled, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Room tone sits around -55 dBFS, so this puts a quiet room near the bottom of the bar. */
private const val FLOOR_DB = -60f
