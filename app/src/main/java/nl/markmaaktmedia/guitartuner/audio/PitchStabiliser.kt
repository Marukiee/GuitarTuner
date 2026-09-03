package nl.markmaaktmedia.guitartuner.audio

import nl.markmaaktmedia.guitartuner.domain.model.PitchReading
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Everything in the pitch chain that has to remember something from one frame to the next.
 *
 * One instance per subscription rather than fields on [PitchEngine], so two collectors
 * cannot share a history and a restart cannot inherit one.
 *
 * ## The octave anchor
 *
 * This is the piece that makes the difference between a tuner that is usually right and
 * one that is right, and it is the trick the pitch detection literature calls onset
 * locking. The estimate from just after the attack, while the signal to noise ratio is at
 * its best, becomes an anchor. Every later estimate is then tested against the small set
 * of ratios a detector actually confuses (a half, a double, a third, a triple) and
 * whichever lands nearest the anchor wins.
 *
 * A note that genuinely moves takes the anchor with it, because the anchor follows the
 * accepted value on its own slow filter. A note that jumps a clean octave between one
 * frame and the next does not, because that is not something a string does. And a note
 * that is genuinely new arrives louder than the one decaying before it, which drops the
 * anchor entirely: that is what lets the chain recover if it ever does lock onto the
 * wrong thing. Play the string again.
 *
 * ## The smoothing
 *
 * Runs on the logarithm of the frequency, so it behaves the same in cents everywhere on
 * the neck. The coefficient scales with how far the new estimate is from the smoothed
 * one: a large move is followed almost immediately, so turning a peg feels direct, and a
 * small one is followed slowly, so a held note stops twitching in the last digit. A fixed
 * coefficient has to choose between those two and is wrong half the time.
 */
internal class PitchStabiliser {
    private val median = MedianFilter(size = 5)
    private val recent = FloatArray(SETTLE_FRAMES)
    private var recentCount = 0
    private var recentHead = 0

    private var framesSinceGood = Int.MAX_VALUE
    private var lastGood: PitchReading? = null

    /** Log frequency of the anchor, or NaN while there is none. */
    private var anchorLog = Float.NaN

    /** The smoothed output, also in log space. */
    private var smoothedLog = Float.NaN

    private var peakLevelDb = SILENCE_DB

    fun reset() {
        median.clear()
        recentCount = 0
        recentHead = 0
        framesSinceGood = Int.MAX_VALUE
        lastGood = null
        anchorLog = Float.NaN
        smoothedLog = Float.NaN
        peakLevelDb = SILENCE_DB
    }

    fun accept(raw: PitchReading?, levelDb: Float): PitchReading? {
        if (raw == null) {
            framesSinceGood++
            if (framesSinceGood <= RELEASE_FRAMES) return lastGood
            reset()
            return null
        }

        // A new attack that is clearly louder than what came before is a new note, and
        // the anchor from the old one would fight it. This is also what lets the anchor
        // recover after it has locked onto something wrong: play the string again.
        if (levelDb > peakLevelDb + ONSET_JUMP_DB) {
            anchorLog = Float.NaN
            smoothedLog = Float.NaN
            median.clear()
            recentCount = 0
            recentHead = 0
        }
        peakLevelDb = maxOf(levelDb, peakLevelDb - PEAK_DECAY_DB)

        framesSinceGood = 0

        val corrected = correctOctave(raw.frequencyHz)
        val filtered = median.push(corrected)
        val filteredLog = ln(filtered)

        smoothedLog = if (smoothedLog.isNaN()) {
            filteredLog
        } else {
            val deltaCents = abs(filteredLog - smoothedLog) * CENTS_PER_LOG
            val alpha = (SMOOTH_MIN + deltaCents / SMOOTH_KNEE).coerceIn(SMOOTH_MIN, SMOOTH_MAX)
            smoothedLog + alpha * (filteredLog - smoothedLog)
        }

        anchorLog = if (anchorLog.isNaN()) filteredLog else anchorLog + ANCHOR_ALPHA * (filteredLog - anchorLog)

        recent[recentHead] = smoothedLog
        recentHead = (recentHead + 1) % SETTLE_FRAMES
        if (recentCount < SETTLE_FRAMES) recentCount++

        val reading = raw.copy(
            frequencyHz = exp(smoothedLog),
            settled = isSettled(),
        )
        lastGood = reading
        return reading
    }

    /**
     * Pulls an estimate back onto the anchor when it lands on one of the ratios a
     * detector actually confuses.
     *
     * The candidate set is deliberately short. A half and a double cover the octave
     * errors, which are almost all of them; a third and a triple cover the case where
     * peak picking lands on the wrong hump of a very harmonic-rich low string. Ratios
     * beyond that are not detector errors, they are different notes.
     */
    fun correctOctave(frequencyHz: Float): Float {
        if (anchorLog.isNaN()) return frequencyHz
        var best = frequencyHz
        var bestDistance = Float.MAX_VALUE
        for (ratio in OCTAVE_CANDIDATES) {
            val candidate = frequencyHz * ratio
            val distance = abs(ln(candidate) - anchorLog) * CENTS_PER_LOG
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        // Too far from the anchor for any ratio to explain: this is a different note,
        // so take it at face value and let the anchor follow.
        return if (bestDistance > ANCHOR_TOLERANCE_CENTS) frequencyHz else best
    }

    private fun isSettled(): Boolean {
        if (recentCount < SETTLE_FRAMES) return false
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (i in 0 until recentCount) {
            val value = recent[i]
            if (value < min) min = value
            if (value > max) max = value
        }
        return (max - min) * CENTS_PER_LOG <= SETTLE_SPREAD_CENTS
    }

    internal companion object {
        const val SILENCE_DB = -120f

        /** 1200 / ln(2): converts a difference of natural logs straight into cents. */
        const val CENTS_PER_LOG = 1731.234f

        /** ~5 frames at a 46 ms hop, so a decaying note holds on screen for a quarter second. */
        const val RELEASE_FRAMES = 5

        /** Frames that must agree before a reading counts as settled. */
        const val SETTLE_FRAMES = 4
        const val SETTLE_SPREAD_CENTS = 12f

        val OCTAVE_CANDIDATES = floatArrayOf(1f, 0.5f, 2f, 1f / 3f, 3f)

        /** How far from the anchor a ratio may land and still be treated as a slip. */
        const val ANCHOR_TOLERANCE_CENTS = 320f

        /** How fast the anchor follows the note. Slow enough to outlast one bad frame. */
        const val ANCHOR_ALPHA = 0.25f

        /** A rise this far above the running peak counts as a fresh pluck. */
        const val ONSET_JUMP_DB = 7f

        /** How fast the running peak forgets, per frame. */
        const val PEAK_DECAY_DB = 0.8f

        const val SMOOTH_MIN = 0.14f
        const val SMOOTH_MAX = 0.85f

        /** Cents of error at which the smoothing is already following almost fully. */
        const val SMOOTH_KNEE = 45f
    }
}
