package nl.markmaaktmedia.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import nl.markmaaktmedia.guitartuner.ui.components.PickSpinner
import nl.markmaaktmedia.guitartuner.ui.components.SectionHeader
import nl.markmaaktmedia.guitartuner.ui.components.SettingsGroup
import nl.markmaaktmedia.guitartuner.ui.components.SwitchRow
import nl.markmaaktmedia.guitartuner.ui.nav.Route
import nl.markmaaktmedia.guitartuner.ui.theme.GroupedSpacing
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.markupdate.MarkUpdate
import nl.markmaaktmedia.markupdate.ReleaseInfo
import nl.markmaaktmedia.markupdate.UpdateState

/**
 * Makes the update check observable.
 *
 * "I get no card" has at least four causes that look identical from outside the app: already on
 * the latest build, no network, GitHub's anonymous rate limit, or a release with no APK attached.
 * So this page states the installed build, asks GitHub on demand, and prints whatever came back,
 * including the failure. It also offers a way out: the releases page in the browser, which is a
 * different app with different permissions and often just works.
 *
 * The check, the download and the install are the shared markupdate module's; the card on the
 * tuner shows the same state this page does.
 */
@Composable
internal fun UpdatesPage(
    viewModel: TunerViewModel,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val context = LocalContext.current
    val updater = MarkUpdate.get()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by updater.state.collectAsStateWithLifecycle()
    val release = releaseOf(update)
    // Kept, so the block still has something to show while it folds away.
    var lastRelease by remember { mutableStateOf(release) }
    if (release != null) lastRelease = release

    PageFrame(stringResource(R.string.settings_updates), onBack) {
        SectionHeader(stringResource(R.string.section_version))
        // One child of the page, so the gap above the new release comes and goes with it.
        Column {
            SettingsGroup {
                InfoRow(0, 2, TunerIcons.Info, stringResource(R.string.installed_version), BuildConfig.VERSION_NAME)
                ActionRow(
                    1, 2, TunerIcons.Refresh, stringResource(R.string.check_updates),
                    when (val s = update) {
                        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                        is UpdateState.Failed -> stringResource(R.string.update_check_failed, s.reason)
                        is UpdateState.Available -> stringResource(R.string.update_available_build, s.release.versionName)
                        else -> stringResource(R.string.check_updates_sub)
                    },
                    onClick = { updater.checkAsync() },
                    trailing = if (update is UpdateState.Checking) {
                        { PickSpinner(size = 24.dp) }
                    } else null,
                )
            }

            AnimatedVisibility(release != null, enter = TunerMotion.unfold(), exit = TunerMotion.fold()) {
                lastRelease?.let { found ->
                    Column(Modifier.padding(top = GroupedSpacing), verticalArrangement = Arrangement.spacedBy(GroupedSpacing)) {
                        SectionHeader(stringResource(R.string.section_available))
                        SettingsGroup {
                            ActionRow(
                                0, 1, TunerIcons.Download,
                                stringResource(R.string.update_install_build, found.versionName),
                                when (val s = update) {
                                    is UpdateState.Downloading ->
                                        stringResource(R.string.update_downloading_percent, (s.progress * 100).toInt())
                                    is UpdateState.ReadyToInstall, is UpdateState.Installing, is UpdateState.AwaitingConfirmation ->
                                        stringResource(R.string.update_installing)
                                    is UpdateState.NeedsPermission -> stringResource(R.string.update_allow_installs)
                                    is UpdateState.Failed -> s.reason
                                    else -> null
                                },
                                onClick = { updater.startUpdate(found) },
                                trailing = if (update is UpdateState.Downloading || update is UpdateState.Installing) {
                                    { PickSpinner(size = 24.dp) }
                                } else null,
                            )
                        }
                    }
                }
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
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updater.releasesPageUrl + "/latest"))) }
                },
            )
            SwitchRow(
                2, 3, TunerIcons.Download, stringResource(R.string.preview_banner), stringResource(R.string.preview_banner_sub),
                checked = state.bannerPreview,
                onChange = { on ->
                    viewModel.setBannerPreview(on)
                    if (on) updater.showPreview() else updater.reset()
                },
            )
        }
    }
}

/** The release an update is about, whatever stage it is in; null when there is none or it is not about a release. */
private fun releaseOf(state: UpdateState): ReleaseInfo? = when (state) {
    is UpdateState.Available -> state.release
    is UpdateState.Downloading -> state.release
    is UpdateState.ReadyToInstall -> state.release
    is UpdateState.NeedsPermission -> state.release
    is UpdateState.Installing -> state.release
    is UpdateState.AwaitingConfirmation -> state.release
    is UpdateState.Failed -> state.release
    else -> null
}
