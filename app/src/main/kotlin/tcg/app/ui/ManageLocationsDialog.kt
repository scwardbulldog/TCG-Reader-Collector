package tcg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import tcg.domain.collection.LocationUse

/** Lists the Locations with their copy counts; each can be renamed or deleted, deleting one in use only after asking. */
@Composable
fun ManageLocationsDialog(
    locations: List<LocationUse>,
    onRename: (LocationUse, String) -> Unit,
    onDelete: (LocationUse) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<LocationUse?>(null) }
    var deleting by remember { mutableStateOf<LocationUse?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Locations") },
        text = {
            if (locations.isEmpty()) {
                Text("No Locations yet. Create one when you edit an Owned Copy.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    locations.forEach { use ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(use.location.name)
                                Text("${use.copies} ${if (use.copies == 1) "copy" else "copies"}")
                            }
                            TextButton(onClick = { renaming = use }) { Text("Rename") }
                            TextButton(onClick = { if (use.copies == 0) onDelete(use) else deleting = use }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    renaming?.let { use ->
        var name by remember(use) { mutableStateOf(use.location.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename Location") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(use, name)
                        renaming = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { use ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${use.location.name}?") },
            text = {
                Text("${use.copies} ${if (use.copies == 1) "copy is" else "copies are"} in it. They stay in your Collection with Location None.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(use)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
