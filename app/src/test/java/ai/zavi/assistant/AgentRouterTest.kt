package ai.zavi.assistant

import ai.zavi.assistant.core.AgentRouter
import ai.zavi.assistant.core.AgentType
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRouterTest {
    @Test
    fun routesCodingRequestsToCodeAgent() {
        assertEquals(AgentType.CODE, AgentRouter.route("Please debug this Kotlin compile error"))
        assertEquals(AgentType.CODE, AgentRouter.route("Python code help"))
    }

    @Test
    fun routesResearchRequestsToResearchAgent() {
        assertEquals(AgentType.RESEARCH, AgentRouter.route("deep dive and compare these options"))
    }

    @Test
    fun routesSchemeRequestsBeforeGeneralResearch() {
        assertEquals(AgentType.SCHEME, AgentRouter.route("latest government scheme subsidy"))
    }
}
