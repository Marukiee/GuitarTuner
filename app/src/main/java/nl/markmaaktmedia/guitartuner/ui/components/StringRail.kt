package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.domain.model.TuningString
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * One chip per string, in peg order.
 *
 * It is the progress bar of a tuning pass and the manual string picker at the same time.
 * Tapping one locks the tuner to that string, which is what you want the moment auto
 * detection guesses wrong, and it is the only control that has to be reachable with a
 * thumb while both hands are otherwise on an instrument, so it sits at the bottom.
 *
 * The chips are laid out by weight rather than at a fixed size. A seven string guitar has
 * to fit the same row as a ukulele, and a row that scrolls horizontally would hide the
 * very thing it exists to show.
 */
@Composable
fun StringRail(
    strings: List<TuningString>,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    inTuneNow: Boolean,
    soundingIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strings.forEach { string ->
            StringChip(
                string = string,
                isActive = string.physicalIndex == activeIndex,
                isTuned = string.physicalIndex in tunedIndices,
                isInTune = inTuneNow && string.physicalIndex == activeIndex,
                isSounding = string.physicalIndex == soundingIndex,
                onClick = { onSelect(string.physicalIndex) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 56.dp)
                    .aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun StringChip(
    string: TuningString,
    isActive: Boolean,
    isTuned: Boolean,
    isInTune: Boolean,
    isSounding: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current

    val container by animateColorAsState(
        targetValue = when {
            isInTune -> signals.inTune
            isTuned -> signals.inTune.copy(alpha = 0.22f)
            isActive -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isInTune -> signals.onSignal
            isTuned -> signals.inTune
            isActive -> scheme.onPrimaryContainer
            else -> scheme.onSurfaceVariant
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "chipContent",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        animationSpec = TunerMotion.spatial(),
        label = "chipRing",
    )
    // The sounding chip swells while the reference tone plays, which is the only
    // confirmation that the sound came from this app and not from the room.
    val swell by animateFloatAsState(
        targetValue = if (isSounding) 1.12f else 1f,
        animationSpec = TunerMotion.springy(),
        label = "chipSwell",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = swell
                scaleY = swell
            }
            .clip(CircleShape)
            .background(container)
            .border(ringWidth, scheme.primary, CircleShape)
            .bouncyClickable(role = Role.RadioButton, onClickLabel = string.fullLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = string.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
        AnimatedVisibility(
            visible = isTuned && !isInTune,
            enter = scaleIn(TunerMotion.springy()) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = TunerIcons.CheckCircleFilled,
                contentDescription = null,
                tint = signals.inTune,
                modifier = Modifier.size(15.dp).scale(1f),
            )
        }
    }
}
