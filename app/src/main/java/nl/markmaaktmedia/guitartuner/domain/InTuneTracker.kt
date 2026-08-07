package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus

/**
 * Debounces the "held in tune" condition that triggers the chime, the haptic and auto-advance.
 *
 * The rule is: the reading must stay inside the in-tune window for [holdMillis], tolerating up
 * to [graceFrames] frames outside it before the clock restarts. [reset] is called whenever the
 * target string changes.
 */
class InTuneTracker(
    private val holdMillis: Long = 500L,
    private val minClarity: Float = 0.82f,
    /**
     * Frames outside the window that are tolerated before the clock restarts.
     *
     * Zero tolerance sounds right and is unusable: a plucked string wobbles for the first few
     * hundred milliseconds and the detector drops the odd frame as the note decays, so a strict
     * reset means the hold almost never completes. Three frames is about 140 ms at a 46 ms hop,
     * short enough that a genuinely out-of-tune string still resets.
     */
    private val graceFrames: Int = 3,
) {
    private var heldSinceMillis: Long = NOT_HELD
    private var alreadyFired = false
    private var missedFrames = 0

    /**
     * @return true exactly once per continuous in-tune stretch, on the frame where the hold
     *         duration is first satisfied.
     */
    fun update(reading: TuningReading?, nowMillis: Long): Boolean {
        val qualifies = reading != null &&
            reading.status == TuningStatus.IN_TUNE &&
            reading.clarity >= minClarity

        if (!qualifies) {
            missedFrames++
            if (missedFrames > graceFrames) {
                heldSinceMillis = NOT_HELD
                alreadyFired = false
            }
            return false
        }
        missedFrames = 0

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
        missedFrames = 0
    }

    private companion object {
        const val NOT_HELD = -1L
    }
}
