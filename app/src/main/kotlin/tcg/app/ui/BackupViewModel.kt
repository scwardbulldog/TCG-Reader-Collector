package tcg.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tcg.domain.backup.BackupFormatException
import tcg.domain.backup.BackupReminder
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.PreparedBackup
import tcg.domain.backup.RestoreCheck
import tcg.domain.backup.RestorePlan
import java.io.IOException
import java.io.InputStream

class BackupViewModel(private val backup: CollectionBackup) : ViewModel() {
    var reminder by mutableStateOf<BackupReminder?>(null)
        private set

    /** A validated backup waiting for the collector to confirm replacing the Collection. */
    var pendingRestore by mutableStateOf<RestorePlan?>(null)
        private set

    /** A backup whose listed Card photos are missing or damaged, waiting for the collector to choose to go on without them. */
    var photoProblems by mutableStateOf<RestoreCheck.PhotoProblems?>(null)
        private set

    var restoreError by mutableStateOf<String?>(null)
        private set

    fun refreshReminder() {
        viewModelScope.launch { reminder = backup.reminder() }
    }

    /**
     * Captures the Collection and hands it to [write] as a zip. The backup counts as made only if
     * [write] returns normally; if it throws, nothing is recorded and the exception propagates.
     */
    suspend fun saveBackup(write: suspend (PreparedBackup) -> Unit) {
        val prepared = backup.prepare()
        write(prepared)
        backup.recordWritten(prepared)
        reminder = backup.reminder()
    }

    /** Validates the picked file in full, reading it from [open]; the Collection is untouched either way. */
    suspend fun onFilePicked(open: () -> InputStream) {
        try {
            when (val check = withContext(Dispatchers.IO) { open().use(backup::validate) }) {
                is RestoreCheck.Ready -> pendingRestore = check.plan
                is RestoreCheck.PhotoProblems -> photoProblems = check
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFormatException) {
            restoreError = e.message ?: "The file is not a valid backup."
        } catch (e: IOException) {
            restoreError = e.message ?: "The file could not be read."
        } catch (e: Exception) {
            restoreError = "The file could not be opened."
        }
    }

    fun cancelRestore() {
        pendingRestore = null
        photoProblems = null
    }

    /** The collector chose to restore everything but the Card photos that are missing or damaged. */
    fun restoreWithoutThosePhotos() {
        pendingRestore = photoProblems?.withoutThosePhotos
        photoProblems = null
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
