package nl.markmaaktmedia.guitartuner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode

/**
 * Fallback palette for Android 11 and older, where there is no wallpaper to derive from.
 * Warm amber, because the tuner's "flat" end of the meter is warm and this keeps the app
 * recognisable rather than defaulting to Compose purple.
 */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF8A5100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF735943),
    tertiary = Color(0xFF5B6236),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C0A5),
    tertiary = Color(0xFFC2CB99),
)

/**
 * The two ends of the tuning meter.
 *
 * These are deliberately *not* taken from the colour scheme. Dynamic colour gives no guarantee
 * that any scheme role is warm or cool, and the whole point of the gradient is that flat reads as
 * warm and sharp reads as cool regardless of wallpaper. Only the in-tune colour comes from the
 * scheme, which is where the wallpaper shows up.
 *
 * They follow the scheme's *polarity* though, and that part is not cosmetic. In a light scheme
 * `primary` is a dark tone with white `onPrimary`; in a dark scheme it is a light tone with dark
 * `onPrimary`. If these accents did not match, the cents readout would be white on a pale bubble
 * for half the meter's range. They are also darker (light theme) and lighter (dark theme) than
 * the first attempt, which was legible in principle and marginal in the hand.
 */
val ColorScheme.flatAccent: Color
    get() = if (isLight) Color(0xFFB54708) else Color(0xFFFFBE85)

val ColorScheme.sharpAccent: Color
    get() = if (isLight) Color(0xFF14479E) else Color(0xFF9CC6FF)

/**
 * "You are there." Green, not `primary`.
 *
 * The original spec put the scheme's primary at the centre of the meter, which is prettier and
 * says nothing: on a blue wallpaper it lands next to the sharp accent, so the one state the user
 * is actually hunting for looks like the state next to it. Green is the only colour that means
 * correct without being read, and it is worth breaking dynamic colour for exactly one signal.
 */
val ColorScheme.inTuneAccent: Color
    get() = if (isLight) Color(0xFF1B7A3E) else Color(0xFF7BE3A3)

/** A scheme is light when its surface is bright; there is no flag on ColorScheme itself. */
private val ColorScheme.isLight: Boolean
    get() = surface.luminance() > 0.5f

@Composable
fun GuitarTunerTheme(
    themeMode: ThemeMode = ThemeMode.System,
    /** Material You wallpaper extraction. Available from Android 12 (API 31). */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> FallbackDark
        else -> FallbackLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GuitarTunerTypography,
        content = content,
    )
}
