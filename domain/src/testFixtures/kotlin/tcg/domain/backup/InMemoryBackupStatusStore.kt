package tcg.domain.backup

class InMemoryBackupStatusStore(var status: BackupStatus = BackupStatus()) : BackupStatusStore {
    override suspend fun load(): BackupStatus = status

    override suspend fun save(status: BackupStatus) {
        this.status = status
    }
}
