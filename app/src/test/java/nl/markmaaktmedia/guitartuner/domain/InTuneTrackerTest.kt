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
    fun `a brief wobble does not reset the clock`() {
        // A plucked string is not stable for the first few hundred milliseconds, so a couple of
        // frames outside the window have to be survivable or the hold never completes.
        val tracker = InTuneTracker(holdMillis = 500L, graceFrames = 3)
        assertFalse(tracker.update(reading(0.5f), 0L))
        assertFalse(tracker.update(reading(9f), 200L)) // wobbled sharp
        assertFalse(tracker.update(reading(9f), 250L))
        assertTrue(tracker.update(reading(0.5f), 520L))
    }

    @Test
    fun `sustained drift outside the window does reset the clock`() {
        val tracker = InTuneTracker(holdMillis = 500L, graceFrames = 3)
        tracker.update(reading(0.5f), 0L)
        repeat(4) { i -> tracker.update(reading(20f), 100L + i * 46L) }
        // Clock restarted, so the full hold has to elapse again from here.
        assertFalse(tracker.update(reading(0.5f), 400L))
        assertFalse(tracker.update(reading(0.5f), 700L))
        assertTrue(tracker.update(reading(0.5f), 950L))
    }

    @Test
    fun `five cents counts as in tune`() {
        // 2.5 cents was unreachable in the hand while a plucked note decays.
        val tracker = InTuneTracker(holdMillis = 500L)
        assertFalse(tracker.update(reading(4.4f), 0L))
        assertTrue(tracker.update(reading(-4.4f), 520L))
    }

    @Test
    fun `a low clarity reading does not count`() {
        val tracker = InTuneTracker(holdMillis = 500L)
        tracker.update(reading(0f, clarity = 0.5f), 0L)
        assertFalse(tracker.update(reading(0f, clarity = 0.5f), 900L))
    }
}
