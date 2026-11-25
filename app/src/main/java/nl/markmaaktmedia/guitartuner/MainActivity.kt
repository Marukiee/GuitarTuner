package nl.markmaaktmedia.guitartuner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.markmaaktmedia.guitartuner.ui.AppRoot
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.ui.theme.ColourSeed
import nl.markmaaktmedia.guitartuner.ui.theme.GuitarTunerTheme
import nl.markmaaktmedia.guitartuner.ui.theme.PaletteStyle
import nl.markmaaktmedia.markupdate.MarkUpdate

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

            // Debug builds can be pointed at any state of the app from the command line, so the
            // screens can be checked without a guitar or a lot of tapping. Release builds
            // compile this away.
            LaunchedEffect(Unit) {
                if (BuildConfig.DEBUG) applyDebugIntent(intent, viewModel)
                // The preview of the update card in Settings stays on until it is switched off.
                if (viewModel.uiState.value.bannerPreview && !(BuildConfig.DEBUG && intent.hasExtra("update"))) {
                    MarkUpdate.get().showPreview()
                }
            }

            GuitarTunerTheme(appearance = state.appearance) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // The update card is part of the tuner, where it pushes the rest down.
                    AppRoot(
                        viewModel,
                        showUpdateCard = !(BuildConfig.DEBUG && intent.getBooleanExtra("nobanner", false)),
                        // A card that is being looked at is not to be replaced by the answer of a real check.
                        checkForUpdates = !(BuildConfig.DEBUG && (intent.hasExtra("update") || intent.hasExtra("banner"))),
                    )
                }
            }
        }
    }

    /**
     * Debug only. `adb shell am start -n <activity> --es demo sweep --ei string 2 --es theme dark`
     * and friends: demo, string, instrument, tuning, theme (light, dark, system), black, seed,
     * style and banner (the made-up update, which plays when its Update is pressed).
     *
     * For the update card: `--es update preview|fail|clear` shows the made-up update, the failure
     * card, or removes either. `--ez nobanner true` keeps the card (and the check for a real one)
     * away from other screens.
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
        if (intent.hasExtra("banner")) {
            val on = intent.getBooleanExtra("banner", false)
            viewModel.setBannerPreview(on)
            if (on) MarkUpdate.get().showPreview() else MarkUpdate.get().reset()
        }
        when (intent.getStringExtra("update")) {
            "preview" -> MarkUpdate.get().showPreview()
            "fail" -> MarkUpdate.get().showFailurePreview("No connection to GitHub")
            "clear" -> MarkUpdate.get().reset()
        }
        intent.getStringExtra("demo")?.let { viewModel.runDemo(it, intent.getIntExtra("string", -1)) }
    }
}
