package nl.markmaaktmedia.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import nl.markmaaktmedia.guitartuner.update.UpdateBanner

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

    BackHandler(enabled = showSettings) { showSettings = false }

    AnimatedContent(
        targetState = showSettings,
        transitionSpec = {
            val spec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
            if (targetState) {
                (slideInHorizontally { it / 4 } + fadeIn(spec)) togetherWith
                    (slideOutHorizontally { -it / 8 } + fadeOut(spec))
            } else {
                (slideInHorizontally { -it / 8 } + fadeIn(spec)) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut(spec))
            }
        },
        label = "screen",
    ) { settings ->
        when {
            settings -> SettingsScreen(
                viewModel = viewModel,
                versionName = BuildConfig.VERSION_NAME,
                onBack = { showSettings = false },
            )

            state.micPermission != MicPermissionState.Granted -> PermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )

            else -> TunerScreen(viewModel, onOpenSettings = { showSettings = true })
        }
    }
}

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
