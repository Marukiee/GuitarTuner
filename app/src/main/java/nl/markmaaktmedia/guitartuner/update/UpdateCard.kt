package nl.markmaaktmedia.guitartuner.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.BuildConfig
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * "New version": a tonal card at the top of the tuner that pushes the tuner down instead of
 * floating over it, and that does the whole update.
 *
 * At rest it says what is new and offers Later and Update, which share the width. Pressing
 * Update shrinks Later away and turns Update into the progress bar of the download, with the
 * percentage in it; when the file is in, it installs at once, with no second press, and the bar
 * says so. If something goes wrong the card says what, and one wide button tries again.
 *
 * Positions and sizes run on springs and colours and opacity on tweens, like everywhere else in
 * the app, so the card moves as one object while the words change under it.
 *
 * Later is remembered per version, so a build is not offered twice.
 */
@Composable
fun UpdateCard(controller: UpdateController, modifier: Modifier = Modifier) {
    val release = controller.release
    // The card keeps its words while it leaves, instead of going blank halfway up.
    val remembered = remember { arrayOfNulls<AppRelease>(1) }
    if (release != null) remembered[0] = release
    val shown = release ?: remembered[0]

    AnimatedVisibility(
        visible = release != null,
        modifier = modifier,
        enter = expandVertically(TunerMotion.sizeSpring(), Alignment.Top) + fadeIn(TunerMotion.fadeSpec()),
        exit = shrinkVertically(TunerMotion.sizeSpring(), Alignment.Top) + fadeOut(TunerMotion.fadeSpec()),
    ) {
        if (shown != null) CardContent(shown, controller)
    }
}

private enum class Phase { Rest, Fetching, Installing, Failed }

private fun DownloadState.phase() = when (this) {
    DownloadState.Idle -> Phase.Rest
    is DownloadState.Downloading -> Phase.Fetching
    DownloadState.Installing -> Phase.Installing
    is DownloadState.Failed -> Phase.Failed
}

@Composable
private fun CardContent(release: AppRelease, controller: UpdateController) {
    val scheme = MaterialTheme.colorScheme
    val state = controller.download
    val phase = state.phase()

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(TunerMotion.sizeSpring())
                .clip(CardSquircle)
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glyph(phase)
                AnimatedContent(
                    targetState = state,
                    modifier = Modifier.weight(1f).clipToBounds(),
                    contentKey = { it::class },
                    transitionSpec = { swap() },
                    label = "updateWords",
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title(current, release),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle(current, release),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = phase == Phase.Rest && release.highlights.isNotEmpty(),
                enter = expandVertically(TunerMotion.sizeSpring()) + fadeIn(TunerMotion.fadeSpec()),
                exit = shrinkVertically(TunerMotion.sizeSpring()) + fadeOut(TunerMotion.fadeSpec()),
            ) {
                Column(Modifier.padding(start = 58.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    release.highlights.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Actions(
                state = state,
                phase = phase,
                onLater = controller::dismiss,
                onUpdate = { controller.install(release) },
            )
        }
    }
}

/** The old words slide up and away and the new ones come in from below. */
private fun AnimatedContentTransitionScope<*>.swap(): ContentTransform =
    (
        (slideInVertically(TunerMotion.spatial()) { it / 2 } + fadeIn(TunerMotion.fadeSpec())) togetherWith
            (slideOutVertically(TunerMotion.spatial()) { -it / 2 } + fadeOut(TunerMotion.fadeSpec()))
        ).using(SizeTransform(clip = false) { _, _ -> TunerMotion.sizeSpring() })

@Composable
private fun title(state: DownloadState, release: AppRelease): String = when (state) {
    DownloadState.Installing -> stringResource(R.string.update_card_installing_title, release.versionName)
    is DownloadState.Failed -> stringResource(R.string.update_card_failed_title)
    else -> stringResource(R.string.update_card_title, release.versionName)
}

@Composable
private fun subtitle(state: DownloadState, release: AppRelease): String = when (state) {
    DownloadState.Idle -> stringResource(R.string.update_card_have, BuildConfig.VERSION_NAME)
    is DownloadState.Downloading -> {
        val locale = LocalConfiguration.current.locales[0]
        val total = state.totalBytes.takeIf { it > 0 } ?: release.sizeBytes
        if (total > 0) {
            stringResource(
                R.string.update_card_progress,
                SizeText.megabytes(state.doneBytes, locale),
                SizeText.total(total, locale),
            )
        } else {
            stringResource(R.string.update_card_progress_unknown, SizeText.megabytes(state.doneBytes, locale))
        }
    }
    DownloadState.Installing -> stringResource(R.string.update_card_restart)
    is DownloadState.Failed -> failureText(state)
}

/** The reason an update failed, in the language of the phone. */
@Composable
fun failureText(failed: DownloadState.Failed): String =
    if (failed.problem.takesDetail) {
        stringResource(failed.problem.text, failed.detail ?: stringResource(R.string.update_err_no_detail))
    } else {
        stringResource(failed.problem.text)
    }

private enum class GlyphKind { Spark, Check, Error }

/** Square with soft corners and a spark; a round green tick once it installs; an alert if it fails. */
@Composable
private fun Glyph(phase: Phase) {
    val scheme = MaterialTheme.colorScheme
    val green = LocalTunerSignals.current.inTune
    val container by animateColorAsState(
        targetValue = when (phase) {
            Phase.Installing -> green.copy(alpha = 0.22f).compositeOver(scheme.surfaceContainerHigh)
            Phase.Failed -> scheme.errorContainer
            else -> scheme.primaryContainer
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "glyphContainer",
    )
    val ink by animateColorAsState(
        targetValue = when (phase) {
            Phase.Installing -> green
            Phase.Failed -> scheme.onErrorContainer
            else -> scheme.onPrimaryContainer
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "glyphInk",
    )
    val corner by animateDpAsState(
        targetValue = if (phase == Phase.Installing) 23.dp else 15.dp,
        animationSpec = TunerMotion.springy(),
        label = "glyphCorner",
    )
    val kind = when (phase) {
        Phase.Installing -> GlyphKind.Check
        Phase.Failed -> GlyphKind.Error
        else -> GlyphKind.Spark
    }

    Box(
        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(corner)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (scaleIn(TunerMotion.springy(), initialScale = 0.3f) + fadeIn(TunerMotion.fadeSpec())) togetherWith
                    (scaleOut(TunerMotion.spatial(), targetScale = 0.3f) + fadeOut(TunerMotion.fadeSpec()))
            },
            label = "glyph",
        ) { shownKind ->
            Icon(
                painter = when (shownKind) {
                    GlyphKind.Spark -> TunerIcons.Spark
                    GlyphKind.Check -> TunerIcons.Check
                    GlyphKind.Error -> TunerIcons.Error
                },
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Later and Update share the whole width, a third and two thirds. Once Update is pressed Later
 * gives its share and its gap back and fades, and Update is the bar. A share rather than a
 * width, because it is the share that has to be able to reach nothing: a weight of zero is not
 * allowed, so Later leaves the row at the last sliver instead.
 */
@Composable
private fun Actions(state: DownloadState, phase: Phase, onLater: () -> Unit, onUpdate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val share by animateFloatAsState(
        targetValue = if (phase == Phase.Rest) 1f else 0f,
        animationSpec = TunerMotion.spatial(),
        label = "laterShare",
    )
    val laterAlpha by animateFloatAsState(
        targetValue = if (phase == Phase.Rest) 1f else 0f,
        animationSpec = TunerMotion.fadeSpec(),
        label = "laterAlpha",
    )
    val laterShare = share.coerceIn(0f, 1f)

    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(46.dp)) {
        if (laterShare > 0.002f) {
            Box(
                modifier = Modifier
                    .weight(laterShare)
                    .fillMaxHeight()
                    .alpha(laterAlpha)
                    .clip(PillShape)
                    .background(scheme.surfaceContainerHighest)
                    .bouncyClickable(enabled = phase == Phase.Rest, onClick = onLater),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.update_card_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    // Free to be wider than the sliver it is shrinking into, so it is cut, not squeezed.
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
            Spacer(Modifier.width(8.dp * laterShare))
        }
        UpdateButton(state, phase, onUpdate, Modifier.weight(2f).fillMaxHeight())
    }
}

/** Update, then the bar with the percentage in it, then "Installing…", or Try again. One shape throughout. */
@Composable
private fun UpdateButton(state: DownloadState, phase: Phase, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val busy = phase == Phase.Fetching || phase == Phase.Installing
    val container by animateColorAsState(
        targetValue = when (phase) {
            Phase.Fetching -> scheme.surfaceContainerHighest
            Phase.Installing -> scheme.primaryContainer
            else -> scheme.primary
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "updateContainer",
    )
    val ink by animateColorAsState(
        targetValue = if (busy) scheme.onPrimaryContainer else scheme.onPrimary,
        animationSpec = TunerMotion.colourSpec(),
        label = "updateInk",
    )
    val fraction by animateFloatAsState(
        targetValue = when (state) {
            is DownloadState.Downloading -> state.progress.coerceIn(0f, 1f)
            DownloadState.Installing -> 1f
            else -> 0f
        },
        animationSpec = TunerMotion.spatial(),
        label = "updateFraction",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = TunerMotion.fadeSpec(),
        label = "updateFillAlpha",
    )
    val fill = scheme.primaryContainer

    Box(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .drawBehind {
                drawRoundRect(
                    color = fill.copy(alpha = fillAlpha),
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            .bouncyClickable(enabled = phase == Phase.Rest || phase == Phase.Failed, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A soft glow runs along the bar while the system installs, and while the size is unknown.
        Sheen(
            active = phase == Phase.Installing || (state is DownloadState.Downloading && state.progress < 0f),
            modifier = Modifier.matchParentSize(),
        )
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { swap() },
            label = "updateLabel",
        ) { current ->
            Text(
                text = when (current) {
                    DownloadState.Idle -> stringResource(R.string.update_card_update)
                    is DownloadState.Downloading ->
                        if (current.progress >= 0f) "${(current.progress * 100).toInt()}%" else "…"
                    DownloadState.Installing -> stringResource(R.string.update_card_installing_button)
                    is DownloadState.Failed -> stringResource(R.string.update_card_retry)
                },
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Sheen(active: Boolean, modifier: Modifier) {
    val visible by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = TunerMotion.fadeSpec(),
        label = "sheenVisible",
    )
    if (visible <= 0f) return
    val sweep by rememberInfiniteTransition(label = "sheen").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "sheenSweep",
    )
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier.drawBehind {
            val centre = size.width * (-0.3f + 1.6f * sweep)
            val half = size.width * 0.3f
            drawRect(
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, ink.copy(alpha = 0.16f * visible), Color.Transparent),
                    startX = centre - half,
                    endX = centre + half,
                ),
            )
        },
    )
}
