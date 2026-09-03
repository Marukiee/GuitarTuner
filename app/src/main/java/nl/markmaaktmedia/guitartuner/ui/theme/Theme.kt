package nl.markmaaktmedia.guitartuner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode

val LocalTunerSignals = staticCompositionLocalOf {
    TunerSignalColors.of(dark = true, pureBlack = true)
}

@Composable
fun GuitarTunerTheme(
    themeMode: ThemeMode = ThemeMode.System,
    /** Material You wallpaper extraction. Available from Android 12 (API 31). */
    dynamicColor: Boolean = true,
    /**
     * True black for the backdrop. On by default: this app is looked at in a dark room
     * more often than not, and on an OLED panel the large flat areas of the dial go to
     * zero power.
     */
    pureBlack: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current

    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> TunerDarkColors
        else -> TunerLightColors
    }

    val usePureBlack = dark && pureBlack
    val target = if (usePureBlack) base.toPureBlack() else base

    // Every role is animated, so flipping theme or pure black crossfades the whole app
    // instead of swapping it in one frame.
    val scheme = target.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalTunerSignals provides TunerSignalColors.of(dark, usePureBlack),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TunerTypography,
            shapes = TunerShapes,
            content = content,
        )
    }
}

/**
 * Pushes the dark scheme down to true black.
 *
 * Not a blanket black: only the backdrop goes to #000000, and the container roles stay a
 * short ladder of very dark greys tinted towards the accent. The panel wins on the large
 * flat areas and cards still have an edge you can see, which is what most pure black
 * modes lose by painting everything the same colour.
 */
private fun ColorScheme.toPureBlack(): ColorScheme {
    fun tinted(alpha: Float) = surfaceContainerHighest.copy(alpha = alpha).compositeOverBlack()
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = tinted(0.05f),
        surfaceContainer = tinted(0.09f),
        surfaceContainerHigh = tinted(0.14f),
        surfaceContainerHighest = tinted(0.19f),
        surfaceVariant = tinted(0.12f),
        outlineVariant = tinted(0.26f),
    )
}

/** Flattens a translucent colour onto black, so the result is opaque. */
private fun Color.compositeOverBlack(): Color =
    Color(red = red * alpha, green = green * alpha, blue = blue * alpha, alpha = 1f)

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = TunerMotion.colourSpec<Color>()

    @Composable
    fun animate(target: Color, label: String) =
        animateColorAsState(targetValue = target, animationSpec = spec, label = label).value

    return copy(
        primary = animate(primary, "primary"),
        onPrimary = animate(onPrimary, "onPrimary"),
        primaryContainer = animate(primaryContainer, "primaryContainer"),
        onPrimaryContainer = animate(onPrimaryContainer, "onPrimaryContainer"),
        secondary = animate(secondary, "secondary"),
        onSecondary = animate(onSecondary, "onSecondary"),
        secondaryContainer = animate(secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animate(onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animate(tertiary, "tertiary"),
        onTertiary = animate(onTertiary, "onTertiary"),
        tertiaryContainer = animate(tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = animate(onTertiaryContainer, "onTertiaryContainer"),
        background = animate(background, "background"),
        onBackground = animate(onBackground, "onBackground"),
        surface = animate(surface, "surface"),
        onSurface = animate(onSurface, "onSurface"),
        surfaceVariant = animate(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animate(onSurfaceVariant, "onSurfaceVariant"),
        surfaceContainerLowest = animate(surfaceContainerLowest, "containerLowest"),
        surfaceContainerLow = animate(surfaceContainerLow, "containerLow"),
        surfaceContainer = animate(surfaceContainer, "container"),
        surfaceContainerHigh = animate(surfaceContainerHigh, "containerHigh"),
        surfaceContainerHighest = animate(surfaceContainerHighest, "containerHighest"),
        outline = animate(outline, "outline"),
        outlineVariant = animate(outlineVariant, "outlineVariant"),
        inverseSurface = animate(inverseSurface, "inverseSurface"),
        inverseOnSurface = animate(inverseOnSurface, "inverseOnSurface"),
        inversePrimary = animate(inversePrimary, "inversePrimary"),
        error = animate(error, "error"),
        onError = animate(onError, "onError"),
        errorContainer = animate(errorContainer, "errorContainer"),
        onErrorContainer = animate(onErrorContainer, "onErrorContainer"),
    )
}
