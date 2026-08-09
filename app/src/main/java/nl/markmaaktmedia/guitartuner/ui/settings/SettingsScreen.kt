package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import kotlin.math.roundToInt

/**
 * Settings in the current Android idiom.
 *
 * The previous version was a stack of Material cards with radio buttons in them, which is the
 * 2021 look. What replaced it is how Android itself now renders settings: each row is its own
 * rounded surface, rows are grouped with a small gap between them, and the outer corners of a
 * group are much larger than the inner ones so the group still reads as one block. Selection is
 * shown by filling the row and moving the check to the trailing edge rather than by a radio
 * button on the left.
 *
 * The microphone group is the one that has to be here rather than inferred. Which capture path
 * works is a property of the individual handset, and this app was inert on a phone with a damaged
 * rear microphone precisely because that choice was hardcoded.
 */
@Composable
fun SettingsScreen(
    instrument: Instrument,
    micSource: MicSource,
    micOptions: List<MicSource>,
    themeMode: ThemeMode,
    referenceHz: Float,
    onInstrument: (Instrument) -> Unit,
    onMicSource: (MicSource) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onReferenceHz: (Float) -> Unit,
    bannerPreview: Boolean,
    onBannerPreview: (Boolean) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // A large collapsing title is the current pattern for a settings destination, and it
            // gives the expressive type scale somewhere to actually show up.
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SettingsGroup(
                title = "Instrument",
                caption = "Sets the tuning, the number of pegs and the headstock shape.",
            ) {
                Instrument.entries.forEachIndexed { index, option ->
                    ChoiceRow(
                        label = option.displayName,
                        detail = option.strings.joinToString("  ") { it.fullLabel },
                        selected = option == instrument,
                        shape = groupShape(index, Instrument.entries.size),
                        onSelect = { onInstrument(option) },
                    )
                }
            }

            SettingsGroup(
                title = "Microphone",
                caption = "Decides which physical microphone the phone opens. If one of yours is " +
                    "damaged, pick another here and watch the level bar on the tuner.",
            ) {
                micOptions.forEachIndexed { index, option ->
                    ChoiceRow(
                        label = option.label,
                        detail = option.description,
                        selected = option == micSource,
                        shape = groupShape(index, micOptions.size),
                        onSelect = { onMicSource(option) },
                    )
                }
            }

            SettingsGroup(title = "Appearance", caption = null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(GROUP_OUTER),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == themeMode,
                                onClick = { onThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    ThemeMode.entries.size,
                                ),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            SettingsGroup(
                title = "Reference pitch",
                caption = "Every target frequency is derived from this, so a whole ensemble can " +
                    "be moved off concert pitch at once.",
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(GROUP_OUTER),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            text = "A = ${referenceHz.roundToInt()} Hz",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Slider(
                            value = referenceHz,
                            onValueChange = { onReferenceHz(it.roundToInt().toFloat()) },
                            valueRange = 415f..466f,
                            steps = 50,
                        )
                    }
                }
            }

            UpdatesGroup(
                versionName = versionName,
                bannerPreview = bannerPreview,
                onBannerPreview = onBannerPreview,
            )
        }
    }
}

private val GROUP_OUTER = 24.dp
private val GROUP_INNER = 6.dp

/**
 * Large radii on the outside of a group, small ones between its rows. That contrast is what makes
 * a stack of separate surfaces read as one grouped list instead of a pile of cards.
 */
private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER else GROUP_INNER
    val bottom = if (index == count - 1) GROUP_OUTER else GROUP_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun SettingsGroup(
    title: String,
    caption: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        content()
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    shape: Shape,
    onSelect: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rowContainer",
    )

    Surface(
        onClick = onSelect,
        shape = shape,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Plain-language explanation of what each capture path actually does. */
private val MicSource.description: String
    get() = when (this) {
        MicSource.VoiceRecognition ->
            "Gain control off, and a microphone that works on nearly every phone. The default."
        MicSource.Main -> "The primary microphone, at the bottom next to the USB-C port."
        MicSource.Unprocessed ->
            "Bypasses all processing, best accuracy, but often a secondary microphone."
        MicSource.Camcorder -> "The rear-facing microphone, next to the camera."
    }
