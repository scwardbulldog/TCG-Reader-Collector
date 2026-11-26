# TCG Collection

A personal inventory of physical trading cards (One Piece first; Dragon Ball and Gundam later), identified by scanning and kept per language.

## Language

**Game**:
A trading card game whose cards are catalogued, e.g. One Piece Card Game.
_Avoid_: TCG, franchise, series

**Collection**:
The collector's personal inventory of **Owned Copies**. A **Game**'s Collection is the subset whose **Printings** belong to that Game.

**Catalog**:
Every **Printing** of a **Game** that the app knows about, in every supported **Language**; scans and searches are matched against it.
_Avoid_: Card database, card list, set list

**Printing**:
One specific catalog card within a **Game** and **Language**, as listed in the **Catalog**; several Printings can share one **Base Number**.
_Avoid_: Card (ambiguous), SKU

**Printing identifier**:
The stable reference to a **Printing**: its **Game**, **Language**, and the source's own card-list ID, e.g. onepiece / EN / OP01-001_p1.
_Avoid_: Card ID, SKU

**Unresolved Printing**:
A **Printing** that an **Owned Copy** refers to by **Printing identifier** only, because the **Catalog** has no record of it (e.g. after a restore).
_Avoid_: Orphan, missing card

**No longer in the Catalog**:
The flag on a **Printing** whose **Owned Copies** remain after a **Catalog** refresh dropped it. Its last-known details are kept and shown; unlike an **Unresolved Printing**, the app once had its details.
_Avoid_: Deleted, removed, orphan

**Base Number**:
The number printed on a card within a **Game**, e.g. OP01-001 in One Piece. In One Piece, the **Printings** of the same card share a Base Number across **Variants** and **Languages**.
_Avoid_: Card ID, set number

**Language**:
The language edition a **Printing** was published in (English, Japanese); the same **Base Number** in two Languages is two **Printings**.
_Avoid_: Region, locale

**Variant**:
What sets a **Printing** apart from others sharing its **Base Number** and **Language**: Normal, Alternate Art, or **Reprint**.
_Avoid_: Version, rarity, treatment

**Alternate Art**:
A **Variant** with different artwork or finish from the Normal **Printing** (parallel, manga, SP, promo-stamped and similar).
_Avoid_: Parallel (too narrow), alt

**Reprint**:
A **Variant** re-releasing a **Printing**'s artwork in a later product; tracked as its own **Printing**.
_Avoid_: Re-release, duplicate

**Release Product**:
The set, pack, or other product associated with a specific **Printing**'s release, not the product implied by its **Base Number**. A **Reprint** belongs to its own Release Product.
_Avoid_: Set number

**Owned Copy**:
One physical card the collector holds, tied to exactly one **Printing**, with its own condition, **Location**, and notes; how many of a Printing are owned is the count of its Owned Copies.
_Avoid_: Card (ambiguous), item, entry, holding

**Location**:
Where an **Owned Copy** is physically kept, e.g. "Binder 3" or "Bulk box A".
_Avoid_: Storage, container, bin

**Scan Session**:
A run of consecutive scans for one **Game** and one collector-chosen **Language**.
_Avoid_: Batch, import
