package tcg.app.scanning

import android.content.Context
import tcg.domain.Language
import tcg.domain.scanning.LanguageStore

/** Keeps the current Language in SharedPreferences, so it survives app restarts. */
class SharedPreferencesLanguageStore(context: Context) : LanguageStore {
    private val preferences = context.applicationContext.getSharedPreferences("scan", Context.MODE_PRIVATE)

    override fun load(): Language =
        Language.entries.firstOrNull { it.name == preferences.getString(KEY, null) } ?: Language.English

    override fun save(language: Language) {
        preferences.edit().putString(KEY, language.name).apply()
    }

    private companion object {
        const val KEY = "language"
    }
}
