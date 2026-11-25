package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.components.HeadSpec
import nl.markmaaktmedia.guitartuner.ui.components.nutSlot
import nl.markmaaktmedia.guitartuner.ui.components.pickPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How much of the head is drawn. The board gets all of it; the small picture in the instrument
 * bar and the picker leaves out what would only be noise at 30 dp: the grain, the shadow, the
 * logo and the coils on the posts. Both come from the one drawing below, so the little head can
 * never drift away from the big one.
 */
internal enum class HeadDetail { Full, Icon }

/** The crown and the tuners are spread wide, so the knobs have room beside the head... */
internal const val XSpread = 1.02f

/** ...but the neck and the nut keep their true width, which is what makes the strings fan. */
internal const val NeckPad = 0.03f

/** Every size in the drawing is written in "design dp": 1 on a 300 dp wide head. */
private const val DesignWidth = 300f

/** How far the shaft shows between the edge of the wood and the button. */
private const val ShaftDd = 9f

private const val ButtonWidthDd = 25f

/**
 * Where every part of one head sits, in pixels.
 *
 * [unit] is the size of one design dp. On the board it is the density, and on a narrow phone it
 * shrinks with the head, so the buttons never crowd the bubbles beside them. In the little picture
 * it is whatever makes the head fit, and [pegScale] makes the tuners bigger than they are on a
 * real head, because a symbol at 30 dp that kept true proportions would lose them.
 */
internal class HeadGeometry(
    val spec: HeadSpec,
    val count: Int,
    val headLeft: Float,
    val headWidth: Float,
    val height: Float,
    val unit: Float,
    val pegScale: Float = 1f,
) {
    val centre = headLeft + headWidth / 2f
    val crownHalf = (spec.crownRight - spec.crownLeft) / 2f * XSpread * headWidth
    private val neckHalf = (spec.nutSpread / 2f + NeckPad) * headWidth

    /** Where the nut sits down the picture, which is also where the head ends. */
    val nutY = height * (spec.headBottom + (1f - spec.headBottom) * 0.22f)

    private val shape: HeadShape? = when {
        spec.paddle -> null
        spec.scroll -> HeadShape.pegbox(neckHalf / crownHalf)
        else -> HeadShape.arch(neckHalf / crownHalf)
    }

    fun x(fraction: Float) = headLeft + ((fraction - 0.5f) * XSpread + 0.5f) * headWidth
    fun y(fraction: Float) = fraction * height
    private fun neckX(fraction: Float) = headLeft + fraction * headWidth

    val neckLeftX = neckX(0.5f - (spec.nutSpread / 2f + NeckPad))
    val neckRightX = neckX(0.5f + (spec.nutSpread / 2f + NeckPad))

    val head: Path = shape?.path(centre, crownHalf, 0f, nutY) ?: paddlePath()
    val bounds = head.getBounds()

    val hasDrone = spec.dronePeg != null
    fun isDrone(index: Int) = hasDrone && index == 0

    val pegs: List<Offset>
    val slots: List<Float>

    init {
        // A banjo's drone is string zero and sits off the neck; every other string takes the peg
        // at its own position in nut order.
        val drone = spec.dronePeg
        val order = if (drone != null) listOf(drone) + spec.pegs else spec.pegs
        pegs = order.take(count).map { Offset(x(it.x), y(it.y)) }
        slots = (0 until pegs.size).map { i ->
            if (spec.dronePeg != null && i == 0) pegs[0].x
            else headLeft + nutSlot(if (spec.dronePeg != null) i - 1 else i, spec.pegs.size, spec) * headWidth
        }
    }

    /** The buttons are as tall as the room between neighbouring tuners allows, within reason. */
    val buttonHeight: Float
    val buttonWidth = ButtonWidthDd * unit * pegScale

    /** Where each tuner button is centred: just outside the wood, level with its own post. */
    val knobs: List<Offset>

    init {
        var gap = Float.MAX_VALUE
        for (side in listOf(-1f, 1f)) {
            val column = pegs.filter { (if (it.x < centre) -1f else 1f) == side }.map { it.y }.sorted()
            for (i in 1 until column.size) gap = min(gap, column[i] - column[i - 1])
        }
        if (gap == Float.MAX_VALUE) gap = 80f * unit
        // Never taller than the gap between tuners allows, or a row of six down one edge (a
        // Fender paddle) piles its buttons on top of each other.
        val lowest = min(24f * unit * pegScale, gap * 0.92f)
        buttonHeight = (gap * 0.66f).coerceIn(lowest, max(lowest, 38f * unit * pegScale))
        knobs = pegs.map { p ->
            val side = if (p.x < centre) -1f else 1f
            Offset(edgeX(p.y, side) + side * (ShaftDd * unit + buttonWidth / 2f), p.y)
        }
    }

    /** The x of the wood's edge on [side] (-1 left, 1 right) at height [yPx]. */
    fun edgeX(yPx: Float, side: Float): Float {
        val v = (yPx / nutY).coerceIn(0f, 1f)
        if (shape != null) return centre + side * shape.edge(v) * crownHalf
        // The paddle only has tuners on its left, so only its left edge needs to be known.
        val l = x(spec.crownLeft)
        val t = ((v - 0.55f) / 0.45f).coerceIn(0f, 1f)
        return l + (neckLeftX - l) * (t * t * (3f - 2f * t))
    }

    /** The left and right extent of everything drawn, buttons included. */
    val extentLeft: Float
        get() = min(bounds.left, knobs.minOfOrNull { it.x - buttonWidth / 2f - 3f * unit } ?: bounds.left)
    val extentRight: Float
        get() = max(bounds.right, knobs.maxOfOrNull { it.x + buttonWidth / 2f + 3f * unit } ?: bounds.right)

    /** A Fender paddle: asymmetric, with every tuner along its bass edge. */
    private fun paddlePath(): Path {
        val bottom = nutY
        val cy = y(spec.crownY)
        val l = x(spec.crownLeft)
        val r = x(spec.crownRight)
        val wl = x(spec.waistLeft)
        val wr = x(spec.waistRight)
        return Path().apply {
            moveTo(l, cy)
            cubicTo(l, 0f, r, 0f, r, cy)
            cubicTo(r, bottom * 0.55f, wr, bottom * 0.86f, neckRightX, bottom)
            lineTo(neckLeftX, bottom)
            cubicTo(wl, bottom * 0.86f, l, bottom * 0.55f, l, cy)
            close()
        }
    }
}

/**
 * Draws a head: the neck under the nut, the wood with its binding, the nut, the strings, and the
 * tuners with their posts and buttons.
 *
 * Light comes from the top left everywhere, so every button, post and the sheen on the wood agree
 * with each other. The string being tuned, and the ones already done, light their own button, so
 * "which peg do I turn" is answered by colour and not by counting.
 */
internal fun DrawScope.drawHeadArt(
    g: HeadGeometry,
    wood: Wood,
    detail: HeadDetail,
    activeIndex: Int = -1,
    tunedIndices: Set<Int> = emptySet(),
    active: Color = Color.Unspecified,
    tuned: Color = Color.Unspecified,
) {
    val full = detail == HeadDetail.Full
    val u = g.unit
    val spec = g.spec
    val hairline = 0.8.dp.toPx()

    /** A length in design dp, never thinner than [floor] pixels so a small head keeps its lines. */
    fun px(dd: Float, floor: Float = 0f) = max(dd * u, floor)

    val b = g.bounds
    val nutY = g.nutY
    val neckSpan = g.height - nutY

    // The neck under the nut: dark, with frets and one inlay, because a plain dark bar reads as
    // a gap in the picture and not as part of the instrument.
    val neck = Path().apply {
        addRoundRect(
            RoundRect(
                left = g.neckLeftX, top = nutY, right = g.neckRightX, bottom = g.height + 12f * u,
                topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius(6f * u), bottomRightCornerRadius = CornerRadius(6f * u),
            ),
        )
    }
    drawPath(neck, Brush.verticalGradient(listOf(wood.fretboardTop, wood.fretboardBottom), startY = nutY, endY = g.height))
    if (full) {
        for (fret in listOf(0.27f, 0.52f, 0.74f, 0.92f)) {
            val fy = nutY + neckSpan * fret
            drawLine(Color(0xFFC9CDD2).copy(alpha = 0.42f), Offset(g.neckLeftX, fy), Offset(g.neckRightX, fy), 1.6f * u)
        }
        drawCircle(wood.binding.copy(alpha = 0.55f), 3.2f * u, Offset((g.neckLeftX + g.neckRightX) / 2f, nutY + neckSpan * 0.395f))
    }

    // A soft shadow under the head, so it lifts off the page instead of lying flat on it. Stacked
    // strokes of falling strength stand in for a blur, which the canvas cannot do on every phone.
    if (full) {
        translate(0f, 5f * u) {
            for (i in 1..6) {
                drawPath(g.head, Color.Black.copy(alpha = 0.045f), style = Stroke(width = i * 2.6f * u, join = StrokeJoin.Round))
            }
            drawPath(g.head, Color.Black.copy(alpha = 0.10f))
        }
    }

    // The wood: a gradient down, the grain, darker edges so it reads as a rounded slab, and a
    // sheen from the top left.
    drawPath(g.head, Brush.verticalGradient(listOf(wood.top, wood.bottom), startY = b.top, endY = nutY))
    clipPath(g.head) {
        if (full) {
            val random = kotlin.random.Random(11)
            val lines = 15
            for (i in 0 until lines) {
                val gx = b.left + b.width * (i + random.nextFloat() * 0.8f) / lines
                val drift = (random.nextFloat() - 0.5f) * 14f * u
                val grain = Path().apply {
                    moveTo(gx, b.top)
                    cubicTo(gx + drift, b.top + b.height * 0.33f, gx - drift, b.top + b.height * 0.66f, gx + drift * 0.4f, nutY)
                }
                val dark = i % 3 != 0
                drawPath(
                    grain,
                    if (dark) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.07f),
                    style = Stroke(width = (0.8f + random.nextFloat() * 1.6f) * u),
                )
            }
        }
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.24f),
                0.24f to Color.Transparent,
                0.76f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.24f),
                startX = b.left,
                endX = b.right,
            ),
            topLeft = b.topLeft,
            size = b.size,
        )
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                start = Offset(b.left, b.top),
                end = Offset(b.center.x, b.top + b.height * 0.55f),
            ),
            topLeft = b.topLeft,
            size = b.size,
        )
        if (full) {
            // The lacquer catching the light: a broad soft spot high on the left.
            drawRect(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(b.left + b.width * 0.30f, b.top + b.height * 0.14f),
                    radius = b.width * 0.62f,
                ),
                topLeft = b.topLeft,
                size = b.size,
            )
        }
    }

    // The binding: a dark line round the outside, the cream band, and a pinstripe inside it.
    val band = px(3.4f, if (full) 0f else 1.1f.dp.toPx())
    drawPath(g.head, Color.Black.copy(alpha = 0.55f), style = Stroke(width = px(5.4f, 2f.dp.toPx()), join = StrokeJoin.Round))
    clipPath(g.head) {
        if (full) drawPath(g.head, Color.Black.copy(alpha = 0.50f), style = Stroke(width = 2f * (band + 1.1f * u), join = StrokeJoin.Round))
        drawPath(g.head, wood.binding, style = Stroke(width = 2f * band, join = StrokeJoin.Round))
    }

    // A bowed instrument's scroll curls over the top of the pegbox; every other head gets the
    // gold pick where a guitar has its maker's mark.
    if (spec.scroll) {
        drawScroll(g, wood, full)
    } else if (full) {
        val markAt = Offset(g.centre, nutY * 0.115f)
        translate(markAt.x, markAt.y) {
            val mark = pickPath(30f * u)
            drawPath(mark, Brush.linearGradient(listOf(Color(0xFFF1D58A), Color(0xFFB98A2E)), start = Offset(-15f * u, -12f * u), end = Offset(15f * u, 14f * u)))
            drawPath(mark, Color.Black.copy(alpha = 0.45f), style = Stroke(width = 1.1f * u, join = StrokeJoin.Round))
        }
    }

    // The nut: a thick bone band with the slots the strings sit in.
    if (full) {
        val nutTop = nutY - 3.4f * u
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFAF1DC), Color(0xFFD9CBA6)), startY = nutTop, endY = nutTop + 6.8f * u),
            topLeft = Offset(g.neckLeftX - 1.5f * u, nutTop),
            size = Size(g.neckRightX - g.neckLeftX + 3f * u, 6.8f * u),
            cornerRadius = CornerRadius(1.8f * u),
        )
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(g.neckLeftX - 1.5f * u, nutTop + 6.8f * u), Offset(g.neckRightX + 1.5f * u, nutTop + 6.8f * u), 1f * u)
        for (i in 0 until g.pegs.size) {
            if (g.isDrone(i)) continue
            drawLine(Color.Black.copy(alpha = 0.40f), Offset(g.slots[i], nutTop + 1.2f * u), Offset(g.slots[i], nutTop + 4.6f * u), 1.5f * u, StrokeCap.Round)
        }
    } else {
        drawLine(Color(0xFFF2E8D2), Offset(g.neckLeftX, nutY), Offset(g.neckRightX, nutY), px(4f, 1.6f.dp.toPx()), StrokeCap.Butt)
    }

    // Strings: straight down the neck from the nut, and from the nut out to their post. The wound
    // bass strings are bronze and thick, the plain ones nickel and thin.
    val count = g.pegs.size
    for (i in 0 until count) {
        val peg = g.pegs[i]
        val isActive = i == activeIndex
        val isTuned = i in tunedIndices
        val wound = i < (count + 1) / 2
        val base = if (wound) Color(0xFFD9B98A) else Color(0xFFDCE0E5)
        val colour = when {
            isActive -> active
            isTuned -> lerp(base, tuned, 0.55f)
            full -> base.copy(alpha = 0.82f)
            else -> base.copy(alpha = 0.78f)
        }
        val thickness = px(2.5f - 1.4f * i / (count - 1).coerceAtLeast(1), hairline)
        val slot = g.slots[i]
        if (g.isDrone(i)) {
            drawLine(colour, peg, Offset(peg.x, g.height), thickness, StrokeCap.Round)
        } else {
            // The part below the nut is drawn by the vibrating layer for the active string.
            if (!(full && isActive)) drawLine(colour, Offset(slot, g.height), Offset(slot, nutY), thickness, StrokeCap.Round)
            drawLine(colour, Offset(slot, nutY), peg, thickness, StrokeCap.Round)
        }
    }

    // The tuners.
    for (i in 0 until count) {
        val peg = g.pegs[i]
        val knob = g.knobs[i]
        val side = if (peg.x < g.centre) -1f else 1f
        val halo = when {
            i == activeIndex -> active
            i in tunedIndices -> tuned
            else -> null
        }
        val edge = g.edgeX(peg.y, side)

        if (full) {
            // The shaft comes out of the side of the head, behind the button, with the gear
            // housing where it leaves the wood.
            val shaftTop = peg.y - 2.6f * u
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFF1F3F5), Color(0xFF8A9199), Color(0xFFD5D9DE)), startY = shaftTop, endY = shaftTop + 5.2f * u),
                topLeft = Offset(min(edge, knob.x), shaftTop),
                size = Size(kotlin.math.abs(knob.x - edge), 5.2f * u),
            )
            val houseH = g.buttonHeight * 0.52f
            val houseW = 8f * u
            val houseLeft = if (side < 0f) edge - houseW * 0.7f else edge - houseW * 0.3f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFC9CED4), Color(0xFF6E757D)), startY = peg.y - houseH / 2f, endY = peg.y + houseH / 2f),
                topLeft = Offset(houseLeft, peg.y - houseH / 2f),
                size = Size(houseW, houseH),
                cornerRadius = CornerRadius(2.4f * u),
            )
            drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(houseLeft, peg.y - houseH / 2f), Size(houseW, houseH), CornerRadius(2.4f * u), style = Stroke(1f * u))
        } else {
            drawLine(Color(0xFFBFC4CA), Offset(edge, peg.y), Offset(knob.x, peg.y), px(5f, hairline), StrokeCap.Butt)
        }

        // The post the string winds round: a chrome washer, a brass post, and the coils.
        val postR = px(11f, 1.5f.dp.toPx()) * (if (full) 1f else min(g.pegScale, 1.3f))
        drawCircle(Color.Black.copy(alpha = 0.45f), postR + px(1.2f), peg)
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFFF8FAFB), Color(0xFF9AA1A9)), start = Offset(peg.x - postR, peg.y - postR), end = Offset(peg.x + postR, peg.y + postR)),
            radius = postR,
            center = peg,
        )
        if (full) {
            drawCircle(Color(0xFF7A828B), postR * 0.62f, peg)
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0xFFF3DC9C), Color(0xFFA7741F)), start = Offset(peg.x - postR * 0.4f, peg.y - postR * 0.4f), end = Offset(peg.x + postR * 0.4f, peg.y + postR * 0.4f)),
                radius = postR * 0.42f,
                center = peg,
            )
            // The slot in the top of the post, and the string wound round it three times.
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(peg.x - postR * 0.3f, peg.y), Offset(peg.x + postR * 0.3f, peg.y), 1.1f * u)
            val coil = if (i < (count + 1) / 2) Color(0xFFD9B98A) else Color(0xFFDCE0E5)
            for (k in -1..1) {
                drawOval(
                    color = coil.copy(alpha = 0.95f),
                    topLeft = Offset(peg.x - postR * 0.62f, peg.y + k * postR * 0.30f - postR * 0.13f),
                    size = Size(postR * 1.24f, postR * 0.30f),
                    style = Stroke(1.2f * u),
                )
            }
        }

        // The button.
        val button = buttonPath(knob, side, g.buttonWidth, g.buttonHeight)
        if (full) {
            translate(1.8f * u, 3f * u) { drawPath(button, Color.Black.copy(alpha = 0.26f)) }
        }
        if (halo != null) {
            drawPath(button, halo.copy(alpha = 0.95f), style = Stroke(width = px(6.4f, 2f.dp.toPx()), join = StrokeJoin.Round))
        }
        val left = knob.x - g.buttonWidth / 2f
        val right = knob.x + g.buttonWidth / 2f
        val top = knob.y - g.buttonHeight / 2f
        val bottom = knob.y + g.buttonHeight / 2f
        val colours = if (wood.ebonyPegs) {
            listOf(Color(0xFF7A6050), Color(0xFF3A271D), Color(0xFF241812), Color(0xFF120B08))
        } else {
            listOf(Color(0xFFA2A9B1), Color(0xFFFFFFFF), Color(0xFFCDD2D8), Color(0xFF7A818A))
        }
        drawPath(
            button,
            Brush.horizontalGradient(
                0f to colours[0], 0.22f to colours[1], 0.52f to colours[2], 1f to colours[3],
                startX = left, endX = right,
            ),
        )
        // Light from above on the upper half, shade on the lower, and a thin bright rim: the
        // difference between a metal button and a grey shape.
        drawPath(
            button,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (wood.ebonyPegs) 0.22f else 0.50f),
                0.42f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.22f),
                startY = top, endY = bottom,
            ),
        )
        drawPath(button, Color.Black.copy(alpha = 0.42f), style = Stroke(width = px(1f, hairline), join = StrokeJoin.Round))
        if (full) {
            drawOval(
                color = Color.White.copy(alpha = if (wood.ebonyPegs) 0.30f else 0.75f),
                topLeft = Offset(left + g.buttonWidth * 0.20f, top + g.buttonHeight * 0.14f),
                size = Size(g.buttonWidth * 0.30f, g.buttonHeight * 0.16f),
            )
        }
    }
}

/**
 * The outline of a tuner button: wide and flat on the far end, tapering in to where the shaft
 * meets it, the shape on a real acoustic. [side] is the side of the head it stands on.
 */
private fun buttonPath(centre: Offset, side: Float, width: Float, height: Float): Path {
    // In units of half the button, x running from the far end (-1) towards the head (+1).
    val outline = listOf(
        Offset(1.0f, -0.36f), Offset(0.34f, -0.96f), Offset(-0.78f, -1.0f), Offset(-1.0f, -0.76f),
        Offset(-1.0f, 0.76f), Offset(-0.78f, 1.0f), Offset(0.34f, 0.96f), Offset(1.0f, 0.36f),
    )
    val towardHead = -side
    val points = outline.map { Offset(centre.x + it.x * towardHead * width / 2f, centre.y + it.y * height / 2f) }
    return roundedPolygon(points, min(width, height) * 0.24f)
}

/** A polygon with its corners rounded off by [radius], each as one quadratic curve. */
private fun roundedPolygon(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    fun toward(from: Offset, to: Offset, distance: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy)
        val t = min(distance, length / 2f) / length
        return Offset(from.x + dx * t, from.y + dy * t)
    }
    for (i in 0 until n) {
        val previous = points[(i + n - 1) % n]
        val here = points[i]
        val next = points[(i + 1) % n]
        val entry = toward(here, previous, radius)
        val exit = toward(here, next, radius)
        if (i == 0) path.moveTo(entry.x, entry.y) else path.lineTo(entry.x, entry.y)
        path.quadraticTo(here.x, here.y, exit.x, exit.y)
    }
    path.close()
    return path
}

/** A violin's scroll: the carved head above the pegbox, with its spiral and its eye. */
private fun DrawScope.drawScroll(g: HeadGeometry, wood: Wood, full: Boolean) {
    val u = g.unit
    val w = g.headWidth
    val centre = Offset(g.x(0.5f), g.y(0.1f))
    val radius = w * 0.17f
    val band = max(3.4f * u, if (full) 0f else 1.1.dp.toPx())
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)) }
    drawCircle(Brush.verticalGradient(listOf(wood.top, wood.bottom), startY = centre.y - radius, endY = centre.y + radius), radius, centre)
    clipPath(disc) {
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                start = Offset(centre.x - radius, centre.y - radius),
                end = Offset(centre.x, centre.y + radius * 0.2f),
            ),
            radius = radius,
            center = centre,
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.55f), radius, centre, style = Stroke(5.4f * u))
    drawCircle(wood.binding, radius - band / 2f, centre, style = Stroke(band))
    // The spiral winds in from the rim to the eye, a turn and a half.
    val spiral = Path()
    val steps = 70
    for (s in 0..steps) {
        val t = s / steps.toFloat()
        val angle = (PI * 2.0 * 1.55 * t).toFloat() - 0.6f
        val r = radius * 0.74f * (1f - 0.86f * t)
        val p = Offset(centre.x + cos(angle) * r, centre.y + sin(angle) * r)
        if (s == 0) spiral.moveTo(p.x, p.y) else spiral.lineTo(p.x, p.y)
    }
    drawPath(spiral, Color.Black.copy(alpha = 0.55f), style = Stroke(width = max(1.9f * u, 0.7.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color.Black.copy(alpha = 0.45f), radius * 0.10f, centre)
}
