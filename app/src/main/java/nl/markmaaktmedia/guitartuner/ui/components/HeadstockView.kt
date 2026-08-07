package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockLayout
import nl.markmaaktmedia.guitartuner.domain.model.Instrument

private enum class Side { LEFT, RIGHT }

/** Where one peg sits: which side of the headstock, and how far down that side. */
private data class PegSlot(
    val physicalIndex: Int,
    val side: Side,
    val slot: Int,
    val slotsOnSide: Int,
)

/**
 * Deals the strings out over the two sides of the headstock.
 *
 * The ordering is the real thing, not the obvious thing. On a machine head the post furthest from
 * the nut carries the string that has furthest to travel, so:
 *
 * - the bass side runs low to high going **away** from the nut, which with the nut at the bottom
 *   of the component means the lowest string sits at the top;
 * - the treble side is mirrored, so the high E sits at the top and the G nearest the nut. Getting
 *   this backwards is immediately obvious to anyone who has ever restrung a guitar;
 * - an inline headstock is the treble case for every string: the low E post is the one closest to
 *   the nut, so the whole column reads high to low from the top down.
 */
private fun slotsFor(instrument: Instrument): List<PegSlot> {
    val count = instrument.stringCount
    val leftCount = when (instrument.layout) {
        HeadstockLayout.THREE_PER_SIDE -> 3
        HeadstockLayout.INLINE -> count
        HeadstockLayout.FOUR_THREE -> 4
        HeadstockLayout.TWO_PER_SIDE -> count / 2
    }
    val rightCount = count - leftCount
    val inline = instrument.layout == HeadstockLayout.INLINE

    return List(count) { index ->
        if (index < leftCount) {
            // Inline headstocks put the low E nearest the nut, so the column is reversed.
            val slot = if (inline) leftCount - 1 - index else index
            PegSlot(index, Side.LEFT, slot, leftCount)
        } else {
            val onSide = index - leftCount
            PegSlot(index, Side.RIGHT, rightCount - 1 - onSide, rightCount)
        }
    }
}

/**
 * A flat Material You headstock that reshapes itself for the selected instrument.
 *
 * Nothing here is photorealistic. The body is one filled path in a surface container role, the
 * strings are hairlines, and the only saturated colour in the whole component is the peg the
 * tuner is currently listening for.
 *
 * Two things that were wrong the first time and are worth stating:
 *
 * - The strings run the **full height** of the component. They come up the fretboard evenly
 *   spaced, cross the nut, and only then fan out to their posts. Stopping them at the nut made
 *   the instrument look cut in half.
 * - The peg buttons straddle the edge of the body rather than floating beside it, and each
 *   string terminates underneath its own button. A post drawn somewhere in the middle of the
 *   body has no visible relationship to the button that controls it.
 */
@Composable
fun HeadstockView(
    instrument: Instrument,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    onPegSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val slots = remember(instrument) { slotsFor(instrument) }
    val hasLeft = slots.any { it.side == Side.LEFT }
    val hasRight = slots.any { it.side == Side.RIGHT }
    val busiestSide = slots.maxOf { it.slotsOnSide }

    BoxWithConstraints(modifier) {
        val height = maxHeight
        val centreX = maxWidth / 2f

        // Everything below the nut is fretboard. It has to be tall enough to read as a neck
        // rather than a stub, but the pegs need the lion's share.
        val fretboardHeight = (height * 0.22f).coerceIn(48.dp, 110.dp)
        val nutY = height - fretboardHeight
        val topInset = 10.dp

        // A six-in-a-line headstock has twice as many slots down one side as a three-per-side
        // one, so a fixed peg size overlaps. Size the peg to the space it actually has.
        val band = nutY - topInset
        val pegSize = (band / busiestSide * 0.76f).coerceIn(34.dp, 54.dp)

        val bodyHalfWide = 58.dp
        val bodyHalfNarrow = 24.dp
        val leftHalf = if (hasLeft) bodyHalfWide else bodyHalfNarrow
        val rightHalf = if (hasRight) bodyHalfWide else bodyHalfNarrow
        val neckHalf = 30.dp

        fun slotY(slot: PegSlot): Dp =
            topInset + pegSize / 2f +
                (band - pegSize) * (slot.slot.toFloat() / (slot.slotsOnSide - 1).coerceAtLeast(1))

        // Buttons straddle the edge of the body: half on, half off.
        fun pegX(slot: PegSlot): Dp = when (slot.side) {
            Side.LEFT -> centreX - leftHalf
            Side.RIGHT -> centreX + rightHalf
        }

        Canvas(Modifier.fillMaxSize()) {
            drawHeadstockBody(
                centreX = centreX.toPx(),
                leftHalf = leftHalf.toPx(),
                rightHalf = rightHalf.toPx(),
                neckHalf = neckHalf.toPx(),
                top = topInset.toPx() - 6.dp.toPx(),
                nutY = nutY.toPx(),
                bodyColor = colors.surfaceContainerHighest,
                neckColor = colors.surfaceContainerHigh,
            )

            val spacing = if (instrument.stringCount > 1) {
                neckHalf.toPx() * 2f / (instrument.stringCount - 1)
            } else {
                0f
            }

            slots.forEach { slot ->
                val active = slot.physicalIndex == activeIndex
                val tuned = slot.physicalIndex in tunedIndices

                // Even spacing across the fretboard, low string on the left.
                val fretboardX = centreX.toPx() +
                    (slot.physicalIndex - (instrument.stringCount - 1) / 2f) * spacing

                // The string ends under its own button, just inside the body edge.
                val postX = when (slot.side) {
                    Side.LEFT -> centreX.toPx() - leftHalf.toPx() + pegSize.toPx() * 0.18f
                    Side.RIGHT -> centreX.toPx() + rightHalf.toPx() - pegSize.toPx() * 0.18f
                }
                val postY = slotY(slot).toPx()

                val color = when {
                    active -> colors.primary
                    tuned -> colors.tertiary.copy(alpha = 0.7f)
                    else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
                }
                val width = if (active) 3.5f.dp.toPx() else 1.6f.dp.toPx()

                // Fretboard run: dead straight, parallel, off the bottom of the component.
                drawLine(
                    color = color,
                    start = Offset(fretboardX, size.height),
                    end = Offset(fretboardX, nutY.toPx()),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
                // Fan from the nut out to the post.
                drawLine(
                    color = color,
                    start = Offset(fretboardX, nutY.toPx()),
                    end = Offset(postX, postY),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }

            // The nut sits on top of the strings, which is how it looks in the hand.
            drawRoundRect(
                color = colors.outline,
                topLeft = Offset(centreX.toPx() - neckHalf.toPx() - 3.dp.toPx(), nutY.toPx() - 4.dp.toPx()),
                size = Size(neckHalf.toPx() * 2f + 6.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }

        slots.forEach { slot ->
            PegButton(
                label = instrument.strings[slot.physicalIndex].label,
                isActive = slot.physicalIndex == activeIndex,
                isTuned = slot.physicalIndex in tunedIndices,
                onClick = { onPegSelected(slot.physicalIndex) },
                modifier = Modifier
                    .size(pegSize)
                    .offset(
                        x = pegX(slot) - pegSize / 2f,
                        y = slotY(slot) - pegSize / 2f,
                    ),
            )
        }
    }
}

/**
 * One machine head. Active is the loud one; tuned is a quieter confirmation so a finished string
 * still reads as done without competing with the string being worked on.
 */
@Composable
private fun PegButton(
    label: String,
    isActive: Boolean,
    isTuned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    val container by animateColorAsState(
        targetValue = when {
            isActive -> colors.primary
            isTuned -> colors.tertiaryContainer
            // Not a surface role: against a surface-coloured body an inactive peg in
            // surfaceContainerHighest disappears into the headstock it is sitting on.
            else -> colors.secondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pegContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isActive -> colors.onPrimary
            isTuned -> colors.onTertiaryContainer
            else -> colors.onSecondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pegContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.14f else 1f,
        // Bouncy on purpose: the peg the tuner jumps to should feel like it snapped into place.
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pegScale",
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = if (isActive) 8.dp else 2.dp,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/**
 * The body: a wide paddle that tapers into the neck, drawn as one path with corner radii large
 * enough to read as an M3 shape rather than a rounded rectangle.
 */
private fun DrawScope.drawHeadstockBody(
    centreX: Float,
    leftHalf: Float,
    rightHalf: Float,
    neckHalf: Float,
    top: Float,
    nutY: Float,
    bodyColor: Color,
    neckColor: Color,
) {
    // Fretboard first, so the body overlaps it at the nut rather than butting against it.
    drawRect(
        color = neckColor,
        topLeft = Offset(centreX - neckHalf, nutY - 4f),
        size = Size(neckHalf * 2f, size.height - nutY + 4f),
    )

    val corner = 30f * density
    val taperStart = nutY - (nutY - top) * 0.34f

    val body = Path().apply {
        moveTo(centreX - leftHalf + corner, top)
        lineTo(centreX + rightHalf - corner, top)
        quadraticTo(centreX + rightHalf, top, centreX + rightHalf, top + corner)
        lineTo(centreX + rightHalf, taperStart)
        // One smooth shoulder down into the neck. A single cubic keeps it from pinching.
        cubicTo(
            centreX + rightHalf, nutY - (nutY - taperStart) * 0.25f,
            centreX + neckHalf + 6f, nutY - (nutY - taperStart) * 0.30f,
            centreX + neckHalf + 2f, nutY,
        )
        lineTo(centreX - neckHalf - 2f, nutY)
        cubicTo(
            centreX - neckHalf - 6f, nutY - (nutY - taperStart) * 0.30f,
            centreX - leftHalf, nutY - (nutY - taperStart) * 0.25f,
            centreX - leftHalf, taperStart,
        )
        lineTo(centreX - leftHalf, top + corner)
        quadraticTo(centreX - leftHalf, top, centreX - leftHalf + corner, top)
        close()
    }
    drawPath(body, bodyColor)
}
