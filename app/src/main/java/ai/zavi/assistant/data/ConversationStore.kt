package ai.zavi.assistant.data

import android.content.SharedPreferences
import ai.zavi.assistant.models.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

class ConversationStore(private val preferences: SharedPreferences) {
    fun loadMessages(): List<ChatMessage> {
        return try {
            val raw = preferences.getString(KEY_MESSAGES, "[]") ?: "[]"
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val role = item.optString("role")
                    val content = item.optString("content")
                    if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                        add(
                            ChatMessage(
                                role = role,
                                content = content,
                                agent = item.optString("agent"),
                                createdAt = item.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("agent", message.agent)
                    .put("createdAt", message.createdAt)
            )
        }
        preferences.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun loadMemory(): String = preferences.getString(KEY_MEMORY, "") ?: ""

    fun saveMemory(memory: String) {
        preferences.edit().putString(KEY_MEMORY, memory.trim().take(MAX_MEMORY_CHARS)).apply()
    }

    fun clearMessages() {
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    fun clearMemory() {
        preferences.edit().remove(KEY_MEMORY).apply()
    }

    companion object {
        private const val KEY_MESSAGES = "zavi_chat_messages"
        private const val KEY_MEMORY = "zavi_user_memory"
        private const val MAX_MESSAGES = 40
        private const val MAX_MEMORY_CHARS = 1200
    }
}
