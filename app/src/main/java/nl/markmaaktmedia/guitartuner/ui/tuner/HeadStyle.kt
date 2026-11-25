package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The directions on the table while the head is being chosen. */
enum class HeadStyle {
    /** One soft slab with chunky pill tuners. */
    SOFT,

    /** An arched head whose tuners are the note buttons themselves. */
    BUTTONS,

    /** Stacked tiles with scalloped tuners. */
    BLOCKS,
}

/** Which direction is drawn. Only a debug intent changes it. */
object HeadStyleChoice {
    var current by mutableStateOf(HeadStyle.BUTTONS)
}

/** The colours of one head, all taken from the scheme so the guitar follows the wallpaper. */
internal data class HeadColors(
    val head: Color,
    val onHead: Color,
    val neck: Color,
    val fret: Color,
    val nut: Color,
    val knob: Color,
    val accent: Color,
    val post: Color,
    val postDot: Color,
)

internal fun ColorScheme.headColors(style: HeadStyle): HeadColors = when (style) {
    HeadStyle.SOFT -> HeadColors(
        head = primaryContainer,
        onHead = onPrimaryContainer,
        neck = surfaceContainerHighest,
        fret = outlineVariant,
        nut = onPrimaryContainer,
        knob = primary,
        accent = tertiary,
        post = surface,
        postDot = primary,
    )

    HeadStyle.BUTTONS -> HeadColors(
        head = tertiaryContainer,
        onHead = onTertiaryContainer,
        neck = surfaceContainerHighest,
        fret = outlineVariant,
        nut = onTertiaryContainer,
        knob = tertiaryContainer,
        accent = tertiary,
        post = onTertiaryContainer,
        postDot = tertiaryContainer,
    )

    HeadStyle.BLOCKS -> HeadColors(
        head = primaryContainer,
        onHead = onPrimaryContainer,
        neck = secondaryContainer,
        fret = lerp(secondaryContainer, onSecondaryContainer, 0.25f),
        nut = primary,
        knob = tertiary,
        accent = tertiary,
        post = primary,
        postDot = onPrimary,
    )
}
