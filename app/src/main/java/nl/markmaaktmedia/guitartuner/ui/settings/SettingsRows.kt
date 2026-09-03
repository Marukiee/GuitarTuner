package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.GroupedSpacing
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.connectedShape
import nl.markmaaktmedia.guitartuner.ui.theme.groupedShape

/**
 * Settings are grouped slabs: 24dp on the group's outer corners, 4dp on the ones facing a
 * neighbour, 2dp between. No dividers.
 *
 * A block of rows then reads as one object that has been cut into pieces rather than as a
 * stack of cards, and the shape carries the grouping so no line has to. It is the same
 * treatment the other Mark apps use, which is most of why they look related.
 *
 * The builder block is deliberately *not* composable. The corner treatment depends on how
 * many rows a group ends up with, and a row cannot know that while it is being composed,
 * so the rows are collected as lambdas first and drawn afterwards with their shape handed
 * to them. Callers write rows in order and never count them.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: SettingsGroupScope.() -> Unit,
) {
    val scope = SettingsGroupScope().apply(content)
    Column(modifier = modifier.padding(bottom = 22.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedSpacing),
        ) {
            scope.rows.forEachIndexed { index, row ->
                row(groupedShape(index, scope.rows.size))
            }
        }
    }
}

class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable (RoundedCornerShape) -> Unit>()

    fun row(content: @Composable (RoundedCornerShape) -> Unit) {
        rows += content
    }

    fun switch(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        description: String? = null,
        icon: (@Composable () -> Painter)? = null,
    ) = row { shape ->
        RowSurface(shape, onClick = { onCheckedChange(!checked) }, role = Role.Switch, label = title) {
            RowLabels(title, description, icon?.invoke(), Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
    }

    fun action(
        title: String,
        onClick: () -> Unit,
        description: String? = null,
        icon: (@Composable () -> Painter)? = null,
        trailing: @Composable (() -> Unit)? = null,
    ) = row { shape ->
        RowSurface(shape, onClick = onClick, role = Role.Button, label = title) {
            RowLabels(title, description, icon?.invoke(), Modifier.weight(1f))
            Box(Modifier.padding(end = 18.dp)) {
                if (trailing != null) {
                    trailing()
                } else {
                    Icon(
                        painter = TunerIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    fun info(
        title: String,
        description: String? = null,
        icon: (@Composable () -> Painter)? = null,
        trailing: @Composable (() -> Unit)? = null,
    ) = row { shape ->
        RowSurface(shape) {
            RowLabels(title, description, icon?.invoke(), Modifier.weight(1f))
            if (trailing != null) Box(Modifier.padding(end = 18.dp)) { trailing() }
        }
    }

    /**
     * A row of exclusive options, drawn as a segmented pill.
     *
     * Cheaper than a dialog for three or four choices: every option is visible, the
     * current one is visible, and choosing costs one tap rather than three.
     */
    fun <T> choice(
        title: String,
        options: List<T>,
        selected: T,
        label: (T) -> String,
        onSelect: (T) -> Unit,
        description: String? = null,
        icon: (@Composable () -> Painter)? = null,
    ) = row { shape ->
        RowSurface(shape) {
            Column(
                modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp),
            ) {
                RowLabelsInline(title, description, icon?.invoke())
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(GroupedSpacing),
                ) {
                    options.forEachIndexed { index, option ->
                        SegmentButton(
                            text = label(option),
                            selected = option == selected,
                            shape = connectedShape(index, options.size),
                            onClick = { onSelect(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    /**
     * A number with a minus and a plus.
     *
     * The reference pitch is the one setting here that is a value rather than a choice,
     * and it moves in single hertz steps that a slider cannot hit reliably with a thumb.
     */
    fun stepper(
        title: String,
        value: String,
        description: String? = null,
        icon: (@Composable () -> Painter)? = null,
        onDecrease: () -> Unit,
        onIncrease: () -> Unit,
    ) = row { shape ->
        RowSurface(shape) {
            RowLabels(title, description, icon?.invoke(), Modifier.weight(1f))
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StepButton(TunerIcons.Remove, "Lower", onDecrease)
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                StepButton(TunerIcons.Add, "Raise", onIncrease)
            }
        }
    }
}

@Composable
private fun StepButton(icon: Painter, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bouncyClickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RowSurface(
    shape: RoundedCornerShape,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    label: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
    Row(
        modifier = if (onClick == null) base
        else base.bouncyClickable(role = role, onClickLabel = label, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        animationSpec = TunerMotion.colourSpec(),
        label = "segment",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = TunerMotion.colourSpec(),
        label = "segmentContent",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .bouncyClickable(role = Role.RadioButton, onClickLabel = text, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
private fun RowLabels(
    title: String,
    description: String?,
    icon: Painter?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(vertical = 14.dp)) {
        RowLabelsInline(title, description, icon)
    }
}

@Composable
private fun RowLabelsInline(title: String, description: String?, icon: Painter?) {
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
