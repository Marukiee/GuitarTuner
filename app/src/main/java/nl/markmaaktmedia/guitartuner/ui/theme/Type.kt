package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.guitartuner.R

/**
 * Google Sans Flex with the rounded axis wound all the way up.
 *
 * One variable file covers every weight, which is the whole reason to use it: the static
 * instances this app used to bundle were three copies of the glyph set for three
 * weights. The ROND axis at 100 gives the rounded terminals, and that single setting is
 * what makes the type match the Material Symbols Rounded icon set instead of fighting
 * it.
 *
 * It is on Google Fonts under the OFL, so shipping it is fine; see
 * `LICENSE-GoogleSansFlex.txt` in the repository root.
 */
private const val RoundedAxis = 100f

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: FontWeight) = Font(
    resId = R.font.gflex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", RoundedAxis),
    ),
)

val GoogleSansRounded = FontFamily(
    flex(FontWeight.Light),
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Bold),
)

/**
 * Font padding is off across the scale. It is the difference between a row whose text
 * sits centred and one that looks a pixel or two high, which is exactly the kind of
 * thing that reads as sloppy without anyone being able to name it.
 */
private val noPadding = PlatformTextStyle(includeFontPadding = false)

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = GoogleSansRounded,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    platformStyle = noPadding,
)

val TunerTypography = Typography(
    displayLarge = style(FontWeight.Bold, 48, 56, -0.5f),
    displayMedium = style(FontWeight.Bold, 36, 44, -0.4f),
    displaySmall = style(FontWeight.SemiBold, 30, 38),
    headlineLarge = style(FontWeight.SemiBold, 32, 40, -0.3f),
    headlineMedium = style(FontWeight.SemiBold, 28, 36, -0.2f),
    headlineSmall = style(FontWeight.SemiBold, 23, 30),
    titleLarge = style(FontWeight.Medium, 21, 28),
    titleMedium = style(FontWeight.Medium, 17, 24, 0.1f),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.15f),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.2f),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.3f),
    labelLarge = style(FontWeight.Medium, 15, 20, 0.1f),
    labelMedium = style(FontWeight.Medium, 13, 16, 0.4f),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.4f),
)

/**
 * The note letter on the dial.
 *
 * Off the Material scale because nothing on that scale is big enough: this is the one
 * piece of text in the app that has to be legible from a metre away, on a guitar stand,
 * at a glance. Tracking is pulled in hard because two or three large glyphs at default
 * tracking drift apart.
 */
val NoteDisplayStyle = TextStyle(
    fontFamily = GoogleSansRounded,
    fontWeight = FontWeight.Bold,
    fontSize = 96.sp,
    lineHeight = 100.sp,
    letterSpacing = (-3).sp,
    platformStyle = noPadding,
)
