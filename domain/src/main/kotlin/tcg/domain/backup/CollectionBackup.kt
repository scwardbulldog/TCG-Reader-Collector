package tcg.domain.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tcg.domain.CardPhotoStore
import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.PrintingIdentifier
import tcg.domain.StagedPhotos
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Why the collector is being reminded to back up. */
enum class BackupReminder {
    /** The Collection changed after the last successful backup write. */
    CollectionChanged,

    /** A restore replaced the Collection and no backup has been saved since. */
    RestoredNotBackedUp,
}

/**
 * A backup ready to be written as a zip. Pass it to [CollectionBackup.recordWritten] only after the write succeeds.
 * The Card photos are read from the store as they are written, one at a time.
 */
class PreparedBackup internal constructor(
    private val snapshot: CollectionSnapshot,
    private val createdAt: Instant,
    private val photos: CardPhotoStore,
    internal val fingerprint: String,
) {
    /** Writes the zip to [out] without closing it. Throws if a listed photo cannot be read. */
    suspend fun writeTo(out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(BackupArchive.COLLECTION_ENTRY))
        zip.write(BackupFormat.serialize(snapshot, createdAt).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (printing in snapshot.customPrintings.filter { it.cardPhoto != null }) {
            val photo = photos.read(printing.identifier) ?: throw IOException("The Card photo of ${printing.name} is missing")
            zip.putNextEntry(ZipEntry(BackupFormat.PHOTO_FOLDER + BackupFormat.photoFileName(printing.identifier)))
            zip.write(photo)
            zip.closeEntry()
        }
        zip.finish()
    }
}

/**
 * What reading a backup file found. A problem in the collection JSON is not here: [CollectionBackup.validate] throws
 * [BackupFormatException] for it and there is no way past it.
 */
sealed interface RestoreCheck {
    /** Everything checked out. */
    class Ready(val plan: RestorePlan) : RestoreCheck

    /**
     * [affectedPhotos] listed Card photos are missing from the zip or not valid JPEGs. By default that fails the restore;
     * [withoutThosePhotos] restores everything else, leaving those Custom Printings without a photo.
     */
    class PhotoProblems(val affectedPhotos: Int, val withoutThosePhotos: RestorePlan) : RestoreCheck
}

/** A backup file that passed validation, ready to replace the Collection once the collector confirms. */
class RestorePlan internal constructor(
    internal val snapshot: CollectionSnapshot,
    val createdAt: Instant,
    internal val photos: Map<PrintingIdentifier, ByteArray>,
    /** A version 1 file carries no photos, so the photos already here for its Custom Printings stay. */
    val keepsExistingPhotos: Boolean,
) {
    val copyCount: Int get() = snapshot.copies.size
    val locationCount: Int get() = snapshot.locations.size
    val photoCount: Int get() = photos.size
}

/** Manual backup and restore of the whole Collection, and the reminders that go with them. */
class CollectionBackup(
    private val collection: CollectionStore,
    private val status: BackupStatusStore,
    private val photos: CardPhotoStore,
    private val clock: () -> Instant = Instant::now,
) {
    /** Captures the Collection as a backup zip. Nothing is recorded until [recordWritten]. */
    suspend fun prepare(): PreparedBackup {
        val snapshot = currentSnapshot()
        return PreparedBackup(snapshot, clock(), photos, BackupFormat.fingerprint(snapshot))
    }

    /** The Collection, where a Custom Printing whose photo file is gone counts as having no photo. */
    private suspend fun currentSnapshot(): CollectionSnapshot {
        val snapshot = collection.snapshot()
        return snapshot.copy(
            customPrintings = snapshot.customPrintings.map {
                if (it.cardPhoto != null && photos.resolve(it.cardPhoto) == null) it.copy(cardPhoto = null) else it
            },
        )
    }

    /** Records [backup] as the latest one. Call only once its file was written successfully. */
    suspend fun recordWritten(backup: PreparedBackup) {
        status.save(BackupStatus(lastBackupAt = clock(), backedUpFingerprint = backup.fingerprint, restoredSinceBackup = false))
    }

    /**
     * Checks the whole file, a zip or an old plain JSON backup, and changes nothing. Throws [BackupFormatException]
     * if it is damaged or unsupported; a photo problem comes back as [RestoreCheck.PhotoProblems] instead.
     */
    fun validate(source: InputStream): RestoreCheck {
        val input = BufferedInputStream(source)
        input.mark(4)
        val head = input.readNBytes(4)
        input.reset()
        return if (BackupArchive.isZip(head)) validateZip(input) else validateJson(input)
    }

    private fun validateJson(input: InputStream): RestoreCheck {
        val parsed = BackupFormat.parse(BackupArchive.readText(input))
        if (parsed.version != 1) throw BackupFormatException("The backup is damaged: this version of backup is a zip")
        return RestoreCheck.Ready(RestorePlan(parsed.snapshot, parsed.createdAt, emptyMap(), keepsExistingPhotos = true))
    }

    private fun validateZip(input: InputStream): RestoreCheck {
        val entries = BackupArchive.read(input)
        val json = entries[BackupArchive.COLLECTION_ENTRY] ?: throw BackupFormatException("The file is not a Collection backup")
        val parsed = BackupFormat.parse(BackupArchive.decodeText(json))
        if (parsed.version != 2) throw BackupFormatException("Unsupported backup version ${parsed.version}")

        val restorable = mutableMapOf<PrintingIdentifier, ByteArray>()
        var affected = 0
        for ((printing, name) in parsed.photoNames) {
            val photo = entries[name]
            if (photo != null && isJpeg(photo)) restorable[printing] = photo else affected++
        }
        val plan = RestorePlan(parsed.snapshot, parsed.createdAt, restorable, keepsExistingPhotos = false)
        return if (affected == 0) RestoreCheck.Ready(plan) else RestoreCheck.PhotoProblems(affected, plan)
    }

    /** A JPEG starts with the start-of-image marker and ends with the end-of-image marker; a cut-short file lacks the end. */
    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() &&
            bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()

    /**
     * Replaces the whole Collection with [plan], and the Card photos with its photos (or, for an old backup that has
     * none, keeps the photos of the Custom Printings it contains and deletes the rest). If that fails the Collection
     * and the photos are as they were, and so is the reminder state. The reminder is raised first, so a crash in
     * between only ever reminds too much. The photos are staged first and swapped in only after the Collection is replaced.
     */
    suspend fun restore(plan: RestorePlan) {
        val before = status.load()
        status.save(before.copy(restoredSinceBackup = true))
        var staged: StagedPhotos? = null
        try {
            val keep = if (plan.keepsExistingPhotos) plan.snapshot.customPrintings.map { it.identifier }.toSet() else emptySet()
            staged = photos.stage(plan.photos, keep)
            val references = staged.references
            collection.replaceCollection(
                plan.snapshot.copy(customPrintings = plan.snapshot.customPrintings.map { it.copy(cardPhoto = references[it.identifier]) }),
            )
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                staged?.discard()
                status.save(before)
            }
            throw e
        }
        val toCommit = checkNotNull(staged)
        try {
            toCommit.commit()
        } catch (e: Throwable) {
            withContext(NonCancellable) { toCommit.discard() }
            throw e
        }
    }

    suspend fun reminder(): BackupReminder? {
        val saved = status.load()
        return when {
            saved.restoredSinceBackup -> BackupReminder.RestoredNotBackedUp
            saved.backedUpFingerprint != null && saved.backedUpFingerprint != BackupFormat.fingerprint(currentSnapshot()) ->
                BackupReminder.CollectionChanged
            else -> null
        }
    }
}
