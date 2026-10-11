package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape

/**
 * A row of choices with one pill that travels between them.
 *
 * The same idea as the navigation bar, and here for the same reason: three chips that
 * each recolour themselves are three things changing at once, and the eye has to find
 * the new selection. One pill that moves is a single object going somewhere, and the
 * eye follows it there.
 *
 * The pill is the same [ElasticIndicator] as the navigation bar's, so the two stretch
 * and settle identically. Labels take their colour from how much of their slot the
 * pill covers, which keeps text and pill in step through the whole move.
 *
 * By default the slots are not equal width, because "Unread" is not as wide as
 * "Needs attention". Each label reports where it ended up, and the pill animates to
 * that position and that width, so it grows and shrinks as it travels. With
 * [equalWidth] the row fills the width it is given and every option gets an equal
 * slot with its label centred, which is what a short list of settings wants.
 */
@Composable
fun <T> SegmentedPillRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    equalWidth: Boolean = false,
) {
    if (options.isEmpty()) return

    // Filled in on the first layout pass. Until then the pill has nowhere to be, so it
    // is simply not drawn rather than parked at zero and sliding in from the left.
    val slots = remember(options) { mutableStateMapOf<Int, Slot>() }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val selectedSlot = slots[selectedIndex]
    val target = remember(selectedIndex, selectedSlot) {
        selectedSlot?.let { IndicatorTarget(selectedIndex, it.x, it.x + it.width) }
    }
    val indicator = rememberElasticIndicator(target)

    val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedContent = MaterialTheme.colorScheme.onPrimary
    val fill = if (equalWidth) Modifier.fillMaxWidth() else Modifier

    Box(
        modifier = modifier
            .then(fill)
            .clip(PillShape)
            .background(containerColor)
            .padding(Inset),
    ) {
        Row(modifier = fill.elasticIndicator(indicator, indicatorColor)) {
            options.forEachIndexed { index, option ->
                val slot = slots[index]
                val covered = if (slot != null) indicator.coverage(slot.x, slot.x + slot.width) else 0f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = (if (equalWidth) Modifier.weight(1f) else Modifier)
                        .height(SlotHeight)
                        .onGloballyPositioned { coordinates ->
                            val placed = Slot(
                                x = coordinates.positionInParent().x,
                                width = coordinates.size.width.toFloat(),
                            )
                            // Written only when it changed, so a layout pass that
                            // lands on the same numbers does not invalidate anything.
                            if (slots[index] != placed) slots[index] = placed
                        }
                        .clip(PillShape)
                        .bouncyClickable(role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = if (equalWidth) EqualLabelPadding else LabelPadding),
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = lerp(unselectedContent, selectedContent, covered),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Where one choice sits inside the row, in pixels. */
private data class Slot(val x: Float, val width: Float)

private val Inset = 4.dp
private val SlotHeight = 38.dp
private val LabelPadding = 16.dp
private val EqualLabelPadding = 8.dp
