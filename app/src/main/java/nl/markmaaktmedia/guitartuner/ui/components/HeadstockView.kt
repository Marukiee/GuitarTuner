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
 * The rule is set by the one constraint a real headstock has: **the strings must not cross.**
 *
 * At the nut the low E is the outermost string on the bass side. If it ran to the post at the tip
 * it would have to cut across every string inboard of it, so it goes to the post *nearest* the
 * nut, and the innermost string of that group (the D on a six string) travels furthest, to the
 * tip. With the nut at the bottom of this component that reads D, A, E from the top down. The
 * treble side mirrors it: the high E is outermost, so it takes the nearest post, and the G runs
 * to the tip, giving G, B, E from the top.
 *
 * An inline headstock is the same case with every post on the bass side: low E nearest the nut,
 * high E at the tip.
 *
 * So: the left column is always reversed against string order, the right column never is. An
 * earlier version had both backwards, which any guitarist spots instantly.
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
            PegSlot(index, Side.LEFT, leftCount - 1 - index, leftCount)
        } else {
            PegSlot(index, Side.RIGHT, index - leftCount, rightCount)
        }
    }
}

/**
 * A flat Material You headstock that reshapes itself for the selected instrument.
 *
 * ## What changed, and why the first two attempts looked wrong
 *
 * Everything is now **proportional to the space available** rather than a set of fixed dp values.
 * Fixed sizes are what made the neck look like a drinking straw: a 60dp neck is fine under a
 * 116dp headstock and absurd on a 400dp-wide phone, and it could not adapt when six inline pegs
 * needed twice the vertical room three-per-side pegs do.
 *
 * The peg band also stops well short of the nut. Spreading pegs over the full height put the
 * bottom pair right on top of the nut, where no machine head has ever been, and left the top of
 * the headstock empty. Pegs now live in the upper 74% of the body, which is where they are on a
 * real instrument and reads far better.
 *
 * Strings run the full height of the component: up the fretboard, across the nut, then fanning
 * out to their posts. The peg buttons straddle the body edge so each string visibly terminates
 * under the control that tightens it.
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
    val hasRight = slots.any { it.side == Side.RIGHT }
    val busiestSide = slots.maxOf { it.slotsOnSide }

    BoxWithConstraints(modifier) {
        val height = maxHeight
        val width = maxWidth
        val centreX = width / 2f

        // Proportional geometry. The neck is a real neck: over a third of the body width, which
        // is roughly the ratio on an actual guitar and the thing that was most obviously wrong.
        val bodyHalf = (width * 0.20f).coerceIn(56.dp, 108.dp)
        val narrowHalf = bodyHalf * 0.42f
        val neckHalf = bodyHalf * 0.62f

        val leftHalf = bodyHalf
        val rightHalf = if (hasRight) bodyHalf else narrowHalf

        val fretboardHeight = (height * 0.20f).coerceIn(44.dp, 96.dp)
        val nutY = height - fretboardHeight
        val bodyTop = 8.dp

        // Pegs occupy the upper part of the body only. The gap above the nut is what stops the
        // lowest pair from sitting on the nut itself.
        val bandTop = bodyTop + height * 0.06f
        val bandBottom = nutY - (nutY - bodyTop) * 0.20f
        val band = bandBottom - bandTop

        val pegSize = (band / busiestSide * 0.82f).coerceIn(34.dp, 56.dp)

        fun slotY(slot: PegSlot): Dp =
            if (slot.slotsOnSide <= 1) {
                bandTop + band / 2f
            } else {
                bandTop + band * (slot.slot.toFloat() / (slot.slotsOnSide - 1))
            }

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
                top = bodyTop.toPx(),
                nutY = nutY.toPx(),
                bodyColor = colors.surfaceContainerHighest,
                neckColor = colors.surfaceContainerHigh,
                edgeColor = colors.outlineVariant,
            )

            val spacing = if (instrument.stringCount > 1) {
                (neckHalf.toPx() * 2f * 0.82f) / (instrument.stringCount - 1)
            } else {
                0f
            }

            slots.forEach { slot ->
                val active = slot.physicalIndex == activeIndex
                val tuned = slot.physicalIndex in tunedIndices

                val fretboardX = centreX.toPx() +
                    (slot.physicalIndex - (instrument.stringCount - 1) / 2f) * spacing

                val postX = when (slot.side) {
                    Side.LEFT -> centreX.toPx() - leftHalf.toPx() + pegSize.toPx() * 0.20f
                    Side.RIGHT -> centreX.toPx() + rightHalf.toPx() - pegSize.toPx() * 0.20f
                }
                val postY = slotY(slot).toPx()

                val color = when {
                    active -> colors.primary
                    tuned -> colors.tertiary.copy(alpha = 0.75f)
                    else -> colors.onSurfaceVariant.copy(alpha = 0.42f)
                }
                // Thicker for the lower strings, exactly like a real set. Nearly free, and it is
                // the detail that stops six identical hairlines looking like a wireframe.
                val gauge = 1.3f + (instrument.stringCount - 1 - slot.physicalIndex) * 0.22f
                val strokeWidth = (if (active) gauge + 1.6f else gauge).dp.toPx()

                drawLine(
                    color = color,
                    start = Offset(fretboardX, size.height),
                    end = Offset(fretboardX, nutY.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(fretboardX, nutY.toPx()),
                    end = Offset(postX, postY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            // The nut sits on top of the strings, which is how it looks in the hand.
            drawRoundRect(
                color = colors.outline,
                topLeft = Offset(
                    centreX.toPx() - neckHalf.toPx() - 3.dp.toPx(),
                    nutY.toPx() - 4.5f.dp.toPx(),
                ),
                size = Size(neckHalf.toPx() * 2f + 6.dp.toPx(), 9.dp.toPx()),
                cornerRadius = CornerRadius(4.5f.dp.toPx(), 4.5f.dp.toPx()),
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
 * The body: a broad paddle with a soft square top that narrows into the neck.
 *
 * Drawn top-down as one closed path. The earlier version used control points derived from the
 * corner radius, which pinched the waist into an hourglass on tall layouts; the shoulder is now a
 * single cubic anchored to the body height, so it holds its shape whatever the aspect ratio.
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
    edgeColor: Color,
) {
    // Fretboard first, so the body overlaps it at the nut rather than butting against it.
    drawRect(
        color = neckColor,
        topLeft = Offset(centreX - neckHalf, nutY - 6f),
        size = Size(neckHalf * 2f, size.height - nutY + 6f),
    )

    val bodyHeight = nutY - top
    val corner = minOf(leftHalf, rightHalf) * 0.72f
    // Where the straight sides give way to the shoulder.
    val shoulder = top + bodyHeight * 0.66f

    val body = Path().apply {
        moveTo(centreX - leftHalf + corner, top)
        lineTo(centreX + rightHalf - corner, top)
        quadraticTo(centreX + rightHalf, top, centreX + rightHalf, top + corner)
        lineTo(centreX + rightHalf, shoulder)
        cubicTo(
            centreX + rightHalf, shoulder + bodyHeight * 0.20f,
            centreX + neckHalf + (rightHalf - neckHalf) * 0.30f, nutY - bodyHeight * 0.04f,
            centreX + neckHalf, nutY,
        )
        lineTo(centreX - neckHalf, nutY)
        cubicTo(
            centreX - neckHalf - (leftHalf - neckHalf) * 0.30f, nutY - bodyHeight * 0.04f,
            centreX - leftHalf, shoulder + bodyHeight * 0.20f,
            centreX - leftHalf, shoulder,
        )
        lineTo(centreX - leftHalf, top + corner)
        quadraticTo(centreX - leftHalf, top, centreX - leftHalf + corner, top)
        close()
    }
    drawPath(body, bodyColor)
    // A hairline edge keeps the silhouette legible when the body and the background are close
    // in tone, which happens on some dynamic-colour schemes.
    drawPath(body, edgeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density))
}
