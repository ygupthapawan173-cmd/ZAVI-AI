package ai.zavi.assistant.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoModelRouterTest {
    private fun config(provider: AiProvider, model: String = provider.defaultModel) =
        ProviderConfig(provider, "test-key", model, true)

    @Test fun imageTaskPrefersGeminiThenOpenRouter() {
        val configs = listOf(config(AiProvider.OPENROUTER), config(AiProvider.GEMINI), config(AiProvider.GROQ))
        val ordered = AutoModelRouter.order(configs, ZaviTaskType.IMAGE)
        assertEquals(AiProvider.GEMINI, ordered.first().provider)
        assertEquals(AiProvider.OPENROUTER, ordered[1].provider)
    }

    @Test fun codingTaskPrefersGroq() {
        val configs = listOf(config(AiProvider.GEMINI), config(AiProvider.GROQ), config(AiProvider.MISTRAL))
        assertEquals(AiProvider.GROQ, AutoModelRouter.order(configs, ZaviTaskType.CODING).first().provider)
    }

    @Test fun generalTaskPrefersGroq() {
        val configs = listOf(config(AiProvider.GEMINI), config(AiProvider.GROQ))
        assertEquals(AiProvider.GROQ, AutoModelRouter.order(configs, ZaviTaskType.GENERAL).first().provider)
    }

    @Test fun detectsTeluguScript() {
        val messages = listOf(OpenRouterMessage("user", "తెలుగులో సమాధానం చెప్పు"))
        assertEquals(ZaviTaskType.TELUGU, AutoModelRouter.detectTask(messages))
    }
}
