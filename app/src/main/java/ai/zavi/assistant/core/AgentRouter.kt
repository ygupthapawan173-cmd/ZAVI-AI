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
        "Structure research carefully. Separate established facts, assumptions and uncertainty. Do not claim to have browsed the web or cite sources unless actual source material is supplied."
    ),
    FINANCE(
        "finance",
        "Finance Assistant",
        "Help with budgeting, arithmetic, comparisons and financial education. Explain assumptions. Do not guarantee returns or present uncertain information as personal financial advice."
    ),
    MARKET(
        "market",
        "Market Research Agent",
        "Help analyze customers, competitors, demand, positioning and market opportunities. Do not invent market statistics, survey results or competitor facts."
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
        "Help explain Indian government schemes, subsidies and eligibility at a high level. Do not invent scheme names, benefits, deadlines or eligibility. Clearly say when current official verification is needed."
    )
}

object AgentRouter {
    private fun containsAny(text: String, terms: List<String>): Boolean =
        terms.any { text.contains(it, ignoreCase = true) }

    fun route(query: String): AgentType {
        val q = query.trim().lowercase()

        return when {
            containsAny(q, listOf(
                "government scheme", "govt scheme", "subsidy", "scheme", "loan scheme",
                "పథకం", "సబ్సిడీ", "ప్రభుత్వ పథకం"
            )) -> AgentType.SCHEME

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
