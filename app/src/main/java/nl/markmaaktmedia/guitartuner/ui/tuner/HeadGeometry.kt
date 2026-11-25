package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.ui.geometry.Offset
import nl.markmaaktmedia.guitartuner.ui.components.HeadSpec
import nl.markmaaktmedia.guitartuner.ui.components.nutSlot
import kotlin.math.min

/** The crown and the tuners are spread a touch wider than the layout says... */
private const val XSpread = 1.02f

/** ...but the neck and the nut keep their true width, which is what makes the strings fan. */
private const val NeckPad = 0.03f

/**
 * Where every part of one head sits on the board, in pixels.
 *
 * [unit] is the size of one design dp: the density on a full size head, and less on a narrow
 * phone, so the tuners never crowd the buttons beside them.
 */
internal class HeadGeometry(
    val spec: HeadSpec,
    val count: Int,
    private val headLeft: Float,
    private val headWidth: Float,
    private val height: Float,
    val unit: Float,
) {
    val centre = headLeft + headWidth / 2f

    /** Half the width of the head at its widest. */
    val crownHalf = (spec.crownRight - spec.crownLeft) / 2f * XSpread * headWidth

    /** Where the nut sits down the picture, which is also where the head ends. */
    val nutY = height * (spec.headBottom + (1f - spec.headBottom) * 0.22f)

    val neckHalf = (spec.nutSpread / 2f + NeckPad) * headWidth

    private val hasDrone = spec.dronePeg != null

    /** A banjo's drone is string zero, sits off the neck and never reaches the nut. */
    fun isDrone(index: Int) = hasDrone && index == 0

    /** The post of each string's tuner, in string order. */
    val pegs: List<Offset>

    /** Where each string crosses the nut, or for a drone, where it leaves its tuner. */
    val slots: List<Float>

    /** How tall a tuner can be before it runs into its neighbour. */
    val knobHeight: Float

    init {
        val drone = spec.dronePeg
        val order = if (drone != null) listOf(drone) + spec.pegs else spec.pegs
        pegs = order.take(count).map {
            Offset(headLeft + ((it.x - 0.5f) * XSpread + 0.5f) * headWidth, it.y * height)
        }
        slots = pegs.indices.map { i ->
            if (isDrone(i)) pegs[0].x
            else headLeft + nutSlot(if (hasDrone) i - 1 else i, spec.pegs.size, spec) * headWidth
        }

        var gap = Float.MAX_VALUE
        for (leftSide in listOf(true, false)) {
            val column = pegs.filter { (it.x < centre) == leftSide }.map { it.y }.sorted()
            for (i in 1 until column.size) gap = min(gap, column[i] - column[i - 1])
        }
        if (gap == Float.MAX_VALUE) gap = 80f * unit
        knobHeight = min(gap * 0.82f, 40f * unit)
    }
}
