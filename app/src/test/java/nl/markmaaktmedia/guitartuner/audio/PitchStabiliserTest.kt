package nl.markmaaktmedia.guitartuner.audio

import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.PitchReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The stabiliser is where an unreliable tuner becomes a reliable one, so the failures it
 * exists to prevent are written down here as tests rather than as comments.
 */
class PitchStabiliserTest {

    private fun raw(hz: Float, clarity: Float = 0.95f) =
        PitchReading(frequencyHz = hz, clarity = clarity, levelDb = -20f, settled = false)

    private fun feed(
        stabiliser: PitchStabiliser,
        hz: Float,
        levelDb: Float = -20f,
        times: Int = 1,
    ): PitchReading? {
        var last: PitchReading? = null
        repeat(times) { last = stabiliser.accept(raw(hz), levelDb) }
        return last
    }

    @Test
    fun `a single octave jump is pulled back onto the note`() {
        val stabiliser = PitchStabiliser()
        feed(stabiliser, 82.41f, times = 8)

        // The detector reports the second harmonic for one frame, which is the classic
        // failure on a freshly plucked low string.
        val slipped = stabiliser.accept(raw(164.82f), -20f)

        assertEquals(82.41f, slipped!!.frequencyHz, 2f)
    }

    @Test
    fun `a sustained genuine move is followed`() {
        val stabiliser = PitchStabiliser()
        feed(stabiliser, 82.41f, times = 8)

        // Turning a peg: a semitone up over a couple of dozen frames.
        var last: PitchReading? = null
        for (step in 1..30) {
            val hz = 82.41f * Math.pow(2.0, step / 12.0 / 30.0).toFloat()
            last = stabiliser.accept(raw(hz), -20f)
        }
        val expected = 82.41f * Math.pow(2.0, 1.0 / 12.0).toFloat()
        assertEquals(expected, last!!.frequencyHz, expected * 0.01f)
    }

    @Test
    fun `a louder new pluck drops the anchor`() {
        val stabiliser = PitchStabiliser()
        feed(stabiliser, 82.41f, levelDb = -30f, times = 8)

        // Now a different string is played, and it arrives louder than the decaying one.
        // Without the onset reset the anchor would drag the new note back an octave.
        val fresh = feed(stabiliser, 329.63f, levelDb = -14f, times = 8)

        assertEquals(329.63f, fresh!!.frequencyHz, 4f)
    }

    @Test
    fun `settled only once a run of frames agrees`() {
        val stabiliser = PitchStabiliser()
        assertFalse(stabiliser.accept(raw(110f), -20f)!!.settled)
        assertFalse(stabiliser.accept(raw(140f), -20f)!!.settled)
        val settled = feed(stabiliser, 110f, times = 12)
        assertTrue(settled!!.settled)
    }

    @Test
    fun `a wobbling estimate never counts as settled`() {
        val stabiliser = PitchStabiliser()
        var last: PitchReading? = null
        // Alternating a quarter tone either way: a real signal does not do this, and a
        // reading that does must not be allowed to fire the chime.
        repeat(20) { index ->
            val hz = if (index % 2 == 0) 110f else 110f * 1.03f
            last = stabiliser.accept(raw(hz), -20f)
        }
        assertFalse(last!!.settled)
    }

    @Test
    fun `the last good reading survives a short gap and then clears`() {
        val stabiliser = PitchStabiliser()
        feed(stabiliser, 110f, times = 8)

        // A decaying note drops the odd frame; the readout must not flicker to empty.
        repeat(PitchStabiliser.RELEASE_FRAMES) {
            assertEquals(110f, stabiliser.accept(null, -60f)!!.frequencyHz, 2f)
        }
        assertNull(stabiliser.accept(null, -60f))
    }

    @Test
    fun `smoothing settles a jittering estimate to well under a cent`() {
        val stabiliser = PitchStabiliser()
        var last: PitchReading? = null
        // Plus or minus three cents of frame to frame noise, which is what a real
        // detector produces on a held note.
        repeat(60) { index ->
            val jitter = if (index % 3 == 0) 1.0017f else if (index % 3 == 1) 0.9983f else 1f
            last = stabiliser.accept(raw(146.83f * jitter), -20f)
        }
        val cents = abs(Note.centsBetween(last!!.frequencyHz, 146.83f))
        assertTrue("Settled to $cents cents off", cents < 1.5f)
    }
}
