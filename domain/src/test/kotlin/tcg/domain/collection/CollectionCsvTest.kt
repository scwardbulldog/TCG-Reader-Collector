package tcg.domain.collection

import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionCsvTest {
    private val header = "Game,Language,Base Number,Printing identifier,Name,Variant,Release Product,Condition,Location,Notes"

    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val zoroDetails = Printing(zoro, "OP01-001", "Romance Dawn", "L", "Roronoa Zoro", "https://example.test/zoro.png", Variant.AlternateArt)

    private fun copy(
        id: Long,
        printing: PrintingIdentifier = zoro,
        condition: Condition = Condition.NearMint,
        location: Long? = null,
        notes: String = "",
    ) = OwnedCopy(OwnedCopyId(id), printing, condition, location?.let(::LocationId), notes)

    private fun csv(
        copies: List<OwnedCopy>,
        details: Map<PrintingIdentifier, Printing> = mapOf(zoro to zoroDetails),
        locations: Map<LocationId, String> = emptyMap(),
    ) = CollectionCsv.build(copies, details, locations)

    @Test
    fun `an Owned Copy becomes one row with the listed columns in order`() {
        val text = csv(
            listOf(copy(1, condition = Condition.LightlyPlayed, location = 7, notes = "sleeved")),
            locations = mapOf(LocationId(7) to "Binder A"),
        )

        assertEquals(
            listOf(
                header,
                "onepiece,EN,OP01-001,onepiece / EN / OP01-001_p1,Roronoa Zoro,Alternate Art,Romance Dawn,Lightly Played,Binder A,sleeved",
            ),
            text.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `an Unresolved Printing leaves its Catalog details blank instead of failing`() {
        val gone = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP09-999")

        val text = csv(listOf(copy(1, printing = gone, condition = Condition.Damaged)), details = emptyMap())

        assertEquals(
            listOf(header, "onepiece,JP,,onepiece / JP / OP09-999,,,,Damaged,,"),
            text.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `separate Owned Copies of one Printing stay separate rows`() {
        val text = csv(listOf(copy(1, notes = "first"), copy(2, notes = "second")))

        val rows = text.lines().filter { it.isNotEmpty() }.drop(1)
        assertEquals(2, rows.size)
        assertEquals(listOf("first", "second"), rows.map { it.substringAfterLast(',') })
    }

    @Test
    fun `notes with commas, quotes and newlines survive a standard CSV parser`() {
        val notes = "tight, \"mint\"\nsee binder\r\nsecond line"

        val table = parseCsv(csv(listOf(copy(1, notes = notes), copy(2, notes = "plain"))))

        assertEquals(3, table.size)
        assertEquals(10, table[1].size)
        assertEquals(notes, table[1][9])
        assertEquals("plain", table[2][9])
    }

    /** A minimal RFC 4180 reader, so the escaping is checked against the standard rather than the builder. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> cell.append(c)
                c == '"' -> quoted = true
                c == ',' -> { row.add(cell.toString()); cell.clear() }
                c == '\r' && text.getOrNull(i + 1) == '\n' -> {
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = mutableListOf(); i++
                }
                else -> cell.append(c)
            }
            i++
        }
        return rows
    }
}
