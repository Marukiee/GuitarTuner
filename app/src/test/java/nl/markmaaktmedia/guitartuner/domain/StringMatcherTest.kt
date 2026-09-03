package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class StringMatcherTest {

    private val matcher = StringMatcher()
    private val guitar = Instrument.ACOUSTIC.defaultTuning
    private val reference = 440f

    @Test
    fun `picks the nearest string from a clean pitch`() {
        assertEquals(0, matcher.match(82.41f, guitar, reference, currentIndex = null)) // E2
        assertEquals(3, matcher.match(196.0f, guitar, reference, currentIndex = null)) // G3
        assertEquals(5, matcher.match(329.6f, guitar, reference, currentIndex = null)) // E4
    }

    @Test
    fun `holds the locked string while it is being tuned through a wide range`() {
        // G3 dragged a whole tone flat is closer to F3 than to G3, but the user is clearly
        // still working on G3, so the target must not jump.
        val badlyFlatG = 196.0f * 0.89f
        assertEquals(3, matcher.match(badlyFlatG, guitar, reference, currentIndex = 3))
    }

    @Test
    fun `switches when another string is played decisively`() {
        assertEquals(0, matcher.match(82.41f, guitar, reference, currentIndex = 3))
    }

    @Test
    fun `folds an octave error back onto the target`() {
        val a2 = guitar.strings[1]
        // A detector reporting the second harmonic of A2 should read as in tune, not +1200.
        val cents = matcher.foldedCents(220.0f, a2, reference)
        assertEquals(0f, abs(cents), 1f)
    }
}
