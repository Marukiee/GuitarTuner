package nl.markmaaktmedia.guitartuner.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Gets the APK of a release onto the phone. Split from the installer so each can be tested alone. */
interface ApkFetcher {
    /** [onProgress] gets the bytes so far and the total, or a total of 0 when it is not known. */
    suspend fun fetch(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): File
}

/**
 * The download is in the app and not in the browser. A browser download ends with a file in the
 * Downloads folder that has to be found, opened and trusted by hand; this one ends with the file
 * in the cache, handed straight to the installer.
 */
class HttpApkFetcher(private val cacheDir: File) : ApkFetcher {

    override suspend fun fetch(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(cacheDir, "updates").apply { mkdirs() }
            // The file is named for its build, and a complete one is kept: when Android asks to
            // allow installing it restarts the app, and the download should not be done twice.
            val target = File(dir, "guitartuner-${release.versionCode}.apk")
            if (release.sizeBytes > 0 && target.isFile && target.length() == release.sizeBytes) {
                onProgress(release.sizeBytes, release.sizeBytes)
                return@withContext target
            }
            // One build at a time: an old one left in the cache is only ever wasted space.
            dir.listFiles()?.forEach { it.delete() }
            val partial = File(dir, "guitartuner-${release.versionCode}.apk.part")

            val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "GuitarTuner-Android")
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw UpdateException(UpdateProblem.Server, code.toString())
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                var done = 0L
                var reported = 0L
                // About one report per percent: the card animates between them, and a report per
                // buffer would only be 300 recompositions for the same picture.
                val step = if (total > 0) maxOf(total / 100, 32 * 1024L) else 256 * 1024L
                onProgress(0L, total)
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (done - reported >= step) {
                                reported = done
                                onProgress(done, total)
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw UpdateException(UpdateProblem.Incomplete)
                onProgress(done, total)
            } catch (e: IOException) {
                partial.delete()
                throw UpdateException(problemFor(e), e.message)
            } finally {
                connection.disconnect()
            }
            if (!partial.renameTo(target)) throw UpdateException(UpdateProblem.Storage)
            target
        }

    private fun problemFor(error: IOException): UpdateProblem =
        if (error.message?.contains("ENOSPC") == true || error.message?.contains("No space left") == true) {
            UpdateProblem.Storage
        } else {
            UpdateProblem.Network
        }
}
