package com.leaf.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DiscoveredArticle(
    val doi: String,
    val title: String,
    val authors: String,
    val journal: String,
    val year: Int?,
    val sourceUrl: String
)

/** Crossref provides bibliographic metadata, not a license to display the article body. */
class CrossrefArticleDiscovery {
    suspend fun search(query: String): List<DiscoveredArticle> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = URL("https://api.crossref.org/works?query.bibliographic=$encoded&rows=20&select=DOI,title,author,container-title,published,URL,type").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "LEAF/0.1 (personal Android reader)")
        try {
            if (connection.responseCode !in 200..299) error("Article search is unavailable (${connection.responseCode})")
            val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val items = root.optJSONObject("message")?.optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val doi = item.optString("DOI").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = item.optJSONArray("title")?.optString(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val authorArray = item.optJSONArray("author")
                val authors = (0 until (authorArray?.length() ?: 0)).take(3).mapNotNull { i ->
                    authorArray?.optJSONObject(i)?.let { person ->
                        listOf(person.optString("given"), person.optString("family")).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
                    }
                }.joinToString(", ").ifBlank { "Unknown authors" }
                val journal = item.optJSONArray("container-title")?.optString(0)?.takeIf { it.isNotBlank() } ?: item.optString("type", "Article")
                val year = item.optJSONObject("published")?.optJSONArray("date-parts")?.optJSONArray(0)?.optInt(0)?.takeIf { it > 0 }
                DiscoveredArticle(doi, title, authors, journal, year, "https://doi.org/$doi")
            }
        } finally { connection.disconnect() }
    }
}
