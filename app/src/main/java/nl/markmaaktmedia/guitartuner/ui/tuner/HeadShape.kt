package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.ui.graphics.Path

/**
 * The outline of an instrument's head, as the one half that gets mirrored.
 *
 * Written in units where the crown is 1 wide either side of the middle and the head runs 0 at
 * the top to 1 at the nut, so the same drawing serves any size and any neck width. Each curve
 * is a cubic given as its two control points and its end, starting from where the last one
 * stopped, beginning at the middle of the top edge.
 *
 * A guitar head is not a rectangle with a bend at the bottom, which is what the first version
 * drew and why it looked like a tombstone. It has a crown that rises to the middle, rounded
 * shoulders, sides that run nearly straight past the tuners, and then one long S shaped
 * sweep in to the neck.
 */
internal class HeadShape private constructor(
    private val right: List<FloatArray>,
) {
    /** The right edge sampled as (v, u) pairs, going down, so [edge] can look a width up by height. */
    private val samples: List<Pair<Float, Float>> = buildList {
        var startU = 0f
        var startV = 0f
        add(0f to 0f)
        for (s in right) {
            for (i in 1..14) {
                val t = i / 14f
                add(cubic(startV, s[1], s[3], s[5], t) to cubic(startU, s[0], s[2], s[4], t))
            }
            startU = s[4]
            startV = s[5]
        }
    }

    /** Half the width at height [v], in crown units, for the knobs to stand just outside of. */
    fun edge(v: Float): Float {
        val clamped = v.coerceIn(0f, 1f)
        for (i in 1 until samples.size) {
            val (v1, u1) = samples[i]
            val (v0, u0) = samples[i - 1]
            if (clamped <= v1 && v1 > v0) return u0 + (u1 - u0) * ((clamped - v0) / (v1 - v0))
        }
        return samples.last().second
    }

    /** The closed outline, in pixels: [cx] the middle, [cw] the crown's half width, [top] to [bottom]. */
    fun path(cx: Float, cw: Float, top: Float, bottom: Float): Path {
        fun px(u: Float) = cx + u * cw
        fun py(v: Float) = top + v * (bottom - top)
        return Path().apply {
            moveTo(px(0f), py(0f))
            val starts = ArrayList<Pair<Float, Float>>()
            var u = 0f
            var v = 0f
            for (s in right) {
                starts += u to v
                cubicTo(px(s[0]), py(s[1]), px(s[2]), py(s[3]), px(s[4]), py(s[5]))
                u = s[4]
                v = s[5]
            }
            // Across the nut, then back up the left side: the same curves mirrored, in reverse.
            lineTo(px(-u), py(v))
            for (k in right.indices.reversed()) {
                val s = right[k]
                val (startU, startV) = starts[k]
                cubicTo(px(-s[2]), py(s[3]), px(-s[0]), py(s[1]), px(-startU), py(startV))
            }
            close()
        }
    }

    private fun cubic(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val k = 1f - t
        return k * k * k * p0 + 3f * k * k * t * p1 + 3f * k * t * t * p2 + t * t * t * p3
    }

    companion object {
        /** An acoustic or electric head, bass, ukulele, banjo and mandolin: the crown and the sweep. */
        fun arch(neck: Float) = HeadShape(
            listOf(
                // A nearly flat crown with small rounded corners, then straight sides: the tall,
                // narrow slab a real acoustic head is, and not the tombstone of a wide one.
                floatArrayOf(0.60f, 0f, 0.94f, 0f, 1f, 0.055f),
                // The sides bow out a little and then fall away towards the neck, so the head
                // is a shape and not a slab with a bend in it.
                floatArrayOf(1.025f, 0.20f, 1.0f, 0.42f, 0.935f, 0.60f),
                floatArrayOf(0.89f, 0.74f, neck + 0.26f, 0.80f, neck + 0.10f, 0.91f),
                floatArrayOf(neck + 0.05f, 0.95f, neck, 0.965f, neck, 1f),
            ),
        )

        /** A violin's pegbox: straight cheeks under the scroll, then in to the neck. */
        fun pegbox(neck: Float) = HeadShape(
            listOf(
                floatArrayOf(0.55f, 0f, 1f, 0.02f, 1f, 0.14f),
                floatArrayOf(1f, 0.30f, 1f, 0.45f, 1f, 0.54f),
                floatArrayOf(1f, 0.74f, neck + 0.20f, 0.82f, neck, 1f),
            ),
        )
    }
}
