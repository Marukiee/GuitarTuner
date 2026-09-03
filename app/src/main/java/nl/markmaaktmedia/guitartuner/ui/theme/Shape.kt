package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val TunerShapes = Shapes(
    extraSmall = RoundedCornerShape(CornerSize(8.dp)),
    small = RoundedCornerShape(CornerSize(14.dp)),
    medium = RoundedCornerShape(CornerSize(20.dp)),
    large = RoundedCornerShape(CornerSize(28.dp)),
    extraLarge = RoundedCornerShape(CornerSize(36.dp)),
)

/** The pill used for chips, badges and the mode toggle. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * A squircle with continuous corners, the kind where the curve blends into the straight
 * edge instead of meeting it.
 *
 * A plain rounded rectangle has a visible seam at every corner: the arc stops and the
 * line starts, and at the 28dp radius used across this app that seam is easy to see.
 * This draws each corner as a pair of cubics whose control points run along the edges,
 * so curvature ramps up rather than switching on. It is the difference between a card
 * that looks drawn and one that looks moulded.
 *
 * The corner is capped against the shorter side, because past that the control points
 * cross and the shape folds in on itself.
 */
class SquircleShape(private val radius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val maxRadius = minOf(size.width, size.height) * 0.36f
        val r = with(density) { radius.toPx() }.coerceAtMost(maxRadius)
        if (r <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))

        val extend = r * SMOOTHING
        val control = r * CONTROL

        val path = Path().apply {
            moveTo(extend, 0f)
            lineTo(size.width - extend, 0f)
            cubicTo(size.width - control, 0f, size.width, control, size.width, extend)
            lineTo(size.width, size.height - extend)
            cubicTo(
                size.width, size.height - control,
                size.width - control, size.height,
                size.width - extend, size.height,
            )
            lineTo(extend, size.height)
            cubicTo(control, size.height, 0f, size.height - control, 0f, size.height - extend)
            lineTo(0f, extend)
            cubicTo(0f, control, control, 0f, extend, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    private companion object {
        /** Corner reach along the edge. Higher spreads the curve further out. */
        const val SMOOTHING = 1.28f

        /** Control point distance, tuned against the reach so the join stays smooth. */
        const val CONTROL = 0.34f
    }
}

/**
 * The corner treatment for one item inside a grouped list.
 *
 * The group's outer corners stay large and the ones facing a neighbour pull in to almost
 * nothing, so a block of settings reads as a single rounded slab that has been cut
 * rather than as a stack of cards. A lone item keeps all four corners large, which is
 * why the count matters and not only the position.
 */
fun groupedShape(index: Int, total: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        total <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == total - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/** The gap between grouped items, kept small so the group still reads as one block. */
val GroupedSpacing = 2.dp

val CardSquircle = SquircleShape(28.dp)
val SheetSquircle = SquircleShape(36.dp)
val ChipSquircle = SquircleShape(16.dp)
