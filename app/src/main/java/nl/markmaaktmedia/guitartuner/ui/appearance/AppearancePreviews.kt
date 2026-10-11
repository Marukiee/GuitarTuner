package nl.markmaaktmedia.guitartuner.ui.appearance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.PaletteStyle
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.appearanceScheme
import nl.markmaaktmedia.guitartuner.ui.theme.blendSchemes
import nl.markmaaktmedia.guitartuner.ui.theme.rememberBlended
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/*
 * The idea behind these previews comes from the palette page in PixelPlayer
 * (theovilardo/PixelPlayer): every choice is drawn in the scheme it would produce, not
 * in the app's own colours, so you compare real results instead of names. PixelPlayer's
 * licence does not allow reuse of its code, so nothing here is copied. The tiles, the
 * mock screen and the selection motion are drawn for the tuner.
 */

/**
 * The scheme a preview tile shows, or null for the few frames before it exists.
 *
 * Generating a scheme costs a millisecond or more, and this page has fourteen tiles
 * that all change together when the light or dark switch flips. Done inline that is a
 * dropped frame or two on the very tap the user is watching, so the tiles are built on
 * a background thread and the old scheme stays on screen until the new one lands.
 * Once it has, it is blended in, so tiles follow a theme change in step with the rest
 * of the app instead of jumping ahead of it.
 */
@Composable
private fun rememberPreviewScheme(seed: Color, style: PaletteStyle, dark: Boolean, pureBlack: Boolean): ColorScheme? {
    val built by produceState<ColorScheme?>(null, seed, style, dark, pureBlack) {
        value = withContext(Dispatchers.Default) { appearanceScheme(seed, style, dark, pureBlack) }
    }
    val target = built ?: return null
    return rememberBlended(target, ::blendSchemes)
}

/**
 * A card that previews the whole scheme of one palette style: a small tuner
 * with its title, a device slab, two chips and the navigation pill, all in that
 * style's primary, secondary, tertiary and container tones.
 */
@Composable
fun StylePreviewCard(
    style: PaletteStyle,
    seed: Color,
    dark: Boolean,
    pureBlack: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = rememberPreviewScheme(seed, style, dark, pureBlack)
    PreviewTile(
        scheme = scheme,
        selected = selected,
        label = label,
        aspect = 0.82f,
        corner = 22.dp,
        badgeSize = 22.dp,
        labelStyle = MaterialTheme.typography.labelLarge,
        onClick = onClick,
        modifier = modifier,
        draw = { drawMiniScreen(it) },
    )
}

/**
 * A tile that previews one accent: the seed spread into primary, secondary and
 * tertiary in the palette style currently in use. [overlay] is for the wallpaper tile,
 * which carries an icon so it reads as "automatic" rather than as one more colour.
 */
@Composable
fun SeedPreviewTile(
    seed: Color,
    style: PaletteStyle,
    dark: Boolean,
    pureBlack: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(ColorScheme) -> Unit = {},
) {
    val scheme = rememberPreviewScheme(seed, style, dark, pureBlack)
    PreviewTile(
        scheme = scheme,
        selected = selected,
        label = label,
        aspect = 1f,
        corner = 22.dp,
        badgeSize = 18.dp,
        labelStyle = MaterialTheme.typography.labelSmall,
        onClick = onClick,
        modifier = modifier,
        overlay = { if (scheme != null) overlay(scheme) },
        draw = { drawOrbs(it) },
    )
}

/**
 * Tile, ring and label in one, shared by both kinds of preview so a style card and a
 * seed tile select in exactly the same way.
 *
 * Selection is a spring, not a state flip: the tile pulls in from a ring that grows
 * around it and a check badge pops onto the corner. A spring because it is size and
 * position, and shared so the ring and the pull-in stay one motion.
 */
@Composable
private fun PreviewTile(
    scheme: ColorScheme?,
    selected: Boolean,
    label: String,
    aspect: Float,
    corner: Dp,
    badgeSize: Dp,
    labelStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    draw: DrawScope.(ColorScheme) -> Unit,
) {
    val app = MaterialTheme.colorScheme
    val chosen by animateFloatAsState(if (selected) 1f else 0f, TunerMotion.springy(), label = "previewChosen")
    // Springs overshoot, and a ring narrower than nothing or a colour past its end
    // points is not a thing, so everything derived from the spring is clamped.
    val ring = chosen.coerceIn(0f, 1f)

    // Tiles fade in once their scheme is ready instead of popping.
    val shown by animateFloatAsState(if (scheme != null) 1f else 0f, TunerMotion.fadeSpec(), label = "previewShown")

    val outer = RoundedCornerShape(corner)
    val inner = RoundedCornerShape(corner - RingGap)

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.RadioButton
                contentDescription = label
            }
            .bouncyClickable(role = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .border(RingWidth * ring, lerp(Color.Transparent, app.primary, ring), outer)
                .padding(RingGap * chosen.coerceAtLeast(0f)),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = shown }
                    .clip(inner)
                    .background(scheme?.background ?: app.surfaceContainerHigh)
                    .drawBehind { if (scheme != null) draw(scheme) }
                    .border(1.dp, app.outlineVariant.copy(alpha = 0.7f), inner),
            ) { overlay() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(badgeSize)
                    .graphicsLayer {
                        val scale = chosen.coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(app.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = TunerIcons.Check,
                    contentDescription = null,
                    tint = app.onPrimary,
                    modifier = Modifier.size(badgeSize * 0.66f),
                )
            }
        }
        Text(
            text = label,
            style = labelStyle,
            color = lerp(app.onSurfaceVariant, app.primary, ring),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val RingWidth = 2.5.dp
private val RingGap = 5.dp

/** A miniature tuner, drawn straight into the tile in the tile's own scheme. */
private fun DrawScope.drawMiniScreen(scheme: ColorScheme) {
    val w = size.width
    val h = size.height
    val pad = w * 0.11f

    fun bar(x: Float, y: Float, width: Float, height: Float, color: Color) =
        drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2f))

    // The instrument bar and the settings button, the two halves of the top of the tuner.
    val topY = h * 0.07f
    val topH = h * 0.12f
    val settingsW = topH
    drawRoundRect(
        scheme.surfaceContainerHigh, Offset(pad, topY), Size(w - pad * 2 - settingsW - w * 0.03f, topH),
        CornerRadius(topH / 2f),
    )
    bar(pad + topH * 0.45f, topY + topH * 0.36f, w * 0.30f, topH * 0.26f, scheme.onSurface.copy(alpha = 0.8f))
    drawRoundRect(scheme.surfaceContainerHigh, Offset(w - pad - settingsW, topY), Size(settingsW, topH), CornerRadius(topH / 2f))
    drawCircle(scheme.primary, topH * 0.17f, Offset(w - pad - settingsW / 2f, topY + topH / 2f))

    // The line and the bubble that hangs from it.
    val lineX = w / 2f
    drawLine(scheme.tertiary, Offset(lineX, h * 0.24f), Offset(lineX, h * 0.55f), w * 0.022f, StrokeCap.Round)
    drawCircle(scheme.primary, w * 0.085f, Offset(lineX + w * 0.1f, h * 0.30f))
    drawCircle(scheme.primaryContainer, w * 0.10f, Offset(lineX, h * 0.575f))

    // Three string bubbles, one of them the target.
    val bubbleY = h * 0.72f
    val bubble = w * 0.052f
    for (i in 0..2) {
        val x = w * (0.28f + i * 0.22f)
        drawCircle(if (i == 1) scheme.secondaryContainer else scheme.surfaceContainerHigh, bubble, Offset(x, bubbleY))
    }

    // The control row: the auto pill and two round buttons, the way the bottom of the tuner sits.
    val rowY = h * 0.84f
    val rowH = h * 0.09f
    drawRoundRect(scheme.primaryContainer, Offset(pad, rowY), Size(w * 0.46f, rowH), CornerRadius(rowH / 2f))
    drawCircle(scheme.surfaceContainerHigh, rowH / 2f, Offset(w * 0.70f, rowY + rowH / 2f))
    drawCircle(scheme.surfaceContainerHigh, rowH / 2f, Offset(w * 0.70f + rowH * 1.25f, rowY + rowH / 2f))
}

/** The accent as three orbs on its container: primary large, secondary and tertiary orbiting it. */
private fun DrawScope.drawOrbs(scheme: ColorScheme) {
    val w = size.width
    val h = size.height
    drawRect(scheme.primaryContainer)
    val unit = min(w, h)
    // The top right corner stays empty: that is where the selection badge lands.
    drawCircle(scheme.primary, unit * 0.26f, Offset(w * 0.40f, h * 0.44f))
    drawCircle(scheme.tertiary, unit * 0.12f, Offset(w * 0.79f, h * 0.56f))
    drawCircle(scheme.secondary, unit * 0.14f, Offset(w * 0.62f, h * 0.80f))
}

/**
 * A small tuner in the colours the app is wearing right now. It is not a screenshot but the
 * same roles the real screen uses, so a palette change shows here in the context it will be
 * used in, above the controls that caused it.
 */
@Composable
fun LiveScreenPreview(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.background)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The line with its bubble on it, in the signal colours that never follow the palette.
        Box(Modifier.size(width = 92.dp, height = 132.dp).drawBehind {
            drawLine(signals.inTune, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height * 0.74f), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(signals.inTune, 15.dp.toPx(), Offset(size.width / 2f, 22.dp.toPx()))
            drawCircle(scheme.surfaceContainerHigh, 24.dp.toPx(), Offset(size.width / 2f, size.height - 24.dp.toPx()))
            drawCircle(signals.inTune, 24.dp.toPx(), Offset(size.width / 2f, size.height - 24.dp.toPx()), style = Stroke(2.dp.toPx()))
        })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MockSlab(scheme.surfaceContainerHigh, scheme.primaryContainer, scheme.onPrimaryContainer, scheme.onSurface, scheme.tertiaryContainer)
            MockSlab(scheme.surfaceContainerHigh, scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.onSurface, scheme.tertiaryContainer)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(start = 14.dp).size(width = 52.dp, height = 8.dp).clip(CircleShape).background(scheme.onPrimaryContainer.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun MockSlab(container: Color, badge: Color, onBadge: Color, text: Color, tag: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(onBadge))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(width = 64.dp, height = 9.dp).clip(CircleShape).background(text.copy(alpha = 0.8f)))
            Box(Modifier.size(width = 40.dp, height = 6.dp).clip(CircleShape).background(text.copy(alpha = 0.35f)))
        }
        Box(Modifier.size(width = 24.dp, height = 14.dp).clip(CircleShape).background(tag))
    }
}
