package nl.markmaaktmedia.guitartuner.ui.nav

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import kotlin.coroutines.cancellation.CancellationException

/** Every page that sits over the tuner. The tuner itself is the root and has no route. */
enum class Route { Settings, TunerSettings, Look, Appearance, Updates, Changelog, About }

/**
 * The pages over the tuner, as a stack of layers.
 *
 * Each page is a layer with an `openness` of its own, 1 for fully over what is beneath it and 0
 * for gone. Push animates the new layer up to 1, pop animates it back to 0 and drops it, and a
 * back gesture scrubs that same number directly. So a peek, a cancelled peek and a real
 * dismissal are one animation stopped at different places, not three animations that have to
 * be talked into agreeing.
 *
 * Pages underneath stay composed. That is what lets predictive back show the page you are
 * about to land on, and it is also why backing out of a sub page returns to the same place in
 * the list you left from: its scroll position was never thrown away.
 */
@Stable
class Navigator(initial: List<Route> = emptyList()) {

    class Layer(val route: Route, startOpen: Boolean) {
        val openness = Animatable(if (startOpen) 1f else 0f)
        var leaving by mutableStateOf(false)
        var exitToRight by mutableStateOf(true)

        /** True while a back gesture holds the page, and while a cancelled one settles back. */
        var dragging by mutableStateOf(false)

        /**
         * How far this page covers the one beneath it, 0 to 1. Arriving it follows the
         * emphasised curve in time; leaving it follows the finger, so the page beneath comes
         * back exactly as fast as this one goes.
         */
        fun cover(): Float {
            val open = openness.value
            return if (leaving || dragging) 1f - Emphasised.transform(1f - open) else Emphasised.transform(open)
        }
    }

    val layers = mutableStateListOf<Layer>().apply { initial.forEach { add(Layer(it, startOpen = true)) } }

    /** The routes that are staying, bottom to top. Used to restore the stack after a recreate. */
    val routes: List<Route> get() = layers.filter { !it.leaving }.map { it.route }

    val canGoBack: Boolean get() = layers.any { !it.leaving }

    fun push(route: Route) {
        // A second tap while the first page is still arriving must not stack a copy of it.
        if (layers.any { !it.leaving && it.route == route }) return
        layers += Layer(route, startOpen = false)
    }

    fun pop() {
        layers.lastOrNull { !it.leaving }?.leaving = true
    }

    /** Back to the tuner from anywhere, which is what a tap on the tuner behind would be. */
    fun popAll() {
        layers.forEach { it.leaving = true }
    }

    companion object {
        val Saver = Saver<Navigator, List<String>>(
            save = { navigator -> navigator.routes.map { it.name } },
            restore = { names -> Navigator(names.map { Route.valueOf(it) }) },
        )
    }
}

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }

private val Emphasised = TunerMotion.Emphasised

/**
 * A page arrives in this long, and going back from rest takes as long again, less if the finger has
 * already taken it part of the way. The same 350 ms both ways as the other apps of the family.
 */
private const val PageMillis = 350

/** How far the page beneath slides away, as a fraction of the screen width. */
private const val BeneathSlide = 1f / 3f

/** How far the page beneath shrinks while another covers it. */
private const val BeneathShrink = 0.08f

/** How far the leaving page shrinks on its way out. */
private const val LeaveShrink = 0.15f

/**
 * Draws the root and every page over it, and owns the back gesture.
 *
 * It is the shared axis the other apps of the family use (PixelPlayer, MarkMySteps): a page
 * arrives from a third of the screen away while it fades in, and the page beneath slides the
 * other way and shrinks a little. The back gesture seeks that same movement. The page follows
 * the finger on a cubic, so a short pull moves it very little and a long pull sends it all the
 * way, shrinking as it goes, and the page beneath comes back as it leaves. Letting go finishes
 * the exit, and bringing the thumb back eases the page home.
 */
@Composable
fun NavHost(
    navigator: Navigator,
    root: @Composable () -> Unit,
    page: @Composable (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = navigator.canGoBack) { events ->
        val top = navigator.layers.lastOrNull { !it.leaving } ?: return@PredictiveBackHandler
        try {
            events.collect { event ->
                top.dragging = true
                // The system says which edge the thumb came from, and the page leaves that
                // way, so it moves with the hand instead of always sliding right.
                top.exitToRight = event.swipeEdge == BackEventCompat.EDGE_LEFT
                top.openness.snapTo(1f - event.progress)
            }
            top.leaving = true
            top.dragging = false
        } catch (cancelled: CancellationException) {
            // The thumb came back. This coroutine is already cancelled, so the settle has to
            // run somewhere that is not, and the exception is not rethrown: for this API the
            // cancellation is the signal itself rather than a failure to propagate.
            scope.launch {
                top.openness.animateTo(1f, tween(200, easing = LinearEasing))
                top.dragging = false
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val cover = navigator.layers.firstOrNull()?.cover() ?: 0f
                    translationX = -size.width * BeneathSlide * cover
                    val shrink = 1f - BeneathShrink * cover
                    scaleX = shrink
                    scaleY = shrink
                },
        ) { root() }

        navigator.layers.forEachIndexed { index, layer ->
            key(layer) {
                LaunchedEffect(layer.leaving) {
                    if (!layer.leaving) {
                        layer.openness.animateTo(1f, tween(PageMillis, easing = LinearEasing))
                    } else {
                        // Picks up wherever a drag left it, which is what keeps a committed
                        // gesture from snapping back to full size before it leaves.
                        val remaining = layer.openness.value
                        layer.openness.animateTo(
                            0f,
                            tween((remaining * PageMillis).toInt().coerceAtLeast(120), easing = LinearEasing),
                        )
                        navigator.layers.remove(layer)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val open = layer.openness.value
                            var x: Float
                            var scale: Float
                            var fade = 1f
                            if (layer.leaving || layer.dragging) {
                                // Away: slow at first and then all at once, shrinking a little.
                                val gone = 1f - open
                                x = size.width * gone * gone * gone * if (layer.exitToRight) 1f else -1f
                                scale = 1f - LeaveShrink * Emphasised.transform(gone)
                            } else {
                                val arrived = Emphasised.transform(open)
                                x = size.width * BeneathSlide * (1f - arrived)
                                scale = 1f
                                fade = arrived
                            }
                            // A page that has another over it sinks away the same way the root does.
                            val cover = navigator.layers.getOrNull(index + 1)?.cover() ?: 0f
                            x -= size.width * BeneathSlide * cover
                            scale *= 1f - BeneathShrink * cover
                            translationX = x
                            scaleX = scale
                            scaleY = scale
                            alpha = fade
                        },
                ) { page(layer.route) }
            }
        }
    }
}
