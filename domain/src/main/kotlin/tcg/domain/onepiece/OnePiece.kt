package tcg.domain.onepiece

/** One Piece-specific rules. Other Games do not inherit Bandai's conventions. */
object OnePiece {
    // OCR often swaps look-alike characters, so digit positions also accept letters that resemble digits.
    private const val DIGIT = "[0-9OQDIL|!SBZ]"
    private const val SEPARATOR = "\\s*[-\u2010\u2011\u2012\u2013\u2014\u2015\u2212~_=.]?\\s*"
    private val setNumber = Regex("(?<![A-Z0-9])(PR[B8]|[O0Q]P|[S5][T7]|E[B8])\\s*($DIGIT{2})$SEPARATOR($DIGIT{3})(?![0-9])")
    private val promoNumber = Regex("(?<![A-Z0-9])P\\s*[-\u2010\u2011\u2012\u2013\u2014\u2015\u2212~_=.]\\s*($DIGIT{3})(?![0-9])")

    private val typedSetNumber = Regex("(PRB|OP|ST|EB)(\\d{2})-?(\\d{3})")
    private val typedPromoNumber = Regex("P-?(\\d{3})")

    /** A typed Base Number written without its hyphen, in any case (`op01001`, `p113`), as printed (`OP01-001`, `P-113`); null if [text] is not one. */
    fun typedBaseNumber(text: String): String? {
        val compact = text.filterNot { it.isWhitespace() }.uppercase()
        typedSetNumber.matchEntire(compact)?.let { return "${it.groupValues[1]}${it.groupValues[2]}-${it.groupValues[3]}" }
        typedPromoNumber.matchEntire(compact)?.let { return "P-${it.groupValues[1]}" }
        return null
    }

    /** The Base Number printed in one line of OCR text (e.g. `OP01-001`, `P-001`), or null if it has none. */
    fun baseNumberIn(text: String): String? {
        val upper = text.uppercase()
        setNumber.find(upper)?.let { match ->
            val prefix = when (match.groupValues[1][0]) {
                'P' -> "PRB"
                'O', '0', 'Q' -> "OP"
                'S', '5' -> "ST"
                else -> "EB"
            }
            return "$prefix${digitsOf(match.groupValues[2])}-${digitsOf(match.groupValues[3])}"
        }
        promoNumber.find(upper)?.let { match -> return "P-${digitsOf(match.groupValues[1])}" }
        return null
    }

    private fun digitsOf(text: String): String = text.map {
        when (it) {
            'O', 'Q', 'D' -> '0'
            'I', 'L', '|', '!' -> '1'
            'S' -> '5'
            'B' -> '8'
            'Z' -> '2'
            else -> it
        }
    }.joinToString("")
}
