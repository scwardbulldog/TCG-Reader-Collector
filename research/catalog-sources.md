# Catalog sources for One Piece Printings in English, Japanese, and Chinese

Research for [issue #2](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/2) (map: #1).
Vocabulary follows `GLOSSARY.md` (**Printing** = card number + **Language** + **Variant**).
Everything below was checked against the live sources on **2026-11-25**. Counts are a snapshot from that day.

## TL;DR

- **Each Language has an official, free card list with card images, and each lists Variants as separate entries.** Bandai runs the English (EN) and Japanese (JP) lists. The Simplified-Chinese (CN) list is run by Bandai's mainland licensee, which uses the `windoent.com` domains. The CN list is backed by an unauthenticated JSON API. No other source covers all three Languages.
- **Card numbers match across Languages, but Variant IDs and set codes don't.** `OP01-001` is the same base card in EN, JP, and CN. Variant suffixes (`_p1`, `_p2`, …) are numbered separately in each Language, so `_p1` can mean different physical cards in EN and JP. CN uses its own set codes (`OPC-01`) and its own suffix scheme (`_01`, `P`, `SP`, …).
- **Community and marketplace sources are English-first** (optcgapi, apitcg, TCGplayer, Limitless). Some also cover JP (Limitless, punk-records, Yuyu-tei). None of them cover CN.
- **Recommendation:** build the catalog from the three official card lists. Use `buhbbl/punk-records` (a daily JSON dump of the official EN and JP lists) as an EN/JP shortcut. Call the CN `windoent` JSON API directly. Store a Printing as *(Language, source-native ID)* plus a normalized base card number. Don't try to match Variants across Languages by suffix.

## Sources compared

| Source | Languages | Variants / reprints split out? | Images | Access | Bulk? | Update cadence | Cost | Terms / constraints | Prices? |
|---|---|---|---|---|---|---|---|---|---|
| **Official EN card list** – [en.onepiece-cardgame.com/cardlist](https://en.onepiece-cardgame.com/cardlist/) | EN | Yes. Each Printing is its own `<dl id>`: `OP01-001`, `OP01-001_p1` (parallel), `OP03-008_r1` (reprint). Has a "Hide reprint cards" filter. | Yes, PNG per ID (`/images/cardlist/card/<id>.png`) | HTML scrape, one page per product (`?series=569xxx`) | ~62 pages cover everything (5,118 IDs) | Updated with each release (OP-18 already listed) | Free | Bandai site terms: use beyond personal use needs Bandai's permission ([EN](https://www.bandai.co.jp/en/site/notice/), [JP](https://www.bandai.co.jp/site/notice/)). No robots.txt (404). | No |
| **Official JP card list** – [onepiece-cardgame.com/cardlist](https://www.onepiece-cardgame.com/cardlist/) | JP | Same markup and ID scheme as EN, plus rulings Q&A per card | Yes | HTML scrape (`?series=550xxx`, `?freewords=`) | ~64 pages (5,271 IDs) | With each release | Free | Same Bandai terms | No |
| **Official CN (Simplified) card list** – [onepiece-cardgame.cn/cardlist](https://www.onepiece-cardgame.cn/cardlist) | CN (zh-Hans) | Yes, every Printing has its own `id`. Suffix scheme is CN-specific (see below). | Yes, `source.windoent.com/OnePiecePc/Picture/...png` | **JSON API** used by the Vue site: `GET https://webadmin.windoent.com/front/op-public/cardList/cardlist/weblist?page=&limit=` (list) and `.../webInfo/{id}` (detail). No key needed. | List supports `limit=1000`; 6 calls returned all **5,244** rows | With each release (OP-18 listed, records created 2026-11-20) | Free | Undocumented internal API. Same IP-holder copyright footer. No published API terms. | No |
| Official Traditional-Chinese / Asia-English lists – [asia-tc](https://asia-tc.onepiece-cardgame.com/cardlist/), [asia-en](https://asia-en.onepiece-cardgame.com/cardlist/) | zh-Hant, EN (Asia) | Same markup and IDs as the EN/JP Bandai sites | Yes | HTML scrape | Yes | With each release | Free | Bandai terms | No |
| **buhbbl/punk-records** – [GitHub](https://github.com/buhbbl/punk-records) | EN, EN-Asia, JP, zh-HK, zh-TW, FR, TH. **No Simplified CN.** | Yes. Keeps the official IDs (`OP01-001_p2`); one JSON file per Printing under `<lang>/cards/<series>/` | Absolute URL into the official site (`img_full_url`) | `git clone` / raw GitHub | Full dump | Automated commits (latest 2026-11-20) | Free | Pipeline code is AGPL-3.0. Card data is © Bandai et al. README: "respect the terms of service of the data sources". `packs.json` was empty (`[]`) in the snapshot. | No |
| Coko7/vegapull – [GitHub](https://github.com/Coko7/vegapull) | Whatever the official Bandai sites serve | Same as official | Can download images | Rust CLI scraper (GPL-3.0) that produces punk-records | Yes | Run it yourself | Free | Same as official | No |
| **optcgapi.com** – [docs](https://optcgapi.com/documentation) | **EN only** | Partly. Variant is in the name text (`"Roronoa Zoro (001) (Parallel)"`); `card_image_id` = official ID (`OP01-001_p1`) | Re-hosted JPGs | REST, GET-only, no auth | `/api/allSetCards/`, `/api/allSTCards/`, `/api/allPromoCards/` | Per-card `date_scraped` | Free (hobby VPS; "please try not to do an insane amount of API calls") | No formal ToS | **Yes** (`market_price`, `inventory_price`, TCGplayer-style) |
| **apitcg.com** – [docs/openapi](https://docs.apitcg.com/) | EN (TCGplayer catalog). Its "names in other languages" feature is Pokémon-only and excludes Japanese. | TCGplayer "printings" (Normal/Foil). Alt-arts are TCGplayer products. | Yes | REST with `x-api-key` | Paged | Daily | Free tier 1,000 req/mo. $19 for 20k. $99 for 200k. | Account required | **Yes** (TCGplayer daily + history) |
| **Limitless** – [onepiece.limitlesstcg.com/cards](https://onepiece.limitlesstcg.com/cards) | EN + JP toggle | Yes. Lists each print, e.g. "Romance Dawn", "Romance Dawn aa", "Premium Card Collection -25th Edition-" | Yes | Website only. The [Limitless API](https://docs.limitlesstcg.com/developer.html) covers **tournaments only**; keys aren't given for private use. | No | Fast (OP18/EB05 listed) | Free (ad-supported) | robots.txt allows all. No card-data API. | **Yes** (TCGplayer USD + Cardmarket EUR) |
| **TCGplayer API** – [docs](https://docs.tcgplayer.com/docs/getting-started) | EN product line | Yes, as separate products ("Alternate Art") | Yes | REST + keys | Yes | — | — | **"We are no longer granting new API access at this time."** | Yes |
| **Cardmarket** – [API docs](https://apiv2.cardmarket.com/ws/documentation/API:Auth_Overview) and public catalog file [`products_singles_18.json`](https://downloads.s3.cardmarket.com/productCatalog/productList/products_singles_18.json) | Language-agnostic. A product is a card; Language is a property of each seller's listing. | Separate `idProduct` per art, but the same `name` (e.g. two "Roronoa Zoro (OP01-001)") with no Variant label | Not in the file | API is restricted to **professional sellers, with manual approval**. The catalog JSON downloads publicly (game 18 = One Piece, ~2.3 MB, regenerated daily). | Yes (file) | Daily (`createdAt`) | Free file | No published licence for the file | Yes (API / price-guide file) |
| **Yuyu-tei** (JP retailer) – [yuyu-tei.jp/sell/opc](https://yuyu-tei.jp/sell/opc/s/op01) | JP | **Finest split.** Splits out things the official list doesn't: "(パラレル)(スーパーパラレル)", "(刻印なし)" (unstamped), and condition (傷有り/無し, damaged/undamaged) | Yes (`card.yuyu-tei.jp`) | HTML only | No | Live stock | Free to view | [Rules](https://yuyu-tei.jp/info/rule/): images and text are Yuyu-tei's; **no reproduction without permission (無断での転載を禁止)** | **Yes** (JPY sell price + stock) |

## Coverage by Language (snapshot 2026-11-25)

| Language | Primary source | Printings (IDs) | Distinct base card numbers |
|---|---|---|---|
| English | Official EN list | 5,118 | 3,381 |
| Japanese | Official JP list | 5,271 | 3,478 |
| Chinese (Simplified) | Official CN API | 5,244 rows | — (suffixes are irregular; see below) |

- **JP has the most.** 101 base numbers appear only in JP (mostly JP-only promos `P-0xx` and event or mail-in prizes). Only 4 appear only in EN. At the exact-ID level, 460 IDs exist only in JP and 307 only in EN. Most of the gap comes from suffix numbering (next section), not from different cards.
- **CN tracks JP closely.** OP-18 is listed in all three Languages (JP release 2026-11-21). CN set sizes are close to JP but not identical (CN `OPC-01` has 153 rows, JP OP-01 has 154). CN merges or skips some starter decks (e.g. "STC31&34" is sold as one product), and it has its own products (e.g. 对战进阶套组2025).
- **"Chinese" is ambiguous.** Simplified (mainland, `onepiece-cardgame.cn`) and Traditional (`asia-tc`, Bandai Asia) are separate print runs and separate catalogs. The Traditional list uses the Bandai ID scheme. punk-records covers only Traditional.

## Variants and reprints

**Official Bandai sites (EN/JP/Asia).** The ID is the card number plus an optional suffix:
- No suffix: the base Printing.
- `_pN`: parallel or alt-art Printing (also special/manga/promo versions with a different illustration).
- `_rN`: reprint with the same illustration in a later product. Example: EN `OP03-008_r1` is Buggy reprinted in PRB-02. The "Hide reprint cards" toggle filters these out.
- Each entry names the product it came from ("Card Set(s)" / 入手情報). That gives you the Printing → product link.

**Suffixes are numbered per Language.** For `OP01-001` (Zoro, leader) on 2026-11-25:

| Physical card | EN ID | JP ID |
|---|---|---|
| OP-01 booster base | `OP01-001` | `OP01-001` |
| OP-01 booster parallel | `OP01-001_p1` | `OP01-001_p2` |
| Premium Card Collection 25th Edition | `OP01-001_p2` | `OP01-001_p1` |
| Weekly Shōnen Jump mail-in | — (JP only) | `OP01-001_p3` |

`OP01-001_p1` therefore means a different physical card in EN than in JP. You can only match Variants across Languages by (base number + source product) or by image, not by suffix.

**Official CN list.** `cardNumber` is the base number, sometimes with a suffix. The scheme is inconsistent. Across 5,244 rows: plain number (4,649), `_01`…`_10` (~515), `P` (51), `CP` (11), `SP` (3), `LP` (2), and one-offs (`_p1`, `_1`, `-01`, `P-SR`). Some SP Printings even reuse the plain number: `OP01-001` appears twice, under 冒险的黎明【OPC-01】 and under 限定商品收录卡牌. Only the image filename shows `SP`. **Use the API's numeric `id` as the CN Printing key.** Treat `cardNumber` as the base number after stripping the suffix. Detail fields include `cardRarity`, `cardOfferType` (product), `cardCartograph` (art type: manga/anime/original), `createTime`, and `updateTime`.

**Marketplaces** go finer than any official list: Yuyu-tei's "super parallel" and "unstamped" (刻印なし), and TCGplayer's "Alternate Art" / "Manga". They also go coarser: Cardmarket has no Variant label, and its product isn't tied to a Language.

## How card numbers relate across Languages

- **Base numbers are shared and 1:1 for shared cards.** `OP18-001` is Karoo (EN), カルー (JP), and 飛毛腿 (zh-Hant), with the same stats. CN uses the same `OPxx-nnn`, `STxx-nnn`, `EBxx-nnn`, `P-nnn` numbers on the card.
- **Set/product codes differ by Language.** CN sells OP-01 as **`OPC-01`**, ST-01 as `STC-01`, EB-01 as `EBC-01`, and PRB-01 as `PRBC-01`. The card numbers inside stay `OP01-…`. The newest CN products (OP-17, OP-18, EB-05) are listed without the "C".
- **Products can be repackaged.** EN never released EB-04 on its own. Its cards are in EN `OP14-EB04` (199 IDs incl. 40 `EB04-*`) and `OP15-EB04`. JP has a standalone `EB-04` (85 IDs). Key Printings by card number, not by set code.
- **Release order:** JP has always led EN (OP-01 JP 2022-07-22, EN 2022-12-02). The gap has shrunk to days for recent sets, so the three catalogs are currently about in sync.

## Recommendation

1. **Catalog of record: the official list for each Language.**
   - EN + JP: consume `buhbbl/punk-records` (`english/`, `japanese/`). It is a daily JSON mirror of the official sites with the official IDs, so no scraping is needed. Fall back to running `vegapull` (or a small scraper over `?series=` pages) if the repo goes stale.
   - CN: call the `webadmin.windoent.com/front/op-public/cardList/cardlist/weblist` + `webInfo/{id}` API directly (6 list calls + per-card detail, or detail lazily).
2. **Printing key = (Language, source ID)**, e.g. `(EN, "OP01-001_p1")` or `(CN, 1573)`. Also store the normalized base number (`OP01-001`), product/set, rarity, name, and image URL. This fits the glossary: the same base number in two Languages is two Printings.
3. **Images:** official PNGs exist for every Printing in all three Languages, which makes them good scan-matching references. Bandai's terms allow only personal use without permission. A single-user personal app that caches images locally fits that, but don't redistribute them or publish the dataset.
4. **Prices (out of scope, for later):** optcgapi (EN, free) and apitcg (EN, free tier) carry TCGplayer prices. Limitless shows USD+EUR. Yuyu-tei shows JPY. Cardmarket's price-guide file is EUR.
5. **Later Games:** Bandai runs the same kind of official lists for Dragon Ball Super Fusion World ([EN](https://www.dbs-cardgame.com/fw/en/cardlist/)/[JP](https://www.dbs-cardgame.com/fw/jp/cardlist/)) and Gundam ([EN](https://www.gundam-gcg.com/en/cards/)/[JP](https://www.gundam-gcg.com/jp/cards/)), and apitcg lists both games. Plan the same official-per-Language approach. Their CN coverage is not researched.

## Open questions

- **Which Chinese?** Simplified (mainland, `onepiece-cardgame.cn`), Traditional (`asia-tc`), or both? They are different catalogs and different physical cards. Asia-English vs. NA/EU English raises the same question.
- **How fine should Variants go?** The official lists don't split out marketplace-level distinctions like Yuyu-tei's "super parallel" / "unstamped" or stamped prize versions. Do we accept the official split as the Variant definition?
- **CN API stability.** It is undocumented, and the suffix scheme is irregular. How we tell CN Variants apart (beyond the `id` and image) needs checking against physical cards.
- **Matching Variants across Languages** (e.g. EN parallel ↔ JP parallel) isn't needed for inventory, but would be if prices or cross-Language views are added later. It would need product + image matching.
- **Permission.** If the app or dataset ever goes beyond personal use, Bandai's terms require its prior consent (and the licensee's for CN).
