package tcg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tcg.domain.backup.BackupReminder

@Composable
fun BackupReminderBanner(reminder: BackupReminder, onBackUp: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (reminder) {
                    BackupReminder.CollectionChanged -> "Your Collection has changed since your last backup."
                    BackupReminder.RestoredNotBackedUp -> "You restored a backup. Save a fresh backup to protect this Collection."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBackUp) { Text("Back up") }
        }
    }
}

/** The restore confirmation and the error shown for a file that cannot be restored. */
@Composable
fun BackupDialogs(viewModel: BackupViewModel, onRestored: (copies: Int) -> Unit) {
    viewModel.pendingRestore?.let { plan ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace your Collection?") },
            text = {
                Text(
                    "This backup, saved ${plan.createdAt}, holds ${plan.copyCount} Owned Copies and ${plan.locationCount} Locations. " +
                        "Restoring replaces your whole current Collection with it, and the current one cannot be recovered.\n\n" +
                        if (plan.keepsExistingPhotos) {
                            "This older backup has no Card photos: photos of Custom Printings it contains stay, and all other Card photos on this phone are deleted."
                        } else {
                            "It holds ${plan.photoCount} Card ${if (plan.photoCount == 1) "photo" else "photos"}. " +
                                "Restoring replaces all Card photos on this phone with them."
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(onRestored) }) { Text("Replace Collection") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
    viewModel.photoProblems?.let { problems ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Some Card photos can't be restored") },
            text = {
                Text(
                    "${problems.affectedPhotos} Card ${if (problems.affectedPhotos == 1) "photo" else "photos"} in this backup " +
                        "${if (problems.affectedPhotos == 1) "is" else "are"} missing or damaged.\n\n" +
                        "You can restore everything else; the Custom Printings affected will have no photo. Your Collection was not changed.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::restoreWithoutThosePhotos) { Text("Restore without those photos") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
    viewModel.restoreError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Can't restore") },
            text = { Text("$message\n\nYour Collection was not changed.") },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}
