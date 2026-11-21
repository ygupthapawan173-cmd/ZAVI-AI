package ai.zavi.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
    const val LANGUAGES = "tel+eng"
    val modelUrls = mapOf(
        "eng" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata",
        "tel" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/tel.traineddata"
    )
}

object TeluguOcrEngine {
    private const val OCR_TARGET_MAX_SIDE = 2400
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

        val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("Photo open చేయలేకపోయాను.")
        val processed = try {
            preprocessForOcr(source)
        } finally {
            source.recycle()
        }
        val engine = TessBaseAPI()
        try {
            if (!engine.init(dataRoot.absolutePath, OcrLanguageConfig.LANGUAGES)) {
                throw IllegalStateException("Telugu + English OCR engine initialize కాలేదు.")
            }
            engine.setImage(processed)
            engine.getUTF8Text()?.trim().orEmpty()
        } finally {
            engine.recycle()
            processed.recycle()
        }
    }

    /**
     * OCR-only copy: upscale small text and improve grayscale contrast.
     * The original photo remains untouched for the vision model.
     */
    private fun preprocessForOcr(source: Bitmap): Bitmap {
        val longestSide = maxOf(source.width, source.height)
        val scale = if (longestSide < OCR_TARGET_MAX_SIDE) {
            minOf(2f, OCR_TARGET_MAX_SIDE.toFloat() / longestSide)
        } else 1f
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val contrast = ColorMatrix().apply {
            set(floatArrayOf(
                1.25f, 0f, 0f, 0f, -28f,
                0f, 1.25f, 0f, 0f, -28f,
                0f, 0f, 1.25f, 0f, -28f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val grayscale = ColorMatrix().apply { setSaturation(0f); postConcat(contrast) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
        }
        Canvas(output).drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), paint)
        return output
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
