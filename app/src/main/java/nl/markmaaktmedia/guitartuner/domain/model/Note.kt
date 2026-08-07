package nl.markmaaktmedia.guitartuner.domain.model

import kotlin.math.ln
import kotlin.math.pow

/**
 * A pitch expressed as a MIDI note number (A4 = 69).
 *
 * We store notes rather than frequencies so the whole app can be re-referenced to A=432/435/442 Hz
 * by changing a single value, and so nothing has to hardcode a frequency table.
 */
@JvmInline
value class Note(val midi: Int) {

    /** Concert frequency of this note for the given A4 reference. */
    fun frequency(referenceHz: Float = STANDARD_REFERENCE_HZ): Float =
        referenceHz * 2f.pow((midi - 69) / 12f)

    /** e.g. "E", "A#". */
    val pitchClass: String get() = PITCH_CLASSES[midi.mod(12)]

    /** Scientific pitch notation octave: MIDI 60 -> 4. */
    val octave: Int get() = midi / 12 - 1

    /** e.g. "E2". */
    val label: String get() = "$pitchClass$octave"

    companion object {
        const val STANDARD_REFERENCE_HZ = 440f

        private val PITCH_CLASSES =
            arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        private const val LN_2 = 0.6931472f

        /** Nearest MIDI note to an arbitrary frequency (fractional, not rounded). */
        fun fractionalMidi(frequencyHz: Float, referenceHz: Float = STANDARD_REFERENCE_HZ): Float =
            69f + 12f * (ln(frequencyHz / referenceHz) / LN_2)

        /**
         * Signed distance in cents from [frequencyHz] to [targetHz].
         * Negative = flat (too low), positive = sharp (too high). 100 cents = 1 semitone.
         */
        fun centsBetween(frequencyHz: Float, targetHz: Float): Float =
            1200f * (ln(frequencyHz / targetHz) / LN_2)
    }
}
