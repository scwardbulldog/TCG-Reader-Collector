# Research: how reliably can a phone camera identify a One Piece card's number?

Resolves [#3](https://github.com/scwardbulldog/TCG-Reader-Collector/issues/3) (map: #1). Researched 2026-11-25.
Vocabulary follows [GLOSSARY.md](../GLOSSARY.md): **Printing**, **Language**, **Variant**, **Scan Session**.

## Short answer

- **Read the card number with OCR, not the whole card.** The card number (e.g. `OP01-001`) is short and uses a fixed Latin-alphanumeric format. It is the same string on the English and Japanese official card lists [S1][S2]. Checking OCR output against a regex and the known catalog leaves only a few candidates. Combined with the Scan Session's **Language**, those candidates are the **Printings** of one card number: the normal one plus any parallel **Variants**.
- **OCR alone cannot tell Variants apart.** Parallel / alt-art Printings share the base card number. The official card lists separate them only by image ID (`OP01-001`, `OP01-001_p1`, `OP01-001_p2`) [S1][S2]. The "propose candidates, user taps to confirm" flow already covers this: show thumbnails of the Variants for the recognised number. Image matching could rank Variants later, but it should not be the primary identifier.
- **Native Android with ML Kit Text Recognition v2 is the strongest option.** It is free and runs on-device. Its docs describe it as real-time for Latin script and provide a frame-dropping CameraX pipeline. A native app also gets full camera control [S3][S4].
- **A PWA is viable but weaker.** Chrome on Android has no built-in OCR that is on by default; `TextDetector` is still behind a flag [S5]. The browser options are Tesseract.js (WASM) or a cloud call. The browser also gives less control over camera focus and lens choice [S6][S7][S8].
- **Cloud OCR is a good PWA fallback and costs almost nothing at personal volume.** Google Cloud Vision gives 1,000 free Text Detection units per month, then $1.50 per 1,000 [S9]. Azure Vision's free F0 tier gives 5,000 free transactions per month, capped at 20 per minute [S10]. The cost is a network round-trip on every scan.

**Recommendation for the prototype ticket:** build a **native Android spike first**, using Kotlin, CameraX, and the ML Kit Latin text recognizer. The spike should:

1. Crop each frame to a guide box over the bottom strip of the card.
2. Run OCR on live frames.
3. Check the text against a card-number regex.
4. Look the number up in the catalog.
5. Propose Printings automatically once two consecutive frames agree.

Then build a cheap second spike: a **PWA that runs Tesseract.js on the cropped strip, with Google Cloud Vision as the fallback**. Test both spikes on the same deck of EN, JP, and CN cards, including foils and parallels. That gives a measured basis for the native-vs-PWA decision.

## Approaches compared

The accuracy and latency entries are expectations based on the cited docs. None has been measured on a Pixel 10 Pro; the prototype must measure them.

| Approach | Platform | Accuracy on the card number (expected) | Latency per scan (expected) | Cost | Notes |
|---|---|---|---|---|---|
| **On-device OCR, native:** ML Kit Text Recognition v2 | Android app (API 23+) [S4] | High for short Latin strings when each character is at least 16×16 px and in focus. Characters larger than about 24×24 px give no extra benefit [S4]. Returns a confidence score and a bounding box for each line, element, and symbol [S3]. | Docs: "Real-time on most devices for Latin script library, slower for others" [S4]. Estimated sub-second lock in a loop that drops stale frames. | Free, runs on-device. App size grows by about 260 KB with the Play Services model, or about 4 MB per script per architecture if bundled [S4]. | Supports Latin, Chinese, Japanese, Korean, and Devanagari scripts [S3]. The card number needs only Latin. CameraX `STRATEGY_KEEP_ONLY_LATEST` drops frames while the analyzer is busy [S4]. |
| **In-browser OCR:** Tesseract.js (WASM) | PWA in Chrome on Android | Medium. Tesseract.js "does not modify the Tesseract recognition model to improve accuracy" [S6], and Tesseract is tuned for document text. It will probably need a tight crop, upscaling, binarisation (conversion to black and white), and a character whitelist (estimate). | Slower than ML Kit. The first load downloads about 15 MB of code and language data; English data alone is about 2 MB. The worker must be reused between images [S7]. Per-frame time on a cropped strip needs measuring. | Free | Works offline after the first load. Only English (`eng`) data is needed for the card number. |
| **In-browser OCR:** Shape Detection API `TextDetector` | Chrome on Android, behind a flag only | n/a | n/a | Free | Face and text detection are only available behind the `#enable-experimental-web-platform-features` flag, and text detection was moved to a separate informative (non-standard) spec [S5]. **Not usable in a shipped PWA.** `BarcodeDetector` is on by default, but One Piece cards have no barcode. |
| **Image matching:** perceptual hash or feature matching against catalog images | Either platform; can run on-device or on a server | Can tell Variants apart, which OCR cannot. Weak at telling Languages apart because the same art is reused, but the Scan Session already fixes the Language. Foil glare and sleeves distort the image. Needs card detection, perspective correction, and a hash index of thousands of catalog images. | Hash lookup is fast; card detection and warping add work. | Free to compute. The catalog images must be gathered; the official sites publish one image per Printing [S1][S2]. | Best kept as a **way to rank a few Variants after OCR**, not as the primary identifier. |
| **Cloud OCR:** Google Cloud Vision Text Detection | Either platform, via an HTTPS call | High (server-side model; returns all the text in the frame) | One network round-trip per scan, including uploading a JPEG crop | First 1,000 units per month free, then $1.50 per 1,000 [S9] | Needs a tiny backend proxy so the API key stays off the device. |
| **Cloud OCR:** Azure Vision Read/OCR, free F0 tier | Either platform, via an HTTPS call | High | One network round-trip per scan | 5,000 free transactions per month, at most 20 per minute [S10] | 20 per minute is enough for one card every 3 seconds. Fast bursts would be throttled. |
| **Cloud multimodal LLM:** for example, the Gemini API | Either platform | Can read the number and describe the art, but its answers are not deterministic. | Seconds | Some models have a free tier, but free-tier content is "used to improve our products" [S11]. | More than is needed to read one short string. Keep only as a last-resort fallback. |

## Where the card number sits, and how legible it is

### Format

The official EN and JP card lists show the same identifier for the same card, for example `OP01-001 | L | LEADER` [S1][S2]. The identifier is a set code followed by a 3-digit number, in ASCII letters and digits whatever the card's Language.

The catalog uses the set prefixes `OP`, `ST`, `EB`, and `PRB`, plus `P-` for promos; confirm this list when writing the regex. A pattern such as `^(OP|ST|EB|PRB)\d{2}-\d{3}$|^P-\d{3}$`, plus a catalog lookup, rejects most OCR noise.

OCR commonly confuses `0` with `O`, `1` with `I`, and `8` with `B`. Letters appear only in the prefix, so a whitelist plus position-aware correction fixes these confusions.

### Location

The card number is printed in the **lower-right area** of the card. Bandai's Block Number announcement places the Block Number in "the lower right corner of each card" [S12]. The Block Number is a separate digit or "X" mark, used from April 2026 [S12], and OCR must not treat it as part of the card number; the regex handles this. On the official card-list images, the card number appears along the bottom edge, near the Block Number.

The app should show a guide overlay so the user places the bottom strip in a fixed region, then run OCR only on that crop. A smaller crop is faster for ML Kit [S4] and for Tesseract.js.

### EN vs JP vs CN

The card number uses Latin letters and digits on EN and JP cards [S1][S2], so **the Latin recognizer is enough for every Language**. The Japanese and Chinese ML Kit models would only matter for reading card names, which this design does not need.

Simplified Chinese is an official edition [S13], but I did not verify the format of CN card numbers from a primary source. See open questions.

### Physical size vs pixels (estimate)

A card is 63 × 88 mm. If the card fills most of a 1080 px-wide frame, that is about 15 px per mm. Glyphs about 1.5–2 mm tall would then be about 22–30 px, which meets ML Kit's 16 px minimum [S4].

The bottom strip of a 1080p or 4K analysis frame should therefore be enough, provided the camera can focus at about 10–15 cm.

### Foils and parallels

Holo and textured foils can reflect light directly into the lens, causing glare. Parallel full-art treatments can also lower the contrast of the text. Ways to reduce this:

- Tilt the card slightly.
- Avoid the torch, which adds glare on foils.
- Scan in diffuse light.
- Require several frames to agree before accepting a number.

Parallel Variants share the base card number [S1][S2], so the number is still the right key. Foils and parallels are a main risk for the prototype to measure.

## Browser camera constraints (PWA path)

- **HTTPS required.** `getUserMedia()` works only in secure contexts. The user must grant permission, and the request's promise may never resolve or reject if the user ignores the prompt [S8].
- **Resolution.** `grabFrame()` returns a frame at the resolution of the video source, which is usually lower than the camera's still-photo resolution. `takePhoto()` uses the full photo resolution but is a slower single exposure [S14]. Request a high `width` and `height` through constraints, then crop.
- **Focus, zoom, and torch.** The Image Capture spec adds these to `MediaTrackConstraints`, along with others [S15]:
  - `focusMode` (`none`, `manual`, `single-shot`, or `continuous`)
  - `pointsOfInterest`
  - `zoom`
  - `torch`

  Chrome on Android has supported Image Capture since Chrome 59 [S14]. Capabilities only appear after streaming starts and vary by device, so check each one with `getCapabilities()` before using it [S14].
- **Lens selection.** A browser sees only the cameras that the OS exposes through `enumerateDevices()` [S14]. Unlike CameraX or Camera2, it cannot choose a specific physical lens such as a macro lens. Close-focus behaviour on a Pixel 10 Pro in Chrome is **unverified**.
- **Compute.** Browser OCR runs in WASM inside a worker [S6][S7]. It cannot use the on-device ML Kit models that a native app gets [S4].

## Native Android camera advantages

- CameraX `ImageAnalysis` with keep-only-latest backpressure, designed for real-time ML Kit loops [S4].
- Frames go directly into `InputImage.fromMediaImage()` in YUV format, with rotation compensation [S4].
- Full control over focus, zoom, and torch through Camera2 or CameraX, with no browser permission prompt on each visit.
- The ML Kit model can be downloaded at install time through Play Services, or bundled so it is available immediately [S4].

## Latency expectations for a one-card-at-a-time loop

The target is about 1 second from the card entering the frame to candidates appearing, followed by one tap.

- **Native:** run OCR on the cropped strip of every frame and require 2 frames to agree. Expected well under 1 second (estimate, consistent with "real-time" in [S4]).
- **PWA with Tesseract.js:** expect noticeably slower per-frame recognition; measure it. Load the worker before the first scan [S7] and run OCR only on the strip.
- **Cloud:** the time to upload, recognise, and respond depends on the network. Capture one frame after focus settles instead of streaming frames.

## Recommendation for the prototype ticket

1. **Spike A (primary): native Android.**
   - Use Kotlin, CameraX `ImageAnalysis`, and the ML Kit Latin `TextRecognizer` on a crop of the bottom strip.
   - Check the result against the regex and correct common OCR confusions.
   - Look up the catalog for the Scan Session's Language, then show Variant thumbnails for the user to tap.
   - Measure time-to-lock and how often the first candidate is correct.
2. **Spike B (comparison): PWA.**
   - Use `getUserMedia` at high resolution, with `focusMode: continuous` where the device supports it.
   - Run Tesseract.js (English data) with a character whitelist on the same crop.
   - If Tesseract fails twice, send a single frame to Google Cloud Vision as the fallback.
   - Measure the same metrics as Spike A.
3. **Test deck.** Use about 30 cards per Language (EN, JP, CN), covering normal, foil, and parallel / alt-art cards, both sleeved and unsleeved.
4. **Decision rule (suggested).** If the PWA's success rate and latency are within roughly 10–20 % of native, the PWA's simpler distribution may win. Otherwise, go native.
5. **Image matching.** Defer ranking Variants by image matching until Variant confirmation by tap has been tried.

## Open questions

- Do **Simplified Chinese** cards use the same ASCII card-number format (for example `OP01-001`) in the same place? Answering this needs a physical card or a primary CN catalog source.
- How do the size and position of the card-number text vary by card type (Leader, Character, Event, Stage, DON!!) and on full-art parallels? Answering this needs measurements on physical cards.
- Can Chrome on the Pixel 10 Pro focus at about 10–15 cm through `getUserMedia`, and which lens does it choose?
- Should the catalog source, which is a separate ticket, provide Variant thumbnails for the confirm screen, keyed by the `_p1`/`_p2` image IDs [S1][S2]?
- Are DON!! cards and promos with non-standard numbering in scope?

## Sources

- [S1] Official ONE PIECE CARD GAME card list (EN), OP-01: https://en.onepiece-cardgame.com/cardlist/?series=569101 (has `OP01-001` and `OP01-001_p1` entries)
- [S2] Official ONE PIECE CARD GAME card list (JP), OP-01: https://www.onepiece-cardgame.com/cardlist/?series=550101 (has `OP01-001` and `OP01-001_p2`)
- [S3] ML Kit Text Recognition v2 overview: https://developers.google.com/ml-kit/vision/text-recognition/v2
- [S4] ML Kit Text Recognition v2 on Android (install options, input image guidelines, performance tips): https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- [S5] Chrome Developers, Shape Detection API: https://developer.chrome.com/docs/capabilities/shape-detection
- [S6] Tesseract.js README: https://github.com/naptha/tesseract.js
- [S7] Tesseract.js performance guide: https://github.com/naptha/tesseract.js/blob/master/docs/performance.md
- [S8] MDN, `MediaDevices.getUserMedia()`: https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia
- [S9] Google Cloud Vision pricing: https://cloud.google.com/vision/pricing
- [S10] Azure Vision pricing: https://azure.microsoft.com/en-us/pricing/details/computer-vision/
- [S11] Gemini Developer API pricing: https://ai.google.dev/gemini-api/docs/pricing
- [S12] Bandai, "Introduction of the Block Number System": https://en.onepiece-cardgame.com/topics/013.php
- [S13] Bandai, "Rules for the use of Different Languages": https://en.onepiece-cardgame.com/rules/announcements/lang_card_rule.php
- [S14] Chrome Developers, "Take photos and control camera settings" (Image Capture API): https://developer.chrome.com/blog/imagecapture
- [S15] W3C, MediaStream Image Capture spec: https://w3c.github.io/mediacapture-image/
