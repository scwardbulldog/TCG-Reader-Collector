package tcg.domain.backup

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** The zip a backup is stored in, as entries (name and bytes) held in memory. */
internal object BackupArchive {
    const val COLLECTION_ENTRY = "collection.json"

    /** Everything a restore holds in memory, so a huge or hostile file fails instead of exhausting the phone. */
    const val MAX_BYTES = 100 * 1024 * 1024

    fun isZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()

    /** Reads every file entry of the zip in [input]; throws [BackupFormatException] if it is damaged or too large. */
    fun read(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        try {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.name in entries) throw BackupFormatException("The backup is damaged: it holds ${entry.name} twice")
                val bytes = zip.readLimited(MAX_BYTES - total)
                total += bytes.size
                entries[entry.name] = bytes
            }
        } catch (e: ZipException) {
            throw BackupFormatException("The file is not a Collection backup", e)
        } catch (e: IOException) {
            throw BackupFormatException("The backup is damaged or cut short", e)
        }
        return entries
    }

    /** Reads a plain (version 1) JSON backup as strict UTF-8, so damage fails instead of restoring substituted characters. */
    fun readText(input: InputStream): String {
        val bytes = try {
            input.readLimited(MAX_BYTES)
        } catch (e: IOException) {
            throw BackupFormatException("The file could not be read", e)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw BackupFormatException("The file is damaged: it is not valid text.", e)
        }
    }

    fun decodeText(bytes: ByteArray): String = readText(bytes.inputStream())

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val bytes = readNBytes(limit + 1)
        if (bytes.size > limit) throw BackupFormatException("The file is too large to be a backup")
        return bytes
    }
}
