package ai.zavi.assistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSource(
    val title: String,
    val url: String,
    val snippet: String
)

class WebResearchClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, limit: Int = 5): List<WebSource> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Search query ఖాళీగా ఉంది." }
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Web search failed: HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            val document = Jsoup.parse(html)
            document.select(".result").mapNotNull { item ->
                val link = item.selectFirst(".result__a") ?: return@mapNotNull null
                val href = link.absUrl("href").ifBlank { link.attr("href") }
                val title = link.text().trim()
                val snippet = item.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                if (title.isBlank() || !href.startsWith("http")) null
                else WebSource(title, href, snippet)
            }.distinctBy { it.url }.take(limit.coerceIn(1, 8))
        }
    }

    fun asGroundedContext(query: String, sources: List<WebSource>): String {
        if (sources.isEmpty()) return "No live web results were retrieved. Do not claim to have searched or invent citations."
        return buildString {
            append("LIVE WEB SEARCH RESULTS for: ")
            append(query)
            append("\nUse only these retrieved sources for current factual claims. Cite them inline using their exact URLs. If sources disagree or are incomplete, say so.\n")
            sources.forEachIndexed { index, source ->
                append("\n[${index + 1}] ${source.title}\nURL: ${source.url}\nSnippet: ${source.snippet}\n")
            }
        }
    }
}
