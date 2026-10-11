package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.BuildConfig
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.CategoryRow
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.components.TunerIconButton
import nl.markmaaktmedia.guitartuner.ui.nav.Route
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import kotlin.math.roundToInt

/**
 * The front page of Settings: the categories, each with a line that says how it is set right
 * now, so most questions are answered without opening anything. Each one opens a page, the same
 * shape as the Tandem settings, so the apps feel like one family.
 */
@Composable
fun SettingsScreen(
    viewModel: TunerViewModel,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TunerIconButton(TunerIcons.Back, stringResource(R.string.action_back), onBack)
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup {
                CategoryRow(
                    0, 2, TunerIcons.Tuning, stringResource(R.string.settings_tuner),
                    stringResource(
                        R.string.settings_tuner_summary,
                        state.referenceHz.roundToInt(),
                        stringResource(state.micSource.labelRes),
                    ),
                    onClick = { onOpen(Route.TunerSettings) },
                )
                CategoryRow(
                    1, 2, TunerIcons.Palette, stringResource(R.string.settings_look),
                    lookSummary(state.appearance),
                    onClick = { onOpen(Route.Look) },
                )
            }
            SettingsGroup {
                CategoryRow(
                    0, 2, TunerIcons.Update, stringResource(R.string.settings_updates),
                    stringResource(R.string.settings_updates_summary, BuildConfig.VERSION_NAME),
                    onClick = { onOpen(Route.Updates) },
                )
                CategoryRow(
                    1, 2, TunerIcons.Info, stringResource(R.string.settings_about),
                    stringResource(R.string.settings_about_summary),
                    onClick = { onOpen(Route.About) },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
