package nl.markmaaktmedia.guitartuner.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import nl.markmaaktmedia.guitartuner.domain.model.PitchReading
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * One analysis window's worth of output.
 *
 * [levelDb] is reported for *every* frame, including frames with no detectable pitch, and
 * it is measured on the raw microphone block before any filtering. That is what lets the
 * UI show a real input meter, and what lets the view model notice it has been handed a
 * dead microphone: without it, a broken mic and a silent room are indistinguishable from
 * "no note".
 */
data class AudioFrame(
    val levelDb: Float,
    val pitch: PitchReading?,
)

/**
 * The whole chain from raw blocks to readings, with no Android in it.
 *
 * The chain is: raw blocks, through [DecimatingPreFilter] into an 11.025 kHz analysis window,
 * through the [PitchDetector], then through the [PitchStabiliser], which exists because a
 * plucked string is not a laboratory signal. It lives apart from [PitchEngine] so a unit test
 * can feed it synthetic strings, with noise and hum and a decaying attack, and measure what the
 * app would have shown, which is the only honest way to know a tuner is accurate.
 *
 * Deliberately no smoothing of the *displayed* value beyond the stabiliser. The visual
 * smoothing belongs in Compose, where a spring interpolates between readings at display
 * refresh rate rather than at the 21 Hz analysis rate.
 */
internal class PitchPipeline(
    sampleRate: Int,
    hopSize: Int,
    detectorFactory: (Int) -> PitchDetector = { rate -> McLeodPitchDetector(sampleRate = rate) },
) {

    private val preFilter = DecimatingPreFilter(sampleRate)

    /** Sample rate the detector actually sees, after decimation. */
    val analysisRate: Int = preFilter.outputRate

    private val detector: PitchDetector = detectorFactory(analysisRate)

    /** Decimated samples per emitted block. */
    private val decimatedHop: Int = hopSize / preFilter.factor

    @Volatile
    private var windowSize: Int = DEFAULT_WINDOW

    /**
     * Point the whole chain at one tuning.
     *
     * The window length is part of this and is not a constant. It is sized to hold about
     * seven periods of the lowest note, which is what the NSDF needs for a stable peak, and no
     * more: a violin's low G needs 65 ms of audio and a five string bass's low B needs 370 ms,
     * so a single window length either makes the violin sluggish or makes the bass unreliable.
     */
    fun configureFor(tuning: Tuning) {
        val minHz = tuning.minDetectableHz
        val maxHz = tuning.maxDetectableHz
        detector.setFrequencyRange(minHz, maxHz)

        // Below the lowest fundamental is hum, handling noise and microphone rumble, and all of
        // it lands in the lag range where a low note is looked for. Above three harmonics is
        // structure the NSDF does not need and can trip over.
        //
        // The high pass corner sits just under the flattest note still worth reporting, at 0.94
        // of it, rather than comfortably below. On a guitar that is 66 Hz, and the extra few Hz
        // matter: at 0.72 the corner landed on 50 Hz itself, so the one interferer the filter
        // exists to remove sat in its passband.
        preFilter.configure(lowHz = minHz * 0.94f, highHz = maxHz * 3.2f)

        val needed = PERIODS_PER_WINDOW * (analysisRate / minHz)
        windowSize = nextPowerOfTwo(needed.toInt()).coerceIn(MIN_WINDOW, MAX_WINDOW)
    }

    /** One run of the chain, with its own history. One per subscription, so none is shared. */
    fun session(): Session = Session()

    inner class Session {
        private val state = PitchStabiliser()
        private var window = FloatArray(windowSize)
        private var filled = 0
        private val decimated = FloatArray(decimatedHop)

        init {
            preFilter.reset()
        }

        fun accept(block: FloatArray): AudioFrame {
            val level = levelDbOf(block)

            // A tuning change while the recorder runs resizes the window in place. Cheaper and
            // less jarring than tearing down AudioRecord for it.
            if (window.size != windowSize) {
                window = FloatArray(windowSize)
                filled = 0
                state.reset()
            }

            val produced = preFilter.process(block, decimated)
            if (produced <= 0) return AudioFrame(level, null)

            // Slide the window left by one hop, then append the new samples.
            val shift = minOf(produced, window.size)
            System.arraycopy(window, shift, window, 0, window.size - shift)
            System.arraycopy(decimated, 0, window, window.size - shift, shift)
            if (filled < window.size) {
                filled += shift
                if (filled < window.size) return AudioFrame(level, null)
            }

            return AudioFrame(level, state.accept(detector.detect(window), level))
        }
    }

    private fun levelDbOf(block: FloatArray): Float {
        var energy = 0.0
        for (sample in block) energy += sample.toDouble() * sample
        val rms = sqrt(energy / block.size).toFloat()
        return if (rms <= 1e-9f) SILENCE_DB else 20f * log10(rms)
    }

    companion object {
        private const val SILENCE_DB = -120f

        /** Periods of the lowest note the analysis window has to hold. */
        private const val PERIODS_PER_WINDOW = 7f

        private const val DEFAULT_WINDOW = 2048
        private const val MIN_WINDOW = 1024
        private const val MAX_WINDOW = 4096

        private fun nextPowerOfTwo(value: Int): Int {
            var result = 1
            while (result < value) result = result shl 1
            return result
        }
    }
}

/**
 * Capture, band limiting, detection and outlier rejection, as one flow of frames: the
 * [PitchPipeline] fed from the microphone.
 */
class PitchEngine(
    private val capture: AudioCaptureSource,
    detectorFactory: (Int) -> PitchDetector = { rate -> McLeodPitchDetector(sampleRate = rate) },
) {

    private val pipeline = PitchPipeline(capture.sampleRate, capture.hopSize, detectorFactory)

    fun availableSources(): List<MicSource> = capture.availableSources()

    fun configureFor(tuning: Tuning) = pipeline.configureFor(tuning)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(source: MicSource): Flow<AudioFrame> = flow {
        val session = pipeline.session()
        capture.hops(source).collect { block -> emit(session.accept(block)) }
    }.flowOn(Dispatchers.Default)
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
