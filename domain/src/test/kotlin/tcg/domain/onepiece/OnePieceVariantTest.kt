package tcg.domain.onepiece

import tcg.domain.Variant
import kotlin.test.Test
import kotlin.test.assertEquals

class OnePieceVariantTest {
    @Test
    fun `an ID with no suffix is a Normal Printing`() {
        assertEquals(Variant.Normal, OnePiece.variantOf("OP01-001"))
        assertEquals(Variant.Normal, OnePiece.variantOf("P-001"))
    }

    @Test
    fun `a _pN suffix is an Alternate Art`() {
        assertEquals(Variant.AlternateArt, OnePiece.variantOf("OP01-001_p1"))
        assertEquals(Variant.AlternateArt, OnePiece.variantOf("OP01-006_p3"))
        assertEquals(Variant.AlternateArt, OnePiece.variantOf("OP01-001_p12"))
    }

    @Test
    fun `a _rN suffix is a Reprint`() {
        assertEquals(Variant.Reprint, OnePiece.variantOf("OP01-001_r1"))
        assertEquals(Variant.Reprint, OnePiece.variantOf("ST01-012_r2"))
    }
}
