package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * A tap that answers back.
 *
 * The content dips under the finger and springs out again with a touch of overshoot,
 * which is what makes a flat rectangle feel like something that was actually pressed. The
 * scale runs on a spring rather than a tween on purpose: a spring keeps its momentum when
 * the finger lifts mid animation.
 *
 * It stays down a moment longer than the finger, as in PixelPlayer. In a scrolling list a quick
 * tap reports press and release at once, so following the pressed state alone shows nothing.
 *
 * The ripple stays, at full size. It is what tells you where the touch landed, and losing it for
 * the sake of the scale would trade information for polish.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = TunerMotion.PressedScale,
    withHaptics: Boolean = false,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    /** Something that happens on a long press, with a buzz. */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.filterIsInstance<PressInteraction>().collectLatest { press ->
            if (press !is PressInteraction.Press) delay(TunerMotion.PressHoldMillis)
            held = press is PressInteraction.Press
        }
    }
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (held && enabled) pressedScale else 1f,
        animationSpec = TunerMotion.press(),
        label = "pressScale",
    )

    combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(),
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
        onLongClick = onLongClick?.let { long ->
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                long()
            }
        },
        onClick = {
            if (withHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    ).graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
