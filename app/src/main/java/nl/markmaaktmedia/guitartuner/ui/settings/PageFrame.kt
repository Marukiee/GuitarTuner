package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.ui.components.TunerIconButton
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons

/**
 * The frame every page under Settings sits in: a back button, the title, and a scrolling
 * column of slabs. One frame so the pages open the same way and the title is where the thumb
 * expects it.
 */
@Composable
fun PageFrame(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TunerIconButton(TunerIcons.Back, stringResource(R.string.action_back), onBack)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}
