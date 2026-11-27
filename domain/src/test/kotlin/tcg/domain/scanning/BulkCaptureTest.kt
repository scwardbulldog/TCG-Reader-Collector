package tcg.domain.scanning

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.collection.InMemoryCollectionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BulkCaptureTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()

    private fun printing(sourceId: String) = Printing(
        PrintingIdentifier(Game.OnePiece, Language.English, sourceId), sourceId.substringBefore('_'), "Product", "L", "Card $sourceId", "",
    )

    private suspend fun batch(vararg printings: Printing): Batch {
        catalog.replaceCatalog(Game.OnePiece, Language.English, printings.toList())
        val session = ScanSession(Game.OnePiece, InMemoryLanguageStore())
        return Batch(ScanSessionInput(session, catalog, collection))
    }

    private class ScriptedSource(private val script: (PhotoId) -> String?) : PhotoSource {
        val released = mutableListOf<PhotoId>()
        val gates = mutableMapOf<PhotoId, CompletableDeferred<Unit>>()
        var inFlight = 0
        var mostInFlight = 0

        override suspend fun baseNumberIn(photo: PhotoId): String? {
            inFlight++
            mostInFlight = maxOf(mostInFlight, inFlight)
            try {
                gates[photo]?.await()
                return script(photo)
            } finally {
                inFlight--
            }
        }

        override fun release(photo: PhotoId) {
            released += photo
        }
    }

    @Test
    fun `snapped photos are read into the batch, counted, and released once read`() = runTest {
        val batch = batch(printing("OP01-001"))
        val source = ScriptedSource { "OP01-001" }
        val capture = BulkCapture(batch, source, backgroundScope)

        capture.snap(PhotoId(1))
        capture.snap(PhotoId(2))
        assertEquals(BulkStatus(snapped = 2, read = 0, needsAttention = 0), capture.status.value)
        runCurrent()

        assertEquals(BulkStatus(snapped = 2, read = 2, needsAttention = 0), capture.status.value)
        assertEquals(2, assertIs<BatchRow.Resolved>(batch.rows.single()).count)
        assertEquals(listOf(PhotoId(1), PhotoId(2)), source.released)
    }

    @Test
    fun `a photo whose read fails or throws is released and flagged for attention`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP02-001"), printing("OP02-001_p1"))
        val source = ScriptedSource {
            when (it.value) {
                1L -> "OP01-001"
                2L -> null
                3L -> error("decode failed")
                4L -> "OP02-001"
                else -> "OP99-999"
            }
        }
        val capture = BulkCapture(batch, source, backgroundScope)

        (1L..5L).forEach { capture.snap(PhotoId(it)) }
        runCurrent()

        assertEquals(BulkStatus(snapped = 5, read = 5, needsAttention = 4), capture.status.value)
        assertEquals((1L..5L).map(::PhotoId).toSet(), source.released.toSet())
        assertEquals(5, source.released.size)
    }

    @Test
    fun `snapping is never blocked and only two photos are read at a time`() = runTest {
        val batch = batch(printing("OP01-001"))
        val source = ScriptedSource { "OP01-001" }
        val photos = (1L..6L).map(::PhotoId)
        photos.forEach { source.gates[it] = CompletableDeferred() }
        val capture = BulkCapture(batch, source, backgroundScope)

        photos.forEach { capture.snap(it) }
        runCurrent()

        assertEquals(6, capture.status.value.snapped)
        assertEquals(2, source.inFlight)
        photos.forEach { source.gates.getValue(it).complete(Unit) }
        runCurrent()
        assertEquals(2, source.mostInFlight)
        assertEquals(6, capture.status.value.read)
    }

    @Test
    fun `review rows list flagged rows first and merge repeat reads`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP02-001"), printing("OP02-001_p1"))
        val source = ScriptedSource {
            when (it.value) {
                1L, 4L -> "OP01-001"
                2L -> "OP02-001"
                else -> null
            }
        }
        val capture = BulkCapture(batch, source, backgroundScope)

        (1L..4L).forEach { capture.snap(PhotoId(it)) }
        runCurrent()

        val rows = capture.rows.value
        assertEquals(listOf("Failed", "ambiguous", "resolved"), rows.map { rowKind(it) })
        assertEquals(2, rows.last().count)
    }

    private fun rowKind(row: BatchRow) = when (row) {
        is BatchRow.Failed -> "Failed"
        is BatchRow.Ambiguous -> "ambiguous"
        is BatchRow.Resolved -> "resolved"
    }

    @Test
    fun `fixing rows updates the review list and what needs attention`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP02-001"), printing("OP02-001_p1"), printing("OP03-001"))
        val source = ScriptedSource {
            when (it.value) {
                1L -> "OP02-001"
                2L -> null
                else -> "OP99-999"
            }
        }
        val capture = BulkCapture(batch, source, backgroundScope)
        (1L..3L).forEach { capture.snap(PhotoId(it)) }
        runCurrent()
        assertEquals(3, capture.status.value.needsAttention)

        val (typed, dropped) = capture.rows.value.filterIsInstance<BatchRow.Failed>().map { it.id }
        capture.enterBaseNumber(typed, "op03-001")
        capture.remove(dropped)
        val ambiguous = capture.rows.value.filterIsInstance<BatchRow.Ambiguous>().single()
        capture.pick(ambiguous.id, ambiguous.candidates.last())

        assertEquals(listOf("OP03-001", "OP02-001_p1"), capture.rows.value.map { (it as BatchRow.Resolved).printing.identifier.sourceId })
        assertEquals(BulkStatus(snapped = 2, read = 2, needsAttention = 0), capture.status.value)
    }

    @Test
    fun `Add all through the capture adds the resolved rows, keeps the flagged ones and refreshes the list`() = runTest {
        val zoro = printing("OP01-001")
        val batch = batch(zoro, printing("OP02-001"), printing("OP02-001_p1"))
        val source = ScriptedSource {
            when (it.value) {
                1L, 2L -> "OP01-001"
                3L -> "OP02-001"
                else -> null
            }
        }
        val capture = BulkCapture(batch, source, backgroundScope)
        (1L..4L).forEach { capture.snap(PhotoId(it)) }
        runCurrent()

        val added = capture.addAll(null)

        assertEquals(2, added.size)
        assertEquals(2, collection.ownedCopies(zoro.identifier).size)
        assertEquals(listOf("Failed", "ambiguous"), capture.rows.value.map(::rowKind))
        assertEquals(BulkStatus(snapped = 2, read = 2, needsAttention = 2), capture.status.value)
    }

    @Test
    fun `cards already added leave the counts, but a photo still being read stays in them`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val batch = batch(printing("OP01-001"))
        val source = ScriptedSource { "OP01-001" }
        source.gates[PhotoId(3)] = gate
        val capture = BulkCapture(batch, source, backgroundScope)
        capture.snap(PhotoId(1))
        capture.snap(PhotoId(2))
        runCurrent()

        capture.snap(PhotoId(3))
        runCurrent()
        capture.addAll(null)

        assertEquals(BulkStatus(snapped = 1, read = 0, needsAttention = 0), capture.status.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(BulkStatus(snapped = 1, read = 1, needsAttention = 0), capture.status.value)
    }

    @Test
    fun `closing stops the reads and releases every photo not yet released`() = runTest {
        val batch = batch(printing("OP01-001"))
        val source = ScriptedSource { "OP01-001" }
        val photos = (1L..4L).map(::PhotoId)
        photos.forEach { source.gates[it] = CompletableDeferred() }
        val capture = BulkCapture(batch, source, backgroundScope)
        photos.forEach { capture.snap(it) }
        runCurrent()

        capture.close()
        photos.forEach { source.gates.getValue(it).complete(Unit) }
        runCurrent()

        assertEquals(photos.toSet(), source.released.toSet())
        assertEquals(4, source.released.size)
        assertEquals(emptyList(), batch.rows)
    }
}
