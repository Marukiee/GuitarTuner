package nl.markmaaktmedia.guitartuner.ui.changelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.BuildConfig
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.settings.PageFrame
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.update.ChangeEntry
import nl.markmaaktmedia.guitartuner.update.Changelog

/** What changed in each version, newest first, in the language of the app. */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val entries = remember(language) { Changelog.load(context, language) }
    val current = BuildConfig.VERSION_NAME

    PageFrame(stringResource(R.string.changelog_title), onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            entries.forEach { ChangeCard(it, installed = it.version == current) }
        }
    }
}

@Composable
private fun ChangeCard(entry: ChangeEntry, installed: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardSquircle)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.version, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                entry.date, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (installed) {
                Text(
                    stringResource(R.string.changelog_installed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(entry.title, style = MaterialTheme.typography.titleMedium)
        Group(R.string.changelog_new, entry.new)
        Group(R.string.changelog_better, entry.better)
        Group(R.string.changelog_fixed, entry.fixed)
    }
}

@Composable
private fun Group(label: Int, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
