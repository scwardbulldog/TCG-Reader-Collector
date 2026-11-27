# Catalog file format

The Catalog is built by a pipeline in the private `TCG-Catalog` repo and downloaded by the app. This document describes the **format only**. The card data is Bandai's and must never be committed to this public repo.

One file per Language, named `catalog/<game>-<language>.json` (`onepiece-en.json`, `onepiece-jp.json`), minified JSON:

```json
{
  "schemaVersion": 1,
  "game": "onepiece",
  "language": "en",
  "generatedAt": "2026-01-01T00:00:00.000Z",
  "printings": [
    {
      "id": "OP01-001_p1",
      "baseNumber": "OP01-001",
      "releaseProduct": "…",
      "rarity": "…",
      "name": "…",
      "imageUrl": "https://…"
    }
  ]
}
```

Each entry maps onto the domain `Printing` ([Printing.kt](../domain/src/main/kotlin/tcg/domain/Printing.kt)).

- `id` is Bandai's ID, or a derived ID (see the duplicate rule below), and is opaque. It is the Printing's identity and is unique within a file.
- `baseNumber` is `id` without a Variant suffix (`_pN` Alternate Art, `_rN` Reprint).
- `releaseProduct` is the pack title the Printing was listed in. For Printings in the four generic promo packs (EN `Promotion card`, `Other Product Card`; JP `プロモーションカード`, `限定商品収録カード`) it is instead Bandai's "Information Obtained" (入手情報) text, such as `Event Pack Vol.5`, because the pack title says nothing about the real product. If that text is blank or missing, the generic pack title is used.
- When Bandai lists the same ID in several packs, listings with the same `releaseProduct` are one Printing. Bandai's IDs are not always unique per physical card: EN `OP07-091_p1` is listed as both "Offline Regional Finalist Card Set 2024 Vol. 3" and "Offline Regional Participation Pack 2024 Vol. 3" with the same image. Each product becomes its own Printing. The listing from the lowest pack ID keeps Bandai's ID (so existing Owned Copies stay valid) and each other gets `<base>@<packId><suffix>`, here `OP07-091@569901_p1`. The Variant suffix stays last, and `baseNumber` is still `OP07-091`. A listing without "Information Obtained" never splits. If two products share both an ID and a pack ID they cannot get distinct IDs, so the lowest product is kept and the build warns.
- `printings` is sorted by `id`, so unchanged data gives a byte-identical file.
- `language` is `en` or `jp`, mapping to the domain `Language` values English and Japanese. `game` is `onepiece`, mapping to `Game.OnePiece`.

A refresh replaces the Catalog only. It never touches Owned Copies (see [ADR 0003](adr/0003-phone-only-storage-no-backend.md)); Printing identity is explained in [ADR 0002](adr/0002-printing-key-game-language-bandai-id.md).

## Publish gate

The pipeline refuses to publish (leaving the previous file untouched) when the new Catalog is empty, when the previous file is unreadable, or when it has more than 10% fewer Printings than the published file. It also refuses when more than half of the promo Printings lack "Information Obtained", which means Bandai's markup changed.
