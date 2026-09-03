package nl.markmaaktmedia.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.update.UpdateChecker

/**
 * Makes the update check observable.
 *
 * "I get no banner" has at least four causes that look identical from outside the app:
 * already on the latest build, no network, GitHub's anonymous rate limit of sixty
 * requests an hour per IP, or a release with no APK attached. Working out which one it
 * was took three rounds of guessing, and the first cause turned out to be a missing
 * INTERNET permission that the checker was politely swallowing as "offline".
 *
 * So this row states the installed build, asks GitHub on demand, and prints whatever came
 * back, including the failure. The preview switch renders the banner with dummy content,
 * mirroring the developer option MarkMySteps has, so the bar itself can be checked
 * without publishing anything.
 *
 * It also offers a way out. Every one of those causes leaves the user with an app that
 * cannot update itself, and on a phone where the resolver is the problem, which is what
 * a per-app network restriction or a misconfigured Private DNS looks like from in here,
 * no amount of retrying inside the app will help. The releases page opens in the browser
 * instead, which is a different app with different permissions and often just works.
 */
@Composable
internal fun UpdatesGroup(
    versionName: String,
    bannerPreview: Boolean,
    onBannerPreview: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember(context) { UpdateChecker(context) }

    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SettingsGroup("Updates") {
        action(
            title = "Check for updates",
            description = result ?: "Installed: $versionName",
            icon = { TunerIcons.Update },
            onClick = {
                if (checking) return@action
                checking = true
                result = null
                scope.launch {
                    // fresh skips the fifteen minute cache and ignoreDismissed ignores an
                    // earlier tap on the banner's close button. A manual check has to
                    // answer the real question.
                    val status = checker.check(ignoreDismissed = true, fresh = true)
                    result = when {
                        status.error != null -> "Check failed. ${status.error}"
                        status.latest == null -> "No release published yet."
                        status.isNewer ->
                            "Build ${status.latest.versionCode} is available. Pull down the " +
                                "banner on the tuner screen to download it."
                        else -> "Up to date. Latest published build is ${status.latest.versionCode}."
                    }
                    checking = false
                }
            },
            trailing = if (checking) {
                {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        )
        action(
            title = "Open releases page",
            description = "Downloads the newest APK in the browser. Use this when the check " +
                "above cannot reach GitHub.",
            icon = { TunerIcons.OpenInNew },
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)))
                }
            },
        )
        switch(
            title = "Preview the banner",
            description = "Shows the update bar with dummy content, so it can be checked " +
                "without waiting for a release.",
            icon = { TunerIcons.Download },
            checked = bannerPreview,
            onCheckedChange = onBannerPreview,
        )
    }
}

/** The human facing page, not the API endpoint the checker uses. */
private const val RELEASES_PAGE =
    "https://github.com/Marukiee/GuitarTuner/releases/latest"
