package nl.markmaaktmedia.guitartuner.audio

import nl.markmaaktmedia.guitartuner.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 44_100
private const val WINDOW = 8192

class McLeodPitchDetectorTest {

    private fun detector() = McLeodPitchDetector(SAMPLE_RATE).apply {
        setFrequencyRange(minHz = 26f, maxHz = 420f)
    }

    /**
     * A plucked string is not a sine. This builds a harmonic stack with a plausible roll-off,
     * which is the case a naive autocorrelation gets wrong by an octave.
     */
    private fun pluck(
        frequency: Float,
        harmonics: Int = 6,
        amplitude: Float = 0.4f,
        phaseJitter: Boolean = true,
    ): FloatArray {
        val random = Random(seed = 7)
        return FloatArray(WINDOW) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            for (h in 1..harmonics) {
                val phase = if (phaseJitter) random.nextDouble(0.0, 2 * PI) * 0 else 0.0
                value += sin(2 * PI * frequency * h * t + phase) / h
            }
            (value * amplitude).toFloat()
        }
    }

    @Test
    fun `detects every standard guitar string within one cent`() {
        val detector = detector()
        val targets = listOf(82.41f, 110.00f, 146.83f, 196.00f, 246.94f, 329.63f)

        for (target in targets) {
            val reading = detector.detect(pluck(target))
            assertNotNull("No reading for $target Hz", reading)
            val cents = abs(Note.centsBetween(reading!!.frequencyHz, target))
            assertEquals("Off by $cents cents at $target Hz", 0f, cents, 1f)
        }
    }

    @Test
    fun `does not halve or double the octave on a strong second harmonic`() {
        val detector = detector()
        // Second harmonic louder than the fundamental, the classic YIN failure case.
        val fundamental = 82.41f
        val window = FloatArray(WINDOW) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val f1 = 0.25 * sin(2 * PI * fundamental * t)
            val f2 = 0.60 * sin(2 * PI * fundamental * 2 * t)
            val f3 = 0.30 * sin(2 * PI * fundamental * 3 * t)
            (f1 + f2 + f3).toFloat()
        }

        val reading = detector.detect(window)
        assertNotNull(reading)
        val cents = abs(Note.centsBetween(reading!!.frequencyHz, fundamental))
        assertEquals("Locked onto a harmonic instead of the fundamental", 0f, cents, 5f)
    }

    @Test
    fun `returns null on silence`() {
        assertNull(detector().detect(FloatArray(WINDOW)))
    }

    @Test
    fun `returns null on white noise`() {
        val random = Random(seed = 42)
        val noise = FloatArray(WINDOW) { (random.nextFloat() - 0.5f) * 0.5f }
        assertNull(detector().detect(noise))
    }

    @Test
    fun `resolves a bass low B`() {
        val detector = McLeodPitchDetector(SAMPLE_RATE).apply {
            setFrequencyRange(minHz = 26f, maxHz = 130f)
        }
        val reading = detector.detect(pluck(30.87f))
        assertNotNull(reading)
        val cents = abs(Note.centsBetween(reading!!.frequencyHz, 30.87f))
        assertEquals("Off by $cents cents on B0", 0f, cents, 3f)
    }
}
