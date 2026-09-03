package nl.markmaaktmedia.guitartuner.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

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
    /**
     * Why the check produced nothing, in words, or null if it succeeded.
     *
     * The banner is allowed to fail silently, because a failed update check is not worth
     * interrupting anyone over. Settings is not: "no banner appeared" has three completely
     * different causes (already up to date, no network, GitHub rate limited) and without this
     * they are indistinguishable from outside the app, which is exactly the hole that made this
     * take three attempts to pin down.
     */
    val error: String? = null,
)

/**
 * "Is there a newer APK?" answered straight from GitHub.
 *
 * MarkMySteps routes the same question through its own backend, which caches GitHub's answer.
 * This app has no backend, so it asks GitHub directly and caches locally instead. Anonymous
 * calls are rate limited to 60 per hour per IP, and we check on every launch and every resume,
 * so a [CACHE_TTL_MS] window sits in front of the network call. A manual check passes
 * `fresh = true` to skip it.
 *
 * There are two ways to ask, and both are used. The Releases API is the good one: it carries
 * the release notes and the real asset URL. The plain releases page is the fallback, because
 * the API lives on its own hostname with its own rate limit, and a phone can be perfectly
 * online while `api.github.com` specifically is unreachable: a Private DNS profile, an ad
 * blocking resolver or a VPN app filtering `api.*` will do exactly that, and so will the
 * hourly limit after a busy afternoon. `github.com/.../releases/latest` answers the same
 * question with a redirect to `releases/tag/v<n>`, which is all the version compare needs.
 *
 * The CI workflow tags each release `v<run_number>`, sets `versionCode` to the same number and
 * always names the asset [APK_NAME], which is what makes both routes work off a plain integer.
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
            lastError = null
            val latest = fetchLatest(fresh)
                ?: return@withContext UpdateStatus(current, null, false, lastError ?: "No release found")

            val dismissed = prefs.getLong(KEY_DISMISSED, -1L)
            val isNewer = latest.versionCode > current &&
                (ignoreDismissed || dismissed != latest.versionCode)

            UpdateStatus(current, latest, isNewer)
        }

    /** Human readable installed version, for the Settings row. */
    val currentVersionName: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }.getOrElse { "?" }

    /** Do not nag about this build again. Remembered per version, exactly like MarkMySteps. */
    fun dismiss(versionCode: Long) {
        prefs.edit().putLong(KEY_DISMISSED, versionCode).apply()
    }

    /** Set by the fetchers so [check] can report why it came back empty. */
    private var lastError: String? = null

    private fun fetchLatest(fresh: Boolean): AppRelease? {
        if (!fresh) {
            val cachedAt = prefs.getLong(KEY_CACHED_AT, 0L)
            if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                prefs.getString(KEY_CACHED_JSON, null)?.let(ReleaseParser::parse)?.let { return it }
            }
        }

        val body = readApi()
        if (body != null) {
            val release = ReleaseParser.parse(body)
            if (release == null) {
                lastError = "Latest release has no APK asset, or its tag is not a build number"
                return null
            }
            prefs.edit()
                .putString(KEY_CACHED_JSON, body)
                .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                .apply()
            return release
        }

        // The API host said no, one way or another. That is not the same answer as "there is
        // no newer build", so ask the other host before reporting a failure. A success here
        // clears the error the API left behind: the user got their answer, and telling them
        // about a route they never asked about is noise.
        return readLatestTag()?.also { lastError = null }
    }

    /** The good answer: release notes and the real asset URL, straight from the API. */
    private fun readApi(): String? = runCatching {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                // 403 here is almost always the anonymous rate limit, 60 per hour per IP.
                lastError = "GitHub returned HTTP ${connection.responseCode}"
                Log.i(TAG, "Release check returned HTTP ${connection.responseCode}")
                return@runCatching null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrElse {
        // Offline, DNS down, or the INTERNET permission missing from the manifest, which is
        // exactly what this used to swallow.
        lastError = describe(it)
        Log.i(TAG, "Release check failed: ${it.message}", it)
        null
    }

    /**
     * The thin answer, from a different hostname.
     *
     * `releases/latest` redirects to `releases/tag/v<n>` and the tag is the version code, so a
     * single HEAD request that is deliberately *not* followed gives the whole comparison away
     * in the `Location` header. No notes come back and the APK URL is reconstructed from the
     * asset name the workflow always publishes, which is enough for the banner and enough for
     * the download button.
     */
    private fun readLatestTag(): AppRelease? = runCatching {
        val connection = (URL(RELEASES_PAGE).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val location = try {
            connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        }

        ReleaseParser.fromRedirect(location, RELEASES_BASE, APK_NAME)
    }.getOrElse {
        Log.i(TAG, "Releases page fallback failed: ${it.message}", it)
        null
    }

    /**
     * Turns the exception into something worth reading on a phone.
     *
     * An [UnknownHostException] is by far the most common failure and the raw message is
     * actively misleading: it reads like GitHub is down when in practice the phone's own
     * resolver never answered.
     *
     * The important thing here is what an app can and cannot tell about why. When the
     * system denies an app the network, it does not throw a permission error: it hides
     * every network from that app, so [ConnectivityManager.getActiveNetwork] returns null
     * and DNS fails, which is bit for bit what a phone in flight mode looks like from in
     * here. "No internet connection" is therefore a claim this class is not entitled to
     * make, and saying it to someone whose phone is plainly online is worse than saying
     * nothing. So the no-network case names both possibilities and points at the one the
     * user can check.
     *
     * By the time any of this reaches the screen the releases page has been tried too, so
     * the wording names GitHub as a whole rather than one hostname.
     */
    private fun describe(error: Throwable): String = when (error) {
        is UnknownHostException ->
            if (isOnline()) {
                // A network is up and validated and the name still would not resolve, so
                // something is intercepting the lookup: Private DNS pointed somewhere
                // unreachable, a VPN or firewall app, or a blocking resolver upstream.
                "The phone is online but could not look up github.com. Check Private DNS, " +
                    "a VPN, or an ad blocker."
            } else {
                // Either there is genuinely no network, or this app has been denied it.
                // Android reports those identically, so both get named.
                "No network reached this app. If the phone is online, allow network access " +
                    "for Guitar Tuner in Android settings (Apps, Guitar Tuner, Mobile data " +
                    "and Wi-Fi), and check any firewall or VPN app."
            }

        is SocketTimeoutException -> "GitHub did not answer in time. Try again."
        else -> error::class.java.simpleName + ": " + (error.message ?: "no detail")
    }

    /**
     * Whether the system believes a network is up and validated. Advisory only: it decides
     * how to word a failure that has already happened, never whether to attempt the call.
     *
     * This is answered per app. An app denied the network sees no active network at all,
     * which is why a false here does not mean the phone is offline.
     */
    private fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private companion object {
        const val TAG = "UpdateChecker"
        const val PREFS = "guitartuner.update"
        const val KEY_DISMISSED = "dismissed_version"
        const val KEY_CACHED_JSON = "cached_release"
        const val KEY_CACHED_AT = "cached_at"

        const val CACHE_TTL_MS = 15 * 60 * 1000L
        const val TIMEOUT_MS = 8_000

        const val USER_AGENT = "GuitarTuner-Android"

        /** The name the release workflow always gives the published APK. */
        const val APK_NAME = "guitartuner.apk"

        const val API_URL =
            "https://api.github.com/repos/Marukiee/GuitarTuner/releases/latest"
        const val RELEASES_BASE = "https://github.com/Marukiee/GuitarTuner/releases"
        const val RELEASES_PAGE = "$RELEASES_BASE/latest"
    }
}

/**
 * Turning a `releases/latest` payload into an [AppRelease], deliberately free of any Android
 * dependency.
 *
 * It lived inside [UpdateChecker] and could not be tested there: the class needs a Context for
 * SharedPreferences, so a unit test could only construct it behind an assumption, and the whole
 * suite silently skipped. Four green-looking skipped tests are worse than no tests.
 */
internal object ReleaseParser {

internal fun parse(json: String): AppRelease? = runCatching {
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

    /**
     * The same answer read out of a redirect instead of out of a payload.
     *
     * `github.com/<repo>/releases/latest` replies with a `Location` of `.../releases/tag/v<n>`,
     * which carries the version code and nothing else: no notes, no asset list. The download
     * URL is rebuilt from the fixed asset name the release workflow publishes, so this thinner
     * route still hands the banner a button that works.
     */
    internal fun fromRedirect(location: String?, releasesBase: String, apkName: String): AppRelease? {
        val tag = location
            ?.substringAfterLast("/tag/", "")
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val versionCode = tag.removePrefix("v").toLongOrNull() ?: return null

        return AppRelease(
            versionCode = versionCode,
            name = "Build $versionCode",
            notes = null,
            apkUrl = "$releasesBase/download/$tag/$apkName",
        )
    }
}
