package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.guitartuner.R

/**
 * Google Sans Flex, the typeface the Pixel apps use, bundled rather than downloaded.
 *
 * It is on Google Fonts under the OFL, so shipping it in an open source app is fine (see
 * `LICENSE-GoogleSansFlex.txt` in the repository root). Bundling beats the downloadable-fonts provider here: three static
 * instances are 384 KB total, they need no Play Services, and there is no first-frame flash
 * while a request resolves.
 */
private val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

/**
 * The expressive type scale, tightened.
 *
 * Material 3 Expressive leans on weight and tight tracking rather than size alone, so the display
 * and headline styles here are heavier and pulled in at the letter spacing. That is most of what
 * separates a current Android app from one that looks like it was built in 2021.
 */
private val Default = Typography()

val GuitarTunerTypography = Typography(
    displayLarge = Default.displayLarge.withFont(FontWeight.Bold, (-1.5).sp),
    displayMedium = Default.displayMedium.withFont(FontWeight.Bold, (-1.0).sp),
    displaySmall = Default.displaySmall.withFont(FontWeight.Bold, (-0.5).sp),
    headlineLarge = Default.headlineLarge.withFont(FontWeight.Bold, (-0.5).sp),
    headlineMedium = Default.headlineMedium.withFont(FontWeight.Bold, (-0.25).sp),
    headlineSmall = Default.headlineSmall.withFont(FontWeight.Bold),
    titleLarge = Default.titleLarge.withFont(FontWeight.Bold, (-0.2).sp),
    titleMedium = Default.titleMedium.withFont(FontWeight.Medium),
    titleSmall = Default.titleSmall.withFont(FontWeight.Medium),
    bodyLarge = Default.bodyLarge.withFont(FontWeight.Normal),
    bodyMedium = Default.bodyMedium.withFont(FontWeight.Normal),
    bodySmall = Default.bodySmall.withFont(FontWeight.Normal),
    labelLarge = Default.labelLarge.withFont(FontWeight.Medium),
    labelMedium = Default.labelMedium.withFont(FontWeight.Medium),
    labelSmall = Default.labelSmall.withFont(FontWeight.Medium),
)

private fun TextStyle.withFont(
    weight: FontWeight,
    letterSpacing: androidx.compose.ui.unit.TextUnit = this.letterSpacing,
) = copy(
    fontFamily = GoogleSansFlex,
    fontWeight = weight,
    letterSpacing = letterSpacing,
)
