package ai.zavi.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeluguOcrConfigTest {
    @Test
    fun prioritizesTeluguAndKeepsEnglishFallback() {
        assertEquals("tel+eng", OcrLanguageConfig.LANGUAGES)
        assertTrue(OcrLanguageConfig.modelUrls.containsKey("tel"))
        assertTrue(OcrLanguageConfig.modelUrls.containsKey("eng"))
    }

    @Test
    fun languageModelsUseFastTessdataDownloads() {
        assertTrue(OcrLanguageConfig.modelUrls.values.all { it.contains("/tessdata_fast/") })
    }
}
