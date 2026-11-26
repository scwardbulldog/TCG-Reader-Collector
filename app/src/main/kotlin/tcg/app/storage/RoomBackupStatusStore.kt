package tcg.app.storage

import tcg.domain.backup.BackupStatus
import tcg.domain.backup.BackupStatusStore
import java.time.Instant

class RoomBackupStatusStore(private val dao: BackupStatusDao) : BackupStatusStore {
    override suspend fun load(): BackupStatus =
        dao.load()?.let {
            BackupStatus(it.lastBackupAtMillis?.let(Instant::ofEpochMilli), it.backedUpFingerprint, it.restoredSinceBackup)
        } ?: BackupStatus()

    override suspend fun save(status: BackupStatus) =
        dao.save(BackupStatusEntity(0, status.lastBackupAt?.toEpochMilli(), status.backedUpFingerprint, status.restoredSinceBackup))
}
