package ai.zavi.assistant.network

object ResearchCitationFormatter {
    fun append(answer: String, sources: List<WebSource>): String {
        val valid = sources.filter {
            it.title.isNotBlank() && (it.url.startsWith("https://") || it.url.startsWith("http://"))
        }.distinctBy { it.url }.take(8)
        if (valid.isEmpty()) return answer
        val references = valid.mapIndexed { index, source ->
            "[${index + 1}] ${source.title}\n${source.url}"
        }.joinToString("\n\n")
        return answer.trimEnd() + "\n\nSources retrieved by ZAVI:\n" + references
    }
}
