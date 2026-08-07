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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
 * Splits the strings over the two sides of the headstock according to the instrument's layout.
 *
 * Strings are dealt out in physical order, which puts the lowest sounding strings on the first
 * side. That is how a real headstock is strung, and it means the peg the user reaches for is the
 * one nearest the string they just played.
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

    return List(count) { index ->
        if (index < leftCount) {
            PegSlot(index, Side.LEFT, index, leftCount)
        } else {
            // The right-hand side reads top to bottom as well, so the highest string ends up
            // furthest from the nut rather than nearest it.
            PegSlot(index, Side.RIGHT, index - leftCount, rightCount)
        }
    }
}

/**
 * A flat, Material You headstock that reshapes itself for the selected instrument.
 *
 * Nothing here is photorealistic. The body is a single filled path in a surface container role,
 * the strings are hairlines, and the only saturated colour in the whole component is the peg the
 * tuner is currently listening for. That keeps the eye on the one thing that matters while the
 * user is turning a machine head.
 *
 * The silhouette is deliberately asymmetric when all the pegs are on one side, because a Fender
 * style inline headstock with a symmetric body looks like a mistake rather than a choice.
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

    BoxWithConstraints(modifier) {
        val density = LocalDensityOf()
        val width = maxWidth
        val height = maxHeight

        val pegSize = 52.dp
        val bodyHalfWide = 62.dp
        val bodyHalfNarrow = 30.dp
        val leftHalf = if (hasLeft) bodyHalfWide else bodyHalfNarrow
        val rightHalf = if (hasRight) bodyHalfWide else bodyHalfNarrow

        // Vertical band the pegs are spread over, leaving room for the nut at the bottom.
        val topInset = 16.dp
        val bottomInset = 74.dp
        val bandHeight = height - topInset - bottomInset

        val centreX = width / 2f

        fun slotY(slot: PegSlot): Dp =
            topInset + bandHeight * ((slot.slot + 0.5f) / slot.slotsOnSide)

        fun pegX(slot: PegSlot): Dp = when (slot.side) {
            Side.LEFT -> centreX - leftHalf - pegSize / 2f - 10.dp
            Side.RIGHT -> centreX + rightHalf + pegSize / 2f + 10.dp
        }

        Canvas(Modifier.fillMaxSize()) {
            drawHeadstock(
                density = density,
                centreX = centreX.toPx(),
                leftHalf = leftHalf.toPx(),
                rightHalf = rightHalf.toPx(),
                topInset = topInset.toPx(),
                bottomInset = bottomInset.toPx(),
                bodyColor = colors.surfaceContainerHigh,
                neckColor = colors.surfaceContainerHighest,
                nutColor = colors.outlineVariant,
            )

            val nutY = size.height - bottomInset.toPx() + 12.dp.toPx()
            val neckHalf = 34.dp.toPx()

            slots.forEach { slot ->
                val postX = when (slot.side) {
                    Side.LEFT -> centreX.toPx() - leftHalf.toPx() * 0.45f
                    Side.RIGHT -> centreX.toPx() + rightHalf.toPx() * 0.45f
                }
                val postY = slotY(slot).toPx()

                // Strings fan from evenly spaced nut slots out to the posts.
                val nutX = centreX.toPx() +
                    (slot.physicalIndex - (instrument.stringCount - 1) / 2f) *
                    (neckHalf * 2f / instrument.stringCount)

                val active = slot.physicalIndex == activeIndex
                drawLine(
                    color = if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f),
                    start = Offset(nutX, nutY),
                    end = Offset(postX, postY),
                    strokeWidth = if (active) 3f.dp.toPx() else 1.5f.dp.toPx(),
                )
                drawCircle(
                    color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                    radius = 5.dp.toPx(),
                    center = Offset(postX, postY),
                )
            }
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
            else -> colors.surfaceContainerHighest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pegContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isActive -> colors.onPrimary
            isTuned -> colors.onTertiaryContainer
            else -> colors.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pegContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.12f else 1f,
        // Bouncy on purpose: the peg the tuner jumps to should feel like it snapped into place.
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pegScale",
    )

    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = if (isActive) 6.dp else 1.dp,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/**
 * The body is one path: a wide organic paddle at the top narrowing into the neck, with corner
 * radii large enough to read as an M3 shape rather than a rectangle with rounded corners.
 */
private fun DrawScope.drawHeadstock(
    density: Density,
    centreX: Float,
    leftHalf: Float,
    rightHalf: Float,
    topInset: Float,
    bottomInset: Float,
    bodyColor: Color,
    neckColor: Color,
    nutColor: Color,
) = with(density) {
    val top = topInset - 8.dp.toPx()
    val bottom = size.height - bottomInset + 12.dp.toPx()
    val neckHalf = 34.dp.toPx()
    val corner = 34.dp.toPx()

    val body = Path().apply {
        moveTo(centreX - leftHalf + corner, top)
        lineTo(centreX + rightHalf - corner, top)
        quadraticTo(centreX + rightHalf, top, centreX + rightHalf, top + corner)
        // Shoulder curving in towards the nut.
        cubicTo(
            centreX + rightHalf, bottom - corner * 1.6f,
            centreX + neckHalf, bottom - corner * 1.2f,
            centreX + neckHalf, bottom,
        )
        lineTo(centreX - neckHalf, bottom)
        cubicTo(
            centreX - neckHalf, bottom - corner * 1.2f,
            centreX - leftHalf, bottom - corner * 1.6f,
            centreX - leftHalf, top + corner,
        )
        quadraticTo(centreX - leftHalf, top, centreX - leftHalf + corner, top)
        close()
    }
    drawPath(body, bodyColor)

    // Neck stub running off the bottom of the component, so the headstock does not float.
    drawRect(
        color = neckColor,
        topLeft = Offset(centreX - neckHalf, bottom),
        size = androidx.compose.ui.geometry.Size(neckHalf * 2f, size.height - bottom),
    )

    // The nut itself.
    drawRoundRectCompat(
        color = nutColor,
        rect = Rect(
            left = centreX - neckHalf - 2.dp.toPx(),
            top = bottom - 5.dp.toPx(),
            right = centreX + neckHalf + 2.dp.toPx(),
            bottom = bottom + 5.dp.toPx(),
        ),
        radius = 5.dp.toPx(),
    )
}

private fun DrawScope.drawRoundRectCompat(color: Color, rect: Rect, radius: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left, rect.top),
        size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}

@Composable
private fun LocalDensityOf(): Density = androidx.compose.ui.platform.LocalDensity.current
