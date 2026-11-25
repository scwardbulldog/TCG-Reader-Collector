package dev.scward.tcgscanspike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Draws the portrait card guide, the bottom-strip ROI, and recognized text boxes.
 * Image->view mapping assumes PreviewView FILL_CENTER with the back camera (no mirroring),
 * which matches how CameraX crops the preview when analysis and preview share an aspect ratio.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    val guideRect = RectF()
    val roiRect = RectF()
    var roiEnabled = true
        set(value) { field = value; invalidate() }

    private var imageW = 0
    private var imageH = 0
    private var allBoxes: List<RectF> = emptyList()
    private var matchBox: RectF? = null

    private val dimPaint = Paint().apply { color = Color.argb(110, 0, 0, 0) }
    private val guidePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
    }
    private val roiPaint = Paint().apply {
        color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val boxPaint = Paint().apply {
        color = Color.argb(160, 255, 255, 0); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val matchPaint = Paint().apply {
        color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val hintPaint = Paint().apply {
        color = Color.WHITE; textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Card is 63x88 mm; keep the guide clear of the top/bottom control panels.
        var gw = w * 0.78f
        var gh = gw * 88f / 63f
        val maxH = h * 0.58f
        if (gh > maxH) { gh = maxH; gw = gh * 63f / 88f }
        val cx = w / 2f
        val cy = h * 0.50f
        guideRect.set(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2)
        // One Piece card numbers sit in the bottom ~12% of the card (right side); give some slack.
        roiRect.set(guideRect.left, guideRect.bottom - gh * 0.22f, guideRect.right, guideRect.bottom + gh * 0.05f)
    }

    fun setImageSize(w: Int, h: Int) { imageW = w; imageH = h }

    fun imageToView(r: Rect): RectF {
        if (imageW == 0 || imageH == 0 || width == 0) return RectF(r)
        val s = max(width.toFloat() / imageW, height.toFloat() / imageH)
        val dx = (width - imageW * s) / 2f
        val dy = (height - imageH * s) / 2f
        return RectF(r.left * s + dx, r.top * s + dy, r.right * s + dx, r.bottom * s + dy)
    }

    fun inRoi(viewBox: RectF): Boolean =
        !roiEnabled || roiRect.contains(viewBox.centerX(), viewBox.centerY())

    fun setBoxes(all: List<RectF>, match: RectF?) {
        allBoxes = all; matchBox = match; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(guideRect, 24f, 24f, Path.Direction.CW)
        }
        canvas.drawPath(path, dimPaint)
        canvas.drawRoundRect(guideRect, 24f, 24f, guidePaint)
        if (roiEnabled) canvas.drawRect(roiRect, roiPaint)
        canvas.drawText("Fill the frame with ONE card (portrait)", width / 2f, guideRect.top - 20f, hintPaint)
        allBoxes.forEach { canvas.drawRect(it, boxPaint) }
        matchBox?.let { canvas.drawRect(it, matchPaint) }
    }
}
