package ai.zavi.assistant.network

enum class ZaviTaskType {
    GENERAL, CODING, RESEARCH, TELUGU, IMAGE
}

/**
 * Chooses among providers the user has enabled and configured.
 * It never invents model IDs or changes the user's saved model selection.
 */
object AutoModelRouter {
    fun detectTask(messages: List<OpenRouterMessage>): ZaviTaskType {
        val query = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val system = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
        if (query.contains("[[ZAVI_IMAGE:")) return ZaviTaskType.IMAGE

        val text = "$query $system".lowercase()
        if (Regex("[\\u0C00-\\u0C7F]").containsMatchIn(query) ||
            listOf("telugu", "తెలుగు", "తెలుగులో", "telugu assistant").any { text.contains(it) }) {
            return ZaviTaskType.TELUGU
        }
        if (listOf("code assistant", "coding", "debug", "compile error", "github", "termux", "కోడ్").any { text.contains(it) }) {
            return ZaviTaskType.CODING
        }
        if (listOf("research assistant", "research", "latest", "compare sources", "web search", "పరిశోధన").any { text.contains(it) }) {
            return ZaviTaskType.RESEARCH
        }
        return ZaviTaskType.GENERAL
    }

    fun order(configs: List<ProviderConfig>, task: ZaviTaskType): List<ProviderConfig> =
        configs.sortedByDescending { score(it, task) }

    private fun score(config: ProviderConfig, task: ZaviTaskType): Int {
        val model = config.model.lowercase()
        val providerScore = when (task) {
            ZaviTaskType.IMAGE -> when (itProvider(config)) {
                AiProvider.GEMINI -> 40
                AiProvider.OPENROUTER -> 30
                AiProvider.MISTRAL -> 10
                AiProvider.GROQ -> 0
            }
            ZaviTaskType.CODING -> when (itProvider(config)) {
                AiProvider.GROQ -> 40
                AiProvider.OPENROUTER -> 30
                AiProvider.GEMINI -> 20
                AiProvider.MISTRAL -> 10
            }
            ZaviTaskType.RESEARCH -> when (itProvider(config)) {
                AiProvider.GEMINI -> 40
                AiProvider.OPENROUTER -> 30
                AiProvider.MISTRAL -> 20
                AiProvider.GROQ -> 10
            }
            ZaviTaskType.TELUGU -> when (itProvider(config)) {
                AiProvider.GEMINI -> 40
                AiProvider.MISTRAL -> 30
                AiProvider.GROQ -> 20
                AiProvider.OPENROUTER -> 10
            }
            ZaviTaskType.GENERAL -> when (itProvider(config)) {
                AiProvider.GROQ -> 40
                AiProvider.GEMINI -> 30
                AiProvider.OPENROUTER -> 20
                AiProvider.MISTRAL -> 10
            }
        }
        val modelBonus = when {
            task == ZaviTaskType.CODING && listOf("code", "coder", "deepseek", "qwen").any { model.contains(it) } -> 8
            task == ZaviTaskType.TELUGU && listOf("multilingual", "gemini", "mistral").any { model.contains(it) } -> 5
            task == ZaviTaskType.IMAGE && listOf("vision", "gemini", "4o", "llava").any { model.contains(it) } -> 8
            else -> 0
        }
        return providerScore + modelBonus
    }

    private fun itProvider(config: ProviderConfig) = config.provider
}
