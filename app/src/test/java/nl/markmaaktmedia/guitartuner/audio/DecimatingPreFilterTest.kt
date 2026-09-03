package nl.markmaaktmedia.guitartuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The band limiting is the one part of the chain whose whole job is to throw signal away,
 * so it is worth proving that it throws away the right signal.
 */
class DecimatingPreFilterTest {

    private val rate = 44_100

    private fun tone(hz: Float, samples: Int): FloatArray =
        FloatArray(samples) { sin(2.0 * PI * hz * it / rate).toFloat() }

    private fun rms(values: FloatArray, from: Int): Float {
        var energy = 0.0
        for (i in from until values.size) energy += values[i].toDouble() * values[i]
        return sqrt(energy / (values.size - from)).toFloat()
    }

    private fun passThrough(hz: Float, lowHz: Float, highHz: Float): Float {
        val filter = DecimatingPreFilter(rate)
        filter.configure(lowHz, highHz)
        val input = tone(hz, 44_100)
        val output = FloatArray(input.size / filter.factor)
        val written = filter.process(input, output)
        assertEquals(input.size / filter.factor, written)
        // Skip the first tenth of a second: the filter has to settle first.
        return rms(output, output.size / 10)
    }

    @Test
    fun `a note inside the band comes through`() {
        // A2, right in the middle of a guitar's range.
        val level = passThrough(110f, lowHz = 60f, highHz = 1400f)
        assertTrue("Passband level was $level", level > 0.6f)
    }

    @Test
    fun `mains hum below the lowest string is rejected`() {
        // 50 Hz sits under a guitar's low E, and it lands in exactly the lag range where
        // an autocorrelator hunts for a bass note.
        val level = passThrough(50f, lowHz = 60f, highHz = 1400f)
        assertTrue("Hum level was $level", level < 0.25f)
    }

    @Test
    fun `high partials that would alias are gone before decimation`() {
        // Anything above the decimated Nyquist of 5512 Hz folds back into the band, and
        // there is no undoing that afterwards.
        val level = passThrough(7000f, lowHz = 60f, highHz = 1400f)
        assertTrue("Alias source level was $level", level < 0.02f)
    }

    @Test
    fun `a five string bass keeps its low B`() {
        // 30.87 Hz. The corner has to move with the instrument or the fundamental of the
        // lowest string on the list is filtered out by the filter meant to help it.
        // 0.80 is what the engine ends up using: 0.85 of slack for a flat string, then
        // 0.94 of that for the corner.
        val level = passThrough(30.87f, lowHz = 30.87f * 0.80f, highHz = 1400f)
        assertTrue("Low B level was $level", level > 0.6f)
    }

    @Test
    fun `the decimation phase is carried across calls`() {
        val filter = DecimatingPreFilter(rate)
        filter.configure(60f, 1400f)
        // A hop that is not a multiple of the factor would otherwise silently drop or
        // duplicate a sample at every boundary.
        val output = FloatArray(3)
        repeat(4) {
            assertEquals(3, filter.process(FloatArray(12) { 0.5f }, output))
        }
    }
}
