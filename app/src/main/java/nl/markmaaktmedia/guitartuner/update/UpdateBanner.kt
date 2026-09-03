package nl.markmaaktmedia.guitartuner.update

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.R

/**
 * "Nieuwe versie beschikbaar" bar, visually and behaviourally the same as the one in
 * MarkMySteps: a full width strip that runs up underneath the transparent status bar, floats
 * over the page rather than pushing it down, rolls away when dismissed, and remembers the
 * dismissal per version so it does not nag about the same build twice.
 *
 * The one deliberate difference: MarkMySteps hardcodes its brand orange, while this app is a
 * Material You app, so the strip takes [MaterialTheme.colorScheme].primary and follows the
 * wallpaper. Layout, sizes and motion are unchanged.
 *
 * @param forceShow developer option: render the bar with dummy content so it can be checked
 *        without publishing a release.
 */
@Composable
fun UpdateBanner(
    modifier: Modifier = Modifier,
    forceShow: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember(context) { UpdateChecker(context) }

    var release by remember { mutableStateOf<AppRelease?>(null) }

    // A release published while the app was open should still surface, so re-check on every
    // resume and not only on first composition.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            val status = checker.check()
            release = status.latest.takeIf { status.isNewer }
        }
        onPauseOrDispose { job.cancel() }
    }

    val shown = if (forceShow) PREVIEW_RELEASE else release

    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier.fillMaxWidth().zIndex(50f),
        enter = fadeIn(tween(420, easing = Ease)) +
            slideInVertically(tween(420, easing = Ease)) { -it / 4 },
        exit = fadeOut(tween(280, easing = Ease)) +
            slideOutVertically(tween(280, easing = Ease)) { -it / 4 },
    ) {
        val info = shown ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Top padding clears the status bar the banner runs underneath.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_available),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        lineHeight = 18.sp,
                    )
                    info.notes?.let { notes ->
                        Text(
                            text = notes,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Icon(TunerIcons.Download, contentDescription = null, Modifier.size(15.dp))
                        Text(
                            text = stringResource(R.string.download),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                checker.dismiss(info.versionCode)
                                release = null
                            },
                    ) {
                        Icon(
                            TunerIcons.Close,
                            contentDescription = stringResource(R.string.hide),
                            Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Matches the `--ease` curve MarkMySteps uses for the same bar. */
private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val PREVIEW_RELEASE = AppRelease(
    versionCode = 9999,
    name = "Build 9999",
    notes = "Testmelding uit ontwikkelaarsopties",
    apkUrl = "https://github.com/Marukiee/GuitarTuner/releases/latest",
)
