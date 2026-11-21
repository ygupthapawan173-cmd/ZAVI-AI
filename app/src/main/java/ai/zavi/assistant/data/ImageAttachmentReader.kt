package ai.zavi.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

data class ImportedImage(
    val name: String,
    val mimeType: String,
    val base64: String
) {
    fun toPromptBlock(): String = "[[ZAVI_IMAGE:$mimeType:$base64]]"
}

object ImageAttachmentReader {
    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 82
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun read(context: Context, uri: Uri): ImportedImage {
        val mime = context.contentResolver.getType(uri)
            ?: throw IllegalArgumentException("Image format గుర్తించలేకపోయాను.")
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "JPG, PNG లేదా WEBP image మాత్రమే ఎంచుకోండి."
        }
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "image"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Image open కాలేదు.")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("ఈ image decode కాలేదు.")
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true
        ) else bitmap
        val output = ByteArrayOutputStream()
        var quality = JPEG_QUALITY
        resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
        while (output.size() > MAX_BYTES && quality > 50) {
            output.reset()
            quality -= 8
            resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        require(output.size() <= MAX_BYTES) { "Image size చాలా పెద్దది. చిన్న image ఎంచుకోండి." }
        return ImportedImage(name, "image/jpeg", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
    }
}
