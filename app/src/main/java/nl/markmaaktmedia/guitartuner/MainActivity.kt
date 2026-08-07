package nl.markmaaktmedia.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import nl.markmaaktmedia.guitartuner.ui.theme.GuitarTunerTheme
import nl.markmaaktmedia.guitartuner.update.UpdateBanner

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GuitarTunerTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        TunerHost()
                        // Floats over the screen instead of pushing it down, same as MarkMySteps.
                        UpdateBanner(Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

/**
 * Wires permission, lifecycle and one shot feedback around the view model.
 *
 * The expressive visualizer and headstock live in [TunerScreen]; this scaffolding is what they
 * plug into and is intentionally kept free of any drawing.
 */
@Composable
private fun TunerHost(viewModel: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val feedback = remember { TunerFeedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(
            if (granted) MicPermissionState.Granted else MicPermissionState.Denied,
        )
    }

    // Re-read the permission on every resume: the user may have granted it in system settings
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
                TunerEvent.AllStringsTuned -> Unit
            }
        }
    }

    if (state.micPermission != MicPermissionState.Granted) {
        PermissionGate(onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
    } else {
        TunerScreen(viewModel)
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mic_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}
