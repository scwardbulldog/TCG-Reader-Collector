package tcg.app.scanning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import tcg.domain.Game
import tcg.domain.onepiece.OnePiece
import tcg.domain.scanning.BaseNumberRead
import tcg.domain.scanning.CardScanner
import tcg.domain.scanning.ScanScope
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [CardScanner] backed by CameraX and ML Kit's bundled Latin text recognizer. It binds the camera to
 * [lifecycleOwner] while the flow is collected and shows the preview in [previewView].
 *
 * Preview and analysis share one view port, so each analysed frame is cropped to the visible preview and
 * then to [CardOutline]: only the region the collector sees inside the outline is read.
 */
class CameraXCardScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val debugLog: ScanDebugLog? = null,
) : CardScanner {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var torchOn = false

    /** Applied now if the camera is bound, and again on every later bind. */
    fun setTorch(enabled: Boolean) {
        torchOn = enabled
        debugLog?.note("torch", enabled)
        camera?.cameraControl?.enableTorch(enabled)
    }

    override fun scan(scope: ScanScope): Flow<BaseNumberRead> = callbackFlow {
        val extract: (String) -> String? = when (scope.game) {
            Game.OnePiece -> OnePiece::baseNumberIn
        }
        val provider = cameraProvider()
        val viewPort = awaitViewPort()
        val preview = newPreview()
        debugLog?.note("game", scope.game.name)
        debugLog?.note("torch", torchOn)
        debugLog?.note("analysisSize", "${ANALYSIS_SIZE.width}x${ANALYSIS_SIZE.height}")
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ASPECT)
                    .setResolutionStrategy(
                        ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    ).build(),
            )
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val executor = Executors.newSingleThreadExecutor()
        // Results are handled on the main executor, which outlives this flow, so late callbacks are never rejected.
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        analysis.setAnalyzer(executor) { proxy ->
            val frame = runCatching { outlineImage(proxy) }.getOrNull()
            if (frame == null) {
                proxy.close()
                return@setAnalyzer
            }
            recognizer.process(InputImage.fromBitmap(frame.bitmap, frame.rotation))
                .addOnSuccessListener(mainExecutor) { text ->
                    val read = text.lowestBaseNumber(extract)
                    debugLog?.frame(
                        frame.bitmap,
                        frame.rotation,
                        text.text,
                        text.textBlocks.flatMap { it.lines }.map { ScanDebugLog.Line(it.text, it.boundingBox?.bottom) },
                        read,
                    )
                    read?.let { trySend(BaseNumberRead(it)) }
                }
                .addOnCompleteListener(mainExecutor) { proxy.close() }
        }

        fun release() {
            analysis.clearAnalyzer()
            provider.unbind(preview, analysis)
            camera = null
            executor.shutdown()
            recognizer.close()
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(analysis).build(),
            )
            camera?.cameraControl?.enableTorch(torchOn)
        } catch (e: Exception) {
            release()
            throw e
        }

        awaitClose { release() }
    }

    /**
     * Photo mode for Bulk Add: the preview plus a still-image capture, with nothing read live. Suspends,
     * with the camera bound, until cancelled. [takePhoto] works only meanwhile.
     */
    suspend fun runPhotoMode() {
        val provider = cameraProvider()
        val viewPort = awaitViewPort()
        val preview = newPreview()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ASPECT)
                    .setResolutionStrategy(ResolutionStrategy(PHOTO_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(capture).build(),
            )
            camera?.cameraControl?.enableTorch(torchOn)
            imageCapture = capture
            awaitCancellation()
        } finally {
            imageCapture = null
            provider.unbind(preview, capture)
            camera = null
        }
    }

    /** Saves a full-size photo to [file]. Never blocks: CameraX queues taps made while an earlier photo is still saving. */
    fun takePhoto(file: File, onSaved: () -> Unit, onFailed: () -> Unit) {
        val capture = imageCapture ?: return onFailed()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved()
                override fun onError(exception: ImageCaptureException) = onFailed()
            },
        )
    }

    private suspend fun awaitViewPort(): ViewPort {
        var viewPort = previewView.viewPort
        while (viewPort == null) {
            delay(VIEW_PORT_POLL_MILLIS)
            viewPort = previewView.viewPort
        }
        return viewPort
    }

    private fun newPreview(): Preview = Preview.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ASPECT).build())
        .build()
        .also { it.surfaceProvider = previewView.surfaceProvider }

    private suspend fun cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** The part of [proxy] inside [CardOutline], as ML Kit reads it; null if the frame has no usable area. */
    private fun outlineImage(proxy: ImageProxy): OutlineFrame? {
        val rotation = proxy.imageInfo.rotationDegrees
        val region = outlineInBuffer(proxy.cropRect, rotation) ?: return null
        val bitmap = proxy.toBitmap()
        val clipped = Rect(region).apply { intersect(0, 0, bitmap.width, bitmap.height) }
        if (clipped.width() < MIN_REGION_PX || clipped.height() < MIN_REGION_PX) return null
        return OutlineFrame(Bitmap.createBitmap(bitmap, clipped.left, clipped.top, clipped.width(), clipped.height()), rotation)
    }

    /** An outline crop in buffer orientation; ML Kit and the debug log rotate it by [rotation] to upright. */
    private class OutlineFrame(val bitmap: Bitmap, val rotation: Int)

    private companion object {
        const val VIEW_PORT_POLL_MILLIS = 50L
        const val MIN_REGION_PX = 32
        val ANALYSIS_SIZE = android.util.Size(1920, 1080)
        val PHOTO_SIZE = android.util.Size(2560, 1440)
        val ASPECT = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
    }
}

/**
 * [CardOutline] mapped into the unrotated image buffer. [visible] is the buffer area the preview shows
 * (the view port's crop); the frame is shown rotated by [rotation], so the outline is placed on the
 * rotated visible area and then rotated back into buffer coordinates.
 */
internal fun outlineInBuffer(visible: Rect, rotation: Int): Rect? {
    val width = visible.width()
    val height = visible.height()
    if (width <= 0 || height <= 0) return null
    val rotatedWidth = if (rotation % 180 == 0) width else height
    val rotatedHeight = if (rotation % 180 == 0) height else width
    val outline = CardOutline.within(rotatedWidth.toFloat() / rotatedHeight)
    val left = outline.left * rotatedWidth
    val top = outline.top * rotatedHeight
    val right = outline.right * rotatedWidth
    val bottom = outline.bottom * rotatedHeight

    fun toBuffer(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
        90 -> y to height - x
        180 -> width - x to height - y
        270 -> width - y to x
        else -> x to y
    }
    val (ax, ay) = toBuffer(left, top)
    val (bx, by) = toBuffer(right, bottom)
    return Rect(
        visible.left + minOf(ax, bx).toInt(),
        visible.top + minOf(ay, by).toInt(),
        visible.left + maxOf(ax, bx).toInt(),
        visible.top + maxOf(ay, by).toInt(),
    )
}
