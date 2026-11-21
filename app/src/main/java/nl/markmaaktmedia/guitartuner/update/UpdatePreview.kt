package nl.markmaaktmedia.guitartuner.update

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.io.File

/**
 * A download that is not one, for the preview of the update card in Settings and for the debug
 * builds' screenshots: it counts up to a made-up 22 MB over a few seconds, or stops part of the
 * way ([stallAt]), fails ([fail]) or is over at once ([instant]) so a state can be looked at for
 * as long as needed.
 */
class PreviewFetcher(
    private val stallAt: Float? = null,
    private val fail: Boolean = false,
    private val instant: Boolean = false,
) : ApkFetcher {
    override suspend fun fetch(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): File {
        val total = release.sizeBytes
        if (fail) throw UpdateException(UpdateProblem.Network)
        if (instant) return File.createTempFile("preview", ".apk")
        val steps = 100
        for (step in 0..steps) {
            val fraction = step / steps.toFloat()
            onProgress((total * fraction).toLong(), total)
            if (stallAt != null && fraction >= stallAt) awaitCancellation()
            delay(45)
        }
        return File.createTempFile("preview", ".apk")
    }
}

/** An installer that installs nothing. */
class PreviewInstaller : ApkInstaller {
    override fun canInstall() = true
    override fun askPermission() = Unit
    override suspend fun install(file: File, release: AppRelease) {
        file.delete()
    }
}
