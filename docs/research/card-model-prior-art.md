# How other card databases model card, printing and language

Research for [#53](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/53). Sources were read on 2026-11-27 and are limited to official docs, API references and live API responses from the projects themselves. Statements that are the author's inference, not the source's, are marked **(inference)**. No card data is reproduced; only field names and identifier shapes.

Current model for comparison (see [GLOSSARY.md](../../GLOSSARY.md) and [ADR 0002](../adr/0002-printing-key-game-language-bandai-id.md)): a **Printing** is keyed by Game + Language + Bandai's opaque card-list ID (e.g. `OP01-001_p1`); **Base Number** (`OP01-001`), Release Product, rarity, name and image are details; the same Base Number across Variants and Languages is several Printings.

## Per-source summaries

### Scryfall (Magic: The Gathering)

Sources: [Card objects](https://scryfall.com/docs/api/cards), [Languages](https://scryfall.com/docs/api/languages), [GET /cards/:code/:number(/:lang)](https://scryfall.com/docs/api/cards/collector), [Bulk data](https://scryfall.com/docs/api/bulk-data).

- **Hierarchy:** two levels but one object type. A "card object" is a *print*; the abstract card is the `oracle_id` ("unique ID for this card's oracle identity ... consistent across reprinted card editions"). `prints_search_uri` links a print to all prints of the same card. Sets are separate objects (`set`, `set_id`).
- **Identity:** print = `id` (UUID). Human key is `set` + `collector_number` + `lang` (the collector endpoint is `/cards/:code/:number(/:lang)`). Card = `oracle_id`.
- **Language:** a field (`lang`) on every print, "a language code for this printing". A print in another language is its own object with its own `id`, same `oracle_id`.
- **Translations of a name:** `name` is the English/Oracle name; the translation is `printed_name` (plus `printed_text`, `printed_type_line`), "the localized name printed on this card, if any". Translations are linked by sharing `oracle_id` (and typically set + collector number), not by a translation table. The docs state Oracle text is always English and printed text is a translation "at the time the card was printed".
- **Variants:** attributes of the print: `promo`, `promo_types`, `frame_effects`, `full_art`, `finishes` (foil/nonfoil/etched), `variation` + `variation_of` (points at the print this one varies from), `reprint` (boolean), `illustration_id` (stable across reprints of the same artwork), `released_at`.
- **Missing or partial data:** `printed_name` is nullable; the docs say "our support for multiple languages in older sets is limited". Two bulk files encode the policy: *Default Cards* = "every card object ... in English or the printed language if the card is only available in one language"; *All Cards* = every language. Unofficial languages (Phyrexian, Klingon, ...) are included as normal `lang` values.
- **External IDs:** `tcgplayer_id`, `cardmarket_id`, `mtgo_id`, `arena_id` stored as nullable fields.

### Pokémon TCG API (pokemontcg.io)

Sources: [Card object](https://docs.pokemontcg.io/api-reference/cards/card-object/), [Set object](https://docs.pokemontcg.io/api-reference/sets/set-object/), [Overview](https://docs.pokemontcg.io/).

- **Hierarchy:** Set → Card. The card embeds its `set` object and has a `number` (string) within the set. No separate abstract "card" level exists in the documented schema.
- **Identity:** card `id` (string, "unique identifier"); set `id` (e.g. `swsh1` in the docs' sample). The set-relative key is set + `number`; the set object distinguishes `printedTotal` (number printed on the card) from `total` (including secret rares, alternate art).
- **Language:** none documented. Neither the card nor the set object has a language field, and the set's `releaseDate` is "released (in the USA)". **(inference)** The API is effectively English-only.
- **Variants:** not separate cards. Finishes appear as price buckets under `tcgplayer.prices` (`normal`, `holofoil`, `reverseHolofoil`, `1stEditionHolofoil`, `1stEditionNormal`); `rarity` is a free string such as "Rare Rainbow". Alternate art is a separate card with its own `number`, counted in the set's `total`.
- **Translations / missing data:** not addressed.

### TCGdex (multilingual Pokémon API)

Sources: [Home](https://tcgdex.dev/), [Card object](https://tcgdex.dev/reference/card), [Single card](https://tcgdex.dev/rest/card), [TypeScript SDK](https://tcgdex.dev/sdks/typescript), [REST cards](https://tcgdex.dev/rest/cards).

- **Hierarchy:** Serie → Set → Card; `variants` is a block of booleans on the card (`normal`, `reverse`, `holo`, `firstEdition`; the sample also shows `wPromo`).
- **Identity:** card `id` (e.g. `swsh3-136`) = set id + `localId` ("card number within its set").
- **Language:** a *request-scoped* parameter, not a field on the card. The language is in the URL (`https://api.tcgdex.net/v2/en/cards`) and the SDK is constructed with it (`new TCGdex('en')`). "The whole API is available in more than 10 languages"; "each language has different completion levels" and a status page tracks it.
- **Translations:** each language endpoint returns the full object with a localized `name`, `description`, set name, booster names, etc. The shared `id` is what links the translations. **(inference)** from the documented examples; the docs do not state that ids are identical across languages for every set.
- **Missing data:** handled as per-language completion status, plus an omitted-when-empty field convention.

### YGOPRODeck (Yu-Gi-Oh!)

Source: [API guide v7](https://ygoprodeck.com/api-guide/).

- **Hierarchy:** Card → `card_sets` (printings) and `card_images` (artworks), all nested under one card object. A separate `cardsets.php` lists sets (`set_name`, `set_code`, `num_of_cards`, `tcg_date`).
- **Identity:** card `id` = the 8-digit passcode (printed on the card; a separate `konami_id` is exposed via `misc=yes`). A printing is an entry in `card_sets` identified by `set_code` (the guide's samples show `BLRR-EN084`, `MRD-098`, `MRD-E098`, `MRD-EN098`) plus `set_rarity`. Alt-art images are extra entries in `card_images`, each with its own `id`.
- **Language:** two mechanisms. (1) The *printing* language is embedded in the `set_code` region part (`-EN`, `-E`, or none in the examples). (2) The card *text* language is request-scoped: `&language=fr|de|it|pt` on `cardinfo.php`, default English. The guide says images "are only stored in English" and that newly revealed Japan-only cards are English-only "until the official translation is released".
- **Translations:** the same card `id` identifies the card in every language; `name`/`desc` are localized. `misc=yes` adds `beta_name` ("old/temporary/translated name").
- **Missing data:** "a handful of cards may not have a translation yet"; null/empty fields are omitted from the response.

### optcgapi.com (One Piece)

Source: [Documentation](https://optcgapi.com/documentation) and a live response from `GET /api/sets/card/OP01-001/` (field names only).

- **Hierarchy:** endpoints are split by product kind: sets, starter decks, promos, DON!!. Each card row carries `set_id`/`set_name` and `card_set_id`.
- **Identity:** `card_set_id` is the Base Number (shared by both rows returned for one number); `card_image_id` is Bandai's per-art ID including the `_p1` suffix, and is what distinguishes the rows. The parallel row has `(Parallel)` appended to `card_name` and its own price pair (`market_price`, `inventory_price`).
- **Language:** no language field in the documented response. **(inference)** English only.
- **Variants:** encoded in the `card_image_id` suffix and in the display name, not in a structured field.
- **Missing data:** read-only mirror ("You can only use GET requests"); no mechanism for user-supplied printings.

### Limitless TCG

Sources: [Developer docs](https://docs.limitlesstcg.com/developer.html), [Tournaments](https://docs.limitlesstcg.com/developer/tournaments.html).

- The documented API is **tournament data only** (tournaments, standings, decklists, pairings). Decklist shape is "(game specific)". No card, printing or language schema is documented, so Limitless is not usable prior art for this question. The only relevant trait is a `game` ID on every tournament with game-specific payloads, similar in spirit to Game being part of our key.

### Bandai's official One Piece card list (the upstream of our Catalog)

Sources: [EN card list](https://en.onepiece-cardgame.com/cardlist/), [JP card list](https://www.onepiece-cardgame.com/cardlist/). Both are HTML pages with no documented API; this is observation of the pages, not an API reference.

- **Hierarchy:** a flat list of card entries filtered by booster (product). Each entry shows the number, rarity and type (`OP17-119 | SEC | CHARACTER`), name, stats, and an "Information Obtained" (入手情報) line naming the product.
- **Identity:** the image file name is the entry's ID: the same Base Number appears once per art with suffixes (`_p2`, `_p3`; the plain ID for the normal print). The page has a "Reprinted Card" show/hide filter, so reprints are first-class entries.
- **Language:** two separate sites (EN and JP), each its own list. No cross-language link is provided; the Base Number is the only shared token. ADR 0002 already records that the `_pN` suffixes differ between languages.

## Comparison

| Source | Levels | Card identity | Printing identity | Language lives | Translation link | Variants | Prints the source lacks |
|---|---|---|---|---|---|---|---|
| Scryfall | print object; abstract card via `oracle_id` | `oracle_id` | `id` UUID; natural key set + collector number + `lang` | field on every print | shared `oracle_id`; `printed_name` | fields on print (`promo_types`, `frame_effects`, `finishes`, `variation_of`) | no mechanism; limited older-language coverage, unofficial languages included |
| Pokémon TCG API | set → card | none | card `id`; set + `number` | not modelled | n/a | price buckets; alt art = separate number | none |
| TCGdex | serie → set → card | none | `id` = set + `localId` | request parameter (URL / SDK) | same `id` per language endpoint | boolean `variants` block | per-language completion status |
| YGOPRODeck | card → `card_sets` + `card_images` | passcode `id` | `set_code` (+ rarity) | in `set_code`; text via `&language=` | same card `id` | rarity per set entry; alt art in `card_images` | English fallback until translated |
| optcgapi | flat rows per product kind | `card_set_id` (Base Number) | `card_image_id` (`_pN`) | not modelled | n/a | suffix + `(Parallel)` in name | none (read-only) |
| Limitless | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| Bandai list | flat, by product | Base Number (implicit) | image ID with `_pN` | separate site per language | none | suffix; reprint filter | n/a (is the source) |
| **This app today** | Catalog → Printing | Base Number (detail) | Game + Language + Bandai ID | in the key and catalog file | Base Number (not a key) | `Variant` from ID suffix | Custom Printing, Unresolved Printing, "No longer in the Catalog" flag |

## Patterns worth noting

1. **Sources with languages keep a card-level key separate from the print-level key.** Scryfall (`oracle_id` vs `id`) and YGOPRODeck (card `id` vs `set_code`) both do; the card-level key is what links translations. Our Base Number plays this role for One Piece without a new table, because Bandai prints it on the card.
2. **Language is either part of the print identity (Scryfall, YGOPRODeck's `set_code`) or a view over shared ids (TCGdex, YGOPRODeck text).** ADR 0002 chose the first, which matches Bandai's per-language `_pN` numbering. The second style only works when ids are language-independent.
3. **Translations are never a separate entity in these sources.** They are fields (`printed_name`) or whole-record localisations. None has a translation table.
4. **None of the surveyed sources lets a user add a printing the source lacks.** They handle gaps by omitting fields, English fallback, or completion status. Custom Printing is our own requirement, not borrowed.
5. **Variants are attributes, not identity,** in the structured sources (Scryfall, TCGdex, Pokémon price buckets). Only optcgapi and Bandai encode them in the ID suffix; we derive Variant from the suffix, as Bandai does.

## Designs applicable to a single-user, phone-only app

These are **(inference)**, drawn from the patterns above and the constraints in [ADR 0003](../adr/0003-phone-only-storage-no-backend.md).

1. **Keep the flat Printing, add a derived "card" view (recommended; Scryfall-lite).** Printing stays keyed by Game + Language + source ID as in ADR 0002. "The same card in other languages" is a query grouping by Game + Base Number, the equivalent of Scryfall's `oracle_id` join, with no extra stored table. Fits a catalog of about 4,000 Printings per language that is replaced wholesale on refresh. Gap: a translated name is only available by looking up the sibling Printing in the other language's file.
2. **Two-level Card + Printing in the catalog file (YGOPRODeck-style).** Add a card level (Game + Base Number) holding names per language, with printings nested beneath it. Gives cross-language name search and avoids repeating text, but changes the catalog format and the pipeline. Only worth it if cross-language search becomes a requirement.
3. **Per-language catalog files as overlays (TCGdex-style).** Already roughly what `catalog/<game>-<language>.json` does. Extending it would mean a shared language-neutral file plus thin per-language name files: a smaller download for a single-language user, but a join and a second refresh path. Probably not justified at current sizes.

For printings the source lacks, no prior art exists. The only transferable idea is Scryfall's `variation_of`, a pointer from a variant back to a base print, which maps to a Custom Printing pointing at an existing Base Number.
