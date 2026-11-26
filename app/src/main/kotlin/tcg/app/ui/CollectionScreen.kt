package tcg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.CollectionRow
import tcg.domain.onepiece.OnePiece

/** What the Collection list can start; later slices replace the stubs behind these. */
class CollectionActions(
    val onAdd: () -> Unit,
    val onExportCsv: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onUpdateCatalog: () -> Unit,
    /** Debug builds only; null hides the menu entry. */
    val onSeedSampleData: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    actions: CollectionActions,
    updatingCatalog: Boolean,
    catalogRevision: Int,
    snackbarHostState: SnackbarHostState,
    onOpen: (PrintingIdentifier) -> Unit,
) {
    LaunchedEffect(catalogRevision) { viewModel.refresh() }
    var menuOpen by remember { mutableStateOf(false) }
    val rows = viewModel.rows

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collection") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuEntry("Export CSV", onDismiss = { menuOpen = false }, onClick = actions.onExportCsv)
                        MenuEntry("Backup", onDismiss = { menuOpen = false }, onClick = actions.onBackup)
                        MenuEntry("Restore", onDismiss = { menuOpen = false }, onClick = actions.onRestore)
                        MenuEntry(
                            "Update catalog",
                            enabled = !updatingCatalog,
                            onDismiss = { menuOpen = false },
                            onClick = actions.onUpdateCatalog,
                        )
                        actions.onSeedSampleData?.let {
                            MenuEntry("Add sample data (debug)", onDismiss = { menuOpen = false }, onClick = it)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onAdd) { Icon(Icons.Default.Add, "Add cards") }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = viewModel.search,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Search Base Number or name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                rows == null -> Unit
                rows.isEmpty() -> EmptyState(
                    if (viewModel.search.isBlank()) "Your Collection is empty.\nTap + to add your first cards."
                    else "No cards match your search.",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { with(it.printing) { "$game/$language/$sourceId" } }) { row ->
                        CollectionRowItem(row, onClick = { onOpen(row.printing) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, onDismiss: () -> Unit, onClick: () -> Unit, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CollectionRowItem(row: CollectionRow, onClick: () -> Unit) {
    val variant = row.catalogEntry?.let { OnePiece.variantOf(row.printing.sourceId).label() }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name ?: row.printing.sourceId, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(row.baseNumber, row.printing.language.label(), variant)
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        Text("×${row.ownedCopyCount}", style = MaterialTheme.typography.titleLarge)
    }
}
