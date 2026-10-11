package nl.markmaaktmedia.guitartuner.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.components.PickSpinner
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * "New version available": a card that floats over the top of the screen instead of pushing the
 * tuner down, with the download in it.
 *
 * Pressing Download fetches the build inside the app and opens the system installer on it, and
 * the button is the progress: one pill of one width that says Download, then the percentage,
 * then hands over to the installer. It never changes size, so nothing beside it moves while
 * the number counts up.
 *
 * It remembers a dismissal per version, so it does not nag about the same build twice.
 *
 * @param forceShow developer option: render the card with dummy content so it can be checked
 *        without publishing a release.
 */
@Composable
fun UpdateBanner(
    controller: UpdateController,
    modifier: Modifier = Modifier,
    forceShow: Boolean = false,
) {
    // A release published while the app was open should still surface, so ask again on every
    // resume and not only on first composition.
    LifecycleResumeEffect(Unit) {
        controller.checkOnOpen()
        onPauseOrDispose { }
    }

    val shown = if (forceShow) PreviewRelease else controller.release

    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier.fillMaxWidth().zIndex(50f),
        enter = fadeIn(TunerMotion.fadeSpec()) + slideInVertically(TunerMotion.spatial()) { -it },
        exit = fadeOut(TunerMotion.fadeSpec()) + slideOutVertically(TunerMotion.spatial()) { -it },
    ) {
        val info = shown ?: return@AnimatedVisibility
        BannerCard(info = info, controller = controller, preview = forceShow)
    }
}

@Composable
private fun BannerCard(info: AppRelease, controller: UpdateController, preview: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val state = controller.download

    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(8.dp, CardSquircle, clip = false)
            .clip(CardSquircle)
            .background(scheme.primaryContainer)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TunerIcons.Update, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(R.string.update_available),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
            )
            Text(
                text = (state as? DownloadState.Failed)?.let { stringResource(R.string.update_download_failed) }
                    ?: info.notes
                    ?: info.name,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DownloadPill(
            state = state,
            onClick = { if (!preview) controller.install(info) },
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .bouncyClickable(onClickLabel = stringResource(R.string.hide), onClick = { controller.dismiss() }),
        ) {
            Icon(
                TunerIcons.Close,
                contentDescription = stringResource(R.string.hide),
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One pill, one width: Download, then how far along it is. */
@Composable
private fun DownloadPill(state: DownloadState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val busy = state is DownloadState.Downloading
    val container by animateColorAsState(
        targetValue = if (busy) scheme.surfaceContainerHighest else scheme.primary,
        animationSpec = TunerMotion.colourSpec(),
        label = "downloadContainer",
    )
    val ink by animateColorAsState(
        targetValue = if (busy) scheme.onSurface else scheme.onPrimary,
        animationSpec = TunerMotion.colourSpec(),
        label = "downloadInk",
    )

    Row(
        modifier = Modifier
            .width(118.dp)
            .height(40.dp)
            .clip(PillShape)
            .background(container)
            .bouncyClickable(enabled = !busy, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(TunerMotion.fadeSpec()) togetherWith fadeOut(TunerMotion.fadeSpec()) },
            label = "downloadPill",
        ) { current ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (current) {
                    is DownloadState.Downloading -> {
                        PickSpinner(size = 20.dp, color = ink)
                        Text(
                            text = if (current.progress >= 0f) "${(current.progress * 100).toInt()}%" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = ink,
                        )
                    }
                    else -> {
                        Icon(TunerIcons.Download, null, tint = ink, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelLarge,
                            color = ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val PreviewRelease = AppRelease(
    versionCode = 9999,
    name = "Build 9999",
    notes = null,
    apkUrl = "https://github.com/Marukiee/GuitarTuner/releases/latest",
)
