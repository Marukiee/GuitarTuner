package nl.markmaaktmedia.guitartuner.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.PitchReading
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * One analysis window's worth of output.
 *
 * [levelDb] is reported for *every* frame, including frames with no detectable pitch. That is
 * what lets the UI show an input meter and what lets the engine notice it has been handed a dead
 * microphone: without it, a broken mic and a silent room are indistinguishable from "no note".
 */
data class AudioFrame(
    val levelDb: Float,
    val pitch: PitchReading?,
)

/**
 * Capture plus detection plus outlier rejection, as one flow of frames.
 *
 * Detection runs on [Dispatchers.Default] rather than the IO dispatcher that feeds it, so a long
 * NSDF pass on a 5 string bass cannot delay the next `AudioRecord.read`.
 *
 * Two filters sit between the detector and the UI:
 *
 * - a **median of 5** over frequency, which erases the single frame octave jumps that survive the
 *   detector's own peak picking, at the cost of ~2 frames (92 ms) of lag;
 * - a **release hold**, which keeps the last good reading alive for a few frames after a note
 *   decays below the level gate, so the readout does not flicker to empty between plucks.
 *
 * Deliberately no smoothing of the *displayed value* beyond that. The visual smoothing belongs in
 * Compose, where a spring can interpolate between readings at display refresh rate instead of at
 * the 21 Hz analysis rate.
 */
class PitchEngine(
    private val capture: AudioCaptureSource,
    private val detector: PitchDetector,
) {

    fun availableSources(): List<MicSource> = capture.availableSources()

    fun configureFor(instrument: Instrument) {
        detector.setFrequencyRange(instrument.minDetectableHz, instrument.maxDetectableHz)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(source: MicSource): Flow<AudioFrame> = flow {
        val median = MedianFilter(size = 5)
        var framesSinceLastGood = Int.MAX_VALUE
        var lastGood: PitchReading? = null

        capture.windows(source).collect { window ->
            val level = levelDbOf(window)
            val raw = detector.detect(window)

            if (raw == null) {
                framesSinceLastGood++
                if (framesSinceLastGood > RELEASE_FRAMES) {
                    median.clear()
                    lastGood = null
                    emit(AudioFrame(level, null))
                } else {
                    emit(AudioFrame(level, lastGood))
                }
                return@collect
            }

            framesSinceLastGood = 0
            val smoothed = raw.copy(frequencyHz = median.push(raw.frequencyHz))
            lastGood = smoothed
            emit(AudioFrame(level, smoothed))
        }
    }.flowOn(Dispatchers.Default)

    private fun levelDbOf(window: FloatArray): Float {
        var energy = 0.0
        for (sample in window) energy += sample.toDouble() * sample
        val rms = sqrt(energy / window.size).toFloat()
        return if (rms <= 1e-9f) SILENCE_DB else 20f * log10(rms)
    }

    private companion object {
        /** ~5 frames at a 46 ms hop, so a decaying note holds on screen for a quarter second. */
        const val RELEASE_FRAMES = 5
        const val SILENCE_DB = -120f
    }
}

/** Fixed size running median. Cheap at n=5; do not grow this without switching to a heap. */
internal class MedianFilter(private val size: Int) {
    private val ring = FloatArray(size)
    private val sorted = FloatArray(size)
    private var count = 0
    private var head = 0

    fun push(value: Float): Float {
        ring[head] = value
        head = (head + 1) % size
        if (count < size) count++

        System.arraycopy(ring, 0, sorted, 0, size)
        java.util.Arrays.sort(sorted, 0, count)
        return sorted[count / 2]
    }

    fun clear() {
        count = 0
        head = 0
    }
}
