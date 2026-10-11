package nl.markmaaktmedia.guitartuner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode

/** How the app looks. All of it is a setting, none of it is baked in. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val seed: ColourSeed = ColourSeed.WALLPAPER,
    val style: PaletteStyle = PaletteStyle.TONAL_SPOT,
    val pureBlack: Boolean = false,
)

/**
 * The appearance the theme was given, for the one screen that edits it. A plain
 * compositionLocalOf, not a static one: only readers recompose when it changes, where a
 * static local would rebuild the whole app on every palette tap.
 */
val LocalAppearance = compositionLocalOf { Appearance() }

/** Whether this appearance is dark right now, which for System depends on the phone. */
@Composable
fun Appearance.isDark(): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

val LocalTunerSignals = staticCompositionLocalOf {
    TunerSignalColors.of(dark = true, pureBlack = false)
}

/** The scheme and the signal colours that go with it, so both blend by the same progress. */
private class Look(val scheme: ColorScheme, val signals: TunerSignalColors)

private fun blendLooks(from: Look, to: Look, fraction: Float) = Look(
    scheme = blendSchemes(from.scheme, to.scheme, fraction),
    signals = TunerSignalColors(
        flat = lerp(from.signals.flat, to.signals.flat, fraction),
        sharp = lerp(from.signals.sharp, to.signals.sharp, fraction),
        inTune = lerp(from.signals.inTune, to.signals.inTune, fraction),
        onSignal = lerp(from.signals.onSignal, to.signals.onSignal, fraction),
        track = lerp(from.signals.track, to.signals.track, fraction),
        isPureBlack = if (fraction < 0.5f) from.signals.isPureBlack else to.signals.isPureBlack,
    ),
)

@Composable
fun GuitarTunerTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.isDark()
    val context = LocalContext.current

    // The scheme is generated from a seed colour instead of taken from
    // dynamicLightColorScheme, which only ever produces Tonal Spot. Going through the
    // generator is what makes the palette style a real setting.
    val seed = appearance.seed.resolve(context)
    val usePureBlack = dark && appearance.pureBlack

    val target = remember(seed, appearance.style, dark, usePureBlack) {
        Look(
            scheme = appearanceScheme(seed, appearance.style, dark, usePureBlack),
            signals = TunerSignalColors.of(dark, usePureBlack),
        )
    }
    // One progress value carries every role, so a switch is a single soft transition.
    val look = rememberBlended(target, ::blendLooks)
    val scheme = look.scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Follows the animated background, so the icons flip half way through the fade
        // instead of a beat before the surface has started to change.
        val lightBackground = scheme.background.luminance() > 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBackground
                isAppearanceLightNavigationBars = lightBackground
            }
        }
    }

    CompositionLocalProvider(
        LocalTunerSignals provides look.signals,
        LocalAppearance provides appearance,
        // Text and icons with no colour of their own read this, and its default is black.
        // Anything not inside a Surface used to keep that black on a dark background, so the
        // theme sets it once at the root.
        LocalContentColor provides scheme.onBackground,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TunerTypography,
            shapes = TunerShapes,
            content = content,
        )
    }
}
