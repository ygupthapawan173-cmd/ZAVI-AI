package ai.zavi.assistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AiProvider(val title: String, val defaultModel: String) {
    OPENROUTER("OpenRouter", "openrouter/free"),
    GEMINI("Google Gemini", "gemini-2.5-flash"),
    GROQ("Groq", "llama-3.3-70b-versatile"),
    MISTRAL("Mistral", "mistral-small-latest")
}

data class ProviderConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val enabled: Boolean
)

class MultiProviderClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build(),
    private val webResearchClient: WebResearchClient = WebResearchClient()
) {
    suspend fun complete(configs: List<ProviderConfig>, messages: List<OpenRouterMessage>): String =
        withContext(Dispatchers.IO) {
            val active = configs.filter { it.enabled && it.apiKey.isNotBlank() }
            require(active.isNotEmpty()) { "Settingsలో కనీసం ఒక AI provider API key add చేసి Enable చేయండి." }

            // Research is opt-in by intent: ordinary chat stays fast and does not make a web request.
            val query = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
            val researchMode = shouldUseLiveSearch(query) ||
                messages.firstOrNull { it.role == "system" }?.content?.contains("Research Assistant") == true
            val groundedMessages = if (researchMode && query.isNotBlank()) {
                val sources = runCatching { webResearchClient.search(query, 5) }.getOrElse { emptyList() }
                val context = webResearchClient.asGroundedContext(query, sources)
                val system = messages.firstOrNull { it.role == "system" }
                if (system == null) {
                    listOf(OpenRouterMessage("system", context)) + messages
                } else {
                    messages.map { if (it === system) system.copy(content = system.content + "\n\n" + context) else it }
                }
            } else messages

            val errors = mutableListOf<String>()
            for (config in active) {
                try {
                    return@withContext request(config, groundedMessages)
                } catch (e: Exception) {
                    errors += "${config.provider.title}: ${e.message ?: "Request failed"}"
                }
            }
            throw IllegalStateException("అన్ని enabled AI providers విఫలమయ్యాయి.\n" + errors.joinToString("\n"))
        }

    private fun shouldUseLiveSearch(query: String): Boolean {
        val q = query.lowercase()
        val triggers = listOf(
            "latest", "today", "current", "right now", "news", "live", "search web",
            "search internet", "look up", "recent update", "current price", "weather",
            "తాజా", "ఈరోజు", "ఇప్పటి", "ప్రస్తుతం", "వార్తలు", "వెతుకు", "సెర్చ్ చేయి"
        )
        return triggers.any { q.contains(it) }
    }

    suspend fun test(config: ProviderConfig): String = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank()) { "${config.provider.title} API key add చేయండి." }
        request(config, listOf(OpenRouterMessage("user", "Reply with exactly: ZAVI connection OK")))
    }

    private fun request(config: ProviderConfig, messages: List<OpenRouterMessage>): String {
        val model = config.model.ifBlank { config.provider.defaultModel }
        val (url, payload, keyHeader) = when (config.provider) {
            AiProvider.GEMINI -> {
                val contents = JSONArray()
                messages.filter { it.role != "system" }.forEach { message ->
                    val role = if (message.role == "assistant") "model" else "user"
                    contents.put(JSONObject().put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", message.content))))
                }
                val system = messages.firstOrNull { it.role == "system" }?.content
                val body = JSONObject().put("contents", contents)
                if (!system.isNullOrBlank()) body.put("systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                body.put("generationConfig", JSONObject().put("temperature", 0.5).put("maxOutputTokens", 1400))
                Triple("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body.toString(), "x-goog-api-key" to "")
            }
            else -> {
                val array = JSONArray()
                messages.forEach { array.put(JSONObject().put("role", it.role).put("content", it.content)) }
                val body = JSONObject().put("model", model).put("messages", array)
                    .put("temperature", 0.5).put("max_tokens", 1400)
                val endpoint = when (config.provider) {
                    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                    AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                    AiProvider.MISTRAL -> "https://api.mistral.ai/v1/chat/completions"
                    else -> error("Unsupported provider")
                }
                Triple(endpoint, body.toString(), "Authorization" to "Bearer ")
            }
        }
        val header = keyHeader.first
        val prefix = keyHeader.second
        val request = Request.Builder().url(url)
            .addHeader(header, prefix + config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                        ?: JSONObject(raw).optString("message")
                } catch (_: Exception) { null }
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}")
            }
            val answer = try {
                if (config.provider == AiProvider.GEMINI) {
                    JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text")
                } else {
                    JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content")
                }
            } catch (_: Exception) { "" }
            answer.trim().ifBlank { throw IllegalStateException("ఖాళీ response వచ్చింది") }
        }
    }
}
