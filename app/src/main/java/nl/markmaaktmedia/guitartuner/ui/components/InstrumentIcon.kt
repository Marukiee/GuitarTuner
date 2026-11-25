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
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.ui.tuner.HeadStyle
import nl.markmaaktmedia.guitartuner.ui.tuner.HeadStyleChoice
import nl.markmaaktmedia.guitartuner.ui.tuner.drawScallop

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
    val style = HeadStyleChoice.current
    val scheme = MaterialTheme.colorScheme
    // The same family of colour as the head on the board; the chosen one takes the strong tone.
    val (tile, ink) = when {
        style == HeadStyle.BUTTONS && selected -> scheme.tertiary to scheme.onTertiary
        style == HeadStyle.BUTTONS -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        selected -> scheme.primary to scheme.onPrimary
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Canvas(modifier) {
        val unit = size.minDimension / Grid
        withTransform({
            translate(size.width / 2f - Grid / 2f * unit, size.height / 2f - Grid / 2f * unit)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            when (style) {
                HeadStyle.SOFT -> drawCircle(tile, Grid / 2f, Centre)
                HeadStyle.BUTTONS -> drawRoundRect(tile, size = Size(Grid, Grid), cornerRadius = CornerRadius(15f))
                HeadStyle.BLOCKS -> drawScallop(Centre, 25.5f, tile)
            }
            // Leaning with the head up to the right, the way an instrument is held.
            withTransform({
                rotate(45f, Centre)
                val fit = instrument.iconSize()
                scale(fit, fit, Centre)
            }) {
                drawInstrument(instrument, tile, ink)
            }
        }
    }
}

/** The symbol is written on a square of this many units, upright, head at the top. */
private const val Grid = 48f

private val Centre = Offset(Grid / 2f, Grid / 2f)

private fun Instrument.iconSize(): Float = when (this) {
    Instrument.UKULELE -> 0.86f
    Instrument.MANDOLIN, Instrument.VIOLIN -> 0.90f
    else -> 0.98f
}

private fun DrawScope.drawInstrument(instrument: Instrument, tile: Color, ink: Color) {
    when (instrument) {
        Instrument.ACOUSTIC, Instrument.UKULELE -> {
            neck(ink, from = if (instrument == Instrument.UKULELE) 13f else 9f)
            symmetricHead(ink, top = if (instrument == Instrument.UKULELE) 7f else 2.5f, perSide = 2)
            drawPath(acousticBody(), ink)
            drawCircle(tile, 3.3f, Offset(24f, 32.5f))
            bar(tile, 38.2f, 8.5f)
        }

        Instrument.ELECTRIC, Instrument.GUITAR_7 -> {
            neck(ink, from = 9f)
            if (instrument == Instrument.ELECTRIC) inlineHead(ink, 3) else symmetricHead(ink, 2.5f, 3)
            drawPath(electricBody(), ink)
            bar(tile, 30.8f, 8f)
            bar(tile, 36f, 8f)
        }

        Instrument.BASS_4, Instrument.BASS_5, Instrument.BASS_6 -> {
            neck(ink, from = 8f)
            if (instrument == Instrument.BASS_4) symmetricHead(ink, 1.5f, 2) else inlineHead(ink, 3)
            withTransform({ translate(0f, 1.5f) }) {
                drawPath(electricBody(), ink)
                bar(tile, 33.5f, 9f)
                drawCircle(tile, 1.5f, Offset(28.5f, 39f))
            }
        }

        Instrument.BANJO -> {
            neck(ink, from = 9f)
            symmetricHead(ink, 2.5f, 2)
            drawCircle(ink, 10.5f, Offset(24f, 33.5f))
            drawCircle(tile, 7.6f, Offset(24f, 33.5f), style = Stroke(1.5f))
            bar(tile, 36f, 8f)
        }

        Instrument.MANDOLIN -> {
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

        Instrument.VIOLIN, Instrument.CELLO -> {
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
            if (instrument == Instrument.CELLO) drawLine(ink, Offset(24f, 43f), Offset(24f, 48f), 1.8f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.neck(ink: Color, from: Float) =
    drawRect(ink, Offset(22.1f, from), Size(3.8f, 27f - from))

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
private fun DrawScope.inlineHead(ink: Color, pegs: Int) {
    drawPath(
        Path().apply {
            moveTo(22.1f, 12f)
            lineTo(20.8f, 5f)
            cubicTo(20.5f, 2f, 23.5f, 1f, 26.5f, 2.5f)
            cubicTo(29.5f, 4f, 29.5f, 6.5f, 27.5f, 7f)
            cubicTo(26f, 7.4f, 25.9f, 9f, 25.9f, 12f)
            close()
        },
        ink,
    )
    repeat(pegs) { drawCircle(ink, 1.25f, Offset(19.2f, 3.8f + it * 3f)) }
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
