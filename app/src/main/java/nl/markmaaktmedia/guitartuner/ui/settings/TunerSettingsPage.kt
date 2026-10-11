package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.ActionRow
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.components.StepperRow
import nl.markmaaktmedia.guitartuner.ui.components.SwitchRow
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import kotlin.math.roundToInt

/**
 * Everything about how the tuner listens and answers: the pitch it measures against, the
 * microphone, and what it does when a string is right.
 *
 * The tuner keeps listening while this is open, so changing the reference pitch or the
 * microphone takes effect straight away and can be checked by playing a string.
 */
@Composable
internal fun TunerSettingsPage(viewModel: TunerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val moved = state.referenceHz != Note.STANDARD_REFERENCE_HZ

    PageFrame(stringResource(R.string.settings_tuner), onBack) {
        SectionHeader(stringResource(R.string.section_pitch))
        SettingsGroup {
            val total = if (moved) 2 else 1
            StepperRow(
                0, total, TunerIcons.Calibration,
                stringResource(R.string.reference_pitch), stringResource(R.string.reference_pitch_sub),
                value = stringResource(R.string.hertz, state.referenceHz.roundToInt()),
                onDecrease = { viewModel.setReferencePitch(state.referenceHz - 1f) },
                onIncrease = { viewModel.setReferencePitch(state.referenceHz + 1f) },
                decreaseLabel = stringResource(R.string.lower),
                increaseLabel = stringResource(R.string.raise),
            )
            if (moved) {
                ActionRow(
                    1, 2, TunerIcons.Restart, stringResource(R.string.reference_pitch_reset),
                    onClick = { viewModel.setReferencePitch(Note.STANDARD_REFERENCE_HZ) },
                )
            }
        }

        SectionHeader(stringResource(R.string.section_microphone))
        SettingsGroup {
            val sources = viewModel.micSourceOptions()
            sources.forEachIndexed { index, source ->
                val chosen = source == state.micSource
                ActionRow(
                    index, sources.size, TunerIcons.Mic,
                    stringResource(source.labelRes), stringResource(source.descriptionRes),
                    onClick = { viewModel.setMicSource(source) },
                    trailing = {
                        // Always the same 20dp box, so choosing a row changes a colour and a
                        // glyph and nothing about the width of the text beside it.
                        Icon(
                            painter = TunerIcons.Check,
                            contentDescription = null,
                            tint = if (chosen) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }

        SectionHeader(stringResource(R.string.section_feedback))
        SettingsGroup {
            SwitchRow(
                0, 3, TunerIcons.ReferenceTone, stringResource(R.string.chime), stringResource(R.string.chime_sub),
                checked = state.chime, onChange = viewModel::setChime,
            )
            SwitchRow(
                1, 3, TunerIcons.Vibration, stringResource(R.string.haptics), stringResource(R.string.haptics_sub),
                checked = state.haptics, onChange = viewModel::setHaptics,
            )
            SwitchRow(
                2, 3, TunerIcons.Phone, stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_sub),
                checked = state.keepScreenOn, onChange = viewModel::setKeepScreenOn,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
