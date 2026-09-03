package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.GroupedSpacing
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.groupedShape

/**
 * Instrument and tuning, in one sheet.
 *
 * They belong together because picking one nearly always means checking the other: the
 * player who switches to a bass is the same player who wants to know whether it is still
 * in drop D. Splitting them across two screens turns one decision into two navigations.
 */
@Composable
fun InstrumentSheet(
    instrument: Instrument,
    tuning: Tuning,
    onInstrument: (Instrument) -> Unit,
    onTuning: (Tuning) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    LaunchedEffect(instrument) {
        val index = Instrument.entries.indexOf(instrument)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            SheetHeading("Instrument")
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Instrument.entries, key = { it.name }) { entry ->
                    InstrumentCard(
                        instrument = entry,
                        selected = entry == instrument,
                        onClick = { onInstrument(entry) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SheetHeading("Tuning")
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(GroupedSpacing),
            ) {
                instrument.tunings.forEachIndexed { index, entry ->
                    TuningRow(
                        tuning = entry,
                        selected = entry.id == tuning.id,
                        shape = groupedShape(index, instrument.tunings.size),
                        onClick = { onTuning(entry) },
                    )
                }
            }

            if (tuning.isReentrant) {
                Text(
                    text = "This tuning is re-entrant: the strings are not in rising order, so " +
                        "auto advance follows pitch rather than the pegs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun InstrumentCard(
    instrument: Instrument,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "instrumentCard",
    )
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface

    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(CardSquircle)
            .background(container)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
                shape = CardSquircle,
            )
            .bouncyClickable(onClickLabel = instrument.displayName, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeadstockView(
            layout = instrument.layout,
            stringCount = instrument.defaultTuning.stringCount,
            scale = instrument.headstockScale,
            // The fill is a tint of the card's own content colour rather than a light
            // surface: on a selected card a near white silhouette swallows the outline,
            // the tuners and the strings, and all that is left is a pale blob.
            bodyColor = content.copy(alpha = 0.10f),
            outlineColor = content.copy(alpha = 0.75f),
            pegColor = if (selected) scheme.primary else content.copy(alpha = 0.85f),
            stringColor = content.copy(alpha = 0.55f),
            modifier = Modifier.size(width = 48.dp, height = 66.dp),
        )
        Text(
            text = instrument.shortName,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TuningRow(
    tuning: Tuning,
    selected: Boolean,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "tuningRow",
    )
    val content = if (selected) scheme.onSecondaryContainer else scheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .bouncyClickable(onClickLabel = tuning.displayName, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tuning.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
            )
            Text(
                text = tuning.notation,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.72f),
            )
        }
        if (selected) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = TunerIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
