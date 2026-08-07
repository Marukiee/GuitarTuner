package nl.markmaaktmedia.guitartuner.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published build, as advertised by the repository's latest GitHub Release. */
data class AppRelease(
    val versionCode: Long,
    val name: String,
    val notes: String?,
    val apkUrl: String,
)

data class UpdateStatus(
    val currentVersionCode: Long,
    val latest: AppRelease?,
    val isNewer: Boolean,
)

/**
 * "Is there a newer APK?" answered straight from the GitHub Releases API.
 *
 * MarkMySteps routes the same question through its own backend, which caches GitHub's answer.
 * This app has no backend, so it asks GitHub directly and caches locally instead. Anonymous
 * calls are rate limited to 60 per hour per IP, and we check on every launch and every resume,
 * so a [CACHE_TTL_MS] window sits in front of the network call. A manual check passes
 * `fresh = true` to skip it.
 *
 * The CI workflow tags each release `v<run_number>` and sets `versionCode` to the same number,
 * which is what makes the comparison a plain integer compare.
 */
class UpdateChecker(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val currentVersionCode: Long
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrElse { e ->
            Log.w(TAG, "Could not read own version", e as? PackageManager.NameNotFoundException ?: e)
            0L
        }

    suspend fun check(ignoreDismissed: Boolean = false, fresh: Boolean = false): UpdateStatus =
        withContext(Dispatchers.IO) {
            val current = currentVersionCode
            val latest = fetchLatest(fresh) ?: return@withContext UpdateStatus(current, null, false)

            val dismissed = prefs.getLong(KEY_DISMISSED, -1L)
            val isNewer = latest.versionCode > current &&
                (ignoreDismissed || dismissed != latest.versionCode)

            UpdateStatus(current, latest, isNewer)
        }

    /** Do not nag about this build again. Remembered per version, exactly like MarkMySteps. */
    fun dismiss(versionCode: Long) {
        prefs.edit().putLong(KEY_DISMISSED, versionCode).apply()
    }

    private fun fetchLatest(fresh: Boolean): AppRelease? {
        if (!fresh) {
            val cachedAt = prefs.getLong(KEY_CACHED_AT, 0L)
            if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return prefs.getString(KEY_CACHED_JSON, null)?.let(::parse)
            }
        }

        val body = runCatching {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GuitarTuner-Android")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "Release check returned HTTP ${connection.responseCode}")
                    return@runCatching null
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            // Offline, or no release published yet. Silent: an update banner is not worth an error.
            Log.i(TAG, "Release check failed: ${it.message}")
            null
        } ?: return null

        val release = parse(body) ?: return null
        prefs.edit()
            .putString(KEY_CACHED_JSON, body)
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
        return release
    }

    private fun parse(json: String): AppRelease? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null

        // Tags are "v<run_number>"; the run number is also the versionCode.
        val tag = root.optString("tag_name").removePrefix("v")
        val versionCode = tag.toLongOrNull() ?: return null

        val assets = root.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            if (url.endsWith(".apk", ignoreCase = true)) {
                apkUrl = url
                break
            }
        }

        AppRelease(
            versionCode = versionCode,
            name = root.optString("name").ifBlank { "Build $versionCode" },
            notes = root.optString("body").takeIf { it.isNotBlank() }?.lineSequence()?.first()?.trim(),
            apkUrl = apkUrl ?: return null,
        )
    }.getOrNull()

    private companion object {
        const val TAG = "UpdateChecker"
        const val PREFS = "guitartuner.update"
        const val KEY_DISMISSED = "dismissed_version"
        const val KEY_CACHED_JSON = "cached_release"
        const val KEY_CACHED_AT = "cached_at"

        const val CACHE_TTL_MS = 15 * 60 * 1000L

        const val RELEASES_URL =
            "https://api.github.com/repos/Marukiee/GuitarTuner/releases/latest"
    }
}
