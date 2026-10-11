package nl.markmaaktmedia.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.BuildConfig
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.ActionRow
import nl.markmaaktmedia.guitartuner.ui.components.InfoRow
import nl.markmaaktmedia.guitartuner.ui.components.NoteRow
import nl.markmaaktmedia.guitartuner.ui.components.PickSpinner
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.components.SwitchRow
import nl.markmaaktmedia.guitartuner.ui.nav.Route
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.update.DownloadState
import nl.markmaaktmedia.guitartuner.update.UpdateController

/**
 * Makes the update check observable.
 *
 * "I get no banner" has at least four causes that look identical from outside the app: already
 * on the latest build, no network, GitHub's anonymous rate limit, or a release with no APK
 * attached. So this page states the installed build, asks GitHub on demand, and prints whatever
 * came back, including the failure. It also offers a way out: the releases page in the browser,
 * which is a different app with different permissions and often just works.
 */
@Composable
internal fun UpdatesPage(
    controller: UpdateController,
    viewModel: TunerViewModel,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val newer = controller.release
    val download = controller.download

    PageFrame(stringResource(R.string.settings_updates), onBack) {
        SectionHeader(stringResource(R.string.section_version))
        SettingsGroup {
            InfoRow(0, 2, TunerIcons.Info, stringResource(R.string.installed_version), BuildConfig.VERSION_NAME)
            ActionRow(
                1, 2, TunerIcons.Refresh, stringResource(R.string.check_updates),
                controller.message ?: stringResource(R.string.check_updates_sub),
                onClick = controller::checkNow,
                trailing = if (controller.checking) {
                    { PickSpinner(size = 24.dp) }
                } else null,
            )
        }

        if (newer != null) {
            SectionHeader(stringResource(R.string.section_available))
            SettingsGroup {
                ActionRow(
                    0, 1, TunerIcons.Download,
                    stringResource(R.string.update_install_build, newer.versionCode),
                    when {
                        download is DownloadState.Downloading ->
                            if (download.progress >= 0f) stringResource(R.string.update_downloading_percent, (download.progress * 100).toInt())
                            else stringResource(R.string.update_downloading)
                        download is DownloadState.Failed -> stringResource(R.string.update_download_failed_detail, download.message)
                        controller.needsInstallPermission -> stringResource(R.string.update_allow_installs)
                        else -> newer.notes
                    },
                    onClick = { controller.install(newer) },
                    trailing = if (download is DownloadState.Downloading) {
                        { PickSpinner(size = 24.dp) }
                    } else null,
                )
            }
        }

        SectionHeader(stringResource(R.string.section_more))
        SettingsGroup {
            ActionRow(
                0, 3, TunerIcons.History, stringResource(R.string.changelog_title), stringResource(R.string.changelog_sub),
                onClick = { onOpen(Route.Changelog) },
            )
            ActionRow(
                1, 3, TunerIcons.OpenInNew, stringResource(R.string.open_releases), stringResource(R.string.open_releases_sub),
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleasesPage))) }
                },
            )
            SwitchRow(
                2, 3, TunerIcons.Download, stringResource(R.string.preview_banner), stringResource(R.string.preview_banner_sub),
                checked = state.bannerPreview, onChange = viewModel::setBannerPreview,
            )
        }
    }
}

/** The human facing page, not the API endpoint the checker uses. */
private const val ReleasesPage = "https://github.com/Marukiee/GuitarTuner/releases/latest"
