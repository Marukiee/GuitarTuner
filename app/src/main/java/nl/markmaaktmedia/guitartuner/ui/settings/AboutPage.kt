package nl.markmaaktmedia.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import nl.markmaaktmedia.guitartuner.BuildConfig
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.components.ActionRow
import nl.markmaaktmedia.guitartuner.ui.components.InfoRow
import nl.markmaaktmedia.guitartuner.ui.components.NoteRow
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons

private const val SourceUrl = "https://github.com/Marukiee/GuitarTuner"

@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current

    PageFrame(stringResource(R.string.settings_about), onBack) {
        SectionHeader(stringResource(R.string.app_name))
        SettingsGroup {
            InfoRow(0, 3, TunerIcons.Info, stringResource(R.string.installed_version), BuildConfig.VERSION_NAME)
            InfoRow(1, 3, TunerIcons.Code, stringResource(R.string.licence), stringResource(R.string.licence_value))
            ActionRow(
                2, 3, TunerIcons.OpenInNew, stringResource(R.string.source_code), stringResource(R.string.source_code_sub),
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SourceUrl))) } },
            )
        }

        SectionHeader(stringResource(R.string.section_privacy))
        SettingsGroup {
            NoteRow(0, 1, TunerIcons.Shield, stringResource(R.string.audio_stays_here), stringResource(R.string.audio_stays_here_body))
        }
    }
}
