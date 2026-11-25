package dev.scward.tcgscanspike

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private data class Result(
        val index: Int, val locked: String?, val raw: String, val timeToLockMs: Long?,
        val avgLatencyMs: Long, val verdict: String, val torch: Boolean,
        val roi: Boolean, val n: Int, val res: String,
    ) {
        fun csv(): String = listOf(
            index, locked ?: "", "\"" + raw.replace("\"", "\"\"").replace("\n", " | ") + "\"",
            timeToLockMs ?: "", avgLatencyMs, verdict, if (torch) "on" else "off",
            if (roi) "bottom" else "whole", n, res,
        ).joinToString(",")
    }

    private data class Match(val number: String, val raw: String, val box: RectF)

    companion object {
        const val TAG = "SCANSPIKE"
        const val CSV_HEADER = "index,locked,raw,time_to_lock_ms,avg_frame_latency_ms,verdict,torch,roi,n,res"

        private const val D = "[0-9OQDIL|!SBZ]"
        private const val SEP = "\\s*[-\u2010\u2011\u2012\u2013\u2014\u2015\u2212~_=.]?\\s*"
        private val SET_RE = Regex(
            "(?<![A-Z0-9])(PR[B8]|[O0Q]P|[S5][T7]|E[B8])\\s*($D{2})$SEP($D{3})(?![0-9])"
        )
        private val PROMO_RE = Regex(
            "(?<![A-Z0-9])P\\s*[-\u2010\u2011\u2012\u2013\u2014\u2015\u2212~_=.]\\s*($D{3})(?![0-9])"
        )

        private fun digits(s: String) = s.map {
            when (it) {
                'O', 'Q', 'D' -> '0'
                'I', 'L', '|', '!' -> '1'
                'S' -> '5'
                'B' -> '8'
                'Z' -> '2'
                else -> it
            }
        }.joinToString("")

        fun extract(line: String): String? {
            val u = line.uppercase()
            SET_RE.find(u)?.let { m ->
                val prefix = when (m.groupValues[1][0]) {
                    'P' -> "PRB"
                    'O', '0', 'Q' -> "OP"
                    'S', '5' -> "ST"
                    else -> "EB"
                }
                return "$prefix${digits(m.groupValues[2])}-${digits(m.groupValues[3])}"
            }
            PROMO_RE.find(u)?.let { m -> return "P-${digits(m.groupValues[1])}" }
            return null
        }
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var lockedText: TextView
    private lateinit var statusText: TextView
    private lateinit var rawText: TextView
    private lateinit var tallyText: TextView
    private lateinit var btnTorch: Button
    private lateinit var btnRoi: Button
    private lateinit var btnN: Button
    private lateinit var btnRes: Button

    private lateinit var analysisExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var camera: Camera? = null

    private var torchOn = false
    private var roiOn = true
    private var stableN = 3
    private var hiRes = true

    // Per-card state (reset after each verdict)
    private var firstSeenAt: Long? = null
    private var candidate: String? = null
    private var candidateRaw = ""
    private var streak = 0
    private var locked: String? = null
    private var lockedRaw = ""
    private var awaitingSwap: String? = null
    private var timeToLock: Long? = null
    private var latencySum = 0L
    private var frameCount = 0
    private var lastLatency = 0L

    private val results = mutableListOf<Result>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.preview)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        overlay = findViewById(R.id.overlay)
        lockedText = findViewById(R.id.lockedText)
        statusText = findViewById(R.id.statusText)
        rawText = findViewById(R.id.rawText)
        tallyText = findViewById(R.id.tallyText)
        btnTorch = findViewById(R.id.btnTorch)
        btnRoi = findViewById(R.id.btnRoi)
        btnN = findViewById(R.id.btnN)
        btnRes = findViewById(R.id.btnRes)
        analysisExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.btnCorrect).setOnClickListener { record("correct") }
        findViewById<Button>(R.id.btnWrong).setOnClickListener { record("wrong") }
        findViewById<Button>(R.id.btnMissed).setOnClickListener { record("missed") }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyCsv() }
        btnTorch.setOnClickListener {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            updateToggles()
        }
        btnRoi.setOnClickListener {
            roiOn = !roiOn
            overlay.roiEnabled = roiOn
            resetCard()
            updateToggles()
        }
        btnN.setOnClickListener {
            stableN = when (stableN) { 2 -> 3; 3 -> 5; else -> 2 }
            resetCard()
            updateToggles()
        }
        btnRes.setOnClickListener {
            hiRes = !hiRes
            resetCard()
            updateToggles()
            startCamera()
        }
        updateToggles()
        updateTally()
        resetCard()
        Log.i(TAG, CSV_HEADER)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else statusText.text = "Camera permission denied"
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val aspect = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val target = if (hiRes) Size(1920, 1080) else Size(1280, 720)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspect)
                        .setResolutionStrategy(
                            ResolutionStrategy(target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { analyze(it) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera?.cameraControl?.enableTorch(torchOn)
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: run { proxy.close(); return }
        val rot = proxy.imageInfo.rotationDegrees
        val (w, h) = if (rot == 90 || rot == 270) proxy.height to proxy.width else proxy.width to proxy.height
        val resLabel = "${proxy.width}x${proxy.height}"
        val input = InputImage.fromMediaImage(media, rot)
        val t0 = SystemClock.elapsedRealtime()
        recognizer.process(input)
            .addOnSuccessListener(ContextCompat.getMainExecutor(this)) { text ->
                val now = SystemClock.elapsedRealtime()
                val latency = now - t0
                overlay.setImageSize(w, h)
                val boxes = mutableListOf<RectF>()
                val matches = mutableListOf<Match>()
                for (block in text.textBlocks) for (line in block.lines) {
                    val box = line.boundingBox?.let { overlay.imageToView(it) } ?: continue
                    boxes += box
                    if (!overlay.inRoi(box)) continue
                    extract(line.text)?.let { matches += Match(it, line.text, box) }
                }
                // Prefer the lowest match on screen: the card number sits at the bottom of the card.
                val best = matches.maxByOrNull { it.box.bottom }
                overlay.setBoxes(boxes, best?.box)
                onFrame(best, latency, now, resLabel)
            }
            .addOnFailureListener { Log.w(TAG, "ML Kit failure", it) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onFrame(frameMatch: Match?, latency: Long, now: Long, res: String) {
        // After a verdict, ignore the just-recorded number until the card leaves frame or a different number shows.
        var m = frameMatch
        if (awaitingSwap != null) {
            if (m == null || m.number != awaitingSwap) awaitingSwap = null else m = null
        }
        lastLatency = latency
        latencySum += latency
        frameCount++
        if (m != null) {
            if (firstSeenAt == null) firstSeenAt = now
            if (m.number == candidate) streak++ else { candidate = m.number; streak = 1 }
            candidateRaw = m.raw
            if (locked == null && streak >= stableN) {
                locked = m.number
                lockedRaw = m.raw
                timeToLock = now - (firstSeenAt ?: now)
            }
        } else {
            streak = 0
            candidate = null
        }
        lockedText.text = locked ?: (candidate?.let { "$it ?" } ?: if (awaitingSwap != null) "next card…" else "—")
        lockedText.setTextColor(if (locked != null) 0xFF69F0AE.toInt() else 0xFFFFFFFF.toInt())
        rawText.text = "raw: " + (if (locked != null) lockedRaw else m?.raw ?: "")
        statusText.text = buildString {
            append("frame ${latency}ms  avg ${avgLatency()}ms  n=$frameCount  $res")
            append("  streak $streak/$stableN")
            timeToLock?.let { append("  LOCK ${it}ms") }
        }
    }

    private fun avgLatency() = if (frameCount == 0) 0L else latencySum / frameCount

    private fun record(verdict: String) {
        val r = Result(
            index = results.size + 1,
            locked = locked,
            raw = if (locked != null) lockedRaw else candidateRaw,
            timeToLockMs = timeToLock,
            avgLatencyMs = avgLatency(),
            verdict = verdict,
            torch = torchOn,
            roi = roiOn,
            n = stableN,
            res = if (hiRes) "1080p" else "720p",
        )
        results += r
        Log.i(TAG, r.csv())
        updateTally()
        awaitingSwap = locked ?: candidate
        resetCard()
    }

    private fun resetCard() {
        firstSeenAt = null; candidate = null; candidateRaw = ""; streak = 0
        locked = null; lockedRaw = ""; timeToLock = null
        latencySum = 0; frameCount = 0
        if (::lockedText.isInitialized) {
            lockedText.text = "—"
            lockedText.setTextColor(0xFFFFFFFF.toInt())
            rawText.text = ""
        }
    }

    private fun updateTally() {
        val c = results.count { it.verdict == "correct" }
        val w = results.count { it.verdict == "wrong" }
        val m = results.count { it.verdict == "missed" }
        val total = results.size
        val acc = if (total == 0) 0.0 else c * 100.0 / total
        val ttl = results.mapNotNull { it.timeToLockMs }.sorted()
        val median = when {
            ttl.isEmpty() -> "-"
            ttl.size % 2 == 1 -> "${ttl[ttl.size / 2]}"
            else -> "${(ttl[ttl.size / 2 - 1] + ttl[ttl.size / 2]) / 2}"
        }
        tallyText.text = "#$total  ✓$c  ✗$w  missed $m  acc ${"%.1f".format(acc)}%  median lock ${median}ms"
    }

    private fun updateToggles() {
        btnTorch.text = if (torchOn) "Torch: on" else "Torch: off"
        btnRoi.text = if (roiOn) "ROI: bottom" else "ROI: whole"
        btnN.text = "N=$stableN"
        btnRes.text = if (hiRes) "1080p" else "720p"
    }

    private fun copyCsv() {
        val csv = (listOf(CSV_HEADER) + results.map { it.csv() }).joinToString("\n")
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("scan-spike-results", csv))
        Toast.makeText(this, "Copied ${results.size} rows", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        recognizer.close()
    }
}
