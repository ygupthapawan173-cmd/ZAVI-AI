package ai.zavi.assistant.models

data class ChatMessage(
    val role: String,
    val content: String,
    val agent: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = role == "user"
}
