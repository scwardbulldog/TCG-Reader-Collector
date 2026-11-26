package tcg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tcg.domain.Condition
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.PrintingIdentifier
import tcg.domain.onepiece.OnePiece

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintingDetailScreen(viewModel: PrintingDetailViewModel, printing: PrintingIdentifier, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val entry = state.catalogEntry

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.name ?: printing.sourceId) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val header = listOfNotNull(
                entry?.baseNumber,
                printing.language.label(),
                entry?.rarity,
                entry?.releaseProduct,
                entry?.let { OnePiece.variantOf(printing.sourceId).label() },
            )
            Text(
                header.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.copies.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No Owned Copies of this card.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(state.copies, key = { it.id.value }) { copy ->
                        OwnedCopyEditor(
                            copy = copy,
                            locations = state.locations,
                            onSave = viewModel::save,
                            onDelete = { viewModel.requestDelete(copy.id) },
                            onCreateLocation = viewModel::createLocation,
                        )
                    }
                }
            }
        }
    }

    if (state.pendingDelete != null) DeleteConfirmation(viewModel)
}

@Composable
private fun DeleteConfirmation(viewModel: PrintingDetailViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDelete,
        title = { Text("Delete this Owned Copy?") },
        text = { Text("This removes the copy from your Collection and cannot be undone.") },
        confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
    )
}

@Composable
private fun OwnedCopyEditor(
    copy: OwnedCopy,
    locations: List<Location>,
    onSave: (OwnedCopy) -> Unit,
    onDelete: () -> Unit,
    onCreateLocation: (String, (LocationId) -> Unit) -> Unit,
) {
    var condition by remember(copy) { mutableStateOf(copy.condition) }
    var location by remember(copy) { mutableStateOf(copy.location) }
    var notes by remember(copy) { mutableStateOf(copy.notes) }
    var newLocationDialog by remember { mutableStateOf(false) }
    val edited = copy.copy(condition = condition, location = location, notes = notes)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    label = "Condition",
                    value = condition.label(),
                    options = Condition.entries.map { it.label() to { condition = it } },
                    modifier = Modifier.weight(1f),
                )
                Picker(
                    label = "Location",
                    value = locations.firstOrNull { it.id == location }?.name ?: "None",
                    options = listOf<Pair<String, () -> Unit>>("None" to { location = null }) +
                        locations.map { it.name to { location = it.id } } +
                        ("New location…" to { newLocationDialog = true }),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(edited) }, enabled = edited != copy) { Text("Save") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }

    if (newLocationDialog) {
        NewLocationDialog(
            onDismiss = { newLocationDialog = false },
            onCreate = { name ->
                newLocationDialog = false
                onCreateLocation(name) { location = it }
            },
        )
    }
}

@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, onPick) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onPick()
                })
            }
        }
    }
}

@Composable
private fun NewLocationDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Location") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
