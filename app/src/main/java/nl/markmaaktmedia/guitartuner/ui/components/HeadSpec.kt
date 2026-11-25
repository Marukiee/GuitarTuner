package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.ui.geometry.Offset
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockLayout

/**
 * A head, as the few numbers it takes to draw one.
 *
 * [pegs] is ordered **by nut slot**, not by position on the head. String one leaves the
 * leftmost slot at the nut and climbs to `pegs[0]`, string two to `pegs[1]`, and so on.
 *
 * Which peg that is follows one rule, and it is the rule the whole drawing rests on:
 *
 *   **the outermost string at the nut takes the peg nearest the nut.**
 *
 * Outer strings turn off almost immediately, which leaves the middle of the head clear for
 * the inner strings to run straight up to the far pegs between them. Do it the other way
 * round, sending the outer string to the far peg, and the inner strings have to cut back
 * out across it to reach the near pegs: every head then draws a cobweb. This is not a
 * stylistic choice, it is the only assignment with no crossings, and it holds for a column
 * of two, of three, or for six in a line down one edge.
 */
internal data class HeadSpec(
    /** The widest the head gets, as x fractions of the picture. */
    val crownLeft: Float,
    val crownRight: Float,
    /** Where the head ends and the neck begins, as a fraction of the height. */
    val headBottom: Float,
    /** Peg positions as (x, y) fractions, in nut slot order. */
    val pegs: List<Offset>,
    /** How wide the strings sit at the nut, as a fraction of the width. */
    val nutSpread: Float,
    /** Strings sit in tight pairs at the nut, the way a mandolin's courses do. */
    val paired: Boolean = false,
    val scroll: Boolean = false,
    /**
     * A tuner partway down the neck whose string never reaches the nut. A banjo's drone
     * is the only one, and running its string to the nut like the others is precisely
     * what makes a banjo stop looking like a banjo.
     */
    val dronePeg: Offset? = null,
)

/**
 * Where a peg column sits, given how wide the strings are at the nut.
 *
 * Lined up with the outermost nut slot, so the outer string leaves the nut straight up and
 * turns off to its peg over the shortest run on the head.
 */
internal fun pegEdge(nutSpread: Float): Float = 0.5f + nutSpread / 2f

internal fun specFor(layout: HeadstockLayout, stringCount: Int): HeadSpec = when (layout) {
    // One head for six and seven strings. An inline head is drawn with its tuners split over both
    // sides too: six buttons down one edge would stand taller than the head, and split is how the
    // player sees them from behind the neck.
    HeadstockLayout.THREE_PER_SIDE, HeadstockLayout.FOUR_THREE, HeadstockLayout.INLINE -> {
        val nut = 0.34f
        HeadSpec(
            crownLeft = 0.22f, crownRight = 0.78f,
            headBottom = 0.74f,
            pegs = sidePegs(
                stringCount - stringCount / 2, stringCount / 2,
                1f - pegEdge(nut), pegEdge(nut), 0.15f, 0.60f,
            ),
            nutSpread = nut,
        )
    }

    HeadstockLayout.TWO_PER_SIDE -> {
        // Short and square: nothing like the long flare of a six string head.
        val nut = 0.34f
        HeadSpec(
            crownLeft = 0.24f, crownRight = 0.76f,
            headBottom = 0.54f,
            pegs = sidePegs(
                stringCount - stringCount / 2, stringCount / 2,
                1f - pegEdge(nut), pegEdge(nut), 0.16f, 0.38f,
            ),
            nutSpread = nut,
        )
    }

    HeadstockLayout.PAIRED_FOUR -> {
        // A mandolin is eight strings in four courses: four tuners a side in two tight
        // pairs each, and eight slots at the nut that pair up the same way.
        val perSide = stringCount / 2
        val nut = 0.36f
        HeadSpec(
            crownLeft = 0.23f, crownRight = 0.77f,
            headBottom = 0.70f,
            pegs = pairedColumn(perSide, 1f - pegEdge(nut), 0.16f, 0.56f, downwards = false) +
                pairedColumn(perSide, pegEdge(nut), 0.16f, 0.56f, downwards = true),
            nutSpread = nut,
            paired = true,
        )
    }

    HeadstockLayout.BANJO -> {
        val nut = 0.32f
        HeadSpec(
            crownLeft = 0.22f, crownRight = 0.78f,
            headBottom = 0.50f,
            pegs = sidePegs(2, 2, 1f - pegEdge(nut), pegEdge(nut), 0.15f, 0.35f),
            nutSpread = nut,
            // The drone tuner sits partway down the neck, off to the treble side, and its
            // string never reaches the nut, so it is outside the no-crossing rule entirely.
            dronePeg = Offset(0.64f, 0.78f),
        )
    }

    HeadstockLayout.SCROLL -> {
        // A pegbox: narrow and straight sided, with the scroll curled over the top.
        val nut = 0.24f
        HeadSpec(
            crownLeft = 0.34f, crownRight = 0.66f,
            headBottom = 0.80f,
            pegs = listOf(
                Offset(1f - pegEdge(nut), 0.66f), Offset(1f - pegEdge(nut), 0.44f),
                Offset(pegEdge(nut), 0.44f), Offset(pegEdge(nut), 0.66f),
            ).take(stringCount),
            nutSpread = nut,
            scroll = true,
        )
    }
}

/**
 * Two columns of tuners, listed in nut slot order.
 *
 * The bass side runs bottom to top and the treble side top to bottom, which sounds backwards
 * until you follow one string: the leftmost slot is the outermost string on the bass side, and
 * by the rule in [HeadSpec.pegs] it takes the *lowest* peg, so the bass column has to be listed
 * starting at the bottom. The treble side is the mirror of that.
 */
private fun sidePegs(
    left: Int,
    right: Int,
    leftX: Float,
    rightX: Float,
    top: Float,
    bottom: Float,
): List<Offset> {
    fun column(count: Int, x: Float, downwards: Boolean) = List(count) { index ->
        val step = if (downwards) index else count - 1 - index
        val t = if (count == 1) 0.5f else step / (count - 1f)
        Offset(x, top + t * (bottom - top))
    }
    return column(left, leftX, downwards = false) + column(right, rightX, downwards = true)
}

/** One column of tuners grouped into pairs, for a course strung instrument. */
private fun pairedColumn(
    count: Int,
    x: Float,
    top: Float,
    bottom: Float,
    downwards: Boolean,
): List<Offset> {
    val pairs = (count / 2).coerceAtLeast(1)
    val gap = (bottom - top) * 0.18f
    val positions = List(count) { index ->
        val t = if (pairs == 1) 0.5f else (index / 2) / (pairs - 1f)
        top + t * (bottom - top - gap) + (index % 2) * gap
    }
    return List(count) { index ->
        Offset(x, positions[if (downwards) index else count - 1 - index])
    }
}

/** Where string [index] of [count] crosses the nut, as an x fraction. */
internal fun nutSlot(index: Int, count: Int, spec: HeadSpec): Float {
    if (count <= 1) return 0.5f
    val half = spec.nutSpread / 2f
    if (!spec.paired) return 0.5f - half + spec.nutSpread * (index / (count - 1f))

    // Courses: the gap inside a pair is about a third of the gap between them.
    val courses = count / 2
    val within = (index % 2) * 0.34f
    val t = (index / 2 + within) / (courses - 1 + 0.34f)
    return 0.5f - half + spec.nutSpread * t
}
