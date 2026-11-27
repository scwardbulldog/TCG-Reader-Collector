package tcg.app.scanning

import com.google.mlkit.vision.text.Text

/** The Base Number [extract] finds in recognised text; it is printed at the bottom of the card, so the lowest match wins. */
internal fun Text.lowestBaseNumber(extract: (String) -> String?): String? =
    textBlocks.flatMap { it.lines }
        .mapNotNull { line -> extract(line.text)?.let { it to (line.boundingBox?.bottom ?: 0) } }
        .maxByOrNull { it.second }
        ?.first
