package tcg.domain.backup

import kotlinx.coroutines.test.runTest
import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.InMemoryCollectionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionBackupTest {
    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
    private val milk = PrintingIdentifier(Game.OnePiece, Language.English, "custom:milk")
    private val stamp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "custom:stamp")

    private var now = Instant.parse("2026-11-26T18:00:00Z")
    private val collection = InMemoryCollectionStore()
    private val status = InMemoryBackupStatusStore()
    private val photos = InMemoryCardPhotoStore()
    private val backup = CollectionBackup(collection, status, photos, clock = { now })

    private fun jpeg(vararg body: Int) = (intArrayOf(0xFF, 0xD8, 0xFF, 0xE0) + body + intArrayOf(0xFF, 0xD9)).map { it.toByte() }.toByteArray()

    private suspend fun seed() {
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(zoro, Condition.NearMint, binder.id, "sleeved"))
        collection.add(NewOwnedCopy(zoro, Condition.NearMint, binder.id, "sleeved"))
        collection.add(NewOwnedCopy(nami, Condition.Damaged, null, ""))
    }

    private suspend fun addCustom(identifier: PrintingIdentifier, label: String, photo: ByteArray? = null, store: InMemoryCardPhotoStore = photos) {
        collection.saveCustomPrinting(
            Printing(identifier, "OP01-001", "Custom", "", label, "", null, cardPhoto = photo?.let { store.put(identifier, it) }),
        )
    }

    private suspend fun backUp(): ByteArray {
        val prepared = backup.prepare()
        val out = ByteArrayOutputStream()
        prepared.writeTo(out)
        backup.recordWritten(prepared)
        return out.toByteArray()
    }

    private fun CollectionBackup.check(file: ByteArray) = validate(ByteArrayInputStream(file))

    private fun CollectionBackup.plan(file: ByteArray) = (check(file) as RestoreCheck.Ready).plan

    private fun entries(zip: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) put((input.nextEntry ?: break).name, input.readBytes())
        }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun fresh(): Triple<CollectionBackup, InMemoryCollectionStore, InMemoryCardPhotoStore> {
        val store = InMemoryCollectionStore()
        val freshPhotos = InMemoryCardPhotoStore()
        return Triple(CollectionBackup(store, InMemoryBackupStatusStore(), freshPhotos), store, freshPhotos)
    }

    @Test
    fun `a restored backup reproduces the same Owned Copies and Locations`() = runTest {
        seed()
        val file = backUp()
        val original = collection.snapshot()

        val (restored, store) = fresh()
        restored.restore(restored.plan(file))

        assertEquals(original, store.snapshot())
    }

    @Test
    fun `a backup is always a zip, even when no Custom Printing has a photo`() = runTest {
        seed()

        val written = entries(backUp())

        assertEquals(setOf("collection.json"), written.keys)
    }

    @Test
    fun `a backup holds a photo for every Custom Printing that has one`() = runTest {
        addCustom(milk, "Milk Box Stamp", jpeg(1, 2, 3))
        addCustom(stamp, "Event Stamp")

        val written = entries(backUp())

        assertEquals(2, written.size)
        assertContentEquals(jpeg(1, 2, 3), written.getValue("photos/onepiece_en_milk.jpg"))
    }

    @Test
    fun `a v2 zip round-trips Custom Printings and their photos onto a clean phone`() = runTest {
        seed()
        addCustom(milk, "Milk Box Stamp", jpeg(1, 2, 3))
        addCustom(stamp, "Event Stamp")
        val file = backUp()
        val original = collection.snapshot()

        val (restored, store, freshPhotos) = fresh()
        restored.restore(restored.plan(file))

        val result = store.snapshot()
        assertEquals(original.copies, result.copies)
        assertEquals(original.locations, result.locations)
        assertEquals(original.customPrintings.map { it.copy(cardPhoto = null) }.toSet(), result.customPrintings.map { it.copy(cardPhoto = null) }.toSet())
        assertContentEquals(jpeg(1, 2, 3), freshPhotos.read(milk))
        assertNotNull(freshPhotos.resolve(assertNotNull(store.customPrinting(milk)?.cardPhoto)))
        assertNull(store.customPrinting(stamp)?.cardPhoto)
        assertEquals(setOf(milk), freshPhotos.printings)
    }

    @Test
    fun `restore replaces the existing Collection rather than merging into it`() = runTest {
        seed()
        val file = backUp()
        collection.add(NewOwnedCopy(PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP02-001")))
        collection.createLocation("Bulk box A")

        backup.restore(backup.plan(file))

        assertEquals(3, collection.snapshot().copies.size)
        assertEquals(listOf("Binder 1"), collection.locations().map { it.name })
    }

    @Test
    fun `a damaged or unsupported file is rejected without touching the Collection`() = runTest {
        seed()
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val before = collection.snapshot()
        val json = "{ not a backup".toByteArray()
        val future = """{"format":"tcg-collection-backup","schemaVersion":99}""".toByteArray()

        assertFailsWith<BackupFormatException> { backup.check(json) }
        assertFailsWith<BackupFormatException> { backup.check(future) }
        assertFailsWith<BackupFormatException> { backup.check(zip(mapOf("collection.json" to json))) }
        assertFailsWith<BackupFormatException> { backup.check(zip(mapOf("collection.json" to future))) }
        assertFailsWith<BackupFormatException> { backup.check(zip(mapOf("notes.txt" to json))) }
        val good = backUp()
        assertFailsWith<BackupFormatException> { backup.check(good.copyOf(40)) }

        assertEquals(before, collection.snapshot())
        assertEquals(setOf(milk), photos.printings)
    }

    @Test
    fun `a photo listed but missing fails by default, and can be restored without it`() = runTest {
        seed()
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        addCustom(stamp, "Event Stamp", jpeg(2))
        val written = entries(backUp()) - "photos/onepiece_en_milk.jpg"

        val (restored, store, freshPhotos) = fresh()
        val check = restored.check(zip(written))

        val problems = check as RestoreCheck.PhotoProblems
        assertEquals(1, problems.affectedPhotos)
        assertEquals(0, store.snapshot().copies.size)
        restored.restore(problems.withoutThosePhotos)
        assertEquals(3, store.snapshot().copies.size)
        assertNull(store.customPrinting(milk)?.cardPhoto)
        assertNotNull(store.customPrinting(stamp)?.cardPhoto)
        assertEquals(setOf(stamp), freshPhotos.printings)
    }

    @Test
    fun `a photo that is not a valid JPEG counts as a problem`() = runTest {
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        addCustom(stamp, "Event Stamp", jpeg(2))
        val written = entries(backUp()).toMutableMap()
        written["photos/onepiece_en_milk.jpg"] = "not a picture".toByteArray()
        written["photos/onepiece_jp_stamp.jpg"] = jpeg(2).copyOf(5)

        val check = backup.check(zip(written))

        assertEquals(2, (check as RestoreCheck.PhotoProblems).affectedPhotos)
    }

    @Test
    fun `photos in the zip that no Custom Printing lists are ignored`() = runTest {
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val written = entries(backUp()) + ("photos/stray.jpg" to jpeg(9)) + ("readme.txt" to "hi".toByteArray())

        val (restored, _, freshPhotos) = fresh()
        restored.restore(restored.plan(zip(written)))

        assertEquals(setOf(milk), freshPhotos.printings)
    }

    @Test
    fun `after a zip restore the photo folder holds exactly the backup's photos`() = runTest {
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val file = backUp()
        photos.put(stamp, jpeg(7))
        photos.put(nami, jpeg(8))

        backup.restore(backup.plan(file))

        assertEquals(setOf(milk), photos.printings)
        assertContentEquals(jpeg(1), photos.read(milk))
    }

    private val version1 = """{"format":"tcg-collection-backup","schemaVersion":1,"createdAt":"2026-11-26T18:00:00Z","locations":[],""" +
        """"ownedCopies":[],"customPrintings":[{"game":"onepiece","language":"en","sourceId":"custom:milk","baseNumber":"OP01-001","label":"Milk Box Stamp"}]}"""

    @Test
    fun `a v1 JSON backup restores, keeping photos of Custom Printings it contains and deleting the rest`() = runTest {
        photos.put(milk, jpeg(1))
        photos.put(stamp, jpeg(2))

        backup.restore(backup.plan(version1.toByteArray()))

        assertEquals(setOf(milk), photos.printings)
        assertContentEquals(jpeg(1), photos.read(milk))
        assertNotNull(photos.resolve(assertNotNull(collection.customPrinting(milk)?.cardPhoto)))
    }

    @Test
    fun `a v1 JSON backup restores a Custom Printing without a photo when the phone has none`() = runTest {
        backup.restore(backup.plan(version1.toByteArray()))

        assertNull(collection.customPrinting(milk)?.cardPhoto)
        assertEquals(0, photos.count)
    }

    @Test
    fun `a restore that fails part-way leaves the old Collection, photos and reminder state intact`() = runTest {
        seed()
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val file = backUp()
        collection.add(NewOwnedCopy(nami))
        photos.put(stamp, jpeg(5))
        val before = collection.snapshot()
        val statusBefore = status.status
        val failing = CollectionBackup(FailingReplaceStore(collection), status, photos, clock = { now })

        assertFailsWith<IOException> { failing.restore(failing.plan(file)) }

        assertEquals(before, collection.snapshot())
        assertEquals(statusBefore, status.status)
        assertEquals(setOf(milk, stamp), photos.printings)
        assertContentEquals(jpeg(1), photos.read(milk))
    }

    @Test
    fun `photos that cannot be staged leave the Collection and the reminder state as they were`() = runTest {
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val file = backUp()
        collection.add(NewOwnedCopy(nami))
        val before = collection.snapshot()
        val statusBefore = status.status
        photos.failingToStage = true

        assertFailsWith<IOException> { backup.restore(backup.plan(file)) }

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
    fun `adding, replacing and removing a Card photo each bring the reminder`() = runTest {
        addCustom(milk, "Milk Box Stamp")
        backUp()

        addCustom(milk, "Milk Box Stamp", jpeg(1))
        assertEquals(BackupReminder.CollectionChanged, backup.reminder())

        backUp()
        assertNull(backup.reminder())
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        assertEquals(BackupReminder.CollectionChanged, backup.reminder())

        backUp()
        photos.delete(milk)
        collection.saveCustomPrinting(collection.customPrinting(milk)!!.copy(cardPhoto = null))
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
        val file = backUp()

        backup.restore(backup.plan(file))

        assertEquals(BackupReminder.RestoredNotBackedUp, backup.reminder())
        backUp()
        assertNull(backup.reminder())
    }

    @Test
    fun `a restore on a fresh install brings the reminder even though no backup was ever written there`() = runTest {
        seed()
        val file = backUp()
        val (fresh) = fresh()

        fresh.restore(fresh.plan(file))

        assertEquals(BackupReminder.RestoredNotBackedUp, fresh.reminder())
    }

    @Test
    fun `the preview of a valid file says what a restore would bring`() = runTest {
        seed()
        addCustom(milk, "Milk Box Stamp", jpeg(1))
        val file = backUp()

        val plan = backup.plan(file)

        assertEquals(3, plan.copyCount)
        assertEquals(1, plan.locationCount)
        assertEquals(1, plan.photoCount)
        assertEquals(now, plan.createdAt)
        assertTrue(!plan.keepsExistingPhotos)
    }

    /** A store whose replace always fails, standing in for a failure part-way through a restore. */
    private class FailingReplaceStore(private val inner: CollectionStore) : CollectionStore by inner {
        override suspend fun replaceCollection(snapshot: CollectionSnapshot) {
            throw IOException("disk full")
        }
    }
}
