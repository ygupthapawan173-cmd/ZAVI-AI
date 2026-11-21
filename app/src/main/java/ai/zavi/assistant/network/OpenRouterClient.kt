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

data class OpenRouterMessage(
    val role: String,
    val content: String
)

class OpenRouterClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()
) {
    suspend fun complete(
        apiKey: String,
        model: String,
        messages: List<OpenRouterMessage>
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Settingsలో OpenRouter API key add చేయండి." }
        require(model.isNotBlank()) { "AI model name ఖాళీగా ఉండకూడదు." }

        val messageArray = JSONArray()
        messages.forEach { message ->
            messageArray.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
            )
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messageArray)
            .put("temperature", 0.5)
            .put("max_tokens", 1400)
            .toString()

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/ygupthapawan173-cmd/ZAVI-AI")
            .addHeader("X-Title", "ZAVI AI")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(
                    detail?.takeIf { it.isNotBlank() }
                        ?: "OpenRouter request failed (HTTP ${response.code})."
                )
            }

            val content = try {
                JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
            } catch (_: Exception) {
                ""
            }
            content.trim().ifBlank { throw IllegalStateException("AI నుంచి ఖాళీ response వచ్చింది. మరోసారి ప్రయత్నించండి.") }
        }
    }
}
