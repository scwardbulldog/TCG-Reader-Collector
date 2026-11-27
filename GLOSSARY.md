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

**Card**:
What all **Printings** sharing a **Game** and **Base Number** have in common, across **Languages** and **Variants**. It has no record of its own: it exists wherever at least one **Printing** does, including a **Custom Printing**.
_Avoid_: Card ID, Card number (that is the **Base Number**)

**Card name**:
What a **Card** is called in a **Language**, read from that Language's **Printings** (Normal preferred). Never an identifier. A Card with no **Printing** in a Language has no name in it, so a JP-only Card has only its Japanese name.
_Avoid_: Translation (no name is machine-translated), Printing label (that is a **Custom Printing**'s own text)

**Printing**:
One specific printed version of a **Card** within a **Game** and **Language**, as listed in the **Catalog**; several Printings can share one **Base Number**.
_Avoid_: Card (that is the level above), SKU

**Printing identifier**:
The stable reference to a **Printing**: its **Game**, **Language**, and the source's own card-list ID, e.g. onepiece / EN / OP01-001_p1.
_Avoid_: Card ID, SKU

**Unresolved Printing**:
A **Printing** that an **Owned Copy** refers to by **Printing identifier** only, because the **Catalog** has no record of it (e.g. after a restore).
_Avoid_: Orphan, missing card

**Custom Printing**:
A **Printing** the collector recorded themselves because the **Catalog** has none for a card they hold, with the **Base Number**, **Language** and a free-text label they chose. It is part of the **Collection**, not the **Catalog**; unlike an **Unresolved Printing**, its details are the collector's own.
_Avoid_: Manual card, homebrew, user-added card

**No longer in the Catalog**:
The flag on a **Printing** whose **Owned Copies** remain after a **Catalog** refresh dropped it. Its last-known details are kept and shown; unlike an **Unresolved Printing**, the app once had its details.
_Avoid_: Deleted, removed, orphan

**Base Number**:
The number printed on a card within a **Game**, e.g. OP01-001 in One Piece; it identifies a **Card**. In One Piece, the **Printings** of the same **Card** share a Base Number across **Variants** and **Languages**.
_Avoid_: Card ID, set number

**Language**:
The language edition a **Printing** was published in (English, Japanese); the same **Base Number** in two Languages is two **Printings**.
_Avoid_: Region, locale

**Variant**:
What sets a **Printing** apart from others sharing its **Base Number** and **Language**: Normal, Alternate Art, or **Reprint**.
_Avoid_: Version, rarity

**Alternate Art**:
A **Variant** with different artwork or finish from the Normal **Printing**; its **Treatment** says which (parallel, manga, SP, promo-stamped and similar).
_Avoid_: Parallel (too narrow), alt

**Reprint**:
A **Variant** re-releasing a **Printing**'s artwork in a later product; tracked as its own **Printing**.
_Avoid_: Re-release, duplicate

**Treatment**:
How a **Printing** looks beyond its **Variant**: zero or more tags such as Parallel, Manga, SP, Wanted Poster, a foil kind, or Stamped. No Treatment means unknown, not plain.
_Avoid_: Variant (that is how a **Printing** relates to others sharing its **Base Number** and **Language**), finish

**Rarity**:
The rarity code Bandai prints on a **Printing**, e.g. SR or SP CARD. Bandai's code wins whenever another source disagrees.
_Avoid_: Treatment

**Release Product**:
The set, pack, or other product associated with a specific **Printing**'s release, not the product implied by its **Base Number**. A **Reprint** belongs to its own Release Product. It may carry a **Product code** such as OP-01, used for sorting and grouping, never for identity.

**Product code**:
Bandai's short code for a **Release Product**, e.g. OP-01. Promos and other products may have none.
_Avoid_: Set number, Base Number

**Owned Copy**:
One physical card the collector holds, tied to exactly one **Printing**, with its own condition, **Location**, and notes; how many of a Printing are owned is the count of its Owned Copies.
_Avoid_: Card (that is what its **Printing** is a version of), item, entry, holding

**Location**:
Where an **Owned Copy** is physically kept, e.g. "Binder 3" or "Bulk box A".
_Avoid_: Storage, container, bin

**Scan Session**:
A run of consecutive scans for one **Game**. It has a current **Language** that the collector can change mid-session.
_Avoid_: Import

**Bulk Add**:
The **Scan Session** mode where the collector snaps one photo per card and reviews them all once at the end, instead of confirming each scan. Nothing is added until "Add all".
_Avoid_: Batch scan, import

**Batch**:
One **Bulk Add** run's set of snapped cards, shown as review rows. It is discarded unless "Add all" is tapped.
_Avoid_: Scan Session (that is the whole run of scans), import
