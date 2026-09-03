package nl.markmaaktmedia.guitartuner.domain

import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tuning tables are data, and data is where a tuner goes wrong silently: a wrong MIDI
 * number does not crash, it just tunes the instrument to the wrong note.
 */
class TuningsTest {

    @Test
    fun `standard guitar tuning is E A D G B E`() {
        val standard = Instrument.ACOUSTIC.defaultTuning
        assertEquals("E A D G B E", standard.notation)
        assertEquals(82.41f, standard.strings.first().targetHz(440f), 0.02f)
        assertEquals(329.63f, standard.strings.last().targetHz(440f), 0.02f)
    }

    @Test
    fun `every tuning has one string per note and a unique id`() {
        Instrument.entries.forEach { instrument ->
            val ids = instrument.tunings.map { it.id }
            assertEquals("${instrument.name} has duplicate tuning ids", ids.size, ids.toSet().size)
            instrument.tunings.forEach { tuning ->
                assertEquals(tuning.midi.size, tuning.strings.size)
                assertEquals(tuning.midi.size, tuning.ascendingByPitch.size)
                assertTrue("${instrument.name}/${tuning.id} has no strings", tuning.stringCount > 0)
            }
        }
    }

    @Test
    fun `a ukulele in standard C is re-entrant and a low G one is not`() {
        val uke = Instrument.UKULELE
        assertTrue(uke.tuningById("standard")!!.isReentrant)
        assertFalse(uke.tuningById("low_g")!!.isReentrant)
    }

    @Test
    fun `auto advance walks a re-entrant ukulele by pitch`() {
        val standard = Instrument.UKULELE.tuningById("standard")!!
        // Pegs are G C E A; the tuning order has to be C E G A.
        assertEquals(listOf("C4", "E4", "G4", "A4"), standard.ascendingByPitch.map { it.fullLabel })
    }

    @Test
    fun `every detection range covers its own strings with headroom`() {
        Instrument.entries.forEach { instrument ->
            instrument.tunings.forEach { tuning ->
                val lowest = tuning.ascendingByPitch.first().targetHz(440f)
                val highest = tuning.ascendingByPitch.last().targetHz(440f)
                assertTrue(
                    "${instrument.name}/${tuning.id} cannot reach its own low string",
                    tuning.minDetectableHz < lowest,
                )
                assertTrue(
                    "${instrument.name}/${tuning.id} cannot reach its own high string",
                    tuning.maxDetectableHz > highest,
                )
            }
        }
    }

    @Test
    fun `drop D lowers only the sixth string`() {
        val standard = Instrument.ACOUSTIC.tuningById("standard")!!
        val dropD = Instrument.ACOUSTIC.tuningById("drop_d")!!
        assertEquals(standard.midi.drop(1), dropD.midi.drop(1))
        assertEquals(standard.midi.first() - 2, dropD.midi.first())
    }

    @Test
    fun `each instrument is visually distinguishable from the others`() {
        // The complaint that started this: a picker where every entry drew the same head.
        // Layout plus string count is what the silhouette is built from, so no two
        // instruments may share both.
        val signatures = Instrument.entries.map {
            Triple(it.layout, it.defaultTuning.stringCount, it.headstockScale)
        }
        assertEquals(signatures.size, signatures.toSet().size)
    }
}
