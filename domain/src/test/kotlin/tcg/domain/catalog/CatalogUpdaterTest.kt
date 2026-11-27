package tcg.domain.catalog

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.fixtureVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogUpdaterTest {
    private val source = FakeCatalogSource()
    private val store = InMemoryCatalogStore()
    private val updater = CatalogUpdater(source, store)

    private val en = CatalogTarget(Game.OnePiece, Language.English)
    private val jp = CatalogTarget(Game.OnePiece, Language.Japanese)

    @Test
    fun `a fresh install imports both Language Catalogs so matching works without the network`() = runTest {
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001_p1" to "Roronoa Zoro"))
        source.publish(
            Game.OnePiece, Language.Japanese, "v1",
            catalogJson("jp", "OP01-001_p1" to "ロロノア・ゾロ", "OP02-013" to "ポートガス・D・エース"),
        )

        val results = updater.update()

        assertEquals(CatalogUpdateResult.Updated(1), results[en])
        assertEquals(CatalogUpdateResult.Updated(2), results[jp])
        assertEquals(
            Printing(
                identifier = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1"),
                baseNumber = "OP01-001",
                releaseProduct = "Romance Dawn",
                rarity = "L",
                name = "Roronoa Zoro",
                imageUrl = "https://example.invalid/OP01-001_p1.png",
                variant = Variant.AlternateArt,
            ),
            store.find(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")),
        )
        assertEquals(
            listOf("ポートガス・D・エース"),
            store.findByBaseNumber(Game.OnePiece, Language.Japanese, "OP02-013").map { it.name },
        )
        assertEquals("v1", store.catalogVersion(Game.OnePiece, Language.English))
        assertEquals("v1", store.catalogVersion(Game.OnePiece, Language.Japanese))
    }

    @Test
    fun `a schemaVersion 2 file carries Variant, Rarity, Product code and Treatments into the Printing`() = runTest {
        source.publish(
            Game.OnePiece, Language.English, "v1",
            """{"schemaVersion":2,"game":"onepiece","language":"en","generatedAt":"2026-01-01T00:00:00.000Z","printings":[
                {"id":"OP01-001","baseNumber":"OP01-001","variant":"normal","name":"Roronoa Zoro","imageUrl":"https://example.invalid/a.png","releaseProduct":"ROMANCE DAWN [OP-01]","rarity":"L","productCode":"OP-01"},
                {"id":"OP01-001_p1","baseNumber":"OP01-001","variant":"alternate-art","name":"Roronoa Zoro","imageUrl":"https://example.invalid/b.png","releaseProduct":"ROMANCE DAWN [OP-01]","rarity":"L","productCode":"OP-01","treatments":["Parallel","Manga"]},
                {"id":"OP01-001_r1","baseNumber":"OP01-001","variant":"reprint","name":"Roronoa Zoro","imageUrl":"https://example.invalid/c.png","releaseProduct":"Event Pack Vol.5","rarity":"SP CARD"}
            ]}""",
        )

        updater.update()

        suspend fun find(id: String) = store.find(PrintingIdentifier(Game.OnePiece, Language.English, id))!!
        assertEquals(Variant.Normal, find("OP01-001").variant)
        assertEquals("OP-01", find("OP01-001").productCode)
        assertEquals(null, find("OP01-001").treatments)
        assertEquals(Variant.AlternateArt, find("OP01-001_p1").variant)
        assertEquals(listOf("Parallel", "Manga"), find("OP01-001_p1").treatments)
        assertEquals(Variant.Reprint, find("OP01-001_r1").variant)
        assertEquals("SP CARD", find("OP01-001_r1").rarity)
        assertEquals(null, find("OP01-001_r1").productCode)
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z"), store.catalogGeneratedAt())
    }

    @Test
    fun `the Variant is read from the field, not from the ID suffix`() = runTest {
        source.publish(
            Game.OnePiece, Language.English, "v1",
            """{"schemaVersion":2,"game":"onepiece","language":"en","generatedAt":"2026-01-01T00:00:00.000Z","printings":[
                {"id":"OP01-001_p1@569901","baseNumber":"OP01-001","variant":"normal","name":"Zoro","imageUrl":"","releaseProduct":"P","rarity":"L"},
                {"id":"OP01-002","baseNumber":"OP01-002","variant":"reprint","name":"Law","imageUrl":"","releaseProduct":"P","rarity":"L"}
            ]}""",
        )

        updater.update()

        assertEquals(Variant.Normal, store.find(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1@569901"))?.variant)
        assertEquals(Variant.Reprint, store.find(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-002"))?.variant)
    }

    @Test
    fun `an unchanged version is not downloaded again`() = runTest {
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001_p1" to "Roronoa Zoro"))
        source.publish(Game.OnePiece, Language.Japanese, "v1", catalogJson("jp", "OP01-001_p1" to "ロロノア・ゾロ"))
        updater.update()

        val results = updater.update()

        assertEquals(CatalogUpdateResult.UpToDate, results[en])
        assertEquals(CatalogUpdateResult.UpToDate, results[jp])
    }

    @Test
    fun `a newer version replaces the stored Catalog`() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001_p1" to "Roronoa Zoro"))
        source.publish(Game.OnePiece, Language.Japanese, "v1", catalogJson("jp", "OP01-001_p1" to "ロロノア・ゾロ"))
        updater.update()
        source.publish(Game.OnePiece, Language.English, "v2", catalogJson("en", "OP01-001_p1" to "Zoro (fixed)", "OP01-002" to "Law"))

        val results = updater.update()

        assertEquals(CatalogUpdateResult.Updated(2), results[en])
        assertEquals(CatalogUpdateResult.UpToDate, results[jp])
        assertEquals("Zoro (fixed)", store.find(zoro)?.name)
        assertEquals("v2", store.catalogVersion(Game.OnePiece, Language.English))
    }

    @Test
    fun `a failed download keeps the previous Catalog and does not block the other Language`() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001_p1" to "Roronoa Zoro"))
        source.publish(Game.OnePiece, Language.Japanese, "v1", catalogJson("jp", "OP01-001_p1" to "ロロノア・ゾロ"))
        updater.update()
        source.failWith(Game.OnePiece, Language.English, java.io.IOException("connection reset"))
        source.publish(Game.OnePiece, Language.Japanese, "v2", catalogJson("jp", "OP01-001_p1" to "ゾロ", "OP01-002" to "ロー"))

        val results = updater.update()

        assertEquals(CatalogUpdateResult.Failed("connection reset"), results[en])
        assertEquals(CatalogUpdateResult.Updated(2), results[jp])
        assertEquals("Roronoa Zoro", store.find(zoro)?.name)
        assertEquals("v1", store.catalogVersion(Game.OnePiece, Language.English))
    }

    @Test
    fun `a truncated or wrong file keeps the previous Catalog`() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001_p1" to "Roronoa Zoro"))
        source.publish(Game.OnePiece, Language.Japanese, "v1", catalogJson("jp", "OP01-001_p1" to "ロロノア・ゾロ"))
        updater.update()
        val bad = listOf(
            catalogJson("en", "OP01-001_p1" to "Zoro v2").dropLast(40),
            catalogJson("jp", "OP01-001_p1" to "Wrong language"),
            catalogJson("en", "OP01-001_p1" to "Zoro v2").replace("\"schemaVersion\":2", "\"schemaVersion\":1"),
            catalogJson("en", "OP01-001_p1" to "Zoro v2").replace("\"schemaVersion\":2", "\"schemaVersion\":3"),
            catalogJson("en", "OP01-001_p1" to "Zoro v2").replace("\"variant\":\"alternate-art\"", "\"variant\":\"holo\""),
            catalogJson("en", "OP01-001_p1" to "Zoro v2").replace("\"variant\":\"alternate-art\",", ""),
            catalogJson("en"),
            catalogJson("en", "OP01-001_p1" to "a", "OP01-001_p1" to "b"),
        )

        for ((i, content) in bad.withIndex()) {
            source.publish(Game.OnePiece, Language.English, "bad$i", content)

            val result = updater.update()[en]

            assertIs<CatalogUpdateResult.Failed>(result, "file $i")
            assertEquals("Roronoa Zoro", store.find(zoro)?.name, "file $i")
            assertEquals("v1", store.catalogVersion(Game.OnePiece, Language.English), "file $i")
        }
    }
}

/** A schemaVersion 2 file. The pipeline publishes `variant`; this fixture stands in for it. */
fun catalogJson(language: String, vararg printings: Pair<String, String>): String {
    val entries = printings.joinToString(",") { (id, name) ->
        val variant = when (fixtureVariant(id)) {
            Variant.Normal -> "normal"
            Variant.AlternateArt -> "alternate-art"
            Variant.Reprint -> "reprint"
        }
        """{"id":"$id","baseNumber":"${id.substringBefore('_')}","variant":"$variant","releaseProduct":"Romance Dawn","rarity":"L","name":"$name","imageUrl":"https://example.invalid/$id.png"}"""
    }
    return """{"schemaVersion":2,"game":"onepiece","language":"$language","generatedAt":"2026-01-01T00:00:00.000Z","printings":[$entries]}"""
}
