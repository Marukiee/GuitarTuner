package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.domain.model.Instrument

/**
 * One button that says what you are tuning, and a sheet to change it.
 *
 * This replaces a horizontally scrolling row of chips. Chips were wrong for this: there are six
 * instruments and they never fit, so one was always sliced off at the fade, and a row of pills
 * competing for attention above the meter is noise around the thing you are actually reading.
 * A single button states the current instrument and gets out of the way.
 */
@Composable
fun InstrumentButton(
    selected: Instrument,
    onSelect: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        onClick = { showSheet = true },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeadstockGlyph(selected, Modifier.size(26.dp))
            Text(selected.displayName, style = MaterialTheme.typography.titleMedium)
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Instrument",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                )
                Instrument.entries.forEachIndexed { index, option ->
                    InstrumentRow(
                        instrument = option,
                        selected = option == selected,
                        shape = groupShape(index, Instrument.entries.size),
                        onClick = {
                            onSelect(option)
                            showSheet = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentRow(
    instrument: Instrument,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "instrumentRow",
    )

    Surface(onClick = onClick, shape = shape, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeadstockGlyph(instrument, Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Text(instrument.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = instrument.strings.joinToString("  ") { it.fullLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val GROUP_OUTER = 22.dp
private val GROUP_INNER = 6.dp

private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER else GROUP_INNER
    val bottom = if (index == count - 1) GROUP_OUTER else GROUP_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A miniature of the headstock this instrument actually gets, drawn from the same peg layout the
 * full component uses.
 *
 * The alternative was a literal picture of each instrument, and that would have looked like
 * clip art: there is no Material icon for "ukulele", six little guitar silhouettes at 26dp are
 * indistinguishable from each other, and drawing them properly would fight the flat vector
 * language of the rest of the app. An abstract peg pattern is honest about what actually differs
 * between the options, which is the number of strings and where the pegs sit, and it stays
 * legible at button size.
 */
@Composable
fun HeadstockGlyph(instrument: Instrument, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val slots = remember(instrument) { slotsFor(instrument) }
    val hasRight = slots.any { it.side == Side.RIGHT }

    Canvas(modifier) {
        val bodyWidth = size.width * if (hasRight) 0.62f else 0.46f
        val bodyLeft = (size.width - bodyWidth) / 2f +
            if (hasRight) 0f else size.width * 0.08f
        val bodyTop = size.height * 0.06f
        val bodyHeight = size.height * 0.70f

        drawRoundRect(
            color = outline.copy(alpha = 0.30f),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(bodyWidth * 0.34f, bodyWidth * 0.34f),
        )
        // Neck stub, so the glyph reads as a headstock rather than a rounded rectangle.
        drawRoundRect(
            color = outline.copy(alpha = 0.30f),
            topLeft = Offset(size.width / 2f - size.width * 0.09f, bodyTop + bodyHeight - 2f),
            size = Size(size.width * 0.18f, size.height - bodyTop - bodyHeight + 2f),
            cornerRadius = CornerRadius(2f, 2f),
        )

        val dot = size.minDimension * 0.075f
        val bandTop = bodyTop + bodyHeight * 0.16f
        val bandHeight = bodyHeight * 0.60f

        slots.forEach { slot ->
            val x = when (slot.side) {
                Side.LEFT -> bodyLeft + bodyWidth * 0.18f
                Side.RIGHT -> bodyLeft + bodyWidth * 0.82f
            }
            val y = if (slot.slotsOnSide <= 1) {
                bandTop + bandHeight / 2f
            } else {
                bandTop + bandHeight * (slot.slot.toFloat() / (slot.slotsOnSide - 1))
            }
            drawCircle(color = outline, radius = dot, center = Offset(x, y))
        }
    }
}
