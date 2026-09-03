package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.domain.model.TuningString
import kotlin.math.abs

/**
 * Picks which string the player is most likely aiming at.
 *
 * Two things make this harder than "nearest frequency":
 *
 * 1. Octave errors. Even a good detector occasionally reports 2x or 0.5x the fundamental,
 *    especially on a freshly plucked bass note. We fold those candidates back before matching.
 * 2. Flapping. On a 6 string guitar, D3 and G3 are 5 semitones apart, but a badly flat G3 sits
 *    closer to D#3 than to G3. Once a string is locked we require [switchHysteresisCents] of
 *    extra margin before we let another string steal focus, so the target does not oscillate
 *    while the user is turning a peg.
 */
class StringMatcher(
    private val switchHysteresisCents: Float = 60f,
    private val maxDistanceCents: Float = 250f,
) {

    /**
     * @param currentIndex physical index of the string currently locked, or null if none.
     * @return physical index of the best match, or null if nothing is close enough.
     */
    fun match(
        frequencyHz: Float,
        tuning: Tuning,
        referenceHz: Float,
        currentIndex: Int?,
    ): Int? {
        if (frequencyHz <= 0f) return null

        var bestIndex: Int? = null
        var bestDistance = Float.MAX_VALUE

        for (string in tuning.strings) {
            val distance = abs(directCents(frequencyHz, string, referenceHz))
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = string.physicalIndex
            }
        }

        if (bestIndex == null || bestDistance > maxDistanceCents) return currentIndex

        if (currentIndex != null && currentIndex in tuning.strings.indices && currentIndex != bestIndex) {
            val currentString = tuning.strings[currentIndex]
            val currentDistance = abs(directCents(frequencyHz, currentString, referenceHz))
            // Only switch if the new candidate is clearly better than the incumbent.
            if (currentDistance - bestDistance < switchHysteresisCents) return currentIndex
        }

        return bestIndex
    }

    /** Plain cents from [frequencyHz] to [string], no octave folding. */
    private fun directCents(frequencyHz: Float, string: TuningString, referenceHz: Float): Float =
        Note.centsBetween(frequencyHz, string.targetHz(referenceHz))

    /**
     * Cents from [frequencyHz] to [string], after folding octave-doubled or octave-halved
     * detections back onto the target. Returns the smallest magnitude of the three candidates.
     *
     * Only for reporting the offset of a string that has *already* been chosen. Deliberately not
     * used by [match]: folding there lets the low E swallow everything, because a G3 that is
     * being tuned up from flat sits within 100 cents of E2's second harmonic and would steal the
     * lock mid-turn. Octave errors are the detector's job to avoid, and MPM already does.
     */
    fun foldedCents(frequencyHz: Float, string: TuningString, referenceHz: Float): Float {
        val target = string.targetHz(referenceHz)
        val candidates = floatArrayOf(frequencyHz, frequencyHz * 0.5f, frequencyHz * 2f)
        var best = Float.MAX_VALUE
        for (candidate in candidates) {
            val cents = Note.centsBetween(candidate, target)
            if (abs(cents) < abs(best)) best = cents
        }
        return best
    }
}
