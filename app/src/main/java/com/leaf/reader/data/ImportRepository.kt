package com.leaf.reader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

class ImportRepository(private val context: Context, private val dao: LeafDao) {
    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Untitled"
        val folder = File(context.filesDir, "books").apply { mkdirs() }
        val temp = File(folder, "${UUID.randomUUID()}.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: error("Cannot read the selected file")
            val signature = temp.inputStream().use { ByteArray(5).also { bytes -> it.read(bytes) } }
            val format = when {
                signature.take(4).toByteArray().contentEquals("%PDF".toByteArray()) -> "pdf"
                signature.take(2).toByteArray().contentEquals(byteArrayOf(0x50, 0x4b)) && isEpub(temp) -> "epub"
                name.endsWith(".acsm", true) -> error("This edition is DRM protected and cannot currently be imported into Leaf.")
                else -> error("Choose a valid EPUB or PDF file")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            dao.byHash(hash)?.let { return@withContext it }
            val metadata = if (format == "epub") epubMetadata(temp) else null
            val id = UUID.randomUUID().toString()
            val dest = File(folder, "$id.$format")
            if (!temp.renameTo(dest)) error("Could not store the book")
            val book = Book(id, metadata?.first?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'), metadata?.second ?: "Unknown author", format, dest.absolutePath, hash)
            try { dao.insert(book) } catch (e: Exception) { dest.delete(); throw e }
            book
        } finally { temp.delete() }
    }

    private fun isEpub(file: File): Boolean = ZipFile(file).use { zip ->
        zip.getEntry("mimetype")?.let { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText().trim() == "application/epub+zip" } } == true
    }

    private fun epubMetadata(file: File): Pair<String, String>? = ZipFile(file).use { zip ->
        val container = zip.getEntry("META-INF/container.xml") ?: return@use null
        val parser = Xml.newPullParser().apply { setInput(zip.getInputStream(container), "UTF-8") }
        var opf: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") opf = parser.getAttributeValue(null, "full-path")
            parser.next()
        }
        val entry = opf?.let(zip::getEntry) ?: return@use null
        val p = Xml.newPullParser().apply { setInput(zip.getInputStream(entry), "UTF-8") }
        var title = ""; var author = "Unknown author"
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "title" -> title = p.nextText()
                "creator" -> author = p.nextText()
            }
            p.next()
        }
        title to author
    }
}
