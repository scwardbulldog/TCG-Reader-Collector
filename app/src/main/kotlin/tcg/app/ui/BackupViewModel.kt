package tcg.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tcg.domain.backup.BackupFormatException
import tcg.domain.backup.BackupReminder
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.RestorePlan

class BackupViewModel(private val backup: CollectionBackup) : ViewModel() {
    var reminder by mutableStateOf<BackupReminder?>(null)
        private set

    /** A validated backup waiting for the collector to confirm replacing the Collection. */
    var pendingRestore by mutableStateOf<RestorePlan?>(null)
        private set

    var restoreError by mutableStateOf<String?>(null)
        private set

    fun refreshReminder() {
        viewModelScope.launch { reminder = backup.reminder() }
    }

    /**
     * Captures the Collection and hands the file content to [write]. The backup counts as made only if
     * [write] returns normally; if it throws, nothing is recorded and the exception propagates.
     */
    suspend fun saveBackup(write: suspend (content: String) -> Unit) {
        val prepared = backup.prepare()
        write(prepared.content)
        backup.recordWritten(prepared)
        reminder = backup.reminder()
    }

    /** Validates the picked file in full; the Collection is untouched either way. */
    fun onFileRead(content: String) {
        try {
            pendingRestore = backup.validate(content)
        } catch (e: BackupFormatException) {
            restoreError = e.message ?: "The file is not a valid backup."
        }
    }

    fun onFileUnreadable(message: String) {
        restoreError = message
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun dismissError() {
        restoreError = null
    }

    fun confirmRestore(onRestored: (copies: Int) -> Unit) {
        val plan = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            try {
                backup.restore(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restoreError = "The restore failed: ${e.message ?: "unknown error"}"
                return@launch
            }
            reminder = backup.reminder()
            onRestored(plan.copyCount)
        }
    }
}
