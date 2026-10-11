package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading

/**
 * Collects one derived value out of the reading flow.
 *
 * The `distinctUntilChanged` is the entire point: the flow itself fires twenty one times a
 * second, and without it every text on the screen would recompose that often to show a
 * number that changed once.
 */
@Composable
internal fun <T> StateFlow<TuningReading?>.select(
    initial: T,
    selector: (TuningReading?) -> T,
): State<T> {
    val flow = remember(this) { map(selector).distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initial)
}
