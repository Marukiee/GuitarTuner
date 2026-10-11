package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.groupedShape

/** The label above a group of settings rows. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    top: Dp = 12.dp,
    bottom: Dp = 10.dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = top, bottom = bottom),
    )
}

/**
 * One block of rows that reads as a single slab.
 *
 * Children are handed their index so each takes the right corner treatment, and the tiny
 * gap between them turns a solid rectangle into a stack of tiles without needing a divider
 * line anywhere.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** The surface every settings row sits on, shaped for its place in the group. */
@Composable
fun GroupedRow(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(groupedShape(index, total))
        .background(color)
    Column(
        modifier = if (onClick != null) base.bouncyClickable(onClick = onClick) else base,
        content = content,
    )
}

/** A round tonal badge holding an icon, used at the start of every settings row. */
@Composable
fun RowIcon(
    icon: Painter,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Box(modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val RowPadding = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)

/** A row with a switch. The whole row is the target, so the thumb is not a tiny bullseye. */
@Composable
fun SwitchRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedRow(index, total, modifier = modifier, onClick = { onChange(!checked) }) {
        Row(RowPadding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            RowText(title, subtitle, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** A row that does something when tapped, with a chevron or a custom trailing item. */
@Composable
fun ActionRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    GroupedRow(index, total, modifier = modifier, onClick = onClick) {
        Row(RowPadding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            RowText(title, subtitle, Modifier.weight(1f))
            if (trailing != null) trailing() else Icon(
                TunerIcons.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A category row on the Settings front page: icon, name and a line saying how it is set now. */
@Composable
fun CategoryRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    GroupedRow(index, total, onClick = onClick) {
        Row(RowPadding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(TunerIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** A row that shows a value and does nothing. */
@Composable
fun InfoRow(index: Int, total: Int, icon: Painter, title: String, value: String) {
    GroupedRow(index, total) {
        Row(RowPadding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row that explains something: a short title and the words under it in the quiet colour. */
@Composable
fun NoteRow(index: Int, total: Int, icon: Painter, title: String, body: String) {
    GroupedRow(index, total) {
        Row(RowPadding, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            RowText(title, body, Modifier.weight(1f))
        }
    }
}

/** A row holding arbitrary content, like a segmented control under a title. */
@Composable
fun ContentRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    GroupedRow(index, total) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RowIcon(icon)
                RowText(title, subtitle, Modifier.weight(1f))
            }
            content()
        }
    }
}

/** A row with a minus, a value and a plus, for a number that moves in steps a slider cannot hit. */
@Composable
fun StepperRow(
    index: Int,
    total: Int,
    icon: Painter,
    title: String,
    subtitle: String? = null,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
) {
    GroupedRow(index, total) {
        Row(RowPadding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(icon)
            RowText(title, subtitle, Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StepButton(TunerIcons.Remove, decreaseLabel, onDecrease)
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                StepButton(TunerIcons.Add, increaseLabel, onIncrease)
            }
        }
    }
}

@Composable
private fun StepButton(icon: Painter, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bouncyClickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** The dot next to a state: bright when on, dim when not. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        TunerMotion.colourSpec(), label = "statusDot",
    )
    Box(modifier.size(10.dp).background(color, CircleShape))
}
