package ai.zavi.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRouterTest {
    @Test
    fun routesTeluguAndEnglishFinanceQueries() {
        assertEquals(AgentType.FINANCE, AgentRouter.route("నా monthly budget ఎలా plan చేయాలి?"))
        assertEquals(AgentType.FINANCE, AgentRouter.route("calculate my EMI"))
    }

    @Test
    fun routesMarketingAndBrandQueries() {
        assertEquals(AgentType.MARKETING, AgentRouter.route("Instagram promotion ideas"))
        assertEquals(AgentType.BRAND, AgentRouter.route("నా business కి brand name suggest చేయి"))
    }

    @Test
    fun usesGeneralAgentForNormalConversation() {
        assertEquals(AgentType.GENERAL, AgentRouter.route("నమస్కారం, ఎలా ఉన్నావు?"))
    }

    @Test
    fun routesYoutubeVideoSearchAndCreatorRequests() {
        assertEquals(AgentType.VIDEO, AgentRouter.route("Search the web for YouTube videos about Kotlin"))
        assertEquals(AgentType.CREATOR, AgentRouter.route("Create a Telugu YouTube Shorts script"))
    }
}
