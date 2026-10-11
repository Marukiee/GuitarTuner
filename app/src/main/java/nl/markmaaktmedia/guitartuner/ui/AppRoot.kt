package nl.markmaaktmedia.guitartuner.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.MicPermissionState
import nl.markmaaktmedia.guitartuner.domain.model.TunerEvent
import nl.markmaaktmedia.guitartuner.ui.appearance.AppearanceScreen
import nl.markmaaktmedia.guitartuner.ui.changelog.ChangelogScreen
import nl.markmaaktmedia.guitartuner.ui.components.PickMark
import nl.markmaaktmedia.guitartuner.ui.components.PrimaryPillButton
import nl.markmaaktmedia.guitartuner.ui.nav.NavHost
import nl.markmaaktmedia.guitartuner.ui.nav.Route
import nl.markmaaktmedia.guitartuner.ui.nav.rememberNavigator
import nl.markmaaktmedia.guitartuner.ui.settings.AboutPage
import nl.markmaaktmedia.guitartuner.ui.settings.LookPage
import nl.markmaaktmedia.guitartuner.ui.settings.SettingsScreen
import nl.markmaaktmedia.guitartuner.ui.settings.TunerSettingsPage
import nl.markmaaktmedia.guitartuner.ui.settings.UpdatesPage
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.tuner.TunerScreen
import nl.markmaaktmedia.guitartuner.update.UpdateController

/**
 * The tuner, and the pages over it.
 *
 * The tuner keeps listening while Settings is open, so changing the microphone or the
 * reference pitch takes effect immediately and can be checked by playing a string.
 */
@Composable
fun AppRoot(viewModel: TunerViewModel, updates: UpdateController) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberNavigator()

    val feedback = remember { TunerFeedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    SideEffect {
        feedback.chimeEnabled = state.chime
        feedback.hapticsEnabled = state.haptics
    }

    // A phone that dims while you tune is a phone you have to touch to keep tuning.
    val view = LocalView.current
    SideEffect { view.keepScreenOn = state.keepScreenOn }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context as? Activity
        viewModel.onPermissionResult(
            when {
                granted -> MicPermissionState.Granted
                // Once the system stops showing its dialog the only way back is Settings, and
                // the gate has to say so instead of offering a button that does nothing.
                activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                    MicPermissionState.PermanentlyDenied
                else -> MicPermissionState.Denied
            },
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

    // Debug builds only: `--es nav settings,look` opens that stack of pages straight away.
    LaunchedEffect(Unit) {
        if (nl.markmaaktmedia.guitartuner.BuildConfig.DEBUG) {
            (context as? Activity)?.intent?.getStringExtra("nav")?.split(',')?.forEach { name ->
                Route.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(navigator::push)
            }
        }
    }

    NavHost(
        navigator = navigator,
        root = {
            if (state.micPermission == MicPermissionState.Granted) {
                TunerScreen(viewModel, onOpenSettings = { navigator.push(Route.Settings) })
            } else {
                PermissionGate(
                    permanentlyDenied = state.micPermission == MicPermissionState.PermanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }
        },
        page = { route ->
            when (route) {
                Route.Settings -> SettingsScreen(viewModel, onOpen = navigator::push, onBack = navigator::pop)
                Route.TunerSettings -> TunerSettingsPage(viewModel, onBack = navigator::pop)
                Route.Look -> LookPage(viewModel, onBack = navigator::pop, onOpen = navigator::push)
                Route.Appearance -> AppearanceScreen(viewModel, onBack = navigator::pop)
                Route.Updates -> UpdatesPage(updates, viewModel, onBack = navigator::pop, onOpen = navigator::push)
                Route.Changelog -> ChangelogScreen(onBack = navigator::pop)
                Route.About -> AboutPage(onBack = navigator::pop)
            }
        },
    )
}

/**
 * Said once, plainly, and with one button.
 *
 * The button is the same pill whatever happens: it asks, and if the system will not ask again it
 * becomes the way to Settings, so there is never a button that looks like it works and does not.
 */
@Composable
private fun PermissionGate(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CardSquircle)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            PickMark(size = 54.dp, color = scheme.primary)
        }
        Text(
            text = stringResource(R.string.mic_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.mic_permission_blocked else R.string.mic_permission_rationale,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            PrimaryPillButton(
                label = stringResource(R.string.open_app_settings),
                onClick = onOpenSettings,
                icon = TunerIcons.Settings,
            )
        } else {
            PrimaryPillButton(
                label = stringResource(R.string.grant_permission),
                onClick = onRequest,
                icon = TunerIcons.Mic,
            )
        }
    }
}
