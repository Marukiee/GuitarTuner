package nl.markmaaktmedia.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.TunerIconButton
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/Marukiee/GuitarTuner"

/**
 * Settings, as four slabs.
 *
 * The tuner keeps listening while this is open, so changing the reference pitch or the
 * microphone takes effect straight away and can be checked by playing a string without
 * leaving the page.
 */
@Composable
fun SettingsScreen(
    viewModel: TunerViewModel,
    versionName: String,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TunerIconButton(
                icon = TunerIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SettingsGroup("Pitch") {
            stepper(
                title = "Reference pitch",
                description = "Concert A. Baroque and some orchestras do not use 440.",
                icon = { TunerIcons.Calibration },
                value = "${state.referenceHz.roundToInt()} Hz",
                onDecrease = { viewModel.setReferencePitch(state.referenceHz - 1f) },
                onIncrease = { viewModel.setReferencePitch(state.referenceHz + 1f) },
            )
            if (state.referenceHz != Note.STANDARD_REFERENCE_HZ) {
                action(
                    title = "Back to 440 Hz",
                    icon = { TunerIcons.Restart },
                    onClick = { viewModel.setReferencePitch(Note.STANDARD_REFERENCE_HZ) },
                )
            }
        }

        SettingsGroup("Microphone") {
            viewModel.micSourceOptions().forEach { source ->
                action(
                    title = source.label,
                    description = micDescription(source.label),
                    onClick = { viewModel.setMicSource(source) },
                    trailing = if (source == state.micSource) {
                        {
                            Icon(
                                painter = TunerIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        { androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp)) }
                    },
                )
            }
        }

        SettingsGroup("Appearance") {
            choice(
                title = "Theme",
                icon = { TunerIcons.DarkMode },
                options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
                selected = state.themeMode,
                label = { mode ->
                    when (mode) {
                        ThemeMode.System -> "System"
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                    }
                },
                onSelect = viewModel::setThemeMode,
            )
            switch(
                title = "Wallpaper colours",
                description = "Takes the accent from the system palette, Android 12 and up.",
                icon = { TunerIcons.Palette },
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            switch(
                title = "Pure black",
                description = "True black behind the dial. On an OLED panel that is no light at all.",
                icon = { TunerIcons.Contrast },
                checked = state.pureBlack,
                onCheckedChange = viewModel::setPureBlack,
            )
        }

        UpdatesGroup(
            versionName = versionName,
            bannerPreview = state.bannerPreview,
            onBannerPreview = viewModel::setBannerPreview,
        )

        SettingsGroup("About") {
            action(
                title = "Source code",
                description = "MIT licensed, on GitHub.",
                icon = { TunerIcons.Code },
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                },
                trailing = {
                    Icon(
                        painter = TunerIcons.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            info(
                title = "Audio stays here",
                description = "Nothing is recorded and nothing is uploaded. The only network " +
                    "call this app makes asks GitHub whether a newer build exists.",
                icon = { TunerIcons.Shield },
            )
        }

        Text(
            text = "Guitar Tuner $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
    }
}

/**
 * Which physical capsule each source tends to select.
 *
 * Worth saying out loud: the setting looks like a quality knob and is really a "which
 * microphone" knob, and on a phone with one dead capsule that is the difference between a
 * tuner that works and one that does not.
 */
private fun micDescription(label: String): String = when (label) {
    "Unprocessed" -> "No gain control, no noise suppression. Best signal where it works."
    "Voice rec." -> "Usually a secondary capsule, with processing off on most phones."
    "Main mic" -> "The one at the bottom of the phone."
    else -> "The capsule beside the rear camera."
}
