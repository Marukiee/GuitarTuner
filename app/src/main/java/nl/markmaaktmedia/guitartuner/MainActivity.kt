package nl.markmaaktmedia.guitartuner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markmaaktmedia.guitartuner.ui.AppRoot
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.ui.theme.ColourSeed
import nl.markmaaktmedia.guitartuner.ui.theme.GuitarTunerTheme
import nl.markmaaktmedia.guitartuner.ui.theme.PaletteStyle
import nl.markmaaktmedia.guitartuner.update.LocalUpdates
import nl.markmaaktmedia.guitartuner.update.UpdateBanner
import nl.markmaaktmedia.guitartuner.update.UpdateController

// AppCompatActivity so the per-app language (Settings, Look) applies without a restart by hand.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The view model is created *outside* the theme on purpose: the appearance is part
            // of its state, so the theme cannot be the thing that owns it.
            val viewModel: TunerViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val updates = remember { UpdateController(context.applicationContext, scope) }

            // Debug builds can be pointed at any state of the app from the command line, so the
            // screens can be checked without a guitar or a lot of tapping. Release builds
            // compile this away.
            LaunchedEffect(Unit) {
                if (BuildConfig.DEBUG) applyDebugIntent(intent, viewModel)
            }

            GuitarTunerTheme(appearance = state.appearance) {
                CompositionLocalProvider(LocalUpdates provides updates) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppRoot(viewModel, updates)
                            // Floats over the screen instead of pushing it down.
                            if (!(BuildConfig.DEBUG && intent.getBooleanExtra("nobanner", false))) {
                                UpdateBanner(
                                    controller = updates,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    forceShow = state.bannerPreview,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Debug only. `adb shell am start -n <activity> --es demo sweep --ei string 2 --es theme dark`
     * and friends: demo, string, instrument, tuning, theme (light, dark, system), black, seed,
     * style and banner.
     */
    private fun applyDebugIntent(intent: android.content.Intent, viewModel: TunerViewModel) {
        intent.getStringExtra("instrument")?.let { name ->
            Instrument.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { instrument ->
                viewModel.selectInstrument(instrument)
                intent.getStringExtra("tuning")?.let { id -> instrument.tuningById(id)?.let(viewModel::selectTuning) }
            }
        }
        val appearance = viewModel.uiState.value.appearance
        viewModel.setAppearance(
            appearance.copy(
                mode = intent.getStringExtra("theme")?.let { name ->
                    ThemeMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                } ?: appearance.mode,
                seed = intent.getStringExtra("seed")?.let(ColourSeed::fromKey) ?: appearance.seed,
                style = intent.getStringExtra("style")?.let(PaletteStyle::fromKey) ?: appearance.style,
                pureBlack = if (intent.hasExtra("black")) intent.getBooleanExtra("black", false) else appearance.pureBlack,
            ),
        )
        if (intent.hasExtra("banner")) viewModel.setBannerPreview(intent.getBooleanExtra("banner", false))
        intent.getStringExtra("demo")?.let { viewModel.runDemo(it, intent.getIntExtra("string", -1)) }
    }
}
