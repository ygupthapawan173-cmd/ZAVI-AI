package ai.zavi.assistant.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ImportedDocument(val name: String, val text: String)

object DocumentTextExtractor {
    private const val MAX_CHARS = 30_000

    fun read(context: Context, uri: Uri): ImportedDocument {
        val name = queryName(context, uri)
        val extension = name.substringAfterLast('.', "").lowercase()
        val text = when (extension) {
            "pdf" -> readPdf(context, uri)
            "docx" -> readDocx(context, uri)
            "txt", "md", "csv", "json", "xml", "html", "htm", "log" -> readPlain(context, uri, extension)
            else -> throw IllegalArgumentException("ఈ file type support లేదు. PDF, DOCX, TXT, MD, CSV, JSON, XML, HTML files ఎంచుకోండి.")
        }.trim()
        require(text.isNotBlank()) { "ఈ documentలో చదవగలిగే text లేదు." }
        return ImportedDocument(name, text.take(MAX_CHARS))
    }

    private fun queryName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "document"
        }
        return uri.lastPathSegment ?: "document"
    }

    private fun readPlain(context: Context, uri: Uri, extension: String): String {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("File open కాలేదు.")
        return if (extension == "html" || extension == "htm") Jsoup.parse(raw).text() else raw
    }

    private fun readPdf(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("PDF open కాలేదు.")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(!document.isEncrypted) { "Password-protected PDF ప్రస్తుతం support లేదు." }
                return PDFTextStripper().getText(document)
            }
        }
    }

    private fun readDocx(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("DOCX open కాలేదు.")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val xml = zip.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val document = Jsoup.parse(xml, "", Parser.xmlParser())
                        return document.getElementsByTag("w:t").joinToString(" ") { it.text() }
                    }
                    zip.closeEntry()
                }
            }
        }
        throw IllegalArgumentException("DOCXలో main document text కనిపించలేదు.")
    }
}
