package nl.markmaaktmedia.guitartuner.domain.model

import androidx.annotation.StringRes
import nl.markmaaktmedia.guitartuner.R

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
    @StringRes val nameRes: Int,
    /**
     * The name on a picker card, which is 112dp wide and has room for one line.
     * "Acoustic guitar" wraps there and a wrapped label under a drawing looks like a
     * mistake, so the card drops the word every entry in that row shares anyway.
     */
    @StringRes val shortNameRes: Int,
    /** One line in the picker saying what actually differs about this instrument. */
    @StringRes val subtitleRes: Int,
    val layout: HeadstockLayout,
    val headstockScale: HeadstockScale,
    val tunings: List<Tuning>,
) {
    ACOUSTIC(
        nameRes = R.string.instrument_acoustic,
        shortNameRes = R.string.instrument_acoustic_short,
        subtitleRes = R.string.instrument_acoustic_sub,
        layout = HeadstockLayout.THREE_PER_SIDE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.acousticGuitar,
    ),
    ELECTRIC(
        nameRes = R.string.instrument_electric,
        shortNameRes = R.string.instrument_electric_short,
        subtitleRes = R.string.instrument_electric_sub,
        layout = HeadstockLayout.INLINE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.electricGuitar,
    ),
    GUITAR_7(
        nameRes = R.string.instrument_guitar_7,
        shortNameRes = R.string.instrument_guitar_7_short,
        subtitleRes = R.string.instrument_guitar_7_sub,
        layout = HeadstockLayout.FOUR_THREE,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.sevenString,
    ),
    BASS_4(
        nameRes = R.string.instrument_bass_4,
        shortNameRes = R.string.instrument_bass_4_short,
        subtitleRes = R.string.instrument_bass_4_sub,
        layout = HeadstockLayout.TWO_PER_SIDE,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.bassFour,
    ),
    BASS_5(
        nameRes = R.string.instrument_bass_5,
        shortNameRes = R.string.instrument_bass_5_short,
        subtitleRes = R.string.instrument_bass_5_sub,
        layout = HeadstockLayout.INLINE,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.bassFive,
    ),
    BASS_6(
        nameRes = R.string.instrument_bass_6,
        shortNameRes = R.string.instrument_bass_6_short,
        subtitleRes = R.string.instrument_bass_6_sub,
        layout = HeadstockLayout.INLINE,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.bassSix,
    ),
    UKULELE(
        nameRes = R.string.instrument_ukulele,
        shortNameRes = R.string.instrument_ukulele_short,
        subtitleRes = R.string.instrument_ukulele_sub,
        layout = HeadstockLayout.TWO_PER_SIDE,
        headstockScale = HeadstockScale.SMALL,
        tunings = Tunings.ukulele,
    ),
    BANJO(
        nameRes = R.string.instrument_banjo,
        shortNameRes = R.string.instrument_banjo_short,
        subtitleRes = R.string.instrument_banjo_sub,
        layout = HeadstockLayout.BANJO,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.banjo,
    ),
    MANDOLIN(
        nameRes = R.string.instrument_mandolin,
        shortNameRes = R.string.instrument_mandolin_short,
        subtitleRes = R.string.instrument_mandolin_sub,
        layout = HeadstockLayout.PAIRED_FOUR,
        headstockScale = HeadstockScale.SMALL,
        tunings = Tunings.mandolin,
    ),
    VIOLIN(
        nameRes = R.string.instrument_violin,
        shortNameRes = R.string.instrument_violin_short,
        subtitleRes = R.string.instrument_violin_sub,
        layout = HeadstockLayout.SCROLL,
        headstockScale = HeadstockScale.MEDIUM,
        tunings = Tunings.violin,
    ),
    CELLO(
        nameRes = R.string.instrument_cello,
        shortNameRes = R.string.instrument_cello_short,
        subtitleRes = R.string.instrument_cello_sub,
        layout = HeadstockLayout.SCROLL,
        headstockScale = HeadstockScale.LARGE,
        tunings = Tunings.cello,
    );

    val defaultTuning: Tuning get() = tunings.first()

    fun tuningById(id: String): Tuning? = tunings.firstOrNull { it.id == id }
}
