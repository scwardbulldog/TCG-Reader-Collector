package tcg.domain

import java.text.Normalizer

/** [this] folded for search: full-width and half-width forms become one (`Ｄ` and `D`, `＆` and `&`), and case is dropped. */
fun String.foldedForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase()

/** True when [this] contains [query], ignoring case and full-width/half-width forms. */
fun String.containsFolded(query: String): Boolean = foldedForSearch().contains(query.foldedForSearch())
