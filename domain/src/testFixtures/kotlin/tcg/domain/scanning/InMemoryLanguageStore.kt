package tcg.domain.scanning

import tcg.domain.Language

class InMemoryLanguageStore(var saved: Language? = null) : LanguageStore {
    override fun load(): Language = saved ?: Language.English

    override fun save(language: Language) {
        saved = language
    }
}
