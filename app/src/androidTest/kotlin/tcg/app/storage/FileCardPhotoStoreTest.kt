package tcg.app.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.PrintingIdentifier
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCardPhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = FileCardPhotoStore(context)
    private val milk = PrintingIdentifier(Game.OnePiece, Language.English, "custom:1")
    private val capture = File(context.cacheDir, "capture.jpg")

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking { store.deleteAll() }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking { store.deleteAll() }
        capture.delete()
    }

    @Test
    fun a_large_capture_is_kept_as_a_jpeg_of_at_most_1600_px_on_the_long_edge() = runTest {
        writeCapture(width = 3000, height = 4000)

        val reference = store.save(milk, "file://${capture.path}")

        val kept = BitmapFactory.decodeFile(assertNotNull(store.resolve(reference)))
        assertEquals(1600, maxOf(kept.width, kept.height))
        assertEquals(3, kept.width * 4 / kept.height)
    }

    @Test
    fun a_retake_replaces_the_file_and_changes_the_reference() = runTest {
        writeCapture(400, 600)
        val first = store.save(milk, "file://${capture.path}")
        writeCapture(400, 600)
        val second = store.save(milk, "file://${capture.path}")

        assertNotEquals(first, second)
        assertEquals(1, File(context.filesDir, "card-photos").listFiles()?.size)
    }

    @Test
    fun a_capture_that_cannot_be_read_throws_and_keeps_the_earlier_photo() = runTest {
        writeCapture(400, 600)
        val reference = store.save(milk, "file://${capture.path}")

        val failed = runCatching { store.save(milk, "file://${context.cacheDir}/missing.jpg") }

        assertTrue(failed.isFailure)
        assertNotNull(store.resolve(reference))
    }

    @Test
    fun delete_and_delete_all_remove_the_files() = runTest {
        writeCapture(400, 600)
        val reference = store.save(milk, "file://${capture.path}")

        store.delete(milk)
        assertNull(store.resolve(reference))

        writeCapture(400, 600)
        val again = store.save(milk, "file://${capture.path}")
        store.deleteAll()
        assertNull(store.resolve(again))
    }

    private fun writeCapture(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
