package tcg.domain.backup

import kotlinx.coroutines.test.runTest
import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.collection.InMemoryCollectionStore
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CollectionBackupTest {
    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")

    private var now = Instant.parse("2026-11-26T18:00:00Z")
    private val collection = InMemoryCollectionStore()
    private val status = InMemoryBackupStatusStore()
    private val backup = CollectionBackup(collection, status, clock = { now })

    private suspend fun seed() {
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(zoro, Condition.NearMint, binder.id, "sleeved"))
        collection.add(NewOwnedCopy(zoro, Condition.NearMint, binder.id, "sleeved"))
        collection.add(NewOwnedCopy(nami, Condition.Damaged, null, ""))
    }

    private suspend fun backUp(): String {
        val prepared = backup.prepare()
        backup.recordWritten(prepared)
        return prepared.content
    }

    @Test
    fun `a restored backup reproduces the same Owned Copies and Locations`() = runTest {
        seed()
        val content = backUp()
        val original = collection.snapshot()
        val fresh = InMemoryCollectionStore()

        val restored = CollectionBackup(fresh, InMemoryBackupStatusStore())
        restored.restore(restored.validate(content))

        assertEquals(original, fresh.snapshot())
    }

    @Test
    fun `restore replaces the existing Collection rather than merging into it`() = runTest {
        seed()
        val content = backUp()
        collection.add(NewOwnedCopy(PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP02-001")))
        collection.createLocation("Bulk box A")

        backup.restore(backup.validate(content))

        assertEquals(3, collection.snapshot().copies.size)
        assertEquals(listOf("Binder 1"), collection.locations().map { it.name })
    }

    @Test
    fun `a damaged or unsupported file is rejected without touching the Collection`() = runTest {
        seed()
        val before = collection.snapshot()

        assertFailsWith<BackupFormatException> { backup.validate("{ not a backup") }
        assertFailsWith<BackupFormatException> { backup.validate("""{"format":"tcg-collection-backup","schemaVersion":99}""") }

        assertEquals(before, collection.snapshot())
        assertEquals(BackupStatus(), status.status)
    }

    @Test
    fun `a restore that fails part-way leaves the old Collection and reminder state intact`() = runTest {
        seed()
        val content = backUp()
        collection.add(NewOwnedCopy(nami))
        val before = collection.snapshot()
        val statusBefore = status.status
        val failing = CollectionBackup(FailingReplaceStore(collection), status, clock = { now })

        assertFailsWith<IOException> { failing.restore(failing.validate(content)) }

        assertEquals(before, collection.snapshot())
        assertEquals(statusBefore, status.status)
    }

    @Test
    fun `no reminder until a backup has been written or a restore done`() = runTest {
        seed()

        assertNull(backup.reminder())
    }

    @Test
    fun `a backup is recorded only once its write is reported successful`() = runTest {
        seed()

        val prepared = backup.prepare()

        assertEquals(BackupStatus(), status.status)
        backup.recordWritten(prepared)
        assertEquals(now, status.status.lastBackupAt)
        assertNull(backup.reminder())
    }

    @Test
    fun `changing the Collection after a successful backup brings a reminder`() = runTest {
        seed()
        backUp()

        collection.add(NewOwnedCopy(nami))

        assertEquals(BackupReminder.CollectionChanged, backup.reminder())
    }

    @Test
    fun `editing and deleting after a backup also bring the reminder, and a fresh backup clears it`() = runTest {
        seed()
        backUp()
        val copy = collection.ownedCopies(zoro).first()

        collection.update(copy.copy(notes = "graded"))
        assertEquals(BackupReminder.CollectionChanged, backup.reminder())

        backUp()
        assertNull(backup.reminder())

        collection.delete(copy.id)
        assertEquals(BackupReminder.CollectionChanged, backup.reminder())
    }

    @Test
    fun `a change made while the backup was being written still brings the reminder`() = runTest {
        seed()
        val prepared = backup.prepare()

        collection.add(NewOwnedCopy(nami))
        backup.recordWritten(prepared)

        assertEquals(BackupReminder.CollectionChanged, backup.reminder())
    }

    @Test
    fun `a restore brings a reminder that stays until a fresh backup is saved`() = runTest {
        seed()
        val content = backUp()

        backup.restore(backup.validate(content))

        assertEquals(BackupReminder.RestoredNotBackedUp, backup.reminder())
        backUp()
        assertNull(backup.reminder())
    }

    @Test
    fun `a restore on a fresh install brings the reminder even though no backup was ever written there`() = runTest {
        seed()
        val content = backUp()
        val fresh = CollectionBackup(InMemoryCollectionStore(), InMemoryBackupStatusStore())

        fresh.restore(fresh.validate(content))

        assertEquals(BackupReminder.RestoredNotBackedUp, fresh.reminder())
    }

    @Test
    fun `the preview of a valid file says what a restore would bring`() = runTest {
        seed()
        val content = backUp()

        val plan = backup.validate(content)

        assertEquals(3, plan.copyCount)
        assertEquals(1, plan.locationCount)
        assertEquals(now, plan.createdAt)
    }

    /** A store whose replace always fails, standing in for a failure part-way through a restore. */
    private class FailingReplaceStore(private val inner: CollectionStore) : CollectionStore by inner {
        override suspend fun replaceCollection(snapshot: CollectionSnapshot) {
            throw IOException("disk full")
        }
    }
}
