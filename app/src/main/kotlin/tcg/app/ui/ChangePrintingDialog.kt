package tcg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.Card
import tcg.domain.collection.CardViewState
import tcg.domain.collection.productText
import tcg.domain.collection.treatmentText

/**
 * Picks the Printing one Owned Copy moves to. It starts on the copy's own Card in both Languages, a search reaches
 * another Card, and a Custom Printing can be created. Picking a Printing asks for confirmation before anything moves.
 */
@Composable
fun ChangePrintingDialog(
    viewModel: PrintingDetailViewModel,
    current: PrintingIdentifier,
    onMove: (PrintingIdentifier) -> Unit,
    onCreateCustom: (baseNumber: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var card by remember { mutableStateOf<CardViewState?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var picked by remember { mutableStateOf<Printing?>(null) }
    var customDialog by remember { mutableStateOf(false) }

    LaunchedEffect(current) { card = viewModel.ownCard() }
    LaunchedEffect(query) { results = if (query.isBlank()) emptyList() else viewModel.searchCards(query) }

    picked?.let { printing ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Move this copy to ${rememberPrintingTitle(printing).title}?") },
            text = { Text("${printing.identifier.language.label()} · ${printingDetails(printing).ifBlank { printing.baseNumber }}") },
            confirmButton = { TextButton(onClick = { onMove(printing.identifier) }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Cancel") } },
        )
    }
    if (customDialog) {
        CustomPrintingDialog(
            title = "New custom Printing",
            language = current.language.label(),
            baseNumber = card?.baseNumber.orEmpty(),
            onDismiss = { customDialog = false },
            onSave = { number, label, _ ->
                customDialog = false
                onCreateCustom(number, label)
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Printing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search for another Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { customDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create custom Printing") }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (query.isNotBlank()) {
                        items(results, key = { it.baseNumber }) { found ->
                            Text(
                                "${found.baseNumber} · ${found.name(Language.English) ?: found.name(Language.Japanese) ?: ""}",
                                Modifier.fillMaxWidth().clickable {
                                    query = ""
                                    viewModel.showCard(found.baseNumber) { card = it }
                                }.padding(vertical = 8.dp),
                            )
                            HorizontalDivider()
                        }
                    } else {
                        val shown = card
                        if (shown != null) {
                            item { Text("${shown.baseNumber} · ${shown.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium) }
                            shown.languages.forEach { language ->
                                item(key = "language-${language.language}") {
                                    Text(language.language.label(), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                                }
                                language.variants.forEach { group ->
                                    item(key = "variant-${language.language}-${group.variant}") {
                                        Text(group.variant?.label() ?: "Custom", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
                                    }
                                    items(group.entries, key = { it.printing.identifier.toString() }) { entry ->
                                        val isCurrent = entry.printing.identifier == current
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .then(if (isCurrent) Modifier else Modifier.clickable { picked = entry.printing })
                                                .padding(vertical = 8.dp),
                                        ) {
                                            Text(
                                                printingDetails(entry.printing) + if (isCurrent) " (this one)" else "",
                                                Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (entry.owned > 0) Text("×${entry.owned}", style = MaterialTheme.typography.titleMedium)
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun printingDetails(printing: Printing): String =
    listOf(printing.name.takeIf { printing.variant == null }, printing.rarity, printing.treatmentText, printing.productText)
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
