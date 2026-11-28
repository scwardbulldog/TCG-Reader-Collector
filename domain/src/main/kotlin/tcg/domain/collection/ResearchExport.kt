package tcg.domain.collection

import tcg.domain.CardPhotoStore
import tcg.domain.CollectionStore
import tcg.domain.Printing
import tcg.domain.backup.BackupFormat
import tcg.domain.collection.CollectionCsv.code
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A manual export of every Custom Printing with its Card photo, for researching what the Catalog lacks. A zip of
 * `custom-printings.csv` (Base Number, Language, Label, Photo filename; the filename is blank when there is no photo)
 * and the photo JPEGs under `photos/`. It carries no Owned Copies, Locations or notes, cannot be restored, and
 * is not a backup: it never touches the backup status.
 */
class ResearchExport(private val collection: CollectionStore, private val photos: CardPhotoStore) {
    /** Writes the zip to [out] without closing it. Throws if a photo cannot be read. */
    suspend fun writeTo(out: OutputStream) {
        val printings = collection.snapshot().customPrintings
            .sortedWith(compareBy<Printing>({ it.baseNumber }, { it.identifier.language }, { it.name }, { it.identifier.sourceId }))
        val withPhoto = printings.filter { it.cardPhoto?.let(photos::resolve) != null }.toSet()
        val rows = listOf(listOf("Base Number", "Language", "Label", "Photo filename")) + printings.map {
            listOf(it.baseNumber, it.identifier.language.code(), it.name, if (it in withPhoto) BackupFormat.photoFileName(it.identifier) else "")
        }

        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry("custom-printings.csv"))
        zip.write(rows.joinToString("") { cells -> cells.joinToString(",", transform = CollectionCsv::escape) + "\r\n" }.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (printing in printings.filter { it in withPhoto }) {
            val photo = photos.read(printing.identifier) ?: throw IOException("The Card photo of ${printing.name} is missing")
            zip.putNextEntry(ZipEntry(BackupFormat.PHOTO_FOLDER + BackupFormat.photoFileName(printing.identifier)))
            zip.write(photo)
            zip.closeEntry()
        }
        zip.finish()
    }
}
