package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.appearance.seedLabel
import nl.markmaaktmedia.guitartuner.ui.appearance.styleLabel
import nl.markmaaktmedia.guitartuner.ui.components.ActionRow
import nl.markmaaktmedia.guitartuner.ui.components.ContentRow
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SegmentedPillRow
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.components.SwitchRow
import nl.markmaaktmedia.guitartuner.ui.nav.Route
import nl.markmaaktmedia.guitartuner.ui.theme.Appearance
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons

@StringRes
internal fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(themeLabelRes(mode))

/** "System · Wallpaper": how the app looks right now, in the words of the page it opens. */
@Composable
internal fun lookSummary(appearance: Appearance): String =
    "${themeLabel(appearance.mode)} · ${stringResource(seedLabel(appearance.seed))}"

/** Light or dark, true black, the colours, and the language of the app. */
@Composable
internal fun LookPage(viewModel: TunerViewModel, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = state.appearance
    val current = appLanguage()
    val context = LocalContext.current

    PageFrame(stringResource(R.string.settings_look), onBack) {
        // Light, dark and true black are what people change most, so they are here and not a
        // page further.
        SectionHeader(stringResource(R.string.section_theme))
        SettingsGroup {
            ContentRow(0, 2, TunerIcons.DarkMode, stringResource(R.string.theme_mode)) {
                SegmentedPillRow(
                    options = ThemeMode.entries,
                    selected = appearance.mode,
                    label = { context.getString(themeLabelRes(it)) },
                    onSelect = { viewModel.setAppearance(appearance.copy(mode = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    equalWidth = true,
                )
            }
            SwitchRow(
                1, 2, TunerIcons.Contrast, stringResource(R.string.pure_black), stringResource(R.string.pure_black_sub),
                checked = appearance.pureBlack,
                onChange = { viewModel.setAppearance(appearance.copy(pureBlack = it)) },
            )
        }

        SectionHeader(stringResource(R.string.section_colour_language))
        SettingsGroup {
            ActionRow(
                0, 2, TunerIcons.Palette, stringResource(R.string.settings_appearance),
                "${stringResource(seedLabel(appearance.seed))} · ${stringResource(styleLabel(appearance.style))}",
                onClick = { onOpen(Route.Appearance) },
            )
            ContentRow(1, 2, TunerIcons.Language, stringResource(R.string.language)) {
                SegmentedPillRow(
                    options = Languages,
                    selected = if (current in Languages) current else "system",
                    label = { context.getString(languageLabelRes(it)) },
                    onSelect = { if (it != current) setAppLanguage(it) },
                    modifier = Modifier.fillMaxWidth(),
                    equalWidth = true,
                )
            }
        }
    }
}

@StringRes
private fun languageLabelRes(code: String): Int = when (code) {
    "en" -> R.string.language_en
    "nl" -> R.string.language_nl
    else -> R.string.language_system
}
