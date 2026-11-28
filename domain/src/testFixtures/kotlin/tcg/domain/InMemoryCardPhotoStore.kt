package tcg.domain

import java.io.IOException

class InMemoryCardPhotoStore : CardPhotoStore {
    private val captures = mutableMapOf<PrintingIdentifier, String>()
    private val bytes = mutableMapOf<PrintingIdentifier, ByteArray>()
    private val references = mutableMapOf<PrintingIdentifier, String>()
    private var saves = 0

    /** While true, [save] fails like a picture that cannot be read. */
    var failing = false

    /** While true, [stage] fails like a full disk. */
    var failingToStage = false

    val count: Int get() = captures.size

    /** The Printings that have a photo. */
    val printings: Set<PrintingIdentifier> get() = captures.keys.toSet()

    /** What was captured for [printing]'s current photo; null if it has none. */
    fun captured(printing: PrintingIdentifier): String? = captures[printing]

    /** Puts [photo] in as the photo of [printing], as a capture would, and returns its reference. */
    fun put(printing: PrintingIdentifier, photo: ByteArray): String {
        captures[printing] = "bytes"
        bytes[printing] = photo
        return "photo-${++saves}".also { references[printing] = it }
    }

    override suspend fun save(printing: PrintingIdentifier, captured: String): String {
        if (failing) throw IOException("Could not keep the photo")
        return put(printing, captured.toByteArray(Charsets.ISO_8859_1)).also { captures[printing] = captured }
    }

    override fun resolve(reference: String): String? = reference.takeIf { it in references.values }?.let { "memory:$it" }

    override suspend fun delete(printing: PrintingIdentifier) {
        captures.remove(printing)
        bytes.remove(printing)
        references.remove(printing)
    }

    override suspend fun deleteAll() {
        captures.clear()
        bytes.clear()
        references.clear()
    }

    override suspend fun read(printing: PrintingIdentifier): ByteArray? = bytes[printing]

    override suspend fun stage(photos: Map<PrintingIdentifier, ByteArray>, keep: Set<PrintingIdentifier>): StagedPhotos {
        if (failingToStage) throw IOException("Disk full")
        val kept = keep.filter { it in bytes }.associateWith { Triple(captures.getValue(it), bytes.getValue(it), references.getValue(it)) }
        val added = photos.keys.associateWith { "photo-${++saves}" }
        return object : StagedPhotos {
            override val references: Map<PrintingIdentifier, String> = kept.mapValues { it.value.third } + added

            override suspend fun commit() {
                deleteAll()
                kept.forEach { (printing, held) ->
                    captures[printing] = held.first
                    bytes[printing] = held.second
                    this@InMemoryCardPhotoStore.references[printing] = held.third
                }
                photos.forEach { (printing, photo) ->
                    captures[printing] = "bytes"
                    bytes[printing] = photo
                    this@InMemoryCardPhotoStore.references[printing] = added.getValue(printing)
                }
            }

            override suspend fun discard() = Unit
        }
    }
}
