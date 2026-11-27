package tcg.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import tcg.domain.scanning.BatchRow

/** The Bulk Add review list: rows that need the collector first, resolved rows last, then "Add all". */
@Composable
fun BulkReviewList(viewModel: ScanViewModel, modifier: Modifier = Modifier) {
    val rows = viewModel.bulkRows
    val resolvedCards = rows.filterIsInstance<BatchRow.Resolved>().sumOf { it.count }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = viewModel::hideReview) { Text("Back to snapping") }
            Button(onClick = viewModel::addAll, enabled = resolvedCards > 0) {
                Text("Add all ($resolvedCards)")
            }
        }
        LocationPicker(
            viewModel,
            viewModel.bulkLocation,
            onChoose = viewModel::chooseBulkLocation,
            forBatch = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rows.isEmpty()) Text("Nothing to review yet.", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id.value }) { row ->
                when (row) {
                    is BatchRow.Failed -> FailedRow(viewModel, row)
                    is BatchRow.Ambiguous -> AmbiguousRow(viewModel, row)
                    is BatchRow.Resolved -> ResolvedRow(viewModel, row)
                }
            }
        }
    }
}

@Composable
private fun FailedRow(viewModel: ScanViewModel, row: BatchRow.Failed) {
    var typed by remember(row.id) { mutableStateOf(row.baseNumber.orEmpty()) }
    val thumbnail = viewModel.thumbnail(row.photo)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), "Photo", Modifier.width(72.dp), contentScale = ContentScale.Fit)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val language = viewModel.language.label()
                Text(
                    if (row.baseNumber == null) "Couldn't read a Base Number" else "No $language card matches ${row.baseNumber}",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Base Number or name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.fixRow(row.id, typed) }, enabled = typed.isNotBlank()) { Text("Look up") }
                    TextButton(onClick = { viewModel.removeRow(row.id) }) { Text("Remove") }
                }
                val matches = viewModel.rowMatches[row.id]
                if (matches != null && matches.isEmpty()) Text("No $language card matches your search.", style = MaterialTheme.typography.bodyMedium)
                matches?.forEach { card ->
                    CardMatchRow(card, Modifier.clickable { viewModel.enterBaseNumber(row.id, card.baseNumber) })
                }
            }
        }
    }
}

@Composable
private fun AmbiguousRow(viewModel: ScanViewModel, row: BatchRow.Ambiguous) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "${row.preselected.baseNumber} · ×${row.count} · Choose which Printing you have",
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            row.candidates.forEach { printing ->
                PrintingRow(printing, Modifier.clickable { viewModel.pickPrinting(row.id, printing) })
            }
            TextButton(onClick = { viewModel.removeRow(row.id) }, Modifier.padding(horizontal = 4.dp)) { Text("Remove") }
        }
    }
}

@Composable
private fun ResolvedRow(viewModel: ScanViewModel, row: BatchRow.Resolved) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrintingRow(row.printing, Modifier.weight(1f))
            Text("×${row.count}", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.removeRow(row.id) }) { Text("Remove") }
        }
    }
}
