package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

/**
 * One place for how things move, so the whole app moves the same way.
 *
 * The split is deliberate. Anything that changes position or size runs on a spring,
 * because a spring carries momentum and reads as a physical object being moved. Anything
 * that only changes colour or opacity runs on a tween, because a bouncing colour looks
 * like a bug. Mixing those two up is what makes an app feel almost right and slightly
 * cheap.
 */
object TunerMotion {

    /** The house easing: fast out of the gate, long settle. */
    val Standard = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    const val DurationFast = 140
    const val DurationMedium = 260
    const val DurationSlow = 420

    /** Theme flips and other pure colour changes. */
    fun <T> colourSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 320, easing = Standard)

    fun <T> fadeSpec(): FiniteAnimationSpec<T> = tween(durationMillis = DurationMedium, easing = Standard)

    /** Movement with no overshoot: sliding panels, scroll driven offsets. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Movement allowed a little overshoot: chips, indicators, the string rail. */
    fun <T> springy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** The loosest one, kept for the press scale only. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Size changes need their own visibility threshold to avoid a final jitter. */
    fun sizeSpring(): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize(1, 1),
    )

    /**
     * The needle.
     *
     * Under damped on purpose, and this is the one spring in the app that is allowed to
     * overshoot visibly: a meter that eases to a stop reads as software, one that swings
     * past and settles reads as a physical needle. Stiff enough that a 21 Hz stream of
     * readings still looks like one continuous motion rather than a series of restarts.
     */
    const val NeedleDamping = 0.62f
    const val NeedleStiffness = 220f

    /** How far a pressed element shrinks. Small enough to feel, not to distract. */
    const val PressedScale = 0.96f
}
