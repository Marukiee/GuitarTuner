package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.update.UpdateChecker

/**
 * Makes the update check observable.
 *
 * "I get no banner" has at least four causes that look identical from outside the app: already on
 * the latest build, no network, GitHub's anonymous rate limit (60 requests an hour per IP), or a
 * release with no APK attached. Working out which one it was took three rounds of guessing, and
 * the first cause turned out to be a missing INTERNET permission that the checker was politely
 * swallowing as "offline".
 *
 * So this row states the installed build, asks GitHub on demand, and prints whatever came back,
 * including the failure. The preview switch renders the banner with dummy content, mirroring the
 * developer option MarkMySteps has, so the bar itself can be checked without publishing anything.
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

    SettingsGroupScaffold(title = "Updates") {
        Surface(
            onClick = {
                if (checking) return@Surface
                checking = true
                result = null
                scope.launch {
                    // fresh skips the 15 minute cache, ignoreDismissed ignores an earlier tap on
                    // the banner's close button. A manual check must answer the real question.
                    val status = checker.check(ignoreDismissed = true, fresh = true)
                    result = when {
                        status.error != null -> "Check failed. ${status.error}"
                        status.latest == null -> "No release published yet."
                        status.isNewer ->
                            "Build ${status.latest.versionCode} is available. Pull down the banner " +
                                "on the tuner screen to download it."
                        else -> "Up to date. Latest published build is ${status.latest.versionCode}."
                    }
                    checking = false
                }
            },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = result ?: "Installed: $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checking) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Preview the banner", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Shows the update bar with dummy content, so it can be checked " +
                            "without waiting for a release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = bannerPreview, onCheckedChange = onBannerPreview)
            }
        }
    }

    Text(
        text = "Guitar Tuner $versionName",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 32.dp),
    )
}

/** The same header-plus-rows shape the other settings groups use. */
@Composable
private fun SettingsGroupScaffold(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        content()
    }
}
