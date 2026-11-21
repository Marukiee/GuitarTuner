package nl.markmaaktmedia.guitartuner.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.R

/**
 * The update, as one object the card and the Updates page both read.
 *
 * Two screens showing the same fact from two copies of it was how "the banner says there is a
 * newer build and Settings says there is not" happened, so there is one state here and both
 * of them look at it. The download and the install are in [InstallFlow].
 */
class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val checker = UpdateChecker(context)

    private val live = InstallFlow(HttpApkFetcher(context.cacheDir), SessionApkInstaller(context), scope)

    /** A made-up update that walks through the card without downloading or installing anything. */
    private var preview by mutableStateOf<InstallFlow?>(null)
    private var previewJob: Job? = null

    /** Debug only: a release to show in place of the one GitHub has. */
    private var pinned: AppRelease? = null

    private var found by mutableStateOf<AppRelease?>(null)

    private val flow: InstallFlow get() = preview ?: live

    /** The newer build, or null when there is none or it was dismissed. */
    val release: AppRelease? get() = if (preview != null) previewRelease else found

    /** The newest published build whatever its age, for the Updates page. */
    var latest by mutableStateOf<AppRelease?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    /** The words for the result of the last manual check, or null before there was one. */
    var message by mutableStateOf<String?>(null)
        private set

    val download: DownloadState get() = flow.state

    val installedVersionCode: Long get() = checker.currentVersionCode

    init {
        // The system installer's "no" arrives through a receiver; the card is told here.
        scope.launch { InstallOutcomes.events.collect { flow.onInstallResult(it) } }
    }

    /** Asks quietly, from the cache when it is fresh. Used every time the app comes to the front. */
    fun checkOnOpen() {
        live.resume()
        if (checking || pinned != null) return
        scope.launch {
            val status = checker.check()
            if (pinned != null) return@launch
            latest = status.latest
            found = status.latest.takeIf { status.isNewer }
        }
    }

    /** The manual check: skips the cache and a dismissal, and says what came back. */
    fun checkNow() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            val status = checker.check(ignoreDismissed = true, fresh = true)
            if (pinned == null) {
                latest = status.latest
                found = status.latest.takeIf { status.isNewer }
            }
            message = when {
                status.error != null -> context.getString(R.string.update_check_failed, status.error)
                status.latest == null -> context.getString(R.string.update_none_published)
                status.isNewer -> context.getString(R.string.update_available_build, status.latest.versionCode)
                else -> context.getString(R.string.update_up_to_date, status.latest.versionCode)
            }
            checking = false
        }
    }

    /** Later: hides this version until a newer one is published. */
    fun dismiss() {
        if (preview != null) {
            stopPreview()
            return
        }
        found?.let { checker.dismiss(it.versionCode) }
        found = null
    }

    /** Downloads the newer build and hands it to the system installer. */
    fun install(target: AppRelease) = flow.start(target)

    /**
     * Shows the card with a made-up release, and lets a press on Update walk through the whole
     * thing. With a [pin] the card stops in one state so it can be looked at: `fetching`,
     * `installing` or `failed`.
     */
    fun startPreview(pin: String? = null) {
        previewJob?.cancel()
        val fake = InstallFlow(
            fetcher = PreviewFetcher(
                stallAt = if (pin == "fetching") PINNED_PROGRESS else null,
                fail = pin == "failed",
                instant = pin == "installing",
            ),
            installer = PreviewInstaller(),
            scope = scope,
        )
        preview = fake
        if (pin != null) {
            fake.start(previewRelease)
        } else {
            // Unpinned, the preview plays the install for a moment and then offers itself again.
            previewJob = scope.launch {
                while (true) {
                    snapshotFlow { fake.state }.first { it is DownloadState.Installing }
                    delay(3_500)
                    fake.reset()
                }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        preview?.reset()
        preview = null
    }

    /** Debug only: pretend GitHub has this release, so the real download and install can be tried. */
    fun debugRelease(release: AppRelease) {
        pinned = release
        latest = release
        found = release
    }

    private val previewRelease: AppRelease by lazy {
        AppRelease(
            versionCode = 9999,
            name = "Build 9999",
            notes = null,
            apkUrl = "https://github.com/Marukiee/GuitarTuner/releases/latest",
            sizeBytes = 22_400_000L,
            highlights = listOf(
                context.getString(R.string.update_preview_note_1),
                context.getString(R.string.update_preview_note_2),
            ),
        )
    }

    private companion object {
        const val PINNED_PROGRESS = 0.62f
    }
}

val LocalUpdates = staticCompositionLocalOf<UpdateController?> { null }
