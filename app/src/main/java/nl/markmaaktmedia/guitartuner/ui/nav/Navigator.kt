package nl.markmaaktmedia.guitartuner.ui.nav

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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

/** How far a drag is allowed to scrub the transition before the gesture is committed. */
private const val PeekTravel = 0.65f

/** How far a page travels sideways on its way out, as a fraction of the screen width. */
private const val ExitTravel = 0.30f

/** How far the page beneath sinks back while another covers it. */
private const val SinkScale = 0.94f

/**
 * Draws the root and every page over it, and owns the back gesture.
 *
 * The root sits back while a page covers it and grows into place as the page leaves, which is
 * what makes a half finished gesture worth making: the page you are going back to is already
 * moving.
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
                // The system says which edge the thumb came from, and the page leaves that
                // way, so it moves with the hand instead of always sliding right.
                top.exitToRight = event.swipeEdge == BackEventCompat.EDGE_LEFT
                // Only part of the way there. A drag is a preview of leaving and not the
                // leaving itself, so even a full swipe stops short and the commit plays the
                // rest of the distance.
                top.openness.snapTo(1f - PeekTravel * TunerMotion.Standard.transform(event.progress))
            }
            top.leaving = true
        } catch (cancelled: CancellationException) {
            // The thumb came back. This coroutine is already cancelled, so the settle has to
            // run somewhere that is not, and the exception is not rethrown: for this API the
            // cancellation is the signal itself rather than a failure to propagate.
            scope.launch { top.openness.animateTo(1f, TunerMotion.spatial()) }
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val cover = navigator.layers.firstOrNull()?.openness?.value ?: 0f
                    val depth = lerp(1f, SinkScale, cover)
                    scaleX = depth
                    scaleY = depth
                },
        ) { root() }

        navigator.layers.forEachIndexed { index, layer ->
            key(layer) {
                LaunchedEffect(layer.leaving) {
                    if (!layer.leaving) {
                        layer.openness.animateTo(1f, TunerMotion.spatial())
                    } else {
                        // Picks up wherever a drag left it, which is what keeps a committed
                        // gesture from snapping back to full size before it leaves.
                        layer.openness.animateTo(0f, TunerMotion.spatial())
                        navigator.layers.remove(layer)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val open = layer.openness.value
                            translationX = (1f - open) * size.width * ExitTravel *
                                if (layer.exitToRight) 1f else -1f
                            val sunk = navigator.layers.getOrNull(index + 1)?.openness?.value ?: 0f
                            val shrink = lerp(0.90f, 1f, open) * lerp(1f, SinkScale, sunk)
                            scaleX = shrink
                            scaleY = shrink
                            // Opaque for the whole peek, fading only on the way out. A page that
                            // goes translucent under the thumb reads as broken, not as leaving.
                            alpha = (open / (1f - PeekTravel)).coerceIn(0f, 1f)
                            // Corners round off as it lifts, square up as it settles: the same
                            // cue the system uses to say "this is a card now, not the screen".
                            shape = RoundedCornerShape(lerp(28.dp.toPx(), 0f, open))
                            clip = true
                        },
                ) { page(layer.route) }
            }
        }
    }
}
