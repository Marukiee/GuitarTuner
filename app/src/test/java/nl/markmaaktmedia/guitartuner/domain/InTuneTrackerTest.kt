package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneTrackerTest {

    private val string = Instrument.ACOUSTIC_6.strings.first()

    private fun reading(cents: Float, clarity: Float = 0.97f) = TuningReading(
        target = string,
        frequencyHz = 82.41f,
        cents = cents,
        clarity = clarity,
        levelDb = -20f,
    )

    @Test
    fun `fires once after the hold window and not again`() {
        val tracker = InTuneTracker(holdMillis = 500L)
        assertFalse(tracker.update(reading(0.5f), 0L))
        assertFalse(tracker.update(reading(0.5f), 400L))
        assertTrue(tracker.update(reading(0.5f), 520L))
        assertFalse("Must not re-fire while still in tune", tracker.update(reading(0.5f), 900L))
    }

    @Test
    fun `a single frame outside the window resets the clock`() {
        val tracker = InTuneTracker(holdMillis = 500L)
        tracker.update(reading(0.5f), 0L)
        tracker.update(reading(9f), 300L) // wobbled sharp
        // The clock restarts here, so the full 500 ms has to elapse again from 520.
        assertFalse(tracker.update(reading(0.5f), 520L))
        assertFalse(tracker.update(reading(0.5f), 830L))
        assertTrue(tracker.update(reading(0.5f), 1030L))
    }

    @Test
    fun `a low clarity reading does not count`() {
        val tracker = InTuneTracker(holdMillis = 500L)
        tracker.update(reading(0f, clarity = 0.5f), 0L)
        assertFalse(tracker.update(reading(0f, clarity = 0.5f), 900L))
    }
}
