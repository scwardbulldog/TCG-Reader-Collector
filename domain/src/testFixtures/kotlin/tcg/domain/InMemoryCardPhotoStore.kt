package tcg.domain

import java.io.IOException

class InMemoryCardPhotoStore : CardPhotoStore {
    private val captures = mutableMapOf<PrintingIdentifier, String>()
    private val references = mutableMapOf<PrintingIdentifier, String>()
    private var saves = 0

    /** While true, [save] fails like a picture that cannot be read. */
    var failing = false

    val count: Int get() = captures.size

    /** What was captured for [printing]'s current photo; null if it has none. */
    fun captured(printing: PrintingIdentifier): String? = captures[printing]

    override suspend fun save(printing: PrintingIdentifier, captured: String): String {
        if (failing) throw IOException("Could not keep the photo")
        captures[printing] = captured
        return "photo-${++saves}".also { references[printing] = it }
    }

    override fun resolve(reference: String): String? = reference.takeIf { it in references.values }?.let { "memory:$it" }

    override suspend fun delete(printing: PrintingIdentifier) {
        captures.remove(printing)
        references.remove(printing)
    }

    override suspend fun deleteAll() {
        captures.clear()
        references.clear()
    }
}
