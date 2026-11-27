# Catalog file format

The Catalog is built by a pipeline in the private `TCG-Catalog` repo and downloaded by the app. This document describes the **format only**. The card data is Bandai's and must never be committed to this public repo.

One file per Language, named `catalog/<game>-<language>.json` (`onepiece-en.json`, `onepiece-jp.json`), minified JSON:

```json
{
  "schemaVersion": 2,
  "game": "onepiece",
  "language": "en",
  "generatedAt": "2026-01-01T00:00:00.000Z",
  "printings": [
    {
      "id": "OP01-001_p1",
      "baseNumber": "OP01-001",
      "variant": "alternate-art",
      "releaseProduct": "…",
      "productCode": "OP-01",
      "rarity": "…",
      "treatments": ["Parallel"],
      "name": "…",
      "imageUrl": "https://…"
    }
  ]
}
```

Each entry maps onto the domain `Printing` ([Printing.kt](../domain/src/main/kotlin/tcg/domain/Printing.kt)). The app reads only `schemaVersion` 2: a file with any other version, an unknown `variant`, or a missing required field is rejected whole, and the stored Catalog is kept. Adding an optional field does not bump the version (the app ignores unknown keys); changing a field's meaning does. There is no Card-level file: a **Card** is derived by grouping Printings on Game + `baseNumber` across the Language files ([ADR 0004](adr/0004-v2-card-model-derived-card-bandai-authoritative.md)).

- `id` is Bandai's ID, or a derived ID (see the duplicate rule below), and is opaque. It is the Printing's identity and is unique within a file. The app never parses it.
- `baseNumber` is `id` without a Variant suffix (`_pN` Alternate Art, `_rN` Reprint).
- `variant` is `normal`, `alternate-art` or `reprint`, derived by the pipeline from the ID suffix. The app reads it from here and no longer parses the suffix. Required.
- `productCode` is optional: Bandai's bracket code from the pack title (`OP-01`), omitted when there is none. For sorting and grouping only, never identity.
- `treatments` is optional: an array of tags (Parallel, Manga, SP and so on) from a closed vocabulary, omitted when unknown (an empty array is read as unknown too). Absence means unknown, not plain. The app does not check the tags.
- `rarity` is a string holding Bandai's code (`C UC R SR SEC L SP CARD TR P`). Japanese `SPカード` is normalised to `SP CARD`; any other unknown code is passed through with a build warning.
- `imageUrl` has Bandai's `?NNNNNN` cache-buster removed, so a Bandai rebuild no longer changes every URL.
- No `productId` is published.
- `releaseProduct` is the pack title the Printing was listed in. For Printings in the four generic promo packs (EN `Promotion card`, `Other Product Card`; JP `プロモーションカード`, `限定商品収録カード`) it is instead Bandai's "Information Obtained" (入手情報) text, such as `Event Pack Vol.5`, because the pack title says nothing about the real product. If that text is blank or missing, the generic pack title is used.
- When Bandai lists the same ID in several packs, listings with the same `releaseProduct` are one Printing. Bandai's IDs are not always unique per physical card: EN `OP07-091_p1` is listed as both "Offline Regional Finalist Card Set 2024 Vol. 3" and "Offline Regional Participation Pack 2024 Vol. 3" with the same image. Each product becomes its own Printing. The listing from the lowest pack ID keeps Bandai's ID (so existing Owned Copies stay valid) and each other gets `<base>@<packId><suffix>`, here `OP07-091@569901_p1`. The Variant suffix stays last, though the app does not rely on it, and `baseNumber` is still `OP07-091`. A listing without "Information Obtained" never splits. If two products share both an ID and a pack ID they cannot get distinct IDs, so the lowest product is kept and the build warns.
- `printings` is sorted by `id`, so unchanged data gives a byte-identical file.
- `language` is `en` or `jp`, mapping to the domain `Language` values English and Japanese. `game` is `onepiece`, mapping to `Game.OnePiece`.

A refresh replaces the Catalog only. It never touches Owned Copies (see [ADR 0003](adr/0003-phone-only-storage-no-backend.md)); Printing identity is explained in [ADR 0002](adr/0002-printing-key-game-language-bandai-id.md).

## Publish gate

The pipeline refuses to publish (leaving the previous file untouched) when the new Catalog is empty, when the previous file is unreadable, or when it has more than 10% fewer Printings than the published file. It also refuses when more than half of the promo Printings lack "Information Obtained", which means Bandai's markup changed. It warns on an unknown rarity code and fails on an unknown Treatment tag.

## Treatments

Treatments come from a hand-edited overlay in the private `TCG-Catalog` repo, keyed by Language and Printing ID (derived `@packId` IDs included) and matched to Bandai Printings in the pipeline only; the app never calls a third party. An unknown tag fails the build; an entry that matches no Printing is only a warning and publishes nothing. Bandai's data wins over any overlay or other source.

The pipeline's `schemaVersion` 2 output is tracked in the v2 build tracker ([#63](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/63)); the app reader accepts only that version ([#58](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/58)).
