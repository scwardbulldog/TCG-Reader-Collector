# TCG Collection

A personal inventory of physical trading cards (One Piece first; Dragon Ball and Gundam later), identified by scanning and kept per language.

## Language

**Game**:
A trading card game whose cards are catalogued, e.g. One Piece Card Game.
_Avoid_: TCG, franchise

**Catalog**:
Every **Printing** of a **Game** that the app knows about, in every supported **Language**; scans and searches are matched against it.
_Avoid_: Card database, card list, set list

**Printing**:
One specific catalog card within a **Game**, identified by its card number, **Language**, and **Variant**.
_Avoid_: Card (ambiguous), SKU

**Language**:
The language edition a **Printing** was published in (English, Japanese, Chinese); the same card number in two Languages is two **Printings**.
_Avoid_: Region, locale

**Variant**:
The treatment that distinguishes **Printings** sharing a card number and **Language**, e.g. normal vs. parallel/alt-art.
_Avoid_: Version, rarity

**Owned Copy**:
A physical card the collector holds, tied to exactly one **Printing**, with quantity, condition, **Location**, and notes.
_Avoid_: Card (ambiguous), item, entry

**Location**:
Where an **Owned Copy** is physically kept, e.g. "Binder 3" or "Bulk box A".
_Avoid_: Storage, container, bin

**Scan Session**:
A run of consecutive scans that all share one collector-chosen **Language**.
_Avoid_: Batch, import
