package tcg.domain.onepiece

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnePieceBaseNumberTest {
    @Test
    fun `finds a Base Number in a line of card text`() {
        assertEquals("OP01-001", OnePiece.baseNumberIn("OP01-001"))
        assertEquals("ST01-012", OnePiece.baseNumberIn("ST01-012 C"))
        assertEquals("EB01-006", OnePiece.baseNumberIn("EB01-006"))
        assertEquals("PRB01-001", OnePiece.baseNumberIn("PRB01-001"))
        assertEquals("P-001", OnePiece.baseNumberIn("P-001"))
    }

    @Test
    fun `corrects common OCR confusions in the digits and the separator`() {
        assertEquals("OP01-001", OnePiece.baseNumberIn("op01-001"))
        assertEquals("OP01-001", OnePiece.baseNumberIn("OPO1-OO1"))
        assertEquals("OP01-001", OnePiece.baseNumberIn("OP01 – 001"))
        assertEquals("OP01-001", OnePiece.baseNumberIn("OP01001"))
    }

    @Test
    fun `ignores text that is not a Base Number`() {
        assertNull(OnePiece.baseNumberIn("Monkey D. Luffy"))
        assertNull(OnePiece.baseNumberIn("COST 5 POWER 6000"))
        assertNull(OnePiece.baseNumberIn("OP01-0012"))
        assertNull(OnePiece.baseNumberIn("XOP01-001"))
        assertNull(OnePiece.baseNumberIn(""))
    }
}
