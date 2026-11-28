package tcg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.productText
import tcg.domain.collection.treatmentText
import tcg.domain.isCustom
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintingDetailScreen(
    viewModel: PrintingDetailViewModel,
    printing: PrintingIdentifier,
    locationFilter: LocationId?,
    onOpenCard: (Game, String) -> Unit,
    onMoved: (PrintingIdentifier) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(printing) { viewModel.show(printing, locationFilter) }
    val state = viewModel.state
    val entry = state.details
    var renaming by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<OwnedCopyId?>(null) }
    var fullScreenImage by remember { mutableStateOf(false) }
    // Deleting the last Owned Copy leaves nothing to show. Reads the view model afresh because
    // the previous visit's state can still be in the first frame.
    LaunchedEffect(state.loaded, state.copies.isEmpty()) {
        if (viewModel.state.loaded && viewModel.state.copies.isEmpty()) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = entry?.let { rememberPrintingTitle(it) }
                    if (title != null) {
                        Column { PrintingHeading(title, MaterialTheme.typography.titleLarge) }
                    } else Text(printing.sourceId)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loaded) LoadingBar()
            val header = listOfNotNull(
                entry?.baseNumber,
                printing.language.label(),
                entry?.rarity?.takeIf { it.isNotBlank() },
                entry?.treatmentText,
                entry?.productText,
                entry?.variant?.label(),
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PrintingImage(
                    entry?.imageUrl,
                    Modifier.width(120.dp).let { if (entry?.imageUrl.isNullOrBlank()) it else it.clickable { fullScreenImage = true } },
                )
                Column {
                    Text(header.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    if (state.noLongerInCatalog) NoLongerInCatalogFlag()
                    if (state.unresolved) UnresolvedPrintingFlag()
                    if (entry != null && entry.baseNumber.isNotBlank()) {
                        TextButton(onClick = { onOpenCard(printing.game, entry.baseNumber) }) { Text("All Printings of this Card") }
                    }
                    if (printing.isCustom && entry != null) {
                        TextButton(onClick = { renaming = true }) { Text("Rename") }
                    }
                }
            }
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
                        onChangePrinting = { changing = copy.id },
                        onCreateLocation = viewModel::createLocation,
                    )
                }
                item {
                    OutlinedButton(onClick = viewModel::addAnotherCopy, modifier = Modifier.fillMaxWidth()) { Text("Add another copy") }
                }
            }
        }
    }

    if (fullScreenImage && entry != null) {
        Dialog(onDismissRequest = { fullScreenImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullScreenImage = false }, contentAlignment = Alignment.Center) {
                PrintingImage(entry.imageUrl, Modifier.fillMaxWidth())
            }
        }
    }

    if (renaming && entry != null) {
        CustomPrintingDialog(
            title = "Rename custom Printing",
            language = printing.language.label(),
            baseNumber = null,
            initialLabel = entry.name,
            onDismiss = { renaming = false },
            onSave = { _, label ->
                renaming = false
                viewModel.rename(label)
            },
        )
    }
    changing?.let { copyId ->
        ChangePrintingDialog(
            viewModel,
            current = printing,
            onMove = { target ->
                changing = null
                viewModel.move(copyId, target, onMoved)
            },
            onCreateCustom = { number, label ->
                changing = null
                viewModel.moveToNewCustom(copyId, number, label, onMoved)
            },
            onDismiss = { changing = null },
        )
    }
    if (state.pendingDelete != null) DeleteConfirmation(viewModel, offerCustomDelete = printing.isCustom && state.totalCopies == 1)
}

@Composable
private fun DeleteConfirmation(viewModel: PrintingDetailViewModel, offerCustomDelete: Boolean) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDelete,
        title = { Text("Delete this Owned Copy?") },
        text = {
            Text(
                "This removes the copy from your Collection and cannot be undone." +
                    if (offerCustomDelete) " It is the last copy of this custom Printing, which can be deleted too." else "",
            )
        },
        confirmButton = {
            Row {
                if (offerCustomDelete) {
                    TextButton(onClick = { viewModel.confirmDelete(alsoDeleteCustomPrinting = true) }) { Text("Delete both") }
                }
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
    )
}

/** Edits save as they are made: Condition and Location on pick, notes when the field loses focus or the screen closes. */
@Composable
private fun OwnedCopyEditor(
    copy: OwnedCopy,
    locations: List<Location>,
    onSave: (OwnedCopy) -> Unit,
    onDelete: () -> Unit,
    onChangePrinting: () -> Unit,
    onCreateLocation: (String, (LocationId) -> Unit) -> Unit,
) {
    val latest by rememberUpdatedState(copy)
    var condition by remember(copy.id) { mutableStateOf(copy.condition) }
    var location by remember(copy.id) { mutableStateOf(copy.location) }
    var notes by remember(copy.id) { mutableStateOf(copy.notes) }
    var newLocationDialog by remember { mutableStateOf(false) }

    fun persist() {
        val edited = latest.copy(condition = condition, location = location, notes = notes)
        if (edited != latest) onSave(edited)
    }
    DisposableEffect(copy.id) { onDispose { persist() } }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            copy.addedAt?.let { Text("Added ${dateFormat.format(it)}", style = MaterialTheme.typography.labelMedium) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    label = "Condition",
                    value = condition.label(),
                    options = Condition.entries.map {
                        it.label() to {
                            condition = it
                            persist()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker(
                    label = "Location",
                    value = locations.firstOrNull { it.id == location }?.name ?: "None",
                    options = listOf<Pair<String, () -> Unit>>(
                        "None" to {
                            location = null
                            persist()
                        },
                    ) + locations.map {
                        it.name to {
                            location = it.id
                            persist()
                        }
                    } + ("New location…" to { newLocationDialog = true }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) persist() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChangePrinting) { Text("Change Printing") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }

    if (newLocationDialog) {
        NewLocationDialog(
            onDismiss = { newLocationDialog = false },
            onCreate = { name ->
                newLocationDialog = false
                onCreateLocation(name) {
                    location = it
                    persist()
                }
            },
        )
    }
}

@Composable
internal fun Picker(label: String, value: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Collects the label of a Custom Printing. With a [baseNumber] it also lets the collector correct it; without one it only renames. */
@Composable
internal fun CustomPrintingDialog(
    title: String,
    language: String,
    baseNumber: String?,
    initialLabel: String = "",
    onDismiss: () -> Unit,
    onSave: (baseNumber: String, label: String) -> Unit,
) {
    var number by remember { mutableStateOf(baseNumber.orEmpty()) }
    var label by remember { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (baseNumber != null) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Base Number ($language)") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label, e.g. Milk Box Stamp") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(number.trim(), label.trim()) }, enabled = label.isNotBlank() && (baseNumber == null || number.isNotBlank())) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun NewLocationDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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
