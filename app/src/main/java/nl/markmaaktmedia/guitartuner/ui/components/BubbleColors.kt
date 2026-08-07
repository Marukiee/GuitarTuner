package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import nl.markmaaktmedia.guitartuner.ui.theme.flatAccent
import nl.markmaaktmedia.guitartuner.ui.theme.sharpAccent
import kotlin.math.abs

/**
 * The bubble's fill and the colour of the number printed on it, derived together.
 *
 * This lives in one place because the readout and the bubble are drawn by two different
 * composables (one in a `Canvas`, one as `Text`) and they must never disagree. The first version
 * hardcoded the text to `onPrimary`, which is white in a light scheme, and the bubble spends most
 * of its travel in a colour that is not `primary` at all. The result was white on a mid tone.
 *
 * The content colour is picked from the *actual* luminance of the fill rather than lerped between
 * two on-colours: lerping two contrast-safe pairs does not give a contrast-safe midpoint, because
 * the midpoint of a dark and a light fill is a mid tone with mid tone text on it.
 */
internal fun bubbleFill(cents: Float, colors: ColorScheme): Color = lerp(
    if (cents < 0f) colors.flatAccent else colors.sharpAccent,
    colors.primary,
    closenessOf(cents),
)

/** Near black or white, whichever survives on [fill]. */
internal fun contentColorOn(fill: Color): Color =
    if (fill.luminance() > 0.5f) Color(0xFF0E1116) else Color(0xFFFFFFFF)

/**
 * 1 at the target, 0 at 25 cents out. Deliberately steeper than the meter's own 50 cent span so
 * the in-tune colour is earned rather than being the default half the time.
 */
internal fun closenessOf(cents: Float): Float =
    (1f - (abs(cents) / 25f)).coerceIn(0f, 1f)
