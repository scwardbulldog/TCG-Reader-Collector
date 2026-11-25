# Native Android over PWA

v1 is a native Android app (Kotlin, Jetpack Compose, CameraX, ML Kit text recognition), sideloaded onto the collector's Pixel 10 Pro.

Scanning is the reason the app exists. On-device ML Kit read One Piece **Base Numbers** reliably on the Pixel 10 Pro, including Japanese cards. The PWA path was impractical: Chrome's `TextDetector` is behind a flag, Tesseract.js is slower and less accurate, and a smooth multi-card run wasn't achieved. A PWA's one advantage, reaching the **Collection** from a PC, is replaced by a Google Sheets export.

**Consequences**

- The domain and data logic lives in a module with no Android dependencies, and the scanner sits behind an interface, so a later Kotlin Multiplatform port to iPhone is a port, not a rewrite.
- No Play Store distribution; Pixel and recent Android versions only.

Source: [Native Android app or PWA?](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/5), [Prototype card-number scanning on the Pixel 10 Pro](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/4). See [the v1 spec](../spec/v1.md), section 2.
