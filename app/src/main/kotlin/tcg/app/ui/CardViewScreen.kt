package tcg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tcg.domain.Game
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.CardViewEntry
import tcg.domain.collection.productText
import tcg.domain.collection.treatmentText

/** Every Printing of one Card in both Languages, grouped by Language then Variant, with owned counts. Read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardViewScreen(
    viewModel: CardViewViewModel,
    game: Game,
    baseNumber: String,
    onOpenPrinting: (PrintingIdentifier) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(game, baseNumber) { viewModel.show(game, baseNumber) }
    val state = viewModel.state?.takeIf { it.baseNumber == baseNumber }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.name ?: baseNumber, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (viewModel.isLoading && state == null) {
            LoadingBar(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (state == null) return@LazyColumn
            item { Text("$baseNumber · All Printings of this Card", style = MaterialTheme.typography.bodyMedium) }
            state.languages.forEach { language ->
                item(key = "language-${language.language}") {
                    Text(language.language.label(), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
                }
                language.variants.forEach { group ->
                    item(key = "variant-${language.language}-${group.variant}") {
                        Text(group.variant?.label() ?: "Custom", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    group.entries.forEach { entry ->
                        item(key = entry.printing.identifier.toString()) {
                            CardViewRow(entry, onOpen = { onOpenPrinting(entry.printing.identifier) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardViewRow(entry: CardViewEntry, onOpen: () -> Unit) {
    val printing = entry.printing
    val owned = entry.owned > 0
    Row(
        Modifier.fillMaxWidth().then(if (owned) Modifier.clickable(onClick = onOpen) else Modifier).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOf(printing.name.takeIf { printing.variant == null }, printing.rarity, printing.treatmentText, printing.productText)
                .filter { !it.isNullOrBlank() }.joinToString(" · "),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (owned) Text("×${entry.owned}", style = MaterialTheme.typography.titleMedium)
    }
}
