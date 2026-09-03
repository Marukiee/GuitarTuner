package nl.markmaaktmedia.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markmaaktmedia.guitartuner.domain.model.MicPermissionState
import nl.markmaaktmedia.guitartuner.domain.model.TunerEvent
import nl.markmaaktmedia.guitartuner.ui.TunerFeedback
import nl.markmaaktmedia.guitartuner.ui.TunerScreen
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.settings.SettingsScreen
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.GuitarTunerTheme
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.update.UpdateBanner
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The view model is created *outside* the theme on purpose: the theme mode is
            // part of its state, so the theme cannot be the thing that owns it.
            val viewModel: TunerViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            GuitarTunerTheme(
                themeMode = state.themeMode,
                dynamicColor = state.dynamicColor,
                pureBlack = state.pureBlack,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppContent(viewModel)
                        // Floats over the screen instead of pushing it down, same as MarkMySteps.
                        UpdateBanner(
                            modifier = Modifier.align(Alignment.TopCenter),
                            forceShow = state.bannerPreview,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two destinations and one back gesture, which is not worth a navigation library.
 *
 * The tuner keeps listening while Settings is open, so changing the microphone or the
 * reference pitch takes effect immediately and can be checked by playing a string.
 */
@Composable
private fun AppContent(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val feedback = remember { TunerFeedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(
            if (granted) MicPermissionState.Granted else MicPermissionState.Denied,
        )
    }

    // Re-read the permission on every resume: it may have been granted in system settings
    // while the app sat in the background.
    LifecycleResumeEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(
            if (granted) MicPermissionState.Granted else MicPermissionState.Unknown,
        )
        onPauseOrDispose { viewModel.stopListening() }
    }

    // Capture only runs while the screen is actually resumed and permitted.
    LaunchedEffect(state.micPermission) {
        if (state.micPermission == MicPermissionState.Granted) {
            @Suppress("MissingPermission")
            viewModel.startListening()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TunerEvent.StringTuned -> feedback.stringTuned()
                is TunerEvent.AdvancedTo -> Unit
                TunerEvent.AllStringsTuned -> feedback.allTuned()
            }
        }
    }

    // Settings sits *over* the tuner rather than replacing it, because predictive back needs
    // both on screen at once: the whole point of the gesture is that you see where you are
    // going before you commit to going there. [openness] is the one axis both layers read, 1
    // for Settings covering the tuner and 0 for gone, and the drag scrubs it directly, so a
    // peek and a real dismissal are one animation stopped at two different places instead of
    // two animations that have to be talked into agreeing.
    val scope = rememberCoroutineScope()
    val openness = remember { Animatable(if (showSettings) 1f else 0f) }
    // Presence is its own boolean rather than `openness.value > 0f`. Reading an Animatable in
    // composition recomposes everything that read it on every frame of the animation; read
    // inside `graphicsLayer` it only re-runs the draw phase, which is where this belongs.
    var settingsPresent by remember { mutableStateOf(showSettings) }
    var exitToRight by remember { mutableStateOf(true) }

    LaunchedEffect(showSettings) {
        if (showSettings) {
            settingsPresent = true
            exitToRight = true
            openness.animateTo(1f, TunerMotion.spatial())
        } else if (settingsPresent) {
            // Picks up wherever the drag left it, which is the thing that keeps a committed
            // gesture from snapping back to full size before it leaves.
            openness.animateTo(0f, TunerMotion.spatial())
            settingsPresent = false
        }
    }

    PredictiveBackHandler(enabled = showSettings) { events ->
        try {
            events.collect { event ->
                // The system says which edge the thumb came from, and the page leaves that
                // way, so it moves with the hand instead of always sliding right.
                exitToRight = event.swipeEdge == BackEventCompat.EDGE_LEFT
                // Only part of the way there. A drag is a preview of leaving and not the
                // leaving itself, so even a full swipe stops short and the commit plays the
                // rest of the distance.
                openness.snapTo(1f - PeekTravel * TunerMotion.Standard.transform(event.progress))
            }
            showSettings = false
        } catch (cancelled: CancellationException) {
            // The thumb came back. This coroutine is already cancelled, so the settle has to
            // run somewhere that is not, and the exception is not rethrown: for this API the
            // cancellation is the signal itself rather than a failure to propagate.
            scope.launch { openness.animateTo(1f, TunerMotion.spatial()) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The tuner sits back while Settings covers it and grows into place as
                    // Settings leaves. That is what makes a half finished gesture worth
                    // making: the page you are going back to is already moving.
                    val depth = lerp(1f, 0.94f, openness.value)
                    scaleX = depth
                    scaleY = depth
                },
        ) {
            if (state.micPermission != MicPermissionState.Granted) {
                PermissionGate(
                    onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            } else {
                TunerScreen(viewModel, onOpenSettings = { showSettings = true })
            }
        }

        if (settingsPresent) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val open = openness.value
                        translationX = (1f - open) * size.width * ExitTravel *
                            if (exitToRight) 1f else -1f
                        val shrink = lerp(0.90f, 1f, open)
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
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = { showSettings = false },
                )
            }
        }
    }
}

/** How far a drag is allowed to scrub the transition before the gesture is committed. */
private const val PeekTravel = 0.65f

/** How far Settings travels sideways on its way out, as a fraction of the screen width. */
private const val ExitTravel = 0.30f

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CardSquircle)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = TunerIcons.Mic,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(
            text = stringResource(R.string.mic_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(scheme.primary)
                .bouncyClickable(onClick = onRequest)
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.grant_permission),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimary,
            )
        }
    }
}
