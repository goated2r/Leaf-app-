package com.leaf.reader.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/** Small EPUB 2/3 text reader. Each spine item is a location; pagination within a chapter is deferred. */
object ReaderContent {
    suspend fun chapters(file: File): List<String> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: error("Invalid EPUB container")
            val c = Xml.newPullParser().apply { setInput(zip.getInputStream(container), "UTF-8") }
            var opf = ""
            while (c.eventType != XmlPullParser.END_DOCUMENT) {
                if (c.eventType == XmlPullParser.START_TAG && c.name == "rootfile") opf = c.getAttributeValue(null, "full-path") ?: ""
                c.next()
            }
            val packageEntry = zip.getEntry(opf) ?: error("Missing EPUB package")
            val base = opf.substringBeforeLast('/', "")
            val p = Xml.newPullParser().apply { setInput(zip.getInputStream(packageEntry), "UTF-8") }
            val hrefs = mutableMapOf<String, String>(); val spine = mutableListOf<String>()
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                    "item" -> {
                        val id = p.getAttributeValue(null, "id")
                        val href = p.getAttributeValue(null, "href")
                        if (id != null && href != null) hrefs[id] = href
                    }
                    "itemref" -> p.getAttributeValue(null, "idref")?.let(spine::add)
                }
                p.next()
            }
            spine.mapNotNull { id ->
                val href = hrefs[id] ?: return@mapNotNull null
                val path = if (base.isEmpty()) href else "$base/$href"
                val normalized = java.net.URI(null, null, "/$path", null).normalize().path.removePrefix("/")
                val entry = zip.getEntry(normalized) ?: return@mapNotNull null
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Html.fromHtml(html.substringAfter("<body", html).substringAfter('>', html).substringBeforeLast("</body>", html), Html.FROM_HTML_MODE_COMPACT).toString().trim()
            }.filter { it.isNotBlank() }.ifEmpty { error("No readable chapters in this EPUB") }
        }
    }

    suspend fun pdfPage(file: File, index: Int, width: Int = 1200): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val page = renderer.openPage(index.coerceIn(0, renderer.pageCount - 1))
                page.use {
                    val height = (width.toFloat() * it.height / it.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap to renderer.pageCount
                }
            }
        }
    }
}
