package tcg.domain.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import java.time.Instant

/** Why the collector is being reminded to back up. */
enum class BackupReminder {
    /** The Collection changed after the last successful backup write. */
    CollectionChanged,

    /** A restore replaced the Collection and no backup has been saved since. */
    RestoredNotBackedUp,
}

/** A backup ready to be written. Pass it to [CollectionBackup.recordWritten] only after the write succeeds. */
class PreparedBackup internal constructor(
    val content: String,
    internal val fingerprint: String,
)

/** A backup file that passed validation, ready to replace the Collection once the collector confirms. */
class RestorePlan internal constructor(internal val snapshot: CollectionSnapshot, val createdAt: Instant) {
    val copyCount: Int get() = snapshot.copies.size
    val locationCount: Int get() = snapshot.locations.size
}

/** Manual backup and restore of the whole Collection, and the reminders that go with them. */
class CollectionBackup(
    private val collection: CollectionStore,
    private val status: BackupStatusStore,
    private val clock: () -> Instant = Instant::now,
) {
    /** Captures the Collection as backup file content. Nothing is recorded until [recordWritten]. */
    suspend fun prepare(): PreparedBackup {
        val snapshot = collection.snapshot()
        return PreparedBackup(BackupFormat.serialize(snapshot, clock()), BackupFormat.fingerprint(snapshot))
    }

    /** Records [backup] as the latest one. Call only once its file was written successfully. */
    suspend fun recordWritten(backup: PreparedBackup) {
        status.save(BackupStatus(lastBackupAt = clock(), backedUpFingerprint = backup.fingerprint, restoredSinceBackup = false))
    }

    /** Checks the whole file and changes nothing; throws [BackupFormatException] if it is damaged or unsupported. */
    fun validate(content: String): RestorePlan {
        val parsed = BackupFormat.parse(content)
        return RestorePlan(parsed.snapshot, parsed.createdAt)
    }

    /**
     * Replaces the whole Collection with [plan]. If that fails the Collection is as it was and so is the
     * reminder state. The reminder is raised first, so a crash in between only ever reminds too much.
     */
    suspend fun restore(plan: RestorePlan) {
        val before = status.load()
        status.save(before.copy(restoredSinceBackup = true))
        try {
            collection.replaceCollection(plan.snapshot)
        } catch (e: Throwable) {
            withContext(NonCancellable) { status.save(before) }
            throw e
        }
    }

    suspend fun reminder(): BackupReminder? {
        val saved = status.load()
        return when {
            saved.restoredSinceBackup -> BackupReminder.RestoredNotBackedUp
            saved.backedUpFingerprint != null && saved.backedUpFingerprint != BackupFormat.fingerprint(collection.snapshot()) ->
                BackupReminder.CollectionChanged
            else -> null
        }
    }
}
