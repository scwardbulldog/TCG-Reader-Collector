package tcg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.onepiece.OnePiece

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSessionScreen(viewModel: ScanViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadLocations() }
    val sessionInput = viewModel.sessionInput
    val confirmation = viewModel.confirmation

    val title = when {
        sessionInput == null -> "New Scan Session"
        else -> "Scanning · One Piece ${sessionInput.session.language.label()} · ${sessionInput.session.location.name}"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (confirmation != null) viewModel.cancelConfirmation() else onClose()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        viewModel.pendingDiscard?.let { pending ->
            AlertDialog(
                onDismissRequest = viewModel::keepBatch,
                title = { Text("Discard ${pending.cards} unsaved ${if (pending.cards == 1) "card" else "cards"}?") },
                text = { Text("Snapped cards are only added once you tap Add all. Leaving throws them away.") },
                confirmButton = { TextButton(onClick = { if (viewModel.confirmDiscard()) onClose() }) { Text("Discard") } },
                dismissButton = { TextButton(onClick = viewModel::keepBatch) { Text("Keep snapping") } },
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                sessionInput == null -> SetupForm(viewModel)
                else -> Box(Modifier.fillMaxSize()) {
                    // The camera stays bound beneath the confirmation, so the cooldown carries over to the next card; the torch goes off meanwhile and comes back with it.
                    InputForm(viewModel)
                    if (confirmation != null) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {}) {
                            ConfirmationForm(viewModel, confirmation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupForm(viewModel: ScanViewModel) {
    val setup = viewModel.setup
    var newLocationDialog by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Game: One Piece", style = MaterialTheme.typography.titleMedium)
        Text("Language", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Language.entries.forEach {
                FilterChip(
                    selected = setup.language == it,
                    onClick = { viewModel.chooseLanguage(it) },
                    label = { Text(it.label()) },
                )
            }
        }
        Picker(
            label = "Location",
            value = setup.location?.name ?: "Choose",
            options = viewModel.locations.map { it.name to { viewModel.chooseLocation(it) } } +
                ("New location…" to { newLocationDialog = true }),
            modifier = Modifier.fillMaxWidth(),
        )
        Picker(
            label = "Default condition",
            value = setup.condition.label(),
            options = Condition.entries.map { it.label() to { viewModel.chooseCondition(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::start,
            enabled = setup.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start") }
    }
    if (newLocationDialog) {
        NewLocationDialog(
            onDismiss = { newLocationDialog = false },
            onCreate = {
                newLocationDialog = false
                viewModel.createLocation(it)
            },
        )
    }
}

@Composable
private fun InputForm(viewModel: ScanViewModel) {
    val result = viewModel.result
    val scope = viewModel.sessionInput?.session?.scope
    val bulkMode = viewModel.mode == ScanMode.Bulk
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanMode.entries.forEach {
                FilterChip(
                    selected = viewModel.mode == it,
                    onClick = { viewModel.selectMode(it) },
                    label = { Text(it.name) },
                )
            }
        }
        viewModel.lastAdded?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (scope != null && (bulkMode || result !is ScanInputResult.SearchResults)) {
            ScanCameraPane(
                scope,
                onRead = viewModel::onRead,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                visible = viewModel.confirmation == null,
                bulk = if (bulkMode) {
                    BulkShutter(
                        newPhoto = viewModel::newPhoto,
                        onSaved = viewModel::onPhotoSaved,
                        onFailed = viewModel::onPhotoFailed,
                        snapped = viewModel.bulkStatus.snapped,
                    )
                } else null,
            )
        }
        if (bulkMode) {
            val status = viewModel.bulkStatus
            Text(
                "${status.snapped} snapped · ${status.read} read · ${status.needsAttention} needs attention",
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Base Number or name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::lookUp, enabled = viewModel.query.isNotBlank()) { Text("Look up Base Number") }
            OutlinedButton(onClick = viewModel::search, enabled = viewModel.query.isNotBlank()) { Text("Search") }
        }
        when (result) {
            ScanInputResult.None -> Unit
            is ScanInputResult.NoMatch -> {
                Text(
                    "No ${viewModel.sessionInput?.session?.language?.label()} card matches ${result.baseNumber}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::searchManually) { Text("Search manually") }
                    TextButton(onClick = viewModel::skip) { Text("Skip") }
                }
            }
            is ScanInputResult.SearchResults -> {
                TextButton(onClick = viewModel::dismissSearch) { Text("Back to camera") }
                if (result.printings.isEmpty()) {
                    Text("No cards match your search.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(result.printings, key = { it.identifier.sourceId }) { printing ->
                            PrintingRow(printing, Modifier.clickable { viewModel.openSearchResult(printing) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationForm(viewModel: ScanViewModel, confirmation: Confirmation) {
    val session = viewModel.sessionInput?.session ?: return
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (confirmation.candidates.size == 1) "Check this is your card, then tap Add."
            else "Choose which Printing you have.",
            style = MaterialTheme.typography.bodyLarge,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(confirmation.candidates, key = { it.identifier.sourceId }) { printing ->
                val selected = printing == confirmation.selected
                Card(
                    onClick = { viewModel.select(printing) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) { PrintingRow(printing) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.setCount(confirmation.count - 1) }) { Text("−") }
            Text("${confirmation.count}", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { viewModel.setCount(confirmation.count + 1) }) { Text("+") }
        }
        Picker(
            label = "Condition",
            value = (confirmation.condition ?: session.defaultCondition).label(),
            options = Condition.entries.map { it.label() to { viewModel.overrideCondition(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::confirm,
            enabled = confirmation.selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (confirmation.count == 1) "Add" else "Add ${confirmation.count} copies") }
    }
}

/** Alternate Arts differ by artwork, so the row shows the Release Product and rarity that tell them apart. */
@Composable
private fun PrintingRow(printing: Printing, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrintingImage(printing.imageUrl, Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            Text(printing.name, style = MaterialTheme.typography.titleMedium)
            val variant = OnePiece.variantOf(printing.identifier.sourceId).label()
            Text(
                listOf(printing.baseNumber, variant, printing.rarity, printing.releaseProduct)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
