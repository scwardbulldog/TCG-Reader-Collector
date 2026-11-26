package tcg.domain.backup

import java.time.Instant

/** What the app remembers about backups, so it can tell when to remind the collector. */
data class BackupStatus(
    /** When the last backup was written successfully; null if none was. */
    val lastBackupAt: Instant? = null,
    /** [BackupFormat.fingerprint] of the Collection as that backup captured it. */
    val backedUpFingerprint: String? = null,
    /** A restore replaced the Collection and no backup has been written since. */
    val restoredSinceBackup: Boolean = false,
)

/** Persists the [BackupStatus] beside the Collection. */
interface BackupStatusStore {
    suspend fun load(): BackupStatus

    suspend fun save(status: BackupStatus)
}
