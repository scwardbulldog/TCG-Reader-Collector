package tcg.app.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.catalog.CatalogFetch
import tcg.domain.catalog.CatalogSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads Catalog files from the private Catalog repo through the GitHub contents API. The file's ETag is
 * its version, so an unchanged Catalog costs a 304 instead of a download.
 */
class GitHubCatalogSource(private val repo: String, private val token: String) : CatalogSource {
    override suspend fun fetch(game: Game, language: Language, knownVersion: String?): CatalogFetch =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) throw IOException("No Catalog token was built into this app")
            val url = URL("https://api.github.com/repos/$repo/contents/catalog/${game.fileCode()}-${language.fileCode()}.json")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/vnd.github.raw+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (knownVersion != null) connection.setRequestProperty("If-None-Match", knownVersion)
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> CatalogFetch.UpToDate
                    HttpURLConnection.HTTP_OK -> {
                        val version = connection.getHeaderField("ETag") ?: throw IOException("Catalog response has no ETag")
                        CatalogFetch.Fetched(version, connection.inputStream.bufferedReader().use { it.readText() })
                    }
                    else -> throw IOException("Catalog download failed (HTTP $status)")
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun Game.fileCode() = when (this) {
        Game.OnePiece -> "onepiece"
    }

    private fun Language.fileCode() = when (this) {
        Language.English -> "en"
        Language.Japanese -> "jp"
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
