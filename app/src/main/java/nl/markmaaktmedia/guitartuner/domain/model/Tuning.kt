package nl.markmaaktmedia.guitartuner.domain.model

import androidx.annotation.StringRes
import nl.markmaaktmedia.guitartuner.R

/**
 * One named set of target notes for an instrument.
 *
 * Stored as MIDI note numbers rather than frequencies, so the whole app can be
 * re-referenced from A=440 to anything between 400 and 480 Hz by changing one float, and
 * nothing has to carry a frequency table.
 */
data class Tuning(
    val id: String,
    @StringRes val nameRes: Int,
    /** Notes in physical order: the string closest to the player first. */
    val midi: List<Int>,
) {
    val strings: List<TuningString> =
        midi.mapIndexed { index, value -> TuningString(index, Note(value)) }

    val stringCount: Int get() = strings.size

    /**
     * Strings ordered low to high in pitch. This is the order auto advance walks, and it
     * deliberately differs from [strings] for a re-entrant tuning: a standard ukulele
     * advances C4, E4, G4, A4 rather than following the pegs.
     */
    val ascendingByPitch: List<TuningString> = strings.sortedBy { it.note.midi }

    /** A compact spelling for the picker, e.g. "E A D G B E". */
    val notation: String = strings.joinToString(" ") { it.note.pitchClass }

    /** Lowest fundamental to resolve, with a whole tone of drop-tune headroom. */
    val minDetectableHz: Float get() = ascendingByPitch.first().note.frequency() * 0.85f

    /** Highest fundamental, plus headroom for a string tuned sharp. */
    val maxDetectableHz: Float get() = ascendingByPitch.last().note.frequency() * 1.25f

    /** True when the pegs and the pitch order disagree, which the UI says out loud. */
    val isReentrant: Boolean = strings.map { it.physicalIndex } != ascendingByPitch.map { it.physicalIndex }
}

/**
 * The tuning tables.
 *
 * Kept out of the [Instrument] enum because enum constant arguments are evaluated before
 * the companion object exists, so anything shared between constants has to live at file
 * or object scope.
 *
 * Only tunings that are actually played are here. A tuner that offers a hundred of them
 * makes the player scroll past ninety they will never use to reach the two they will.
 */
object Tunings {

    private fun t(id: String, @StringRes name: Int, vararg midi: Int) = Tuning(id, name, midi.toList())

    val acousticGuitar = listOf(
        t("standard", R.string.tuning_standard, 40, 45, 50, 55, 59, 64), // E2 A2 D3 G3 B3 E4
        t("drop_d", R.string.tuning_drop_d, 38, 45, 50, 55, 59, 64),
        t("half_down", R.string.tuning_half_down, 39, 44, 49, 54, 58, 63),
        t("whole_down", R.string.tuning_whole_down, 38, 43, 48, 53, 57, 62),
        t("open_g", R.string.tuning_open_g, 38, 43, 50, 55, 59, 62),
        t("open_d", R.string.tuning_open_d, 38, 45, 50, 54, 57, 62),
        t("open_e", R.string.tuning_open_e, 40, 47, 52, 56, 59, 64),
        t("open_a", R.string.tuning_open_a, 40, 45, 52, 57, 61, 64),
        t("open_c", R.string.tuning_open_c, 36, 43, 48, 55, 60, 64),
        t("dadgad", R.string.tuning_dadgad, 38, 45, 50, 55, 57, 62),
        t("double_drop_d", R.string.tuning_double_drop_d, 38, 45, 50, 55, 59, 62),
    )

    val electricGuitar = listOf(
        t("standard", R.string.tuning_standard, 40, 45, 50, 55, 59, 64),
        t("drop_d", R.string.tuning_drop_d, 38, 45, 50, 55, 59, 64),
        t("half_down", R.string.tuning_half_down, 39, 44, 49, 54, 58, 63),
        t("whole_down", R.string.tuning_whole_down, 38, 43, 48, 53, 57, 62),
        t("drop_c", R.string.tuning_drop_c, 36, 43, 48, 53, 57, 62),
        t("open_e", R.string.tuning_open_e, 40, 47, 52, 56, 59, 64),
        t("open_g", R.string.tuning_open_g, 38, 43, 50, 55, 59, 62),
        t("open_d", R.string.tuning_open_d, 38, 45, 50, 54, 57, 62),
        t("dadgad", R.string.tuning_dadgad, 38, 45, 50, 55, 57, 62),
    )

    val sevenString = listOf(
        t("standard", R.string.tuning_standard_b, 35, 40, 45, 50, 55, 59, 64),
        t("drop_a", R.string.tuning_drop_a, 33, 40, 45, 50, 55, 59, 64),
    )

    val bassFour = listOf(
        t("standard", R.string.tuning_standard, 28, 33, 38, 43), // E1 A1 D2 G2
        t("drop_d", R.string.tuning_drop_d, 26, 33, 38, 43),
        t("half_down", R.string.tuning_half_down, 27, 32, 37, 42),
        t("whole_down", R.string.tuning_whole_down, 26, 31, 36, 41),
    )

    val bassFive = listOf(
        t("standard", R.string.tuning_standard_b, 23, 28, 33, 38, 43), // B0 E1 A1 D2 G2
        t("tenor", R.string.tuning_high_c, 28, 33, 38, 43, 48),
    )

    val bassSix = listOf(
        t("standard", R.string.tuning_standard_b, 23, 28, 33, 38, 43, 48), // B0 E1 A1 D2 G2 C3
    )

    val ukulele = listOf(
        // The G is above the C, which is why auto advance follows pitch and not pegs.
        t("standard", R.string.tuning_standard_c, 67, 60, 64, 69), // G4 C4 E4 A4
        t("low_g", R.string.tuning_low_g, 55, 60, 64, 69),
        t("baritone", R.string.tuning_baritone, 50, 55, 59, 64), // D3 G3 B3 E4
        t("d_tuning", R.string.tuning_d_tuning, 69, 62, 66, 71),
    )

    val banjo = listOf(
        t("open_g", R.string.tuning_open_g, 67, 50, 55, 59, 62), // g4 D3 G3 B3 D4
        t("double_c", R.string.tuning_double_c, 67, 48, 55, 60, 62),
        t("open_d", R.string.tuning_open_d, 66, 50, 54, 57, 62),
        t("g_modal", R.string.tuning_g_modal, 67, 50, 55, 60, 62),
    )

    val mandolin = listOf(
        t("standard", R.string.tuning_standard, 55, 62, 69, 76), // G3 D4 A4 E5
    )

    val violin = listOf(
        t("standard", R.string.tuning_standard, 55, 62, 69, 76), // G3 D4 A4 E5
        t("viola", R.string.tuning_viola, 48, 55, 62, 69), // C3 G3 D4 A4
    )

    val cello = listOf(
        t("standard", R.string.tuning_standard, 36, 43, 50, 57), // C2 G2 D3 A3
    )
}
