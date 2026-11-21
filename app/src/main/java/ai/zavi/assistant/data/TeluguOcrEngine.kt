package ai.zavi.assistant.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object OcrLanguageConfig {
    const val LANGUAGES = "eng+tel"
    val modelUrls = mapOf(
        "eng" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata",
        "tel" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/tel.traineddata"
    )
}

object TeluguOcrEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun recognize(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val dataRoot = File(context.filesDir, "zavi-tesseract")
        val tessData = File(dataRoot, "tessdata")
        if (!tessData.exists() && !tessData.mkdirs()) {
            throw IllegalStateException("OCR data folder create చేయలేకపోయాను.")
        }
        OcrLanguageConfig.modelUrls.forEach { (language, url) ->
            val model = File(tessData, "$language.traineddata")
            if (!model.exists() || model.length() == 0L) downloadModel(url, model)
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("Photo open చేయలేకపోయాను.")
        val engine = TessBaseAPI()
        try {
            if (!engine.init(dataRoot.absolutePath, OcrLanguageConfig.LANGUAGES)) {
                throw IllegalStateException("Telugu + English OCR engine initialize కాలేదు.")
            }
            engine.setImage(bitmap)
            engine.getUTF8Text()?.trim().orEmpty().ifBlank { "" }
        } finally {
            engine.end()
            bitmap.recycle()
        }
    }

    private fun downloadModel(url: String, destination: File) {
        val temporary = File(destination.parentFile, destination.name + ".download")
        val request = Request.Builder().url(url).header("User-Agent", "ZAVI-AI-Android").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("OCR language data download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("OCR model response empty.")
            FileOutputStream(temporary).use { output -> body.byteStream().use { it.copyTo(output) } }
        }
        if (temporary.length() < 1024L) {
            temporary.delete()
            throw IllegalStateException("OCR language model incomplete download.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }
}
