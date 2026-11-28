package tcg.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The backup zip as bytes, as a file would hold it. Does not record the backup as written. */
suspend fun CollectionBackup.backupFile(): ByteArray =
    ByteArrayOutputStream().also { prepare().writeTo(it) }.toByteArray()

/** Restores [file], which must pass validation in full. */
suspend fun CollectionBackup.restoreFile(file: ByteArray) {
    restore((validate(ByteArrayInputStream(file)) as RestoreCheck.Ready).plan)
}
