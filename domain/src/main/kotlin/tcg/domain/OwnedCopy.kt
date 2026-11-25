package tcg.domain

enum class Condition { NearMint, LightlyPlayed, ModeratelyPlayed, HeavilyPlayed, Damaged }

@JvmInline
value class LocationId(val value: Long)

@JvmInline
value class OwnedCopyId(val value: Long)

/** Where Owned Copies are physically kept. Renaming applies to every Owned Copy in it. */
data class Location(val id: LocationId, val name: String)

/** One physical card. There is no quantity: counts are derived by grouping on [printing]. */
data class OwnedCopy(
    val id: OwnedCopyId,
    val printing: PrintingIdentifier,
    val condition: Condition,
    val location: LocationId?,
    val notes: String,
)

data class NewOwnedCopy(
    val printing: PrintingIdentifier,
    val condition: Condition = Condition.NearMint,
    val location: LocationId? = null,
    val notes: String = "",
)
