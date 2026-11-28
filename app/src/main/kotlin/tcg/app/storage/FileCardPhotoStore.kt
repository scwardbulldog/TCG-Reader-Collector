package tcg.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tcg.domain.CardPhotoStore
import tcg.domain.PrintingIdentifier
import tcg.domain.StagedPhotos
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Card photos as JPEG files in the app's private files folder (not the cache, which the system may clear),
 * one per Custom Printing, named after its identifier. Each file is scaled to at most [MAX_EDGE] px on its long edge.
 *
 * A reference is `<file name>@<time saved>`: the file name never changes for a Printing, the time does, so a
 * screen that remembers the reference reloads the picture after a retake.
 */
class FileCardPhotoStore(context: Context) : CardPhotoStore {
    private val folder = File(context.filesDir, FOLDER).apply { mkdirs() }
    private val contentResolver = context.contentResolver

    override suspend fun save(printing: PrintingIdentifier, captured: String): String = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val name = fileName(printing)
        val temp = File(folder, "$name.tmp")
        try {
            val bitmap = decode(Uri.parse(captured))
            try {
                temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)) { "Could not write the photo" } }
            } finally {
                bitmap.recycle()
            }
            Files.move(temp.toPath(), File(folder, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
        discardCapture(captured)
        "$name@${System.currentTimeMillis()}"
    }

    override fun resolve(reference: String): String? {
        val file = File(folder, reference.substringBefore('@'))
        return if (file.parentFile == folder && file.isFile) file.path else null
    }

    override suspend fun delete(printing: PrintingIdentifier) {
        withContext(Dispatchers.IO) { File(folder, fileName(printing)).delete() }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { folder.listFiles()?.forEach { it.delete() } }
    }

    override suspend fun read(printing: PrintingIdentifier): ByteArray? = withContext(Dispatchers.IO) {
        File(folder, fileName(printing)).takeIf { it.isFile }?.readBytes()
    }

    /** Writes [photos] to a staging folder beside the photo folder; [StagedPhotos.commit] then swaps them in. */
    override suspend fun stage(photos: Map<PrintingIdentifier, ByteArray>, keep: Set<PrintingIdentifier>): StagedPhotos =
        withContext(Dispatchers.IO) {
            val staging = File(folder.parentFile, "$FOLDER.staging")
            staging.deleteRecursively()
            staging.mkdirs()
            try {
                photos.forEach { (printing, bytes) -> File(staging, fileName(printing)).writeBytes(bytes) }
            } catch (e: Throwable) {
                staging.deleteRecursively()
                throw e
            }
            val now = System.currentTimeMillis()
            val kept = keep.map { it to File(folder, fileName(it)) }.filter { it.second.isFile }
            val newReferences = photos.keys.associateWith { "${fileName(it)}@$now" } +
                kept.associate { (printing, file) -> printing to "${file.name}@${file.lastModified()}" }
            val keptNames = kept.map { it.second.name }.toSet()

            object : StagedPhotos {
                override val references = newReferences

                override suspend fun commit() {
                    withContext(Dispatchers.IO) {
                        folder.mkdirs()
                        folder.listFiles()?.filter { it.name !in keptNames }?.forEach { it.delete() }
                        staging.listFiles()?.forEach {
                            Files.move(it.toPath(), File(folder, it.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        staging.deleteRecursively()
                    }
                }

                override suspend fun discard() {
                    withContext(Dispatchers.IO) { staging.deleteRecursively() }
                }
            }
        }

    private fun decode(source: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }

    /** The scanner leaves its output in the app's cache; it is only a hand-over, so drop it once kept. */
    private fun discardCapture(captured: String) {
        val uri = Uri.parse(captured)
        if (uri.scheme == "file") uri.path?.let { File(it).delete() }
    }

    private companion object {
        const val FOLDER = "card-photos"
        const val MAX_EDGE = 1600
        const val QUALITY = 90

        fun fileName(printing: PrintingIdentifier): String =
            "${printing.game}_${printing.language}_${printing.sourceId.removePrefix("custom:")}.jpg".replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}
