package ai.zavi.assistant.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchCitationFormatterTest {
    @Test
    fun appendsRetrievedSourceLinks() {
        val result = ResearchCitationFormatter.append(
            "Answer",
            listOf(WebSource("Official page", "https://example.org/info", "Snippet"))
        )
        assertTrue(result.contains("Sources retrieved by ZAVI"))
        assertTrue(result.contains("https://example.org/info"))
    }

    @Test
    fun removesInvalidAndDuplicateUrls() {
        val result = ResearchCitationFormatter.append(
            "Answer",
            listOf(
                WebSource("Unsafe", "javascript:alert(1)", ""),
                WebSource("Valid", "https://example.org", ""),
                WebSource("Duplicate", "https://example.org", "")
            )
        )
        assertEquals(1, Regex("https://example.org").findAll(result).count())
        assertTrue(!result.contains("javascript:"))
    }

    @Test
    fun leavesAnswerAloneWhenNoSourcesWereRetrieved() {
        assertEquals("Answer", ResearchCitationFormatter.append("Answer", emptyList()))
    }
}
