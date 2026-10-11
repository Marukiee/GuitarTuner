package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Where the indicator should sit, in pixels of the box it is drawn in. */
internal data class IndicatorTarget(val index: Int, val left: Float, val right: Float)

/**
 * The moving pill behind the navigation bar and the segmented control.
 *
 * It is two edges, not one box. Each edge is its own spring, and the edge in the
 * direction of travel gets the stiffer one, so it leaps ahead and the other edge
 * follows: the pill stretches on the way out and pulls itself back together on
 * arrival. Width is simply right minus left.
 *
 * The earlier version scaled a fixed box by "distance still to go". That value jumps
 * from zero to its maximum on the first frame of a move, so the pill visibly snapped
 * wider before it began to travel. Here nothing is derived from a target, both edges
 * start where they are and the stretch is only ever the difference between two
 * springs, so it grows from nothing.
 *
 * Retargeting in the middle of a move is safe for the same reason: an [Animatable]
 * keeps its velocity, so a second tap bends the motion instead of restarting it.
 */
@Stable
internal class ElasticIndicator {
    val left = Animatable(0f)
    val right = Animatable(0f)

    /** False until the first target is known, so nothing is drawn parked at zero. */
    var placed by mutableStateOf(false)
        private set

    private var index = -1

    /** Width the pill has at rest, which the squash is measured against. */
    var restWidth = 0f
        private set

    suspend fun settle(target: IndicatorTarget) {
        restWidth = target.right - target.left
        // Same choice, new numbers means the layout changed (rotation, a resize), and
        // that is not something to animate.
        val sameSpot = !placed || target.index == index
        index = target.index
        if (sameSpot) {
            left.snapTo(target.left)
            right.snapTo(target.right)
            placed = true
            return
        }
        val towardsEnd = target.left + target.right > left.value + right.value
        coroutineScope {
            launch { left.animateTo(target.left, if (towardsEnd) Lagging else Leading) }
            launch { right.animateTo(target.right, if (towardsEnd) Leading else Lagging) }
        }
    }

    /**
     * How much of a slot the pill currently covers, 0 to 1. Labels use it to change
     * colour exactly as the pill passes under them rather than on a timer of their own.
     */
    fun coverage(slotLeft: Float, slotRight: Float): Float {
        if (!placed) return 0f
        val width = slotRight - slotLeft
        if (width <= 0f) return 0f
        val overlap = min(right.value, slotRight) - max(left.value, slotLeft)
        return (overlap / width).coerceIn(0f, 1f)
    }
}

/** Follows [target], moving the indicator whenever it changes. */
@Composable
internal fun rememberElasticIndicator(target: IndicatorTarget?): ElasticIndicator {
    val indicator = remember { ElasticIndicator() }
    LaunchedEffect(target) {
        if (target != null) indicator.settle(target)
    }
    return indicator
}

/**
 * Draws the pill behind whatever this modifier is on. Reading the edges here, in the
 * draw phase, means a move redraws one layer instead of recomposing the bar 60 times
 * a second.
 */
internal fun Modifier.elasticIndicator(indicator: ElasticIndicator, color: Color): Modifier = drawBehind {
    if (!indicator.placed) return@drawBehind
    val left = indicator.left.value
    val width = (indicator.right.value - left).coerceAtLeast(0f)
    if (width <= 0f) return@drawBehind

    // Stretched pills thin out a little, the way a real elastic band does. Only ever
    // when wider than rest: a pill squeezed narrower keeps its full height.
    val stretch = if (indicator.restWidth > 0f) (width / indicator.restWidth - 1f).coerceIn(0f, 1f) else 0f
    val height = size.height * (1f - stretch * Squash)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}

/**
 * Leading is the edge in the direction of travel. Just under critical damping, so it
 * arrives with a whisper of overshoot that the eye reads as energy, not wobble.
 * Lagging is softer and slower, and the gap between the two is the stretch. The
 * visibility threshold is in pixels: below half a pixel there is nothing left to see.
 */
private val Leading = spring<Float>(dampingRatio = 0.74f, stiffness = 900f, visibilityThreshold = 0.5f)
private val Lagging = spring<Float>(dampingRatio = 0.88f, stiffness = 330f, visibilityThreshold = 0.5f)

/** Share of height lost at twice the resting width. Small, or it reads as a cartoon. */
private const val Squash = 0.1f
