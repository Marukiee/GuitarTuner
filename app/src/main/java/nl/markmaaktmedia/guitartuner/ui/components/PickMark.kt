package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The launcher icon's guitar pick, as a path.
 *
 * It is a triangle whose corners are all the same generous arc, which is what the icon
 * script draws as well: three centres, each swollen by one radius. The ratio below is that
 * script's (a 240 by 150 triangle with a 100 radius), so the mark in the app and the one on
 * the home screen are the same shape rather than two drawings of the same idea.
 *
 * Returned centred on the origin, [width] wide. Tip down.
 */
fun pickPath(width: Float): Path {
    val radiusUnits = 100f / 240f
    val scale = width / (1f + 2f * radiusUnits)
    val r = radiusUnits * scale
    val vertices = listOf(Offset(0f, 0f), Offset(scale, 0f), Offset(0.5f * scale, 0.625f * scale))

    // Moves the centre of the swollen triangle's box onto the origin.
    val shift = Offset(-0.5f * scale, -(0.625f * scale / 2f))

    fun outward(from: Offset, to: Offset): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)
        // The polygon runs clockwise on screen, so the outward side is the right hand one.
        return Offset(dy / length, -dx / length)
    }

    return Path().apply {
        vertices.forEachIndexed { i, vertex ->
            val previous = vertices[(i + vertices.size - 1) % vertices.size]
            val next = vertices[(i + 1) % vertices.size]
            val n1 = outward(previous, vertex)
            val n2 = outward(vertex, next)
            val start = Math.toDegrees(atan2(n1.y.toDouble(), n1.x.toDouble())).toFloat()
            val end = Math.toDegrees(atan2(n2.y.toDouble(), n2.x.toDouble())).toFloat()
            var sweep = end - start
            while (sweep <= 0f) sweep += 360f
            val centre = vertex + shift
            arcTo(
                rect = Rect(centre.x - r, centre.y - r, centre.x + r, centre.y + r),
                startAngleDegrees = start,
                sweepAngleDegrees = sweep,
                forceMoveTo = i == 0,
            )
        }
        close()
    }
}

/**
 * The loading mark: the launcher icon's pick, turning.
 *
 * It does not spin at a constant rate. Each cycle is two eased turns with a held beat
 * between them, so it winds up, lands back on its own rest angle, pauses just long enough
 * to be read as the app's mark rather than a generic spinner, and goes again.
 */
@Composable
fun PickSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    active: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "pickSpinner")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = TunerMotion.SpinnerCycleMillis
                0f at 0 using TunerMotion.Emphasised
                360f at (TunerMotion.SpinnerCycleMillis * 40 / 100)
                // The beat. Same value twice is what holds it still.
                360f at (TunerMotion.SpinnerCycleMillis * 55 / 100) using TunerMotion.Emphasised
                720f at (TunerMotion.SpinnerCycleMillis * 95 / 100)
                720f at TunerMotion.SpinnerCycleMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // A slow breath underneath, so the held beat is never completely dead.
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    Canvas(modifier = modifier.size(size)) {
        val side = minOf(this.size.width, this.size.height)
        val path = pickPath(side * 0.62f)
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        scale(if (active) breath else 1f, pivot = centre) {
            rotate(degrees = if (active) rotation else 0f, pivot = centre) {
                translateAndDraw(path, centre, color)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.translateAndDraw(
    path: Path,
    centre: Offset,
    color: Color,
) {
    translate(centre.x, centre.y) {
        drawPath(path, color)
    }
}

/** The spinner with a line under it, for a wait long enough to need explaining. */
@Composable
fun PickLoader(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PickSpinner(size = size, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The mark standing still, for empty states and headers. */
@Composable
fun PickMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier = modifier) {
        PickSpinner(size = size, color = color, active = false)
    }
}
