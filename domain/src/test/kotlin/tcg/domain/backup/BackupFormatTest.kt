package tcg.domain.backup

import tcg.domain.Condition
import tcg.domain.CollectionSnapshot
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class BackupFormatTest {
    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-016")
    private val createdAt = Instant.parse("2026-11-26T18:00:00Z")

    private val snapshot = CollectionSnapshot(
        locations = listOf(Location(LocationId(3), "Binder 1"), Location(LocationId(9), "Bulk \"box\" A")),
        copies = listOf(
            OwnedCopy(OwnedCopyId(7), zoro, Condition.NearMint, LocationId(3), "sleeved"),
            OwnedCopy(OwnedCopyId(8), zoro, Condition.NearMint, LocationId(3), "sleeved"),
            OwnedCopy(OwnedCopyId(12), nami, Condition.HeavilyPlayed, null, "line one\nline two ✓"),
            OwnedCopy(OwnedCopyId(20), zoro, Condition.Damaged, LocationId(9), ""),
        ),
    )

    private fun serialized() = BackupFormat.serialize(snapshot, createdAt)

    @Test
    fun `a backup parses back to exactly the same Owned Copies and Locations`() {
        val parsed = BackupFormat.parse(serialized())

        assertEquals(snapshot, parsed.snapshot)
        assertEquals(createdAt, parsed.createdAt)
    }

    @Test
    fun `identical Owned Copies stay separate records`() {
        val parsed = BackupFormat.parse(serialized())

        assertEquals(2, parsed.snapshot.copies.count { it.printing == zoro && it.condition == Condition.NearMint })
    }

    @Test
    fun `a backup holds no Catalog details`() {
        val json = serialized()

        listOf("baseNumber", "releaseProduct", "rarity", "imageUrl", "lastKnownPrintings").forEach { assertFalse(json.contains(it), it) }
    }

    private val retired = Printing(
        identifier = zoro,
        baseNumber = "OP01-001",
        releaseProduct = "Romance Dawn",
        rarity = "L",
        name = "Roronoa Zoro",
        imageUrl = "https://example.invalid/OP01-001_p1.png",
        variant = Variant.AlternateArt,
        productCode = "OP-01",
        treatments = listOf("Manga", "Wanted Poster"),
        noLongerInCatalog = true,
    )

    @Test
    fun `last-known details of a flagged Printing round-trip, including unknown and empty fields`() {
        val bare = retired.copy(identifier = nami, variant = null, productCode = null, treatments = null)
        val plain = retired.copy(identifier = nami.copy(sourceId = "OP01-017"), variant = Variant.Normal, treatments = emptyList())
        val withFlagged = snapshot.copy(lastKnownPrintings = listOf(retired, bare, plain))

        val parsed = BackupFormat.parse(BackupFormat.serialize(withFlagged, createdAt)).snapshot

        assertEquals(withFlagged.lastKnownPrintings.toSet(), parsed.lastKnownPrintings.toSet())
    }

    @Test
    fun `a version 1 backup without last-known details still parses`() {
        val old = """{"format":"tcg-collection-backup","schemaVersion":1,"createdAt":"2026-11-26T18:00:00Z","locations":[],""" +
            """"ownedCopies":[{"id":1,"game":"onepiece","language":"en","sourceId":"OP01-001_p1","condition":"near_mint","locationId":null,"notes":""}]}"""

        val parsed = BackupFormat.parse(old).snapshot

        assertEquals(emptyList(), parsed.lastKnownPrintings)
        assertEquals(zoro, parsed.copies.single().printing)
    }

    @Test
    fun `a flagged Printing changes the fingerprint`() {
        assertNotEquals(BackupFormat.fingerprint(snapshot), BackupFormat.fingerprint(snapshot.copy(lastKnownPrintings = listOf(retired))))
    }

    @Test
    fun `an invalid last-known Printing is rejected`() {
        val withFlagged = BackupFormat.serialize(snapshot.copy(lastKnownPrintings = listOf(retired)), createdAt)

        val custom = snapshot.copy(lastKnownPrintings = listOf(retired.copy(identifier = zoro.copy(sourceId = "custom:1"))))
        listOf(
            withFlagged.replace("\"OP01-001\"", "\" \""),
            withFlagged.replace("\"alternate-art\"", "\"holo\""),
            BackupFormat.serialize(custom, createdAt),
        ).forEach { assertFailsWith<BackupFormatException> { BackupFormat.parse(it) } }
        val duplicate = snapshot.copy(lastKnownPrintings = listOf(retired, retired))
        assertFailsWith<BackupFormatException> { BackupFormat.parse(BackupFormat.serialize(duplicate, createdAt)) }
    }

    @Test
    fun `an empty Collection round-trips`() {
        val empty = CollectionSnapshot(emptyList(), emptyList())

        assertEquals(empty, BackupFormat.parse(BackupFormat.serialize(empty, createdAt)).snapshot)
    }

    @Test
    fun `text that is not JSON is rejected`() {
        assertFailsWith<BackupFormatException> { BackupFormat.parse("not json at all") }
        assertFailsWith<BackupFormatException> { BackupFormat.parse("") }
    }

    @Test
    fun `rejection messages are plain language, without parser internals`() {
        val csv = assertFailsWith<BackupFormatException> { BackupFormat.parse("Game,Language,Base Number\nOne Piece,EN,OP01-001") }
        val missingField = assertFailsWith<BackupFormatException> {
            BackupFormat.parse(serialized().replace("\"notes\"", "\"note\""))
        }

        assertEquals("The file is not a Collection backup", csv.message)
        assertEquals("The backup file is damaged or incomplete", missingField.message)
    }

    @Test
    fun `a truncated file is rejected`() {
        val json = serialized()

        assertFailsWith<BackupFormatException> { BackupFormat.parse(json.substring(0, json.length / 2)) }
    }

    @Test
    fun `JSON that is not a backup is rejected`() {
        assertFailsWith<BackupFormatException> { BackupFormat.parse("[1, 2]") }
        assertFailsWith<BackupFormatException> { BackupFormat.parse("""{"schemaVersion":1,"game":"onepiece","language":"en","printings":[]}""") }
    }

    @Test
    fun `an unsupported schema version is rejected, even if the rest of the file has changed shape`() {
        val future = """{"format":"tcg-collection-backup","schemaVersion":2,"somethingNew":[]}"""

        val error = assertFailsWith<BackupFormatException> { BackupFormat.parse(future) }

        assertEquals(true, error.message?.contains("version 2"))
    }

    @Test
    fun `a missing field is rejected`() {
        assertFailsWith<BackupFormatException> { BackupFormat.parse(serialized().replace("\"notes\"", "\"note\"")) }
    }

    @Test
    fun `an unknown condition, Game or Language is rejected`() {
        assertFailsWith<BackupFormatException> { BackupFormat.parse(serialized().replace("damaged", "mint")) }
        assertFailsWith<BackupFormatException> { BackupFormat.parse(serialized().replace("onepiece", "pokemon")) }
        assertFailsWith<BackupFormatException> { BackupFormat.parse(serialized().replace("\"jp\"", "\"fr\"")) }
    }

    @Test
    fun `an Owned Copy in a Location the file does not define is rejected`() {
        val dangling = CollectionSnapshot(
            locations = emptyList(),
            copies = listOf(OwnedCopy(OwnedCopyId(1), zoro, Condition.NearMint, LocationId(5), "")),
        )

        assertFailsWith<BackupFormatException> { BackupFormat.parse(BackupFormat.serialize(dangling, createdAt)) }
    }

    @Test
    fun `duplicate ids and a blank Printing source id are rejected`() {
        val duplicateCopy = snapshot.copy(copies = snapshot.copies + snapshot.copies.first())
        val duplicateLocation = snapshot.copy(locations = snapshot.locations + snapshot.locations.first())
        val blankSource = snapshot.copy(copies = listOf(snapshot.copies.first().copy(printing = zoro.copy(sourceId = " "))))

        listOf(duplicateCopy, duplicateLocation, blankSource).forEach {
            assertFailsWith<BackupFormatException> { BackupFormat.parse(BackupFormat.serialize(it, createdAt)) }
        }
    }

    @Test
    fun `a bad creation time is rejected`() {
        assertFailsWith<BackupFormatException> { BackupFormat.parse(serialized().replace("2026-11-26T18:00:00Z", "yesterday")) }
    }
}
