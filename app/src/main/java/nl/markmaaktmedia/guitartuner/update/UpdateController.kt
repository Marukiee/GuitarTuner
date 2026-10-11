package nl.markmaaktmedia.guitartuner.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.guitartuner.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where a download stands. [Downloading] carries 0..1, or a negative number when the size is unknown. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * The update, as one object the banner and the Updates page both read.
 *
 * Two screens showing the same fact from two copies of it was how "the banner says there is a
 * newer build and Settings says there is not" happened, so there is one state here and both
 * of them look at it.
 *
 * The download is in the app and not in the browser. A browser download ends with a file in the
 * Downloads folder that has to be found, opened and trusted by hand; this one ends with the
 * system installer already open on the right file.
 */
class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val checker = UpdateChecker(context)

    /** The newer build, or null when there is none or it was dismissed. */
    var release by mutableStateOf<AppRelease?>(null)
        private set

    /** The newest published build whatever its age, for the Updates page. */
    var latest by mutableStateOf<AppRelease?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    /** The words for the result of the last manual check, or null before there was one. */
    var message by mutableStateOf<String?>(null)
        private set

    var download by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    /** Set when installing needs the person to allow it first, so the page can say so. */
    var needsInstallPermission by mutableStateOf(false)
        private set

    val installedVersionCode: Long get() = checker.currentVersionCode

    private var job: Job? = null

    /** Asks quietly, from the cache when it is fresh. Used every time the app comes to the front. */
    fun checkOnOpen() {
        if (checking) return
        scope.launch {
            val status = checker.check()
            latest = status.latest
            release = status.latest.takeIf { status.isNewer }
        }
    }

    /** The manual check: skips the cache and a dismissal, and says what came back. */
    fun checkNow() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            val status = checker.check(ignoreDismissed = true, fresh = true)
            latest = status.latest
            release = status.latest.takeIf { status.isNewer }
            message = when {
                status.error != null -> context.getString(R.string.update_check_failed, status.error)
                status.latest == null -> context.getString(R.string.update_none_published)
                status.isNewer -> context.getString(R.string.update_available_build, status.latest.versionCode)
                else -> context.getString(R.string.update_up_to_date, status.latest.versionCode)
            }
            checking = false
        }
    }

    fun dismiss() {
        release?.let { checker.dismiss(it.versionCode) }
        release = null
    }

    /** Downloads the newer build and hands it to the system installer. */
    fun install(target: AppRelease) {
        if (download is DownloadState.Downloading) return
        job?.cancel()
        needsInstallPermission = false
        download = DownloadState.Downloading(-1f)
        job = scope.launch {
            val file = runCatching { fetch(target.apkUrl) }.getOrElse { error ->
                download = DownloadState.Failed(error.message ?: error::class.java.simpleName)
                return@launch
            }
            download = DownloadState.Idle
            open(file)
        }
    }

    /** Opens the installer on a file that is already downloaded. */
    private fun open(file: File) {
        // Installing is its own permission, granted per app and checked each time. Without it
        // the installer opens and refuses, which looks like the update failing for no reason.
        if (!context.packageManager.canRequestPackageInstalls()) {
            needsInstallPermission = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun fetch(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file, replaced each time: an old build left in the cache is only ever wasted space.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "guitartuner.apk")
        val partial = File(dir, "guitartuner.apk.part")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "GuitarTuner-Android")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        download = DownloadState.Downloading(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(partial.renameTo(target)) { "Could not store the download" }
        target
    }
}

val LocalUpdates = staticCompositionLocalOf<UpdateController?> { null }
