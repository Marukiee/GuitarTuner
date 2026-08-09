package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
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
        modifier = modifier.height(PillHeight),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            HeadstockGlyph(selected, Modifier.size(22.dp))
            Text(
                text = selected.displayName,
                style = MaterialTheme.typography.titleMedium,
                // The label used to wrap: the pill was fighting a competing weight in the row and
                // got squeezed until "Acoustic" broke across two lines inside a rounded capsule.
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
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

/** Every control in the top bar is this tall, so the row reads as one strip. */
val PillHeight = 44.dp

private val GROUP_OUTER = 22.dp
private val GROUP_INNER = 6.dp

private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER else GROUP_INNER
    val bottom = if (index == count - 1) GROUP_OUTER else GROUP_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A miniature fretboard: the nut, and one line per string with the gauge running thick to thin.
 *
 * The first attempt drew the headstock outline with a dot per peg, and at 26dp that read as a
 * dice face or a TV remote rather than an instrument. Strings are the thing that actually differs
 * between these options, they are what the user is about to tune, and parallel lines of varying
 * weight survive being shrunk in a way that a peg pattern does not.
 *
 * Literal pictures of each instrument were the other option and would have been worse: there is
 * no Material icon for "ukulele", and six guitar silhouettes at this size are indistinguishable.
 */
@Composable
fun HeadstockGlyph(instrument: Instrument, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val count = instrument.stringCount

    Canvas(modifier) {
        val inset = size.width * 0.14f
        val innerWidth = size.width - inset * 2f
        val nutY = size.height * 0.17f
        val unit = size.minDimension

        // The nut, so the lines read as a fretboard rather than as a bar chart.
        drawLine(
            color = tint,
            start = Offset(inset - unit * 0.03f, nutY),
            end = Offset(size.width - inset + unit * 0.03f, nutY),
            strokeWidth = unit * 0.10f,
            cap = StrokeCap.Round,
        )

        val step = if (count > 1) innerWidth / (count - 1) else 0f
        for (i in 0 until count) {
            val x = if (count > 1) inset + step * i else size.width / 2f
            // Thick on the left, thin on the right, exactly like a real set.
            val gauge = unit * (0.075f - 0.035f * (i.toFloat() / (count - 1).coerceAtLeast(1)))
            drawLine(
                color = tint.copy(alpha = 0.85f),
                start = Offset(x, nutY),
                end = Offset(x, size.height * 0.96f),
                strokeWidth = gauge,
                cap = StrokeCap.Round,
            )
        }
    }
}
