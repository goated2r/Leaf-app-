package com.leaf.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DiscoveredBook(val sourceId: String, val title: String, val author: String, val year: Int?)

interface BookDiscoveryProvider {
    suspend fun search(query: String): List<DiscoveredBook>
}

/** Metadata only. Search results do not imply ownership, a free download or a purchase. */
class OpenLibraryDiscovery : BookDiscoveryProvider {
    override suspend fun search(query: String): List<DiscoveredBook> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = URL("https://openlibrary.org/search.json?q=$encoded&limit=20&fields=key,title,author_name,first_publish_year").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) error("Book search is unavailable (${connection.responseCode})")
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val docs = json.optJSONArray("docs") ?: return@withContext emptyList()
            (0 until docs.length()).mapNotNull { i ->
                val item = docs.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val key = item.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val authors = item.optJSONArray("author_name")
                DiscoveredBook(key, title, authors?.optString(0)?.takeIf { it.isNotBlank() } ?: "Unknown author", item.optInt("first_publish_year").takeIf { it > 0 })
            }
        } finally { connection.disconnect() }
    }
}
