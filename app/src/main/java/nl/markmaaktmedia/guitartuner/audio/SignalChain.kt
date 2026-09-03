package nl.markmaaktmedia.guitartuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A direct form I biquad. Coefficients follow the Audio EQ Cookbook.
 *
 * State is kept in doubles even though the audio is float: at a 25 Hz corner on a 44.1
 * kHz stream the pole sits within 0.004 of the unit circle, and a float accumulator
 * drifts audibly there.
 */
internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Float): Float {
        val xn = x.toDouble()
        val y = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = xn
        y2 = y1
        y1 = y
        return y.toFloat()
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    companion object {
        fun lowPass(sampleRate: Int, cutoffHz: Float, q: Double): Biquad {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = ((1.0 - cosW) / 2.0) / a0,
                b1 = (1.0 - cosW) / a0,
                b2 = ((1.0 - cosW) / 2.0) / a0,
                a1 = (-2.0 * cosW) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun highPass(sampleRate: Int, cutoffHz: Float, q: Double): Biquad {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            return Biquad(
                b0 = ((1.0 + cosW) / 2.0) / a0,
                b1 = (-(1.0 + cosW)) / a0,
                b2 = ((1.0 + cosW) / 2.0) / a0,
                a1 = (-2.0 * cosW) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        /** The two Q values that make a pair of cascaded biquads a 4th order Butterworth. */
        const val BUTTERWORTH_Q1 = 0.54119610
        const val BUTTERWORTH_Q2 = 1.30656296

        /** The three that make a triple a 6th order Butterworth: 1 / (2 cos(k * PI / 12)). */
        const val BUTTERWORTH6_Q1 = 0.51763809
        const val BUTTERWORTH6_Q2 = 0.70710678
        const val BUTTERWORTH6_Q3 = 1.93185165
    }
}

/**
 * Band limits the microphone signal to the band the instrument can actually produce, then
 * throws away three samples out of four.
 *
 * This is the single largest reliability win in the whole pipeline, and it is worth
 * spelling out why, because it looks like a mere optimisation.
 *
 * **The high pass** is set from the instrument. A guitar's lowest fundamental is 82 Hz,
 * so everything under about 60 Hz is mains hum, the handling noise of a phone being put
 * down on a table, and the low frequency rumble that a phone microphone generates on its
 * own. All of it lands in exactly the lag range where an autocorrelator looks for a bass
 * note, and it is why a tuner sometimes reports a confident 45 Hz from an empty room.
 * Tuning the corner per instrument is what lets it be aggressive: 66 Hz for a guitar,
 * 25 Hz for a five string bass that really does go down to 31 Hz.
 *
 * It is 6th order rather than 4th because the interesting interferer sits very close to
 * the corner. Mains hum at 50 Hz is only four semitones under a guitar's low E, and a
 * 4th order skirt leaves it at -7 dB there, which is not enough when the string is
 * plucked softly. A third biquad takes the same 50 Hz down to -15 dB and costs one more
 * multiply-add per sample, before the decimation makes the rest of the chain cheaper.
 *
 * **The low pass** is set at roughly three harmonics above the top string. A plucked
 * string has significant energy up past 5 kHz, all of it periodic at the fundamental but
 * with its own narrow structure, and that structure puts small ripples on the NSDF that
 * peak picking can mistake for a period. Removing it leaves a waveform that is basically
 * a fundamental plus two harmonics, which is the shape the NSDF is good at.
 *
 * **The decimation** then follows for free, because a signal band limited to a couple of
 * kHz does not need 44.1 kHz to represent it. Dropping to 11.025 kHz cuts the cost of the
 * NSDF by sixteen: the number of lags falls by four and each lag has four times fewer
 * products. That is what pays for the long window a low B needs.
 */
internal class DecimatingPreFilter(
    private val inputRate: Int,
    val factor: Int = DEFAULT_FACTOR,
) {
    val outputRate: Int = inputRate / factor

    private var highPass1 = Biquad.highPass(inputRate, 60f, Biquad.BUTTERWORTH6_Q1)
    private var highPass2 = Biquad.highPass(inputRate, 60f, Biquad.BUTTERWORTH6_Q2)
    private var highPass3 = Biquad.highPass(inputRate, 60f, Biquad.BUTTERWORTH6_Q3)
    private var lowPass1 = Biquad.lowPass(inputRate, 1400f, Biquad.BUTTERWORTH_Q1)
    private var lowPass2 = Biquad.lowPass(inputRate, 1400f, Biquad.BUTTERWORTH_Q2)

    /** Which sample of the current group of [factor] we are on, kept across calls. */
    private var phase = 0

    /**
     * @param lowHz corner of the high pass, below the instrument's lowest fundamental.
     * @param highHz corner of the low pass. Clamped below the decimated Nyquist with room
     *   for the filter skirt, because anything left above it folds back into the band and
     *   there is no undoing that.
     */
    fun configure(lowHz: Float, highHz: Float) {
        val low = lowHz.coerceIn(15f, 200f)
        val high = highHz.coerceIn(low * 3f, outputRate * 0.4f)
        highPass1 = Biquad.highPass(inputRate, low, Biquad.BUTTERWORTH6_Q1)
        highPass2 = Biquad.highPass(inputRate, low, Biquad.BUTTERWORTH6_Q2)
        highPass3 = Biquad.highPass(inputRate, low, Biquad.BUTTERWORTH6_Q3)
        lowPass1 = Biquad.lowPass(inputRate, high, Biquad.BUTTERWORTH_Q1)
        lowPass2 = Biquad.lowPass(inputRate, high, Biquad.BUTTERWORTH_Q2)
        reset()
    }

    fun reset() {
        highPass1.reset(); highPass2.reset(); highPass3.reset()
        lowPass1.reset(); lowPass2.reset()
        phase = 0
    }

    /**
     * Filters [input] and writes every [factor]-th sample into [output].
     *
     * @return how many samples were written. With a hop that is a multiple of [factor]
     *   and the phase carried across calls this is always `input.size / factor`, but it
     *   is returned rather than assumed so a short read cannot corrupt the window.
     */
    fun process(input: FloatArray, output: FloatArray): Int {
        var written = 0
        for (sample in input) {
            val highPassed = highPass3.process(highPass2.process(highPass1.process(sample)))
            val filtered = lowPass2.process(lowPass1.process(highPassed))
            if (phase == 0 && written < output.size) {
                output[written] = filtered
                written++
            }
            phase = (phase + 1) % factor
        }
        return written
    }

    companion object {
        /** 44.1 kHz down to 11.025 kHz. Four is the largest factor that keeps the skirt sane. */
        const val DEFAULT_FACTOR = 4
    }
}
