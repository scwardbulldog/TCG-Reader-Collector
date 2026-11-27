package tcg.app.scanning

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Debug builds only: records what the live scanner saw, so a misread can be replayed. It keeps the last few
 * analysed frames in memory and writes them out, with their OCR text and parsed Base Number, when a number
 * locks ([onLock]) or when text is read but no Base Number is found (a failed read).
 *
 * Each record is a folder under [folder] holding `record.json` and one JPEG per frame, upright, as read.
 * Only the newest [MAX_RECORDS] are kept; [clear] runs at start-up. Pull with
 * `adb -s <serial> exec-out run-as tcg.app tar -c -C files scan-debug > scan-debug.tar`.
 *
 * [frame] and [onLock] are called from the main thread only; writing happens on a background thread.
 */
class ScanDebugLog(private val folder: File, private val clock: () -> Long = System::currentTimeMillis) {
    /** One line ML Kit recognised; the parse picks the matching line with the greatest [bottom]. */
    class Line(val text: String, val bottom: Int?)

    private class Frame(
        val bitmap: Bitmap,
        val rotation: Int,
        val ocrText: String,
        val lines: List<Line>,
        val parsed: String?,
        val at: Long,
    )

    private val recent = ArrayDeque<Frame>()
    private val setup = linkedMapOf<String, Any>()
    private val writer = Executors.newSingleThreadExecutor()
    private var lastMissAt = Long.MIN_VALUE

    /** Sets one fact about how the scanner is set up (torch, Language, vote count...); every record carries the latest values. */
    fun note(key: String, value: Any) {
        setup[key] = value
    }

    /** One analysed frame: the outline crop ML Kit read (rotated by [rotation] to upright), its text and the parse. */
    fun frame(crop: Bitmap, rotation: Int, ocrText: String, lines: List<Line>, parsed: String?) {
        val now = clock()
        recent.addLast(Frame(crop, rotation, ocrText, lines, parsed, now))
        if (recent.size > RECENT_FRAMES) recent.removeFirst()
        if (parsed == null && ocrText.isNotBlank() && now - lastMissAt >= MISS_INTERVAL_MILLIS) {
            lastMissAt = now
            save("miss", lockedBaseNumber = null, votes = emptyMap())
        }
    }

    /** [baseNumber] just locked with these [votes], this read included. */
    fun onLock(baseNumber: String, votes: Map<String, Int>) = save("lock", baseNumber, votes)

    fun clear() {
        folder.deleteRecursively()
    }

    private fun save(kind: String, lockedBaseNumber: String?, votes: Map<String, Int>) {
        val frames = recent.toList()
        val time = clock()
        val setupNow = JSONObject(setup.toMap())
        writer.execute {
            runCatching { write(kind, time, lockedBaseNumber, votes, setupNow, frames) }
        }
    }

    private fun write(
        kind: String,
        time: Long,
        lockedBaseNumber: String?,
        votes: Map<String, Int>,
        setupNow: JSONObject,
        frames: List<Frame>,
    ) {
        val record = File(folder, "%013d-%s".format(time, kind)).apply { mkdirs() }
        val files = frames.mapIndexed { index, frame ->
            val name = "frame-${index + 1}.jpg"
            File(record, name).outputStream().use { upright(frame).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        }
        val json = JSONObject()
            .put("kind", kind)
            .put("timeMillis", time)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            .put("setup", setupNow)
            .put("lockedBaseNumber", lockedBaseNumber ?: JSONObject.NULL)
            .put("votes", JSONObject(votes))
            .put(
                "frames",
                JSONArray(
                    frames.mapIndexed { index, frame ->
                        JSONObject()
                            .put("file", files[index])
                            .put("readAtMillis", frame.at)
                            .put("cropPx", "${frame.bitmap.width}x${frame.bitmap.height}, rotated ${frame.rotation}")
                            .put("ocrText", frame.ocrText)
                            .put(
                                "lines",
                                JSONArray(
                                    frame.lines.map {
                                        JSONObject().put("text", it.text).put("bottom", it.bottom ?: JSONObject.NULL)
                                    },
                                ),
                            )
                            .put("parsedBaseNumber", frame.parsed ?: JSONObject.NULL)
                    },
                ),
            )
        File(record, "record.json").writeText(json.toString(2))
        folder.listFiles()?.sortedBy { it.name }?.dropLast(MAX_RECORDS)?.forEach { it.deleteRecursively() }
    }

    private fun upright(frame: Frame): Bitmap =
        if (frame.rotation == 0) {
            frame.bitmap
        } else {
            Bitmap.createBitmap(
                frame.bitmap, 0, 0, frame.bitmap.width, frame.bitmap.height,
                Matrix().apply { postRotate(frame.rotation.toFloat()) }, true,
            )
        }

    companion object {
        const val FOLDER = "scan-debug"
        private const val RECENT_FRAMES = 6
        private const val MAX_RECORDS = 30
        private const val MISS_INTERVAL_MILLIS = 2_000L
        private const val JPEG_QUALITY = 90
    }
}
