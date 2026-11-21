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
            val taskType = AutoModelRouter.detectTask(messages)
            val routedProviders = AutoModelRouter.order(active, taskType)

            // Research is opt-in by intent: ordinary chat stays fast and does not make a web request.
            val query = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
            val researchMode = shouldUseLiveSearch(query) ||
                messages.firstOrNull { it.role == "system" }?.content?.contains("Research Assistant") == true
            val sources = if (researchMode && query.isNotBlank()) {
                runCatching { webResearchClient.search(query, 5) }.getOrElse { emptyList() }
            } else emptyList()
            val groundedMessages = if (sources.isNotEmpty()) {
                val context = webResearchClient.asGroundedContext(query, sources)
                val system = messages.firstOrNull { it.role == "system" }
                if (system == null) {
                    listOf(OpenRouterMessage("system", context)) + messages
                } else {
                    messages.map { if (it === system) system.copy(content = system.content + "\n\n" + context) else it }
                }
            } else messages

            val containsImage = query.contains("[[ZAVI_IMAGE:")
            val orchestrationRequested = shouldOrchestrate(query) && !containsImage
            val answer = if (orchestrationRequested) {
                runOrchestrated(routedProviders, groundedMessages)
            } else {
                requestWithFallback(routedProviders, groundedMessages)
            }
            ResearchCitationFormatter.append(answer, sources)
        }

    private fun shouldOrchestrate(query: String): Boolean {
        val q = query.lowercase()
        return listOf(
            "multi-agent", "multi agent", "team of agents", "orchestrate this",
            "agent team", "autonomous agent", "agent mode", "plan execute review",
            "మల్టీ ఏజెంట్", "ఏజెంట్ టీమ్", "ఆటోనమస్ ఏజెంట్", "ఏజెంట్ మోడ్"
        ).any { q.contains(it) }
    }

    private fun requestWithFallback(
        active: List<ProviderConfig>,
        messages: List<OpenRouterMessage>
    ): String {
        val errors = mutableListOf<String>()
        for (config in active) {
            try {
                return request(config, messages)
            } catch (e: Exception) {
                errors += "${config.provider.title}: ${e.message ?: "Request failed"}"
            }
        }
        throw IllegalStateException("అన్ని enabled AI providers విఫలమయ్యాయి.\n" + errors.joinToString("\n"))
    }

    private fun runOrchestrated(
        active: List<ProviderConfig>,
        original: List<OpenRouterMessage>
    ): String {
        val system = original.firstOrNull { it.role == "system" }
            ?: OpenRouterMessage("system", "You are ZAVI AI. Reply in the user's language.")
        val userTask = original.lastOrNull { it.role == "user" }?.content.orEmpty()
        val history = original.filter { it.role == "user" || it.role == "assistant" }
            .dropLast(1).takeLast(8)

        val plannerMessages = listOf(
            system.copy(content = system.content + "\nYou are the Planning Agent. Break the user's request into clear objectives, constraints, risks, and a short execution plan. Do not answer the task yet."),
            *history.toTypedArray(),
            OpenRouterMessage("user", userTask)
        )
        val plan = requestWithFallback(active, plannerMessages)

        val specialistMessages = listOf(
            system.copy(content = system.content + "\nYou are the Specialist Agent. Execute the user's task using the supplied plan. Be concrete and produce the actual requested result."),
            OpenRouterMessage("user", "USER TASK:\n" + userTask + "\n\nPLANNER OUTPUT:\n" + plan)
        )
        val work = requestWithFallback(active, specialistMessages)

        val reviewerMessages = listOf(
            system.copy(content = system.content + "\nYou are the Quality Reviewer Agent. Check the draft for correctness, completeness, contradictions, unsupported claims, and safety. Fix issues and return only the improved final answer. Do not claim external verification that did not happen."),
            OpenRouterMessage("user", "USER TASK:\n" + userTask + "\n\nPLAN:\n" + plan + "\n\nSPECIALIST DRAFT:\n" + work)
        )
        val reviewed = requestWithFallback(active, reviewerMessages)
        return "🤝 Multi-Agent Workflow (Planner → Specialist → Reviewer)\n\n" + reviewed
    }

    private fun shouldUseLiveSearch(query: String): Boolean {
        val q = query.lowercase()
        val triggers = listOf(
            "latest", "today", "current", "right now", "news", "live", "search web",
            "search internet", "look up", "recent update", "current price", "weather",
            "youtube", "video search", "find videos", "youtube videos",
            "తాజా", "ఈరోజు", "ఇప్పటి", "ప్రస్తుతం", "వార్తలు", "వెతుకు", "సెర్చ్ చేయి"
        )
        return triggers.any { q.contains(it) }
    }


    private fun extractImage(content: String): Pair<String, String>? {
        val match = Regex("\\[\\[ZAVI_IMAGE:([^:]+):([A-Za-z0-9+/=]+)\\]\\]").find(content)
            ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun stripImageMarker(content: String): String =
        content.replace(Regex("\\[\\[ZAVI_IMAGE:[^:]+:[A-Za-z0-9+/=]+\\]\\]"), "").trim()

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
                    val parts = JSONArray()
                    val image = extractImage(message.content)
                    val text = stripImageMarker(message.content)
                    if (text.isNotBlank()) parts.put(JSONObject().put("text", text))
                    if (image != null) {
                        parts.put(JSONObject().put("inlineData",
                            JSONObject().put("mimeType", image.first).put("data", image.second)))
                    }
                    contents.put(JSONObject().put("role", role).put("parts", parts))
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
                messages.forEach { message ->
                    val image = extractImage(message.content)
                    val messageContent: Any = if (image == null) {
                        message.content
                    } else {
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", stripImageMarker(message.content)))
                            .put(JSONObject().put("type", "image_url").put("image_url",
                                JSONObject().put("url", "data:" + image.first + ";base64," + image.second)))
                    }
                    array.put(JSONObject().put("role", message.role).put("content", messageContent))
                }
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
            AssistantResponseParser.extract(raw, config.provider)
        }
    }
}
