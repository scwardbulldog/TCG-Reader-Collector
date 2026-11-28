# Phone-only storage with no hosted backend

The **Collection** lives only on the phone, in a local SQLite database (Room). There is no hosted backend and no live PC access. The **Catalog** is kept on the phone too, separately from the **Collection**, and is refreshed from per-**Language** files published by a private repo.

The PC gets a manual CSV export for Google Sheets. A separate, manually saved, versioned JSON backup lets the collector restore the **Collection** after a lost or reset phone. (From v2 the backup is a zip that also carries Card photos; see [ADR 0005](0005-backup-is-a-zip-with-card-photos.md). v1 JSON files still restore.)

This keeps v1 free, avoids accounts and servers for a single user, and matches the decision to go native. A direct Sheets API integration was rejected as unnecessary; the CSV is enough to view the **Collection** on a PC.

**Consequences**

- The phone is the only copy of the **Collection**, so the app reminds the collector to back up, and a successful write does not guarantee the file is off the phone.
- Restore validates the whole file and replaces the **Collection** atomically. **Printing** references missing from the **Catalog** are kept and flagged, never discarded.
- The **Catalog** pipeline lives in a private repo because Bandai's terms allow personal use only.

Source: [Native Android app or PWA?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/5), [How is the collection stored on the phone, and how is it exported to Google Sheets?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/6), [Which catalog source does v1 use, and is it looked up live or copied locally?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/7). See [the v1 spec](../spec/v1.md), sections 3 and 7.
