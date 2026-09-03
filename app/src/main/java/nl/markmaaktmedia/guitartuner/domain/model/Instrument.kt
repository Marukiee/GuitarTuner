package nl.markmaaktmedia.guitartuner.domain.model

/**
 * One course of the instrument.
 *
 * [physicalIndex] is the position on the headstock counted from the string listed first,
 * which by convention is the one closest to the player: the fat low E on a guitar, the
 * drone on a banjo. The headstock composable maps this onto peg positions. The tuning
 * sequence uses ascending pitch instead, which is not the same order for re-entrant
 * tunings such as a ukulele's high G or a banjo's fifth string.
 */
data class TuningString(
    val physicalIndex: Int,
    val note: Note,
) {
    val label: String get() = note.pitchClass
    val fullLabel: String get() = note.label

    fun targetHz(referenceHz: Float): Float = note.frequency(referenceHz)
}

/**
 * How the pegs are arranged.
 *
 * This is what stops the instrument picker from being a list of identical drawings with
 * different words under them. Every family gets its own silhouette, because "acoustic"
 * and "electric" share a tuning and nothing else: one is a slotted 3-a-side head, the
 * other is six in a line.
 */
enum class HeadstockLayout {
    /** 3 pegs left, 3 right. Acoustic and Gibson style. */
    THREE_PER_SIDE,

    /** All pegs down one side, Fender style. Six string electric and five string bass. */
    INLINE,

    /** 4 left, 3 right, for the seven string. */
    FOUR_THREE,

    /** 2 per side on a short head. Four string bass and ukulele. */
    TWO_PER_SIDE,

    /** 4 on the head plus one peg partway down the neck: the banjo's fifth string. */
    BANJO,

    /** 4 per side, the courses paired. Mandolin. */
    PAIRED_FOUR,

    /** A carved scroll and a pegbox with the pegs through the cheeks. Bowed instruments. */
    SCROLL,
}

/** Rough physical size of the head, so a ukulele is not drawn at cello scale. */
enum class HeadstockScale { SMALL, MEDIUM, LARGE }

enum class Instrument(
    val displayName: String,
    /** One line in the picker saying what actually differs about this instrument. */
    val subtitle: String,
    val layout: HeadstockLayout,
    val headstockScale: HeadstockScale,
    val tunings: List<Tuning>,
) {
    ACOUSTIC(
        displayName = "Acoustic guitar",
        subtitle = "Six strings, slotted 3+3 head",
        layout = HeadstockLayout.THREE_PER_SIDE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.acousticGuitar,
    ),
    ELECTRIC(
        displayName = "Electric guitar",
        subtitle = "Six strings, six in a line",
        layout = HeadstockLayout.INLINE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.electricGuitar,
    ),
    GUITAR_7(
        displayName = "7-string guitar",
        subtitle = "A low B under standard tuning",
        layout = HeadstockLayout.FOUR_THREE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.sevenString,
    ),
    BASS_4(
        displayName = "Bass",
        subtitle = "Four strings, an octave below the guitar",
        layout = HeadstockLayout.TWO_PER_SIDE,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.bassFour,
    ),
    BASS_5(
        displayName = "5-string bass",
        subtitle = "Down to a low B at 31 Hz",
        layout = HeadstockLayout.INLINE,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.bassFive,
    ),
    UKULELE(
        displayName = "Ukulele",
        subtitle = "Four strings, re-entrant by default",
        layout = HeadstockLayout.TWO_PER_SIDE,
        headstockScale = HeadstockScale.SMALL,
        tunings = Tunings.ukulele,
    ),
    BANJO(
        displayName = "Banjo",
        subtitle = "Five strings, the drone tuned off the neck",
        layout = HeadstockLayout.BANJO,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.banjo,
    ),
    MANDOLIN(
        displayName = "Mandolin",
        subtitle = "Four courses in fifths, strung in pairs",
        layout = HeadstockLayout.PAIRED_FOUR,
        headstockScale = HeadstockScale.SMALL,
        tunings = Tunings.mandolin,
    ),
    VIOLIN(
        displayName = "Violin",
        subtitle = "Four strings in fifths, pegbox and scroll",
        layout = HeadstockLayout.SCROLL,
        headstockScale = HeadstockScale.SMALL,
        tunings = Tunings.violin,
    ),
    CELLO(
        displayName = "Cello",
        subtitle = "An octave and a fifth below the violin",
        layout = HeadstockLayout.SCROLL,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.cello,
    );

    val defaultTuning: Tuning get() = tunings.first()

    fun tuningById(id: String): Tuning? = tunings.firstOrNull { it.id == id }
}
