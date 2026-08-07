package nl.markmaaktmedia.guitartuner.audio

import nl.markmaaktmedia.guitartuner.domain.model.PitchReading

/**
 * Turns one window of mono PCM into a pitch estimate, or null when the window holds no usable
 * note (silence, noise, a chord, a dead note).
 *
 * Implementations are not thread safe: they reuse scratch buffers between calls. Drive one
 * instance from one coroutine.
 */
interface PitchDetector {

    /**
     * Narrow the search to the fundamentals the current instrument can produce. Doing this is
     * what keeps the detector cheap: the autocorrelation only has to evaluate lags inside
     * `[sampleRate / maxHz, sampleRate / minHz]` instead of the whole window.
     */
    fun setFrequencyRange(minHz: Float, maxHz: Float)

    fun detect(window: FloatArray): PitchReading?
}
