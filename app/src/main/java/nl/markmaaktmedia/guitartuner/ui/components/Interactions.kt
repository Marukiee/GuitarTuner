package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * A tap that answers back.
 *
 * The element dips under the finger and springs out again with a touch of overshoot,
 * which is what makes a flat rectangle feel like something that was actually pressed. The
 * scale runs on a spring rather than a tween on purpose: a spring keeps its momentum when
 * the finger lifts mid animation, so a quick tap and a slow press both look right instead
 * of only the one that was designed for.
 *
 * The ripple stays. It is what tells you where the touch landed, and losing it for the
 * sake of the scale would trade information for polish.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = TunerMotion.PressedScale,
    withHaptics: Boolean = false,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = TunerMotion.bouncy(),
        label = "pressScale",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = ripple(),
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
    ) {
        if (withHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}
