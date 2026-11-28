package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.backup.BackupStatus
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.InMemoryBackupStatusStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ResearchExportTest {
    private val milk = PrintingIdentifier(Game.OnePiece, Language.English, "custom:milk")
    private val stamp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "custom:stamp")
    private val collection = InMemoryCollectionStore()
    private val photos = InMemoryCardPhotoStore()
    private val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 0xFF.toByte(), 0xD9.toByte())

    private suspend fun export(): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        ResearchExport(collection, photos).writeTo(out)
        return buildMap {
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
                while (true) put((zip.nextEntry ?: break).name, zip.readBytes())
            }
        }
    }

    private suspend fun addCustom(identifier: PrintingIdentifier, baseNumber: String, label: String, withPhoto: Boolean) {
        collection.saveCustomPrinting(
            Printing(identifier, baseNumber, "Custom", "", label, "", null, cardPhoto = if (withPhoto) photos.put(identifier, photo) else null),
        )
    }

    @Test
    fun `lists every Custom Printing, with a blank photo filename when it has none, and carries the photos`() = runTest {
        addCustom(milk, "OP09-118", "Milk, \"Box\" Stamp", withPhoto = true)
        addCustom(stamp, "OP01-001", "Event Stamp", withPhoto = false)

        val written = export()

        assertEquals(
            "Base Number,Language,Label,Photo filename\r\n" +
                "OP01-001,JP,Event Stamp,\r\n" +
                "OP09-118,EN,\"Milk, \"\"Box\"\" Stamp\",onepiece_en_milk.jpg\r\n",
            written.getValue("custom-printings.csv").decodeToString(),
        )
        assertEquals(setOf("custom-printings.csv", "photos/onepiece_en_milk.jpg"), written.keys)
        assertContentEquals(photo, written.getValue("photos/onepiece_en_milk.jpg"))
    }

    @Test
    fun `carries no Owned Copies and leaves the backup status alone`() = runTest {
        addCustom(milk, "OP09-118", "Milk Box Stamp", withPhoto = true)
        collection.add(NewOwnedCopy(milk, Condition.Damaged, null, "private note"))
        val status = InMemoryBackupStatusStore()
        val backup = CollectionBackup(collection, status, photos)

        val written = export()

        assertEquals(false, written.getValue("custom-printings.csv").decodeToString().contains("private note"))
        assertEquals(BackupStatus(), status.status)
        assertEquals(null, backup.reminder())
    }

    @Test
    fun `an empty Collection exports just the header`() = runTest {
        val written = export()

        assertEquals("Base Number,Language,Label,Photo filename\r\n", written.getValue("custom-printings.csv").decodeToString())
        assertEquals(1, written.size)
    }
}
