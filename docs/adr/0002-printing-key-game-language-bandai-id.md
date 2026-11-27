# Printing key is Game + Language + Bandai ID

A **Printing** is identified by **Game** + **Language** + Bandai's own card-list ID (treated as opaque), for example (One Piece, EN, `OP01-001_p1`). **Base Number**, **Release Product**, rarity, name and image URL are stored as details, not as part of the key.

- **Language** is in the key because Variant suffixes are numbered per **Language**: the OP-01 parallel Zoro is EN `OP01-001_p1` but JP `OP01-001_p2`.
- **Game** is in the key so Dragon Ball Super and Gundam can be added without ID clashes.
- Bandai's IDs have been stable: none of the 4,007 English IDs from March 2026 disappeared or changed **Release Product** by November 2026. That note predates naming promos from "Information Obtained" ([#41](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/41)): **Release Product** values of promo **Printings** changed then, and only because the source field changed. Keys are unaffected.
- Bandai's IDs are not always unique per physical card. EN `OP07-091_p1` is listed twice, as "Offline Regional Finalist Card Set 2024 Vol. 3" and "Offline Regional Participation Pack 2024 Vol. 3", with the same image. The **Catalog** keeps one **Printing** per ID (lowest pack ID wins), so the two products cannot be told apart.
- Matching by **Base Number** or by suffix across **Languages** was rejected as identity, because the suffixes differ and one **Base Number** covers several **Printings**.

**Consequences**

- The same key is used in the **Catalog**, **Owned Copy** references, CSV exports and JSON backups.
- If a refresh drops a **Printing**, the **Owned Copy** is kept and flagged; it is never remapped by **Base Number**.

Source: [What uniquely identifies a Printing, and how are Variants and reprints modelled?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/8), [What must the v1 spec preserve to support another Game later?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/14#issuecomment-7172867774). See [the v1 spec](../spec/v1.md), sections 4 and 8.
