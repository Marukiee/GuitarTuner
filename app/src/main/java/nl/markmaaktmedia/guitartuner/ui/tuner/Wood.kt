package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.ui.graphics.Color
import nl.markmaaktmedia.guitartuner.domain.model.Instrument

/**
 * What an instrument's head is made of, as colours.
 *
 * The head is the one object on the screen that is not part of the app's palette. It is a
 * piece of wood with chrome on it, so it keeps the same colours in light and dark and on every
 * wallpaper: a guitar that turned lavender with the accent would stop looking like a guitar.
 * Each family gets its own wood, because a violin is not mahogany and a ukulele is not walnut.
 */
internal data class Wood(
    val top: Color,
    val bottom: Color,
    /** Cream edge round the head, the binding every acoustic has. */
    val binding: Color = Color(0xFFF0E3C5),
    /** The dark face of the neck under the nut. */
    val fretboardTop: Color = Color(0xFF34211A),
    val fretboardBottom: Color = Color(0xFF1C110D),
    /** Bowed instruments have ebony pegs where a guitar has chrome ones. */
    val ebonyPegs: Boolean = false,
)

internal fun Instrument.wood(): Wood = when (this) {
    Instrument.ACOUSTIC, Instrument.GUITAR_7, Instrument.BANJO ->
        Wood(top = Color(0xFF9C5632), bottom = Color(0xFF63311A))

    Instrument.ELECTRIC ->
        Wood(top = Color(0xFFD69C57), bottom = Color(0xFF9B5B2A))

    Instrument.BASS_4, Instrument.BASS_5, Instrument.BASS_6 ->
        Wood(top = Color(0xFF74503A), bottom = Color(0xFF402B1F))

    Instrument.UKULELE ->
        Wood(top = Color(0xFFBF8841), bottom = Color(0xFF8A5722))

    Instrument.MANDOLIN ->
        Wood(top = Color(0xFFCC7530), bottom = Color(0xFF6D2B12))

    Instrument.VIOLIN, Instrument.CELLO ->
        Wood(top = Color(0xFFB8632B), bottom = Color(0xFF79351A), ebonyPegs = true)
}
