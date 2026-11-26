package tcg.app.scanning

/**
 * The card-shaped outline drawn over the camera preview, as fractions of the visible preview area.
 * The overlay draws it and the scanner crops to it, both from this one function, so the region that is
 * analysed is exactly the region the collector sees.
 */
object CardOutline {
    /** Fractions (0..1) of the visible preview's width and height. */
    data class Fractions(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private const val CARD_ASPECT = 63f / 88f // width / height, portrait
    private const val MAX_WIDTH = 0.84f
    private const val MAX_HEIGHT = 0.92f

    /** The outline for a visible preview whose width / height is [viewAspect], centred. */
    fun within(viewAspect: Float): Fractions {
        var width = MAX_WIDTH
        var height = width * viewAspect / CARD_ASPECT
        if (height > MAX_HEIGHT) {
            height = MAX_HEIGHT
            width = height * CARD_ASPECT / viewAspect
        }
        return Fractions((1 - width) / 2, (1 - height) / 2, (1 + width) / 2, (1 + height) / 2)
    }
}
