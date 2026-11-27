package tcg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tcg.domain.Language
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.collection.CollectionRow
import tcg.domain.collection.CustomPrintings
import tcg.domain.isCustom

/** What the Collection list can start. */
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
    backup: BackupViewModel,
    actions: CollectionActions,
    updatingCatalog: Boolean,
    catalogRevision: Int,
    snackbarHostState: SnackbarHostState,
    onOpen: (PrintingIdentifier) -> Unit,
) {
    LaunchedEffect(catalogRevision) {
        viewModel.refresh()
        backup.refreshReminder()
    }
    val scope = rememberCoroutineScope()
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
            backup.reminder?.let { BackupReminderBanner(it, onBackUp = actions.onBackup) }
            OutlinedTextField(
                value = viewModel.search,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Search Base Number or name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterBar(viewModel)
            when {
                rows == null -> Unit
                rows.isEmpty() -> EmptyState(
                    if (viewModel.search.isBlank() && !viewModel.filter.isActive) {
                        "Your Collection is empty.\nTap + to add your first cards."
                    } else "Nothing in your Collection matches your search and filters.",
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

    BackupDialogs(backup, onRestored = { copies ->
        viewModel.refresh()
        scope.launch { snackbarHostState.showSnackbar("Restored $copies Owned Copies") }
    })
}

@Composable
private fun FilterBar(viewModel: CollectionViewModel) {
    val filter = viewModel.filter
    val any = "Any"
    val width = Modifier.width(200.dp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Picker(
            label = "Release Product",
            value = filter.releaseProduct ?: any,
            options = listOf<Pair<String, () -> Unit>>(any to { viewModel.onFilterChanged(filter.copy(releaseProduct = null)) }) +
                viewModel.releaseProducts.map { it to { viewModel.onFilterChanged(filter.copy(releaseProduct = it)) } },
            modifier = width,
        )
        Picker(
            label = "Language",
            value = filter.language?.label() ?: any,
            options = listOf<Pair<String, () -> Unit>>(any to { viewModel.onFilterChanged(filter.copy(language = null)) }) +
                Language.entries.map { it.label() to { viewModel.onFilterChanged(filter.copy(language = it)) } },
            modifier = width,
        )
        Picker(
            label = "Variant",
            value = filter.variant?.label() ?: any,
            options = listOf<Pair<String, () -> Unit>>(any to { viewModel.onFilterChanged(filter.copy(variant = null)) }) +
                Variant.entries.map { it.label() to { viewModel.onFilterChanged(filter.copy(variant = it)) } },
            modifier = width,
        )
        Picker(
            label = "Location",
            value = viewModel.locations.firstOrNull { it.id == filter.location }?.name ?: any,
            options = listOf<Pair<String, () -> Unit>>(any to { viewModel.onFilterChanged(filter.copy(location = null)) }) +
                viewModel.locations.map { it.name to { viewModel.onFilterChanged(filter.copy(location = it.id)) } },
            modifier = width,
        )
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
    val variant = row.details?.let { if (row.printing.isCustom) CustomPrintings.RELEASE_PRODUCT else it.variant?.label() }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrintingImage(row.details?.imageUrl, Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            val title = row.details?.let { rememberPrintingTitle(it) }
            if (title != null) PrintingHeading(title) else Text(row.printing.sourceId, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(row.baseNumber, row.printing.language.label(), variant)
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (row.noLongerInCatalog) NoLongerInCatalogFlag()
            if (row.unresolved) UnresolvedPrintingFlag()
        }
        Text("×${row.ownedCopyCount}", style = MaterialTheme.typography.titleLarge)
    }
}
