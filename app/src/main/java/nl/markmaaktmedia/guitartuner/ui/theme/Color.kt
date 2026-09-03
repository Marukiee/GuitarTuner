package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette used when the wallpaper cannot supply one, or when the user turns
 * dynamic colour off.
 *
 * It is the same indigo and rose the other Mark apps run on, kept identical on purpose:
 * these three apps sit next to each other on a home screen and share a launcher
 * background, so a tuner with its own hue would read as a stranger. The tuning signals
 * (flat, sharp, in tune) are the only colours this app adds, and they live in
 * [TunerSignalColors] rather than in a Material role, because they have to mean the
 * same thing on every wallpaper.
 */
object TunerPalette {
    val Indigo10 = Color(0xFF0B0A2B)
    val Indigo20 = Color(0xFF1B1A4A)
    val Indigo30 = Color(0xFF2E2C6B)
    val Indigo40 = Color(0xFF454391)
    val Indigo50 = Color(0xFF5B5BD6)
    val Indigo70 = Color(0xFF9E9DF0)
    val Indigo80 = Color(0xFFBEBDF8)
    val Indigo90 = Color(0xFFE2E1FF)
    val Indigo95 = Color(0xFFF1F0FF)

    val Rose20 = Color(0xFF561433)
    val Rose30 = Color(0xFF742C4B)
    val Rose40 = Color(0xFF934464)
    val Rose80 = Color(0xFFFFB0CB)
    val Rose90 = Color(0xFFFFD9E4)

    val Neutral0 = Color(0xFF000000)
    val Neutral6 = Color(0xFF0E0E13)
    val Neutral10 = Color(0xFF15141B)
    val Neutral12 = Color(0xFF1A1922)
    val Neutral17 = Color(0xFF232230)
    val Neutral22 = Color(0xFF2C2B3A)
    val Neutral24 = Color(0xFF302F3F)
    val Neutral80 = Color(0xFFC8C5D5)
    val Neutral90 = Color(0xFFE5E1F0)
    val Neutral95 = Color(0xFFF3F0FB)
    val Neutral98 = Color(0xFFFCFAFF)
    val Neutral100 = Color(0xFFFFFFFF)

    val Error10 = Color(0xFF410002)
    val Error40 = Color(0xFFBA1A1A)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)
}

/**
 * The three things the meter has to say, as colour.
 *
 * Deliberately outside the colour scheme. Dynamic colour gives no guarantee that any
 * role is warm or cool, and the entire readability of the meter rests on flat being
 * warm, sharp being cool, and in tune being green. Wire these to `primary` and on a
 * blue wallpaper the one state the player is hunting for lands next to the state beside
 * it.
 *
 * They do follow the scheme's polarity, which is not cosmetic: in a light scheme these
 * are dark tones carrying white text, in a dark scheme light tones carrying dark text.
 * Skip that and the cents readout is white on a pale ground for half the meter.
 */
data class TunerSignalColors(
    val flat: Color,
    val sharp: Color,
    val inTune: Color,
    val onSignal: Color,
    /** The meter's own track, one step above the surface it sits on. */
    val track: Color,
    /** True when the pure black surface is in use, so a component can skip its tint. */
    val isPureBlack: Boolean,
) {
    companion object {
        fun of(dark: Boolean, pureBlack: Boolean) = if (dark) {
            TunerSignalColors(
                flat = Color(0xFFFFBE85),
                sharp = Color(0xFF9CC6FF),
                inTune = Color(0xFF7BE3A3),
                onSignal = Color(0xFF10231A),
                track = Color(0xFF2C2B3A),
                isPureBlack = pureBlack,
            )
        } else {
            TunerSignalColors(
                flat = Color(0xFFB54708),
                sharp = Color(0xFF14479E),
                inTune = Color(0xFF1B7A3E),
                onSignal = Color(0xFFFFFFFF),
                track = Color(0xFFE2DFEC),
                isPureBlack = false,
            )
        }
    }
}

val TunerLightColors = lightColorScheme(
    primary = TunerPalette.Indigo50,
    onPrimary = TunerPalette.Neutral100,
    primaryContainer = TunerPalette.Indigo90,
    onPrimaryContainer = TunerPalette.Indigo20,
    secondary = TunerPalette.Indigo40,
    onSecondary = TunerPalette.Neutral100,
    secondaryContainer = TunerPalette.Indigo95,
    onSecondaryContainer = TunerPalette.Indigo30,
    tertiary = TunerPalette.Rose40,
    onTertiary = TunerPalette.Neutral100,
    tertiaryContainer = TunerPalette.Rose90,
    onTertiaryContainer = TunerPalette.Rose20,
    background = TunerPalette.Neutral98,
    onBackground = TunerPalette.Neutral10,
    surface = TunerPalette.Neutral98,
    onSurface = TunerPalette.Neutral10,
    surfaceVariant = TunerPalette.Neutral95,
    onSurfaceVariant = Color(0xFF49475A),
    surfaceContainerLowest = TunerPalette.Neutral100,
    surfaceContainerLow = Color(0xFFFAF7FF),
    surfaceContainer = Color(0xFFF4F1FC),
    surfaceContainerHigh = Color(0xFFEEEBF7),
    surfaceContainerHighest = Color(0xFFE8E5F2),
    outline = Color(0xFF7B7890),
    outlineVariant = Color(0xFFCBC7DA),
    error = TunerPalette.Error40,
    onError = TunerPalette.Neutral100,
    errorContainer = TunerPalette.Error90,
    onErrorContainer = TunerPalette.Error10,
    inverseSurface = TunerPalette.Neutral17,
    inverseOnSurface = TunerPalette.Neutral95,
    inversePrimary = TunerPalette.Indigo80,
)

val TunerDarkColors = darkColorScheme(
    primary = TunerPalette.Indigo80,
    onPrimary = TunerPalette.Indigo20,
    primaryContainer = TunerPalette.Indigo30,
    onPrimaryContainer = TunerPalette.Indigo90,
    secondary = TunerPalette.Indigo70,
    onSecondary = TunerPalette.Indigo20,
    secondaryContainer = TunerPalette.Indigo30,
    onSecondaryContainer = TunerPalette.Indigo90,
    tertiary = TunerPalette.Rose80,
    onTertiary = TunerPalette.Rose20,
    tertiaryContainer = TunerPalette.Rose30,
    onTertiaryContainer = TunerPalette.Rose90,
    background = TunerPalette.Neutral10,
    onBackground = TunerPalette.Neutral90,
    surface = TunerPalette.Neutral10,
    onSurface = TunerPalette.Neutral90,
    surfaceVariant = TunerPalette.Neutral22,
    onSurfaceVariant = TunerPalette.Neutral80,
    surfaceContainerLowest = TunerPalette.Neutral6,
    surfaceContainerLow = TunerPalette.Neutral12,
    surfaceContainer = TunerPalette.Neutral17,
    surfaceContainerHigh = TunerPalette.Neutral22,
    surfaceContainerHighest = TunerPalette.Neutral24,
    outline = Color(0xFF908DA3),
    outlineVariant = Color(0xFF49475A),
    error = TunerPalette.Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = TunerPalette.Error90,
    inverseSurface = TunerPalette.Neutral90,
    inverseOnSurface = TunerPalette.Neutral17,
    inversePrimary = TunerPalette.Indigo50,
)
