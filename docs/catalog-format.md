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

- `id` is Bandai's ID and is opaque. It is the Printing's identity and is unique within a file. A card Bandai lists in several packs is one Printing.
- `baseNumber` is `id` without a Variant suffix (`_pN` Alternate Art, `_rN` Reprint).
- `releaseProduct` is the pack title the Printing was listed in. When listed in several packs, the lowest pack ID wins.
- `printings` is sorted by `id`, so unchanged data gives a byte-identical file.
- `language` is `en` or `jp`, mapping to the domain `Language` values English and Japanese. `game` is `onepiece`, mapping to `Game.OnePiece`.

A refresh replaces the Catalog only. It never touches Owned Copies (see [ADR 0003](adr/0003-phone-only-storage-no-backend.md)); Printing identity is explained in [ADR 0002](adr/0002-printing-key-game-language-bandai-id.md).

## Publish gate

The pipeline refuses to publish (leaving the previous file untouched) when the new Catalog is empty, when the previous file is unreadable, or when it has more than 10% fewer Printings than the published file.
