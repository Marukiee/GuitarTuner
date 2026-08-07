package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus

/**
 * Debounces the "held in tune" condition that triggers the chime, the haptic and auto-advance.
 *
 * The rule is: the reading must stay inside the in-tune window continuously for [holdMillis].
 * A single frame outside the window resets the clock, so a wobbling note cannot accumulate
 * credit across dropouts. [reset] is called whenever the target string changes.
 */
class InTuneTracker(
    private val holdMillis: Long = 500L,
    private val minClarity: Float = 0.9f,
) {
    private var heldSinceMillis: Long = NOT_HELD
    private var alreadyFired = false

    /**
     * @return true exactly once per continuous in-tune stretch, on the frame where the hold
     *         duration is first satisfied.
     */
    fun update(reading: TuningReading?, nowMillis: Long): Boolean {
        val qualifies = reading != null &&
            reading.status == TuningStatus.IN_TUNE &&
            reading.clarity >= minClarity

        if (!qualifies) {
            heldSinceMillis = NOT_HELD
            alreadyFired = false
            return false
        }

        if (heldSinceMillis == NOT_HELD) {
            heldSinceMillis = nowMillis
            return false
        }

        if (!alreadyFired && nowMillis - heldSinceMillis >= holdMillis) {
            alreadyFired = true
            return true
        }
        return false
    }

    /** Progress 0..1 through the hold window, for a ring or bar that fills up before the chime. */
    fun holdProgress(nowMillis: Long): Float {
        if (heldSinceMillis == NOT_HELD) return 0f
        return ((nowMillis - heldSinceMillis).toFloat() / holdMillis).coerceIn(0f, 1f)
    }

    fun reset() {
        heldSinceMillis = NOT_HELD
        alreadyFired = false
    }

    private companion object {
        const val NOT_HELD = -1L
    }
}
