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
        .build()
) {
    suspend fun complete(configs: List<ProviderConfig>, messages: List<OpenRouterMessage>): String =
        withContext(Dispatchers.IO) {
            val active = configs.filter { it.enabled && it.apiKey.isNotBlank() }
            require(active.isNotEmpty()) { "Settingsలో కనీసం ఒక AI provider API key add చేసి Enable చేయండి." }
            val errors = mutableListOf<String>()
            for (config in active) {
                try {
                    return@withContext request(config, messages)
                } catch (e: Exception) {
                    errors += "${config.provider.title}: ${e.message ?: "Request failed"}"
                }
            }
            throw IllegalStateException("అన్ని enabled AI providers విఫలమయ్యాయి.\n" + errors.joinToString("\n"))
        }

    suspend fun test(config: ProviderConfig): String = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank()) { "${config.provider.title} API key add చేయండి." }
        request(config, listOf(OpenRouterMessage("user", "Reply with exactly: ZAVI connection OK")))
    }

    private fun request(config: ProviderConfig, messages: List<OpenRouterMessage>): String {
        val model = config.model.ifBlank { config.provider.defaultModel }
        val (url, payload, keyHeader, keyPrefix) = when (config.provider) {
            AiProvider.GEMINI -> {
                val parts = JSONArray()
                messages.filter { it.role != "system" }.forEach { parts.put(JSONObject().put("text", it.content)) }
                val system = messages.firstOrNull { it.role == "system" }?.content
                val body = JSONObject().put("contents", JSONArray().put(JSONObject()
                    .put("role", "user").put("parts", parts)))
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
        client.newCall(request).execute().use { response ->
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
