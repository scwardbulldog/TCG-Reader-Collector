package tcg.domain.scanning

import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location

/** Remembers the current Language across app restarts. */
interface LanguageStore {
    /** The Language last saved; English if none was. */
    fun load(): Language

    fun save(language: Language)
}

/**
 * One Scan Session. It starts straight away in the remembered Language, which the collector can switch
 * at any time. The Location and condition are supplied with each add, so the session only remembers
 * the [location] last used, in memory, as the next add's default.
 */
class ScanSession(
    val game: Game,
    private val languages: LanguageStore,
) {
    var language: Language = languages.load()
        private set

    /** The Location of the last add; null until one is made, or when the last add had none. */
    var location: Location? = null
        internal set

    val scope: ScanScope get() = ScanScope(game, language)

    fun switchLanguage(language: Language) {
        this.language = language
        languages.save(language)
    }
}
