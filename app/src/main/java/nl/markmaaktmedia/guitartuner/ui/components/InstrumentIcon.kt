package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockLayout
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.InstrumentSize
import nl.markmaaktmedia.guitartuner.domain.model.Silhouette

/**
 * The instrument as a small flat symbol on a tonal tile.
 *
 * The whole instrument rather than its head: at 44 dp a head is a block with dots, while a body
 * with a neck reads at once, and an acoustic, an electric, a banjo and a violin are told apart by
 * their outline alone.
 */
@Composable
fun InstrumentIcon(
    instrument: Instrument,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // The same colour as the head on the board; the chosen one takes the strong tone.
    val tile = if (selected) scheme.primary else scheme.primaryContainer
    val ink = if (selected) scheme.onPrimary else scheme.onPrimaryContainer
    val badge = instrument.badge
    val measurer = rememberTextMeasurer()
    val badgeStyle = MaterialTheme.typography.labelLarge
    Canvas(modifier) {
        val unit = size.minDimension / Grid
        withTransform({
            translate(size.width / 2f - Grid / 2f * unit, size.height / 2f - Grid / 2f * unit)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawCircle(tile, Grid / 2f, Centre)
            // Leaning with the head up to the right, the way an instrument is held.
            withTransform({
                rotate(45f, Centre)
                val fit = instrument.size.iconScale()
                scale(fit, fit, Centre)
            }) {
                drawInstrument(instrument, tile, ink)
            }
        }
        if (badge != null) {
            // In the corner the leaning instrument leaves free.
            val radius = size.minDimension * 0.20f
            val centre = Offset(size.width - radius, size.height - radius)
            drawCircle(tile, radius + 1.5.dp.toPx(), centre)
            drawCircle(ink, radius, centre)
            val text = measurer.measure(
                badge,
                badgeStyle.copy(fontSize = (radius * 1.35f).toSp(), lineHeight = (radius * 1.35f).toSp(), color = tile, fontWeight = FontWeight.Bold),
            )
            drawText(text, topLeft = centre - Offset(text.size.width / 2f, text.size.height / 2f))
        }
    }
}

/** The symbol is written on a square of this many units, upright, head at the top. */
private const val Grid = 48f

private val Centre = Offset(Grid / 2f, Grid / 2f)

private fun InstrumentSize.iconScale(): Float = when (this) {
    InstrumentSize.SMALL -> 0.86f
    InstrumentSize.MEDIUM -> 0.93f
    InstrumentSize.LARGE -> 1f
}

private fun DrawScope.drawInstrument(instrument: Instrument, tile: Color, ink: Color) {
    when (instrument.silhouette) {
        Silhouette.ACOUSTIC -> {
            val small = instrument.size == InstrumentSize.SMALL
            neck(ink, from = if (small) 13f else 9f)
            symmetricHead(ink, top = if (small) 7f else 2.5f, perSide = 2)
            drawPath(acousticBody(), ink)
            drawCircle(tile, 3.3f, Offset(24f, 32.5f))
            bar(tile, 38.2f, 8.5f)
        }

        Silhouette.ELECTRIC -> {
            neck(ink, from = 9f)
            if (instrument.layout == HeadstockLayout.INLINE) inlineHead(ink, 3) else symmetricHead(ink, 2.5f, 3)
            drawPath(electricBody(), ink)
            bar(tile, 30.8f, 8f)
            bar(tile, 36f, 8f)
        }

        // A longer neck, a slimmer body with one long horn and a single pickup.
        Silhouette.BASS -> {
            neck(ink, from = 6f, to = 31f)
            inlineHead(ink, 2, lift = -2f)
            drawPath(bassBody(), ink)
            bar(tile, 35.5f, 8.5f)
            drawCircle(tile, 1.6f, Offset(28.2f, 41f))
        }

        Silhouette.BANJO -> {
            neck(ink, from = 9f)
            symmetricHead(ink, 2.5f, 2)
            drawCircle(ink, 10.5f, Offset(24f, 33.5f))
            drawCircle(tile, 7.6f, Offset(24f, 33.5f), style = Stroke(1.5f))
            bar(tile, 36f, 8f)
        }

        Silhouette.MANDOLIN -> {
            neck(ink, from = 12f)
            symmetricHead(ink, 5f, 3)
            drawPath(
                Path().apply {
                    moveTo(24f, 21f)
                    cubicTo(27.5f, 23f, 34.5f, 30f, 34.5f, 36f)
                    cubicTo(34.5f, 41.5f, 30f, 44.5f, 24f, 44.5f)
                    cubicTo(18f, 44.5f, 13.5f, 41.5f, 13.5f, 36f)
                    cubicTo(13.5f, 30f, 20.5f, 23f, 24f, 21f)
                    close()
                },
                ink,
            )
            drawOval(tile, Offset(21f, 29f), Size(6f, 7.5f))
            bar(tile, 39.5f, 8f)
        }

        Silhouette.BOWED -> {
            neck(ink, from = 7f)
            // The scroll, and one peg through each side under it.
            drawCircle(ink, 3.6f, Offset(24f, 5.5f))
            drawCircle(tile, 1.2f, Offset(24f, 5.5f))
            drawLine(ink, Offset(19.5f, 11.5f), Offset(28.5f, 11.5f), 2.2f, StrokeCap.Round)
            drawPath(violinBody(), ink)
            for (side in listOf(-1f, 1f)) {
                drawLine(
                    tile,
                    Offset(24f + side * 4.4f, 31f),
                    Offset(24f + side * 5.6f, 37f),
                    1.5f,
                    StrokeCap.Round,
                )
            }
            bar(tile, 36.8f, 5f)
            // A cello stands on a pin.
            if (instrument.size == InstrumentSize.LARGE) {
                drawLine(ink, Offset(24f, 43f), Offset(24f, 48f), 1.8f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.neck(ink: Color, from: Float, to: Float = 27f) =
    drawRect(ink, Offset(22.1f, from), Size(3.8f, to - from))

/** The bridge, or a pickup: a short pill cut out of the body. */
private fun DrawScope.bar(color: Color, y: Float, width: Float) =
    drawRoundRect(color, Offset(24f - width / 2f, y), Size(width, 2.2f), CornerRadius(1.1f))

private fun DrawScope.symmetricHead(ink: Color, top: Float, perSide: Int) {
    drawRoundRect(ink, Offset(20.6f, top), Size(6.8f, 9.5f), CornerRadius(2.6f))
    val step = if (perSide > 2) 2.9f else 4.2f
    val radius = if (perSide > 2) 1.15f else 1.45f
    repeat(perSide) {
        val y = top + 2.1f + it * step
        drawCircle(ink, radius, Offset(19.4f, y))
        drawCircle(ink, radius, Offset(28.6f, y))
    }
}

/** The leaning head of an electric: every peg on one side. */
private fun DrawScope.inlineHead(ink: Color, pegs: Int, lift: Float = 0f) {
    drawPath(
        Path().apply {
            moveTo(22.1f, 12f + lift)
            lineTo(20.8f, 5f + lift)
            cubicTo(20.5f, 2f + lift, 23.5f, 1f + lift, 26.5f, 2.5f + lift)
            cubicTo(29.5f, 4f + lift, 29.5f, 6.5f + lift, 27.5f, 7f + lift)
            cubicTo(26f, 7.4f + lift, 25.9f, 9f + lift, 25.9f, 12f + lift)
            close()
        },
        ink,
    )
    val big = if (pegs < 3) 1.7f else 1.25f
    val step = if (pegs < 3) 4.4f else 3f
    repeat(pegs) { drawCircle(ink, big, Offset(19.2f, 3.8f + lift + it * step)) }
}

private fun acousticBody(): Path = Path().apply {
    moveTo(24f, 20.5f)
    cubicTo(29f, 20.5f, 31.8f, 22.8f, 31.8f, 25.8f)
    cubicTo(31.8f, 28.4f, 29.8f, 29f, 29.8f, 30.6f)
    cubicTo(29.8f, 32.4f, 34.8f, 32.8f, 34.8f, 37.6f)
    cubicTo(34.8f, 42.4f, 30f, 45f, 24f, 45f)
    cubicTo(18f, 45f, 13.2f, 42.4f, 13.2f, 37.6f)
    cubicTo(13.2f, 32.8f, 18.2f, 32.4f, 18.2f, 30.6f)
    cubicTo(18.2f, 29f, 16.2f, 28.4f, 16.2f, 25.8f)
    cubicTo(16.2f, 22.8f, 19f, 20.5f, 24f, 20.5f)
    close()
}

/** A solid body with its two horns, the upper one longer. */
private fun electricBody(): Path = Path().apply {
    moveTo(22f, 24.5f)
    cubicTo(20f, 24f, 16.8f, 19f, 15.2f, 20.6f)
    cubicTo(13.6f, 22.4f, 17.6f, 26.6f, 17.4f, 29.4f)
    cubicTo(17.2f, 32f, 13.2f, 33f, 13.2f, 37.8f)
    cubicTo(13.2f, 42.6f, 18.4f, 45f, 24.4f, 45f)
    cubicTo(30.8f, 45f, 35f, 42f, 34.8f, 37.2f)
    cubicTo(34.6f, 33f, 29.8f, 32f, 30.2f, 29f)
    cubicTo(30.6f, 26.2f, 34f, 24.8f, 32.6f, 23f)
    cubicTo(31.2f, 21.4f, 28.4f, 25f, 26f, 25.2f)
    close()
}

private fun bassBody(): Path = Path().apply {
    moveTo(22.3f, 29.5f)
    cubicTo(20f, 28.6f, 17.4f, 18.4f, 15.2f, 19.8f)
    cubicTo(13f, 21.4f, 18.4f, 28.6f, 18.2f, 31.8f)
    cubicTo(18f, 34.4f, 15f, 35.8f, 15f, 39.8f)
    cubicTo(15f, 44f, 19.2f, 46f, 24.2f, 46f)
    cubicTo(30f, 46f, 33.4f, 43.6f, 33.2f, 39.6f)
    cubicTo(33f, 35.8f, 29.2f, 35f, 29.4f, 32.6f)
    cubicTo(29.6f, 30.4f, 32f, 29.4f, 31f, 28.2f)
    cubicTo(29.8f, 27f, 27.6f, 29.4f, 25.8f, 29.6f)
    close()
}

private fun violinBody(): Path = Path().apply {
    moveTo(24f, 20.5f)
    cubicTo(28.4f, 20.5f, 31.4f, 22.4f, 31.4f, 25.4f)
    cubicTo(31.4f, 27.6f, 29.6f, 28.4f, 28.4f, 28.8f)
    cubicTo(29.2f, 30.4f, 29.2f, 31.6f, 28.8f, 33f)
    cubicTo(31.6f, 33.4f, 33.8f, 35.6f, 33.8f, 38.4f)
    cubicTo(33.8f, 42.4f, 29.4f, 44.8f, 24f, 44.8f)
    cubicTo(18.6f, 44.8f, 14.2f, 42.4f, 14.2f, 38.4f)
    cubicTo(14.2f, 35.6f, 16.4f, 33.4f, 19.2f, 33f)
    cubicTo(18.8f, 31.6f, 18.8f, 30.4f, 19.6f, 28.8f)
    cubicTo(18.4f, 28.4f, 16.6f, 27.6f, 16.6f, 25.4f)
    cubicTo(16.6f, 22.4f, 19.6f, 20.5f, 24f, 20.5f)
    close()
}
