package tcg.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch
import tcg.domain.scanning.PhotoId
import java.io.File
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import androidx.lifecycle.compose.LocalLifecycleOwner
import tcg.app.scanning.CameraXCardScanner
import tcg.app.TcgApplication
import tcg.app.scanning.CardOutline
import tcg.domain.scanning.BaseNumberRead
import tcg.domain.scanning.ScanScope

private const val RESIZE_SETTLE_MILLIS = 300L
private const val FLASH_ALPHA = 0.6f
private const val FLASH_MILLIS = 150

/** Bulk Add's shutter: where each photo is saved, what to do once it is, and how many are in the batch so far. */
class BulkShutter(
    val newPhoto: () -> Pair<PhotoId, File>,
    val onSaved: (PhotoId) -> Unit,
    val onFailed: (PhotoId) -> Unit,
    val snapped: Int,
)

/**
 * Live camera for a Scan Session: preview with the card outline, a torch toggle and a tilt hint.
 * Reads go to [onRead] for as long as this is on screen. With [bulk] set it is Bulk Add instead: nothing
 * is read live and a shutter saves one photo per tap. Without camera permission it says so and the
 * collector falls back to the manual search below it.
 */
@OptIn(FlowPreview::class)
@Composable
fun ScanCameraPane(
    scope: ScanScope,
    onRead: (BaseNumberRead) -> Unit,
    modifier: Modifier = Modifier,
    /** False while something covers the camera; the torch is then off, and comes back with the toggle when visible again. */
    visible: Boolean = true,
    bulk: BulkShutter? = null,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) requestPermission.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The camera needs permission to read Base Numbers. You can still search manually.")
            Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val scanner = remember(previewView) {
        previewView?.let { CameraXCardScanner(context, lifecycleOwner, it, (context.applicationContext as TcgApplication).scanDebugLog) }
    }

    LaunchedEffect(scanner, torchOn, visible) { scanner?.setTorch(torchOn && visible) }
    // The view port is fixed at bind time, so rebinding on a size change keeps the analysed region equal to the drawn outline.
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val bulkMode = bulk != null
    LaunchedEffect(scanner, scope.game, bulkMode) {
        if (scanner == null) return@LaunchedEffect
        snapshotFlow { previewSize }
            .debounce(RESIZE_SETTLE_MILLIS)
            .filter { it != IntSize.Zero }
            .collectLatest { if (bulkMode) scanner.runPhotoMode() else scanner.scan(scope).collect(onRead) }
    }

    val view = LocalView.current
    val flash = remember { Animatable(0f) }
    val animation = rememberCoroutineScope()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { previewSize = it }) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            OutlineOverlay(Modifier.fillMaxSize())
            FilledTonalButton(
                onClick = { torchOn = !torchOn },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Text(if (torchOn) "Torch on" else "Torch off") }
            if (bulk != null) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.White, alpha = flash.value) })
                Text(
                    "${bulk.snapped}",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                )
                // Never disabled: taps while earlier photos are still saving or being read are queued, not dropped.
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        animation.launch {
                            flash.snapTo(FLASH_ALPHA)
                            flash.animateTo(0f, tween(FLASH_MILLIS))
                        }
                        val (photo, file) = bulk.newPhoto()
                        scanner?.takePhoto(file, onSaved = { bulk.onSaved(photo) }, onFailed = { bulk.onFailed(photo) })
                            ?: bulk.onFailed(photo)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                ) { Text("Snap") }
            }
        }
        Text(
            if (bulk != null) "Snap each card inside the outline; cards are read in the background. Turn the torch on for sharper reads."
            else "Fit the card in the outline. Tilt it slightly if the number is lost in glare.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Dims everything outside [CardOutline] and draws the outline, the exact region the scanner reads. */
@Composable
private fun OutlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val fractions = CardOutline.within(size.width / size.height)
        val topLeft = Offset(fractions.left * size.width, fractions.top * size.height)
        val outlineSize = Size((fractions.right - fractions.left) * size.width, (fractions.bottom - fractions.top) * size.height)
        val corner = CornerRadius(16.dp.toPx())
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRoundRect(RoundRect(androidx.compose.ui.geometry.Rect(topLeft, outlineSize), corner))
        }
        drawPath(dim, Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.White, topLeft, outlineSize, corner, style = Stroke(width = 3.dp.toPx()))
    }
}
