package tcg.domain.scanning

/**
 * Turns a stream of Base Number reads into locks. A number locks after [votesRequired] reads; the reads
 * need not be consecutive, so frames with no read cost nothing. Once a number locks it is ignored until
 * a different number is read, or until [clearGapMillis] passes with no read (the card has been swapped).
 * Time is passed in so scripted sequences are testable.
 */
class BaseNumberLocker(
    private val votesRequired: Int = DEFAULT_VOTES,
    private val clearGapMillis: Long = DEFAULT_CLEAR_GAP_MILLIS,
) {
    private val votes = mutableMapOf<String, Int>()
    private var lastReadAt: Long? = null
    private var ignored: String? = null

    /** The votes counted so far, for diagnostics; a lock clears them. */
    fun currentVotes(): Map<String, Int> = votes.toMap()

    /** Returns the Base Number if this read locks it, else null. */
    fun read(baseNumber: String, atMillis: Long): String? {
        val last = lastReadAt
        if (last != null && atMillis - last >= clearGapMillis) {
            votes.clear()
            ignored = null
        }
        lastReadAt = atMillis

        if (ignored != null) {
            if (baseNumber == ignored) return null
            ignored = null
        }

        val count = (votes[baseNumber] ?: 0) + 1
        if (count < votesRequired) {
            votes[baseNumber] = count
            return null
        }
        votes.clear()
        ignored = baseNumber
        return baseNumber
    }

    /**
     * The collector has dealt with the last lock (added, cancelled or skipped it). Reads that arrived
     * while they were deciding never reached the locker, so the "no read" gap is counted from now.
     */
    fun verdict(atMillis: Long) {
        lastReadAt = atMillis
    }

    companion object {
        /** How many reads of one Base Number lock it. Tune here. */
        const val DEFAULT_VOTES = 3
        const val DEFAULT_CLEAR_GAP_MILLIS = 1_500L
    }
}
