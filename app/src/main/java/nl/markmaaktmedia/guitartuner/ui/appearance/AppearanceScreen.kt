package nl.markmaaktmedia.guitartuner.ui.appearance

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.ContentRow
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.settings.PageFrame
import nl.markmaaktmedia.guitartuner.ui.theme.ColourSeed
import nl.markmaaktmedia.guitartuner.ui.theme.PaletteStyle
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.isDark
import nl.markmaaktmedia.guitartuner.ui.theme.resolve

/**
 * The colours of the app: where the accent comes from, and how it is spread over the palette.
 * Every choice is drawn in the scheme it would produce, so you compare real results instead of
 * names, and a live tuner above them shows the choice in use.
 */
@Composable
fun AppearanceScreen(viewModel: TunerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // What the theme is wearing is what the previews should be compared against.
    val appearance = state.appearance
    val dark = appearance.isDark()

    PageFrame(stringResource(R.string.settings_appearance), onBack) {
        Spacer(Modifier.height(4.dp))
        LiveScreenPreview()
        Spacer(Modifier.height(8.dp))

        SectionHeader(stringResource(R.string.section_colours))
        SettingsGroup {
            ContentRow(0, 2, TunerIcons.Palette, stringResource(R.string.appearance_accent)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Wallpaper is one of the tiles, not a switch above them: it is a choice
                    // between the same things, and a switch made the accent tiles look usable
                    // while they were ignored.
                    ColourSeed.entries.chunked(SeedColumns).forEach { rowSeeds ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowSeeds.forEach { seed ->
                                SeedPreviewTile(
                                    seed = seed.resolve(context),
                                    style = appearance.style,
                                    dark = dark,
                                    pureBlack = appearance.pureBlack,
                                    selected = appearance.seed == seed,
                                    label = stringResource(seedLabel(seed)),
                                    onClick = { viewModel.setAppearance(appearance.copy(seed = seed)) },
                                    modifier = Modifier.weight(1f),
                                    overlay = { scheme ->
                                        if (seed == ColourSeed.WALLPAPER) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(scheme.surface.copy(alpha = 0.9f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(TunerIcons.Image, null, tint = scheme.onSurface, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Note(visible = appearance.seed == ColourSeed.WALLPAPER, text = stringResource(R.string.colours_wallpaper_note))
                    Note(visible = appearance.style == PaletteStyle.MONOCHROME, text = stringResource(R.string.colours_mono_note))
                }
            }
            ContentRow(1, 2, TunerIcons.Palette, stringResource(R.string.appearance_style)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val seed = appearance.seed.resolve(context)
                    PaletteStyle.entries.chunked(StyleColumns).forEach { rowStyles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowStyles.forEach { style ->
                                StylePreviewCard(
                                    style = style,
                                    seed = seed,
                                    dark = dark,
                                    pureBlack = appearance.pureBlack,
                                    selected = appearance.style == style,
                                    label = stringResource(styleLabel(style)),
                                    onClick = { viewModel.setAppearance(appearance.copy(style = style)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = appearance.style,
                        transitionSpec = { fadeIn(TunerMotion.fadeSpec()) togetherWith fadeOut(TunerMotion.fadeSpec()) },
                        label = "styleDescription",
                    ) { style ->
                        Text(
                            text = stringResource(styleDescription(style)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A line of explanation that folds away when it stops being true. */
@Composable
private fun Note(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(TunerMotion.sizeSpring()) + fadeIn(TunerMotion.fadeSpec()),
        exit = shrinkVertically(TunerMotion.sizeSpring()) + fadeOut(TunerMotion.fadeSpec()),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private const val SeedColumns = 4
private const val StyleColumns = 3

@StringRes
internal fun seedLabel(seed: ColourSeed): Int = when (seed) {
    ColourSeed.WALLPAPER -> R.string.colours_seed_wallpaper
    ColourSeed.INDIGO -> R.string.colours_seed_indigo
    ColourSeed.OCEAN -> R.string.colours_seed_ocean
    ColourSeed.FOREST -> R.string.colours_seed_forest
    ColourSeed.AMBER -> R.string.colours_seed_amber
    ColourSeed.ROSE -> R.string.colours_seed_rose
    ColourSeed.PLUM -> R.string.colours_seed_plum
    ColourSeed.SLATE -> R.string.colours_seed_slate
}

@StringRes
internal fun styleLabel(style: PaletteStyle): Int = when (style) {
    PaletteStyle.TONAL_SPOT -> R.string.colours_style_tonal_spot
    PaletteStyle.VIBRANT -> R.string.colours_style_vibrant
    PaletteStyle.EXPRESSIVE -> R.string.colours_style_expressive
    PaletteStyle.FRUIT_SALAD -> R.string.colours_style_fruit_salad
    PaletteStyle.FIDELITY -> R.string.colours_style_fidelity
    PaletteStyle.MONOCHROME -> R.string.colours_style_monochrome
}

@StringRes
private fun styleDescription(style: PaletteStyle): Int = when (style) {
    PaletteStyle.TONAL_SPOT -> R.string.colours_style_tonal_spot_desc
    PaletteStyle.VIBRANT -> R.string.colours_style_vibrant_desc
    PaletteStyle.EXPRESSIVE -> R.string.colours_style_expressive_desc
    PaletteStyle.FRUIT_SALAD -> R.string.colours_style_fruit_salad_desc
    PaletteStyle.FIDELITY -> R.string.colours_style_fidelity_desc
    PaletteStyle.MONOCHROME -> R.string.colours_style_monochrome_desc
}
