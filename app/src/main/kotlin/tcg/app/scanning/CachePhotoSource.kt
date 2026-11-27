package tcg.app.scanning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tcg.domain.onepiece.OnePiece
import tcg.domain.scanning.PhotoId
import tcg.domain.scanning.PhotoSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bulk Add photos: each full-size photo is a temp file in the app's cache folder, deleted by [release] as
 * soon as it has been read. Thumbnails stay in memory only, until [dropThumbnails].
 *
 * A photo is read once: the card outline first (like the live scan), then the whole photo.
 */
class CachePhotoSource(
    context: Context,
    private val extract: (String) -> String? = OnePiece::baseNumberIn,
) : PhotoSource {
    private val folder = folderIn(context).apply { mkdirs() }
    private val nextId = AtomicLong(1)
    private val files = ConcurrentHashMap<PhotoId, File>()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Small in-memory previews of the photos read so far, for the review list. */
    val thumbnails = ConcurrentHashMap<PhotoId, Bitmap>()

    /** Reserves an id and the temp file the camera will write that photo to. */
    fun newPhoto(): Pair<PhotoId, File> {
        val id = PhotoId(nextId.getAndIncrement())
        val file = File(folder, "photo-${id.value}.jpg")
        files[id] = file
        return id to file
    }

    override suspend fun baseNumberIn(photo: PhotoId): String? {
        val file = files[photo] ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeUpright(file) } ?: return null
        try {
            thumbnails[photo] = thumbnailOf(bitmap)
            val outline = CardOutline.within(bitmap.width.toFloat() / bitmap.height)
            val cropped = Bitmap.createBitmap(
                bitmap,
                (outline.left * bitmap.width).toInt(),
                (outline.top * bitmap.height).toInt(),
                ((outline.right - outline.left) * bitmap.width).toInt(),
                ((outline.bottom - outline.top) * bitmap.height).toInt(),
            )
            try {
                recognise(cropped)?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
            return recognise(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun release(photo: PhotoId) {
        files.remove(photo)?.delete()
    }

    fun dropThumbnails() = thumbnails.clear()

    fun dropThumbnail(photo: PhotoId) {
        thumbnails.remove(photo)
    }

    private suspend fun recognise(bitmap: Bitmap): String? =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().lowestBaseNumber(extract)

    companion object {
        private const val FOLDER = "bulk-add"
        private const val MAX_EDGE = 2560
        private const val THUMBNAIL_WIDTH = 120

        private fun folderIn(context: Context) = File(context.cacheDir, FOLDER)

        /** Deletes temp photos a crash left behind; run at start-up, before any batch exists. */
        fun sweep(context: Context) {
            folderIn(context).listFiles()?.forEach { it.delete() }
        }

        private fun decodeUpright(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return decoded
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
            decoded.recycle()
            return upright
        }

        private fun thumbnailOf(bitmap: Bitmap): Bitmap =
            Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH, THUMBNAIL_WIDTH * bitmap.height / bitmap.width, true)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
