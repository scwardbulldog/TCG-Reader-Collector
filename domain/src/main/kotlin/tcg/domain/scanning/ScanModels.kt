package tcg.domain.scanning

import tcg.domain.Game
import tcg.domain.Language

/** What a Scan Session is scoped to: one Game and one collector-chosen Language. */
data class ScanScope(val game: Game, val language: Language)

/** One OCR read of a Base Number, as printed on the card (e.g. `OP01-001`). */
data class BaseNumberRead(val baseNumber: String)
