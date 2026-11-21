package ai.zavi.assistant.core

enum class AgentType(
    val id: String,
    val title: String,
    val instruction: String
) {
    GENERAL(
        "general",
        "General Assistant",
        "Help with everyday questions, explanations, planning, learning and problem solving. Be practical and clear."
    ),
    RESEARCH(
        "research",
        "Research Assistant",
        "Perform evidence-led deep research. Use live web results when available, cite exact supplied URLs, compare sources, distinguish facts from inference, and list unanswered questions. Never invent sources."
    ),
    CODE(
        "code",
        "Code Assistant",
        "Act as a senior software engineer. First understand the existing stack and constraints. Provide runnable code, explain file paths and commands, diagnose errors from their exact logs, preserve existing behavior, and include tests. Never claim code was executed unless it was."
    ),
    FINANCE(
        "finance",
        "Finance Assistant",
        "Help with budgeting, arithmetic, comparisons and financial education. Explain assumptions. Do not guarantee returns or present uncertain information as personal financial advice."
    ),
    MARKET(
        "market",
        "Market Research Agent",
        "Analyze customers, competitors, demand, positioning and market opportunities using retrieved sources when available. Label estimates and do not invent market statistics."
    ),
    BRAND(
        "brand",
        "Brand Agent",
        "Help with brand identity, naming, positioning, taglines, messaging and creative direction. Offer concrete alternatives when useful."
    ),
    MARKETING(
        "marketing",
        "Marketing Agent",
        "Help create ethical marketing plans, audience strategies, campaigns, content calendars and measurable experiments. Avoid fabricated performance claims."
    ),
    SCHEME(
        "scheme",
        "Schemes Assistant",
        "Help explain Indian government schemes, subsidies and eligibility. Prefer official sources for current details. Do not invent scheme names, benefits, deadlines or eligibility."
    ),
    VIDEO(
        "video",
        "Video Research Agent",
        "Find and compare relevant YouTube/video resources using retrieved web results. Give exact source URLs, explain why each result is useful, and never invent video titles, channels, links, views or upload dates."
    ),
    CREATOR(
        "creator",
        "Creator Studio Agent",
        "Create original Telugu-English creator assets: YouTube Shorts scripts, hooks, shot lists, captions, hashtags, thumbnail concepts and posting plans. Do not claim to publish or upload content. Keep facts verifiable and label creative suggestions."
    )
}

object AgentRouter {
    private fun containsAny(text: String, terms: List<String>): Boolean =
        terms.any { text.contains(it, ignoreCase = true) }

    fun route(query: String): AgentType {
        val q = query.trim().lowercase()

        return when {
            containsAny(q, listOf("shorts script", "reel script", "creator studio", "thumbnail idea", "hashtags", "create a reel", "make a reel", "create a short", "youtube shorts", "write a caption", "షార్ట్స్ స్క్రిప్ట్", "రీల్ తయారు చేయి")) -> AgentType.CREATOR

            containsAny(q, listOf("youtube", "video search", "find videos", "youtube videos", "యూట్యూబ్ వీడియోలు")) -> AgentType.VIDEO

            containsAny(q, listOf(
                "government scheme", "govt scheme", "subsidy", "scheme", "loan scheme",
                "పథకం", "సబ్సిడీ", "ప్రభుత్వ పథకం"
            )) -> AgentType.SCHEME

            containsAny(q, listOf(
                "fix this code", "debug", "compile error", "gradle", "kotlin", "python",
                "javascript", "typescript", "programming", "write code", "code review",
                "github", "api integration", "termux", "claude code", "build failed", "fix my code", "కోడ్", "ఎర్రర్ సరిచేయి"
            )) -> AgentType.CODE

            containsAny(q, listOf(
                "brand", "brand name", "tagline", "logo idea", "branding",
                "బ్రాండ్", "ట్యాగ్‌లైన్"
            )) -> AgentType.BRAND

            containsAny(q, listOf(
                "marketing", "advertising", "promotion", "campaign", "social media strategy",
                "మార్కెటింగ్", "ప్రచారం"
            )) -> AgentType.MARKETING

            containsAny(q, listOf(
                "market research", "competitor", "competitors", "market size", "customer demand",
                "industry trend", "మార్కెట్ రీసెర్చ్", "పోటీదారులు"
            )) -> AgentType.MARKET

            containsAny(q, listOf(
                "finance", "budget", "profit", "loss", "investment", "tax", "interest rate",
                "emi", "expense", "ఖర్చు", "బడ్జెట్", "లాభం", "నష్టం"
            )) -> AgentType.FINANCE

            containsAny(q, listOf(
                "research", "research on", "compare", "comparison", "latest information",
                "sources for", "deep dive", "విశ్లేషించు", "పరిశోధన", "పోల్చు"
            )) -> AgentType.RESEARCH

            else -> AgentType.GENERAL
        }
    }
}
