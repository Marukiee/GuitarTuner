package nl.markmaaktmedia.guitartuner.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * From "Update" to the app being replaced, as one state the card reads.
 *
 * Download, then straight on to the installer: there is no second press. The only thing that can
 * stop between the two is Android not yet allowing this app to install packages, which is asked
 * once; the file is kept, and when the person comes back with the permission given the install
 * carries on by itself.
 */
class InstallFlow(
    private val fetcher: ApkFetcher,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    private var job: Job? = null

    /** A downloaded file that is waiting for the install permission, and the build it is. */
    private var waiting: File? = null
    private var waitingFor: Long = -1L
    private var target: AppRelease? = null

    fun start(release: AppRelease) {
        if (state is DownloadState.Downloading || state is DownloadState.Installing) return
        target = release
        job?.cancel()
        job = scope.launch { run(release) }
    }

    private suspend fun run(release: AppRelease) {
        val file = waiting?.takeIf { waitingFor == release.versionCode && it.isFile } ?: download(release) ?: return
        waiting = null

        if (!installer.canInstall()) {
            waiting = file
            waitingFor = release.versionCode
            state = DownloadState.Failed(UpdateProblem.Permission)
            installer.askPermission()
            return
        }
        state = DownloadState.Installing
        try {
            installer.install(file, release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            state = DownloadState.Failed(e.problem, e.detail)
        } catch (e: Exception) {
            state = DownloadState.Failed(UpdateProblem.Other, e.message)
        }
    }

    private suspend fun download(release: AppRelease): File? {
        state = DownloadState.Downloading(0f, 0L, release.sizeBytes)
        return try {
            fetcher.fetch(release) { done, total ->
                state = DownloadState.Downloading(if (total > 0) done.toFloat() / total else -1f, done, total)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            state = DownloadState.Failed(e.problem, e.detail)
            null
        } catch (e: Exception) {
            state = DownloadState.Failed(UpdateProblem.Other, e.message)
            null
        }
    }

    /** The system installer said no, or the person did. Only counts while it was the installer's turn. */
    fun onInstallResult(outcome: InstallOutcome) {
        if (state !is DownloadState.Installing) return
        val problem = UpdateProblem.fromInstallStatus(outcome.status)
        state = DownloadState.Failed(problem, outcome.message)
    }

    /** The app came to the front. If the install permission was the hold-up and it is given now, go on. */
    fun resume() {
        val release = target ?: return
        if (state is DownloadState.Failed && waiting != null && installer.canInstall()) start(release)
    }

    fun reset() {
        job?.cancel()
        waiting = null
        state = DownloadState.Idle
    }
}
