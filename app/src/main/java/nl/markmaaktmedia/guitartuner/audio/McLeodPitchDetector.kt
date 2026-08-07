package nl.markmaaktmedia.guitartuner.audio

import nl.markmaaktmedia.guitartuner.domain.model.PitchReading
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * McLeod Pitch Method (MPM), the "normalised square difference function" variant of
 * autocorrelation described in *A Smarter Way to Find Pitch* (McLeod & Wyvill, 2005).
 *
 * Chosen over YIN because plucked low strings (E2 at 82 Hz, and worse, a 5 string bass B0 at
 * 31 Hz) have a strong second harmonic during the attack, which is exactly the case where YIN's
 * cumulative mean normalisation likes to lock onto the octave above. The NSDF normalises per lag
 * instead of cumulatively, so the true fundamental keeps the taller peak.
 *
 * Cost: the NSDF is evaluated only for lags inside the configured frequency range, so a 6 string
 * guitar needs roughly 5M multiply-adds per window instead of the 67M a full-range O(n^2)
 * autocorrelation would need. No FFT required.
 *
 * If you ever need this cheaper (very old devices, or a 5 string bass where the lag range is
 * four times wider), decimate the input by 4 with an anti-alias low pass first: guitar
 * fundamentals top out around 400 Hz, so 11025 Hz is plenty and the cost drops 16x.
 */
class McLeodPitchDetector(
    private val sampleRate: Int,
    /** Fraction of the tallest NSDF peak a candidate must reach to be accepted as fundamental. */
    private val peakThresholdRatio: Float = 0.9f,
    /** Reject anything below this NSDF peak height; below ~0.85 the window is not periodic. */
    private val minClarity: Float = 0.85f,
    /** Reject windows quieter than this, in dBFS, so silence does not produce a reading. */
    private val minLevelDb: Float = -55f,
) : PitchDetector {

    private var minLagInclusive = 0
    private var maxLagInclusive = 0

    private var nsdf = FloatArray(0)
    private var centred = FloatArray(0)
    private val keyMaxima = ArrayList<Int>(16)

    init {
        setFrequencyRange(DEFAULT_MIN_HZ, DEFAULT_MAX_HZ)
    }

    override fun setFrequencyRange(minHz: Float, maxHz: Float) {
        require(minHz > 0f && maxHz > minHz) { "Bad range: $minHz..$maxHz" }
        minLagInclusive = (sampleRate / maxHz).toInt().coerceAtLeast(2)
        maxLagInclusive = (sampleRate / minHz).toInt() + 1
        if (nsdf.size <= maxLagInclusive) {
            nsdf = FloatArray(maxLagInclusive + 1)
        }
    }

    override fun detect(window: FloatArray): PitchReading? {
        if (window.size <= maxLagInclusive + 2) return null

        val levelDb = removeDcAndMeasureLevel(window)
        if (levelDb < minLevelDb) return null

        computeNsdf(centred, maxLagInclusive)
        collectKeyMaxima()
        if (keyMaxima.isEmpty()) return null

        var tallest = 0f
        for (lag in keyMaxima) if (nsdf[lag] > tallest) tallest = nsdf[lag]
        if (tallest < minClarity) return null

        // The *first* peak clearing the threshold, not the tallest. This is the whole trick:
        // it prefers the lowest periodic lag, which is the fundamental rather than a harmonic.
        val threshold = peakThresholdRatio * tallest
        val chosen = keyMaxima.firstOrNull { nsdf[it] >= threshold } ?: return null

        val (refinedLag, clarity) = parabolicRefine(chosen)
        if (refinedLag <= 0f) return null

        val frequencyHz = sampleRate / refinedLag
        if (frequencyHz < sampleRate / maxLagInclusive.toFloat() ||
            frequencyHz > sampleRate / minLagInclusive.toFloat()
        ) {
            return null
        }

        return PitchReading(
            frequencyHz = frequencyHz,
            clarity = clarity.coerceIn(0f, 1f),
            levelDb = levelDb,
        )
    }

    /**
     * Copies [window] into the scratch buffer with its mean subtracted, and returns the RMS in
     * dBFS. DC offset is common on cheap phone mics and it biases every lag of the NSDF.
     */
    private fun removeDcAndMeasureLevel(window: FloatArray): Float {
        if (centred.size != window.size) centred = FloatArray(window.size)

        var sum = 0.0
        for (sample in window) sum += sample
        val mean = (sum / window.size).toFloat()

        var energy = 0.0
        for (i in window.indices) {
            val value = window[i] - mean
            centred[i] = value
            energy += value.toDouble() * value
        }
        val rms = sqrt(energy / window.size).toFloat()
        return if (rms <= 1e-9f) SILENCE_DB else 20f * log10(rms)
    }

    /**
     * n'(tau) = 2 * r(tau) / m(tau), where
     *   r(tau) = sum x[j] * x[j+tau]
     *   m(tau) = sum x[j]^2 + x[j+tau]^2
     *
     * Evaluated from lag 0 so the zero crossing structure needed by peak picking is intact,
     * even though only lags >= [minLagInclusive] can win.
     */
    private fun computeNsdf(x: FloatArray, maxLag: Int) {
        val size = x.size
        for (tau in 0..maxLag) {
            var correlation = 0.0
            var magnitude = 0.0
            val limit = size - tau
            var j = 0
            while (j < limit) {
                val a = x[j]
                val b = x[j + tau]
                correlation += a.toDouble() * b
                magnitude += a.toDouble() * a + b.toDouble() * b
                j++
            }
            nsdf[tau] = if (magnitude > 0.0) (2.0 * correlation / magnitude).toFloat() else 0f
        }
    }

    /**
     * One key maximum per positive hump of the NSDF, in ascending lag order. The leading hump
     * around lag 0 is skipped because n'(0) is always 1 and means nothing.
     */
    private fun collectKeyMaxima() {
        keyMaxima.clear()

        var tau = 1
        while (tau <= maxLagInclusive && nsdf[tau] > 0f) tau++

        while (tau <= maxLagInclusive) {
            if (nsdf[tau] <= 0f) {
                tau++
                continue
            }
            var peakLag = tau
            var peakValue = nsdf[tau]
            while (tau <= maxLagInclusive && nsdf[tau] > 0f) {
                if (nsdf[tau] > peakValue) {
                    peakValue = nsdf[tau]
                    peakLag = tau
                }
                tau++
            }
            if (peakLag in minLagInclusive..maxLagInclusive) keyMaxima += peakLag
        }
    }

    /**
     * Fits a parabola through the peak and its two neighbours. Without this the resolution is one
     * whole sample of lag, which at E4 (329 Hz, lag 134) is about 13 cents. With it, well under 1.
     *
     * @return refined lag and the interpolated peak height.
     */
    private fun parabolicRefine(lag: Int): Pair<Float, Float> {
        if (lag <= 0 || lag >= nsdf.size - 1) return nsdf[lag].let { lag.toFloat() to it }

        val left = nsdf[lag - 1]
        val centre = nsdf[lag]
        val right = nsdf[lag + 1]

        val denominator = left - 2f * centre + right
        if (denominator == 0f) return lag.toFloat() to centre

        val delta = 0.5f * (left - right) / denominator
        if (delta < -1f || delta > 1f) return lag.toFloat() to centre

        val refinedValue = centre - 0.25f * (left - right) * delta
        return (lag + delta) to refinedValue
    }

    private companion object {
        const val SILENCE_DB = -120f

        /** Wide enough for a 5 string bass B0 up to a guitar high E, before per instrument tuning. */
        const val DEFAULT_MIN_HZ = 26f
        const val DEFAULT_MAX_HZ = 420f
    }
}
