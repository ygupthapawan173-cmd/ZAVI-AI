package ai.zavi.assistant.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalizes text returned by OpenAI-compatible and Gemini chat APIs.
 * Some gateways return content as an array of text blocks instead of a string.
 */
internal object AssistantResponseParser {
    fun extract(raw: String, provider: AiProvider): String {
        val root = JSONObject(raw)
        val content: Any? = if (provider == AiProvider.GEMINI) {
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("AI responseలో candidates లేవు.")
            val candidate = candidates.optJSONObject(0)
                ?: throw IllegalStateException("AI responseలో candidate ఖాళీగా ఉంది.")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("AI responseలో text parts లేవు.")
            parts
        } else {
            val choices = root.optJSONArray("choices")
                ?: throw IllegalStateException("AI responseలో choices లేవు.")
            val message = choices.optJSONObject(0)?.optJSONObject("message")
                ?: throw IllegalStateException("AI responseలో assistant message లేదు.")
            message.opt("content")
        }

        val text = when (content) {
            null, JSONObject.NULL -> ""
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val part = content.opt(index)
                    when (part) {
                        is JSONObject -> {
                            val value = part.opt("text")
                            if (value is String) append(value)
                        }
                        is String -> append(part)
                    }
                }
            }
            is JSONObject -> content.optString("text", content.optString("content", ""))
            else -> ""
        }.trim()

        if (text.isBlank() || text.equals("null", ignoreCase = true)) {
            val finishReason = root.optJSONArray("choices")
                ?.optJSONObject(0)?.optString("finish_reason")
                ?.takeIf { it.isNotBlank() && it != "null" }
            throw IllegalStateException(
                if (finishReason != null) "AI text response ఖాళీగా ఉంది (finish_reason: $finishReason). వేరే model ఎంచుకుని మళ్లీ ప్రయత్నించండి."
                else "AI ఖాళీ response ఇచ్చింది. వేరే model ఎంచుకుని మళ్లీ ప్రయత్నించండి."
            )
        }
        return text
    }
}
