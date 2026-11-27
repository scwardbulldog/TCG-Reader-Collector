# Printing scenarios in One Piece, and which sources cover them

Research for [#52](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/52), under the v2 map. It extends [#45](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/45) (third-party enrichment) and [#43](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/43) (**Custom Printing**) and does not redo them. Terms follow [GLOSSARY.md](../../GLOSSARY.md); identity is [ADR 0002](../adr/0002-printing-key-game-language-bandai-id.md); the file format is [catalog-format.md](../catalog-format.md).

Card data is Bandai's. This note quotes a handful of IDs only. Counts come from the Catalog files in the private `TCG-Catalog` repo, generated 2026-11-27 (EN 5,118 Printings, JP 5,270), which was built before the pipeline change of [#50](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/50), so it still lists `OP07-091_p1` once. Third-party facts were fetched on 2026-11-27 and will drift.

## Sources

| Source | What it is | Primary? |
|---|---|---|
| Bandai official card lists: [EN](https://en.onepiece-cardgame.com/cardlist/), [JP](https://www.onepiece-cardgame.com/cardlist/) | Owner of the IDs, names, rarities, and the "Information Obtained" (入手情報) field per listing. Source of the Catalog. | Yes |
| [optcgapi.com](https://optcgapi.com/documentation) | Free JSON API, EN only. Endpoints for sets, starter decks, promos, Don!!. | Third party |
| [Limitless](https://onepiece.limitlesstcg.com/cards/OP06-068) | Card pages with EN/JP language switch, a variant table per card, TCGplayer/Cardmarket prices. | Third party |
| [onepiece.app](https://onepiece.app/) | Named in #52. `onepiece.app/cards/ST12-003` returned 404, so it is not covered below. | Third party |
| [optcg.app](https://optcg.app/series/), [one-piece-tcg.com](https://one-piece-tcg.com/en/catalog) | Seen in search only; their catalogues label rows "Alternate Art" and "Reprint". Not examined. | Third party |

## What an ID looks like (Bandai)

Checked against the Catalog files and the live lists.

- Shapes: `OP01-001`, optionally `_pN` (parallel, promo, and anything printed again in another product) or `_rN` (Reprint). Prefixes: `OP`, `ST`, `EB`, `PRB`, and `P-` for promos. No other shape exists in either file.
- Every EN Base Number has a plain-ID Printing; JP has two exceptions (`P-081`, `P-082`, which start at `_p1`).
- `_pN` numbering has gaps (EN 122 Base Numbers, JP 89), so the suffix is neither a count nor stable across Languages.
- `_rN` is used only for Printings in the PRB-01/02 reprint boosters and the Starter Decks ST-15 to ST-36 (EN 20 products, JP 21).
- A Base Number plus a Release Product does not identify a Printing: 909 EN and 910 JP such pairs hold several IDs, for example `EB01-001` and `EB01-001_p1` are both in EB-01 (normal and parallel in the same product). The Variant has to come from the ID suffix.
- No two IDs in a file share an image URL.

## Scenarios

"Example" IDs are real. "Bandai" means the official lists (the Catalog's source).

| # | Scenario | Example | Bandai | optcgapi | Limitless |
|---|---|---|---|---|---|
| 1 | **Normal** Printing in its home product | EN `OP01-001` | Plain ID | Plain ID, set endpoints | First row of the variant table |
| 2 | **Alternate Art** in the same product (parallel) | EN `OP01-001_p1`; `EB01-001` + `EB01-001_p1` both in EB-01 | `_pN`, same product text | `_pN` IDs, name suffix such as "(Alternate Art)" | Extra variant row |
| 3 | Alternate Art with a different finish or illustration class (SP, manga, Treasure Rare, full art, Jolly Roger foil) | EN `OP05-119_p6` (rarity "Special"), EN `OP07-109_p2` (TreasureRare) | `_pN`; the kind is only visible through rarity (`Special`, `TreasureRare`) and the picture. No explicit "manga" field. | Name suffix such as "(Full Art)", "(Jolly Roger Foil)" | Variant labels such as "fa", "jr" |
| 4 | **Reprint** in a later product | EN `OP06-068_r1` and `_p2`, `_p3` in PRB-01 | The same card appears as `_rN` (Reprint) or as `_pN` (a parallel in the new product). Both exist for the same card. | `_rN` kept for Bandai ones | Shown as "reprinted in" with new variant rows |
| 5 | **Parallel printed again** in a later product | EN `OP05-119_p4` (PRB-01), `_p6` (OP-09), `_p7`, `_p8` (OP-11) | One `_pN` per product and finish, numbered in listing order | `_pN` | Variant rows |
| 6 | Promo with its own number | EN `P-001` plus `_p1`..`_p6` | `P-NNN`; the real product is only in "Information Obtained" (the four generic promo packs, see catalog-format.md) | Promo endpoint, `_prN` IDs | Variant rows named by product |
| 7 | Promo of a **regular** Base Number | EN `OP07-091_p1` (Offline Regional 2024 Vol. 3), JP `ST12-003_p1` (V Jump application service) | `_pN`, rarity kept (`R`, `SuperRare`), not "Promo". 390 of 570 EN and 523 of 732 JP non-booster Printings are of this kind. | Promo endpoint, ID `<base>_prN` | Variant row by product |
| 8 | One promo ID, several products | EN `OP07-091_p1` listed as Finalist Card Set and as Participation Pack, same ID and image (verified on the live EN list, packs 569801 and 569901) | One ID listed twice | Two entries, `_pr4` and `_pr5` ("[Finalist]" in the name) | Merged into one row, "Regional Participation Pack 2024 Vol.3" |
| 9 | Same card, same ID, reused for another product | EN `PRB02-012` Welcome Pack 2026 Vol.1 (#45) | Not listed. `PRB02-012` and `_p1` are both PRB-02 | Welcome Pack entry with ID `PRB02-012`, the normal card's ID | Not checked |
| 10 | Box, milk and event stamps | JP `OP06-068` Milk Box Stamp (#43) | Not listed: the live JP list has `OP06-068`, `_p1`, `_p2`, `_r1` only | No promo entry for OP06-068; the sets endpoint has the Bandai PRB Printings only | JP page showed fewer variants than Bandai's JP list (base + 2 against base + 3) and no stamp |
| 11 | Printing Bandai never lists (non-stamp) | EN `ST12-003` Illustration Box Vol.4 (#45) | Only the plain Starter Deck listing exists in EN | `ST12-003_pr1` "(Illustration Box Vol.4)"; also a "Regionals Finalist Card Set 26-27" entry whose ID is the plain `ST12-003` | A "Misc. Promos" row for the Illustration Box and a "Regional Participation Pack 2026 Vol.1" row |
| 12 | Printing listed by Bandai in only one Language | JP `ST12-003_p1`, JP `OP05-119_p8` ("English 2nd ANNIVERSARY SET" product in the JP list) | JP-only listing | EN only, so absent | Not checked |
| 13 | Base Number in one Language only | 47 JP-only Base Numbers, all `P-`, for example `P-038`. No EN-only Base Number exists. | JP list only | Absent | Not checked |
| 14 | Printing counts differ per Language for the same Base Number | `EB01-003`: EN 6 Printings, JP 4 (354 of 2,969 shared Base Numbers differ) | Listed per Language | EN only | Not checked |
| 15 | Rarity differs per Language for the same Base Number | `OP07-109`: EN has a TreasureRare Printing, JP has none (21 Base Numbers differ in rarity set) | Listed per Language | n/a | n/a |
| 16 | Suffix number differs per Language | `OP01-001`: in EN `_p1` is the OP-01 parallel and `_p2` the 25th Edition card; in JP `_p1` is the 25th Edition card and `_p2` the OP-01 parallel (ADR 0002). `OP05-119` PRB-01 parallel is EN `_p4`, JP `_p3`. | Per Language | n/a | n/a |
| 17 | ID collision across products that Bandai maps to one ID | Scenario 8, handled by `<base>@<packId><suffix>` since #50 | See 8 | n/a | n/a |

Notes on the table:

- "Not checked" and "n/a" mean I did not look or the source has no equivalent; they are not claims about absence.
- Scenarios 3, 5 and 6 are the same fact seen three ways: Bandai's suffix says only "another Printing". Which kind it is (parallel, manga, foil, promo) cannot be read from the ID.
- Scenarios 9 to 11 are the **Custom Printing** cases from #43; third-party sources partly cover them (9 and 11) and none covers the JP stamp (10).

## Source coverage

| Capability | Bandai lists | optcgapi | Limitless |
|---|---|---|---|
| Languages | EN and JP, separate lists | EN only | EN and JP pages; the JP page showed fewer variants than Bandai's JP list for `OP06-068`; prices are market prices |
| Stable per-printing ID | Yes, `_pN`/`_rN`, per Language | `_pN`/`_rN` for sets, `_prN` for promos (not Bandai's) | URL `?v=N` index per card; not stable |
| Release Product | "Information Obtained" text per listing | `set_name` per entry, "One Piece Promotion Cards" for all promos, product in the card name | Variant row title |
| Variant kind | Not stated; rarity only | In the name: "(Alternate Art)", "(Full Art)", "(Jolly Roger Foil)" | Variant label |
| Never-listed Printings | No | Some (Illustration Box, Welcome Pack) | Some (Illustration Box) |
| Same-ID-two-products | Yes, lists both | Two entries | Merges into one |
| Name | One per Language | English, with "(091)" or "(OP06-068)" suffixes for disambiguation (`OP07-091`: "Monkey.D.Luffy (091)") | One |
| Images | Bandai's, per ID | Own copies (Bandai artwork) | CDN copies (Bandai artwork) |
| Access | HTML, scraped (vegapull fork) | Free JSON, no auth | HTML, no API |

Hence the earlier #45 conclusion stands: third-party sources fill scenarios 9 and 11 only, are EN-only (optcgapi), and need product-name matching to map their IDs onto Bandai's. They also disagree with each other on the Regional Finalist and Participation case and on `ST12-003`, so none is a safe source of record.

## Card name across Variants and Languages

Measured on the Catalog files (Bandai's own `name` field).

- **Within one Language, the name is a function of the Base Number.** In EN, no Base Number with several Printings has two different names (1,144 checked). In JP, 19 of 1,210 differ, and every one is a spelling or width variation, not a different card: `モンキー・D・ルフィ` vs `モンキー・Ｄ・ルフィ` (full-width D), `チャカ＆ペル` vs `チャカ&ペル`, `シャーロット・フランぺ` vs `シャーロット・フランペ` (ぺ/ペ). After Unicode NFKC and a ぺ/ペ fix, zero differ.
- **A name is not a key.** 2,969 EN Base Numbers share 1,235 distinct names; `Monkey.D.Luffy` is the name of 88 Base Numbers. A name can label a Printing but never identify one.
- **Across Languages, names are translations, so equality cannot link them.** The link is the Base Number. EN to JP name pairs are one-to-one except for spelling drift (`Pekoms`: `ペコムズ`/`ぺコムズ`; `Tot Musica`, where one JP Printing uses the Latin text) and spacing variants in EN (`Mr. 9` vs `Mr.9`, `Zephyr (Navy)` vs `Zephyr(Navy)`).
- **Third-party names differ from Bandai's.** The Bandai EN list carries no "(Alternate Art)" in names; optcgapi adds "(Alternate Art)" and "(091)" itself. Do not compare names across sources.

So a Base-Number name link is safe **inside a Language** (take the name from any Printing of that Base Number and Language). It breaks when:

1. The Base Number has no Printing in that Language: 47 `P-` Base Numbers exist in JP only, so an EN **Custom Printing** of one has no name to borrow (the reverse case is empty today).
2. The name must match between Languages: use the Base Number, not the name.
3. The source is not Bandai's list: third-party names carry disambiguation suffixes.
4. Spelling drift: compare after NFKC normalisation if names are ever compared; never use a name for identity.
5. Names change over a card's life (errata, translation fixes): not measured here; none of the 1,144 multi-Printing EN Base Numbers shows it in one snapshot.

## What the model must be able to represent

1. A **Printing** key of Game + Language + opaque ID, with the Variant read from the ID suffix (ADR 0002 stands). IDs can be Bandai's, pipeline-derived (`@packId`), or locally generated (Custom).
2. Several Printings per Base Number **and** per Release Product (909 EN pairs), so Release Product must not be part of the identity.
3. Release Products that are free text and may be in a different language from the **Language** of the Printing (`OP05-119_p8` in the JP list is an English-edition anniversary product).
4. Variants that are not distinguishable from the ID: parallel, manga, SP, foil, promo stamp. If the app wants a kind, it must be a detail (rarity gives only `Special`/`TreasureRare`), not derived from the suffix.
5. Per-Language differences for one Base Number: different Printing counts, different rarities, different suffix numbering, and Base Numbers that exist in one Language only.
6. Promo Printings of regular Base Numbers whose rarity is not "Promo" (390 EN, 523 JP): a promo filter must use Release Product or product class, not rarity.
7. Two products behind one Bandai ID (scenario 8), which the Catalog already splits.
8. Printings Bandai never lists (scenarios 9 to 11): a **Custom Printing** with a locally generated ID and the Base Number, Language and label (#43). Its name can default from a sibling Printing of the same Base Number and Language when one exists, else the collector enters it (the 47 JP-only `P-` numbers).
9. A later Bandai listing of the same card does not remap (ADR 0002): a Custom Printing and a Catalog Printing can coexist for the same physical card, and the Collection must tolerate that.
10. Names are details attached to the Printing; the Base Number name link is a convenience within one Language, never an identity.

## Not verified

- Limitless and optcgapi pages change daily; rows above were read once on 2026-11-27.
- onepiece.app (404 on the tested URL), optcg.app and one-piece-tcg.com were not examined.
- Bandai's rules for `_pN` versus `_rN` assignment are not documented; the description above is inferred from the data.
- No ToS page was found for optcgapi (as in #45); its documentation page only asks users not to make "an insane amount of API calls".
- The JP Milk Box Stamp and Welcome Pack facts come from #43 and #45; this research confirms only that Bandai's JP list lacks the stamp for `OP06-068`.
