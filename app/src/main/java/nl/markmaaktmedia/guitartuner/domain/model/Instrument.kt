package nl.markmaaktmedia.guitartuner.domain.model

/**
 * One course of the instrument.
 *
 * [physicalIndex] is the position on the headstock counted from the *lowest sounding* string
 * outward (0 = the fat string closest to the player on a guitar). The headstock composable maps
 * this onto peg positions; the tuning sequence uses ascending pitch instead, which is not the
 * same order for re-entrant tunings such as a ukulele's high G.
 */
data class TuningString(
    val physicalIndex: Int,
    val note: Note,
) {
    val label: String get() = note.pitchClass
    val fullLabel: String get() = note.label

    fun targetHz(referenceHz: Float): Float = note.frequency(referenceHz)
}

/** How the pegs are arranged, so the headstock vector can lay itself out. */
enum class HeadstockLayout {
    /** 3 pegs left, 3 right, Gibson/acoustic style. */
    THREE_PER_SIDE,

    /** All pegs down one side, Fender style. */
    INLINE,

    /** 4 left, 3 right, for the 7-string. */
    FOUR_THREE,

    /** 2 per side, ukulele and other short headstocks. */
    TWO_PER_SIDE,
}

enum class Instrument(
    val displayName: String,
    val layout: HeadstockLayout,
    val strings: List<TuningString>,
) {
    ACOUSTIC_6(
        displayName = "Acoustic",
        layout = HeadstockLayout.THREE_PER_SIDE,
        strings = notes(40, 45, 50, 55, 59, 64), // E2 A2 D3 G3 B3 E4
    ),
    ELECTRIC_6(
        displayName = "Electric",
        layout = HeadstockLayout.INLINE,
        strings = notes(40, 45, 50, 55, 59, 64),
    ),
    ELECTRIC_7(
        displayName = "7-String",
        layout = HeadstockLayout.FOUR_THREE,
        strings = notes(35, 40, 45, 50, 55, 59, 64), // B1 + standard
    ),
    BASS_4(
        displayName = "Bass",
        layout = HeadstockLayout.TWO_PER_SIDE,
        strings = notes(28, 33, 38, 43), // E1 A1 D2 G2
    ),
    BASS_5(
        displayName = "Bass 5",
        layout = HeadstockLayout.INLINE,
        strings = notes(23, 28, 33, 38, 43), // B0 + E1 A1 D2 G2
    ),
    UKULELE(
        displayName = "Ukulele",
        layout = HeadstockLayout.TWO_PER_SIDE,
        // Re-entrant standard C tuning: physical order 4->1 is G4 C4 E4 A4.
        strings = notes(67, 60, 64, 69),
    );

    val stringCount: Int get() = strings.size

    /**
     * Strings ordered low -> high pitch. This is the order auto-advance walks, and for a ukulele
     * it deliberately differs from [strings]: C4, E4, G4, A4.
     */
    val ascendingByPitch: List<TuningString> = strings.sortedBy { it.note.midi }

    /** Lowest fundamental we must be able to resolve, minus a whole tone of drop-tune headroom. */
    val minDetectableHz: Float
        get() = ascendingByPitch.first().note.frequency() * 0.85f

    /** Highest fundamental, plus headroom. */
    val maxDetectableHz: Float
        get() = ascendingByPitch.last().note.frequency() * 1.25f
}

/**
 * Enum constant arguments are evaluated before the companion object exists, so this helper has to
 * live at file scope rather than inside [Instrument].
 */
private fun notes(vararg midi: Int): List<TuningString> =
    midi.mapIndexed { index, value -> TuningString(index, Note(value)) }
