package ai.zavi.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLanguageConfigTest {
    @Test
    fun enablesTeluguAndEnglishTogether() {
        assertEquals("tel+eng", OcrLanguageConfig.LANGUAGES)
        assertTrue(OcrLanguageConfig.modelUrls.containsKey("eng"))
        assertTrue(OcrLanguageConfig.modelUrls.containsKey("tel"))
    }

    @Test
    fun usesRawTesseractLanguageModels() {
        assertTrue(OcrLanguageConfig.modelUrls.values.all {
            it.startsWith("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/")
        })
    }
}
