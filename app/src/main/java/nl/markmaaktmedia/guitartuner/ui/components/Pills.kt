package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/** The filled pill used for the one primary action on a screen. */
@Composable
fun PrimaryPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    /** Sits after the label. For a button that moves you forward, this is the one. */
    trailingIcon: Painter? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    // Coloured when it can be pressed, grey when it cannot, and the change is a fade, not a switch.
    val fill by animateColorAsState(
        if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHigh,
        TunerMotion.colourSpec(), label = "pillFill",
    )
    val ink by animateColorAsState(
        if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
        TunerMotion.colourSpec(), label = "pillInk",
    )
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(fill)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        if (trailingIcon != null) {
            Icon(painter = trailingIcon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** The quieter sibling, for the action next to the primary one. */
@Composable
fun SecondaryPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    PrimaryPillButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
    )
}
