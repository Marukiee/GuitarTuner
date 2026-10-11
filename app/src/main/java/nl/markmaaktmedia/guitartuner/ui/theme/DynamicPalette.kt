package nl.markmaaktmedia.guitartuner.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/**
 * How a seed colour is spread across the palette.
 *
 * These are the schemes the platform itself uses for Material You, exposed as a
 * choice rather than a fixed one. They are genuinely different characters and not
 * five names for the same thing: Tonal Spot is what Android ships and keeps the
 * accent close to the seed, Vibrant pushes chroma up, Expressive swings the
 * secondary and tertiary hues away from the seed, Fruit Salad shifts the whole
 * family, and Monochrome throws the hue away entirely.
 */
enum class PaletteStyle(val storageKey: String) {
    TONAL_SPOT("tonal_spot"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    FRUIT_SALAD("fruit_salad"),
    FIDELITY("fidelity"),
    MONOCHROME("monochrome");

    companion object {
        val default = TONAL_SPOT

        fun fromKey(value: String?): PaletteStyle =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/**
 * Where the accent colour comes from.
 *
 * Wallpaper is the default and the reason Material You exists. The fixed seeds are
 * for a wallpaper that produces a colour you dislike, or for a phone where the
 * wallpaper changes constantly and the app changing with it is a nuisance.
 */
enum class ColourSeed(val storageKey: String, val seed: Color) {
    WALLPAPER("wallpaper", Color(0xFF5B5BD6)),
    INDIGO("indigo", Color(0xFF5B5BD6)),
    OCEAN("ocean", Color(0xFF1E7C93)),
    FOREST("forest", Color(0xFF3F7D3C)),
    AMBER("amber", Color(0xFFB4761A)),
    ROSE("rose", Color(0xFFB5386B)),
    PLUM("plum", Color(0xFF7A4CC0)),
    SLATE("slate", Color(0xFF5A6472));

    companion object {
        val default = WALLPAPER

        fun fromKey(value: String?): ColourSeed =
            entries.firstOrNull { it.storageKey == value } ?: default

        /** Everything except wallpaper, which is presented as its own switch. */
        val swatches: List<ColourSeed> = entries.filter { it != WALLPAPER }
    }
}

/**
 * Turns a seed colour into a full Material 3 scheme.
 *
 * The colour utilities hand back roles one at a time as ARGB integers, so this maps
 * all of them across in one place. Doing it by hand rather than taking the platform's
 * dynamic scheme is what makes the palette style a real setting: `dynamicColorScheme`
 * only ever gives you Tonal Spot.
 */
fun buildColorScheme(
    seed: Color,
    style: PaletteStyle,
    dark: Boolean,
    contrast: Double = 0.0,
): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val scheme: DynamicScheme = when (style) {
        PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrast)
        PaletteStyle.VIBRANT -> SchemeVibrant(hct, dark, contrast)
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, contrast)
        PaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, contrast)
        PaletteStyle.FIDELITY -> SchemeFidelity(hct, dark, contrast)
        PaletteStyle.MONOCHROME -> SchemeMonochrome(hct, dark, contrast)
    }
    return scheme.toColorScheme()
}

@Suppress("DEPRECATION")
private fun DynamicScheme.toColorScheme(): ColorScheme {
    val roles = MaterialDynamicColors()
    fun role(get: MaterialDynamicColors.() -> com.google.android.material.color.utilities.DynamicColor) =
        Color(roles.get().getArgb(this))

    return ColorScheme(
        primary = role { primary() },
        onPrimary = role { onPrimary() },
        primaryContainer = role { primaryContainer() },
        onPrimaryContainer = role { onPrimaryContainer() },
        inversePrimary = role { inversePrimary() },
        secondary = role { secondary() },
        onSecondary = role { onSecondary() },
        secondaryContainer = role { secondaryContainer() },
        onSecondaryContainer = role { onSecondaryContainer() },
        tertiary = role { tertiary() },
        onTertiary = role { onTertiary() },
        tertiaryContainer = role { tertiaryContainer() },
        onTertiaryContainer = role { onTertiaryContainer() },
        background = role { background() },
        onBackground = role { onBackground() },
        surface = role { surface() },
        onSurface = role { onSurface() },
        surfaceVariant = role { surfaceVariant() },
        onSurfaceVariant = role { onSurfaceVariant() },
        surfaceTint = role { primary() },
        inverseSurface = role { inverseSurface() },
        inverseOnSurface = role { inverseOnSurface() },
        error = role { error() },
        onError = role { onError() },
        errorContainer = role { errorContainer() },
        onErrorContainer = role { onErrorContainer() },
        outline = role { outline() },
        outlineVariant = role { outlineVariant() },
        scrim = role { scrim() },
        surfaceBright = role { surfaceBright() },
        surfaceDim = role { surfaceDim() },
        surfaceContainer = role { surfaceContainer() },
        surfaceContainerHigh = role { surfaceContainerHigh() },
        surfaceContainerHighest = role { surfaceContainerHighest() },
        surfaceContainerLow = role { surfaceContainerLow() },
        surfaceContainerLowest = role { surfaceContainerLowest() },
    )
}

/**
 * The colour a seed choice stands for right now.
 *
 * Wallpaper asks the system for the accent it derived from the wallpaper, everything
 * else is a fixed colour. Kept in one place so the theme and the Appearance previews
 * can never disagree about what "Wallpaper" looks like.
 */
fun ColourSeed.resolve(context: Context): Color = when {
    this != ColourSeed.WALLPAPER -> seed
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Color(context.getColor(android.R.color.system_accent1_500))
    else -> TunerPalette.Indigo50
}

/**
 * The scheme the app actually wears for a given setting.
 *
 * The Appearance previews call this too, so a preview card is not an approximation of
 * what will happen when you tap it but the very scheme that will be applied.
 */
fun appearanceScheme(seed: Color, style: PaletteStyle, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val generated = buildColorScheme(seed = seed, style = style, dark = dark)
    return if (dark && pureBlack) generated.toPureBlack() else generated
}

/**
 * Pure black for the backdrop, with a short ladder of tinted greys for the containers.
 *
 * Only the large flat areas go to #000000. Cards keep an edge you can see, which is
 * what most pure black modes lose by painting everything the same colour.
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

private fun Color.compositeOverBlack(): Color =
    Color(red = red * alpha, green = green * alpha, blue = blue * alpha, alpha = 1f)

/**
 * Every colour role, blended. One function so no role can be forgotten: a role left
 * out of this list would jump to its new value on the first frame of a theme change,
 * which is the flash this exists to prevent. SchemeBlendTest checks the list against
 * the real class.
 */
fun blendSchemes(from: ColorScheme, to: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return from
    if (fraction >= 1f) return to
    fun mix(a: Color, b: Color) = lerp(a, b, fraction)
    return from.copy(
        primary = mix(from.primary, to.primary),
        onPrimary = mix(from.onPrimary, to.onPrimary),
        primaryContainer = mix(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = mix(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = mix(from.inversePrimary, to.inversePrimary),
        secondary = mix(from.secondary, to.secondary),
        onSecondary = mix(from.onSecondary, to.onSecondary),
        secondaryContainer = mix(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = mix(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = mix(from.tertiary, to.tertiary),
        onTertiary = mix(from.onTertiary, to.onTertiary),
        tertiaryContainer = mix(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = mix(from.onTertiaryContainer, to.onTertiaryContainer),
        background = mix(from.background, to.background),
        onBackground = mix(from.onBackground, to.onBackground),
        surface = mix(from.surface, to.surface),
        onSurface = mix(from.onSurface, to.onSurface),
        surfaceVariant = mix(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = mix(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = mix(from.surfaceTint, to.surfaceTint),
        inverseSurface = mix(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = mix(from.inverseOnSurface, to.inverseOnSurface),
        error = mix(from.error, to.error),
        onError = mix(from.onError, to.onError),
        errorContainer = mix(from.errorContainer, to.errorContainer),
        onErrorContainer = mix(from.onErrorContainer, to.onErrorContainer),
        outline = mix(from.outline, to.outline),
        outlineVariant = mix(from.outlineVariant, to.outlineVariant),
        scrim = mix(from.scrim, to.scrim),
        surfaceBright = mix(from.surfaceBright, to.surfaceBright),
        surfaceDim = mix(from.surfaceDim, to.surfaceDim),
        surfaceContainer = mix(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = mix(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = mix(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = mix(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = mix(from.surfaceContainerLowest, to.surfaceContainerLowest),
    )
}

/**
 * Soft in and soft out, unlike [TunerMotion.Standard] which is nearly all in the
 * first tenth. That easing is right for a small chip and wrong for a whole screen
 * changing from light to dark: it puts most of the change in the first two frames,
 * which reads as a flash followed by a slow tail.
 */
private val ThemeEasing = CubicBezierEasing(0.32f, 0f, 0.16f, 1f)
private const val ThemeMillis = 460

/**
 * Follows [target] with one shared progress value instead of one animation per role.
 *
 * Every colour therefore moves in lockstep along the same curve, and a change that
 * arrives half way through starts from what is on screen right now instead of from
 * either end, so quickly tapping through palettes never snaps. The first composition
 * is not animated: the app starts in its look rather than fading into it.
 *
 * [target] must keep its identity until the look really changes (wrap it in
 * `remember`), because a new instance is what starts the next transition.
 */
@Composable
fun <T> rememberBlended(target: T, blend: (from: T, to: T, fraction: Float) -> T): T {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = blend(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ThemeMillis, easing = ThemeEasing))
    }
    return blend(from, to, progress.value)
}
